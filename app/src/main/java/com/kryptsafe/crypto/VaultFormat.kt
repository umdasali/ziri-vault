package com.kryptsafe.crypto

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.nio.ByteBuffer
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

enum class MediaKind { IMAGE, VIDEO, DOCUMENT, OTHER }

/** Original name/type are stored *inside* the encrypted file, so the filename on disk reveals nothing. */
data class FileMeta(val name: String, val mime: String, val kind: MediaKind, val importedAt: Long)

class TamperedFileException(msg: String, cause: Throwable? = null) : Exception(msg, cause)

/**
 * On-disk layout of a .ksf file:
 *
 *   magic "KSF1" | chunkSize:int | fileSalt[16] | metaLen:int | metaCiphertext
 *   then N chunks, each AES-256-GCM: ciphertext(<=chunkSize) + 16-byte tag
 *
 * - Per-file key = HMAC-SHA256(masterKey, fileSalt)  -> a nonce can never repeat under one key.
 * - Chunk nonce = chunk index (12-byte big-endian), so any chunk can be decrypted on its own
 *   (needed for video seeking) without ever writing plaintext to disk.
 * - AAD = header bytes + "last chunk" flag -> reordering, header edits and truncation fail auth.
 */
object VaultFormat {
    private val MAGIC = byteArrayOf('K'.code.toByte(), 'S'.code.toByte(), 'F'.code.toByte(), '1'.code.toByte())
    const val CHUNK_SIZE = 64 * 1024
    const val TAG_LEN = 16
    private const val SALT_LEN = 16
    private const val MAX_META = 64 * 1024
    private val rng = SecureRandom()

    internal fun fileKey(masterKey: ByteArray, salt: ByteArray): SecretKeySpec {
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(masterKey, "HmacSHA256")) }
        return SecretKeySpec(mac.doFinal(salt), "AES")
    }

    internal fun nonce(index: Long, meta: Boolean = false): ByteArray =
        ByteBuffer.allocate(12).putInt(if (meta) -1 else 0).putLong(index).array()

    internal fun aad(header: ByteArray, last: Boolean): ByteArray = header + byteArrayOf(if (last) 1 else 0)

    private fun cipher(mode: Int, key: SecretKeySpec, nonce: ByteArray, aad: ByteArray) =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, key, GCMParameterSpec(TAG_LEN * 8, nonce))
            updateAAD(aad)
        }

    fun encrypt(input: InputStream, output: OutputStream, meta: FileMeta, masterKey: ByteArray) {
        val salt = ByteArray(SALT_LEN).also(rng::nextBytes)
        val key = fileKey(masterKey, salt)

        val header = java.io.ByteArrayOutputStream().also {
            DataOutputStream(it).apply { write(MAGIC); writeInt(CHUNK_SIZE); write(salt) }
        }.toByteArray()

        val metaPlain = java.io.ByteArrayOutputStream().also {
            DataOutputStream(it).apply {
                writeUTF(meta.name); writeUTF(meta.mime); writeInt(meta.kind.ordinal); writeLong(meta.importedAt)
            }
        }.toByteArray()
        val metaCt = cipher(Cipher.ENCRYPT_MODE, key, nonce(0, meta = true), header).doFinal(metaPlain)

        val out = DataOutputStream(output)
        out.write(header); out.writeInt(metaCt.size); out.write(metaCt)

        // Read one chunk ahead so we know which chunk is the last one.
        var cur = readChunk(input)
        var index = 0L
        while (true) {
            val next = if (cur.size == CHUNK_SIZE) readChunk(input) else ByteArray(0)
            val last = next.isEmpty()
            out.write(cipher(Cipher.ENCRYPT_MODE, key, nonce(index), aad(header, last)).doFinal(cur))
            if (last) break
            cur = next; index++
        }
        out.flush()
    }

    private fun readChunk(input: InputStream): ByteArray {
        val buf = ByteArray(CHUNK_SIZE)
        var n = 0
        while (n < CHUNK_SIZE) {
            val r = input.read(buf, n, CHUNK_SIZE - n)
            if (r < 0) break
            n += r
        }
        return if (n == CHUNK_SIZE) buf else buf.copyOf(n)
    }

    /** Random-access reader over an encrypted file. Keeps only one decrypted chunk in memory. */
    class Reader(file: File, masterKey: ByteArray) : java.io.Closeable {
        private val raf = RandomAccessFile(file, "r")
        private val header: ByteArray
        private val key: SecretKeySpec
        private val chunkSize: Int
        private val dataOffset: Long
        val meta: FileMeta
        val size: Long

        private var cachedIndex = -1L
        private var cached = ByteArray(0)

        init {
            try {
                val din = DataInputStream(java.io.BufferedInputStream(java.io.FileInputStream(raf.fd)))
                val magic = ByteArray(4); din.readFully(magic)
                if (!magic.contentEquals(MAGIC)) throw TamperedFileException("Not a KryptSafe file")
                chunkSize = din.readInt()
                if (chunkSize !in 1..(1 shl 24)) throw TamperedFileException("Bad header")
                val salt = ByteArray(SALT_LEN); din.readFully(salt)
                header = java.io.ByteArrayOutputStream().also {
                    DataOutputStream(it).apply { write(MAGIC); writeInt(chunkSize); write(salt) }
                }.toByteArray()
                val metaLen = din.readInt()
                if (metaLen !in TAG_LEN..MAX_META) throw TamperedFileException("Bad header")
                val metaCt = ByteArray(metaLen); din.readFully(metaCt)
                key = fileKey(masterKey, salt)
                val metaPlain = try {
                    cipher(Cipher.DECRYPT_MODE, key, nonce(0, meta = true), header).doFinal(metaCt)
                } catch (e: GeneralSecurityException) { throw TamperedFileException("Metadata failed authentication", e) }
                DataInputStream(metaPlain.inputStream()).apply {
                    meta = FileMeta(readUTF(), readUTF(), MediaKind.entries[readInt()], readLong())
                }
                dataOffset = 4L + 4 + SALT_LEN + 4 + metaLen
                val body = raf.length() - dataOffset
                val stride = chunkSize.toLong() + TAG_LEN
                if (body < TAG_LEN) throw TamperedFileException("Truncated file")
                val chunks = (body + stride - 1) / stride
                val lastCt = body - (chunks - 1) * stride
                if (lastCt < TAG_LEN) throw TamperedFileException("Truncated file")
                size = (chunks - 1) * chunkSize + (lastCt - TAG_LEN)
            } catch (e: EOFException) {
                raf.close(); throw TamperedFileException("Truncated file", e)
            } catch (e: Throwable) { raf.close(); throw e }
        }

        private val chunkCount get() = if (size == 0L) 1 else (size + chunkSize - 1) / chunkSize

        private fun load(index: Long) {
            if (index == cachedIndex) return
            val stride = chunkSize.toLong() + TAG_LEN
            val start = dataOffset + index * stride
            val last = index == chunkCount - 1
            val ctLen = if (last) (raf.length() - start).toInt() else stride.toInt()
            val ct = ByteArray(ctLen)
            raf.seek(start); raf.readFully(ct)
            cached = try {
                cipher(Cipher.DECRYPT_MODE, key, nonce(index), aad(header, last)).doFinal(ct)
            } catch (e: GeneralSecurityException) {
                throw TamperedFileException("Chunk $index failed authentication", e)
            }
            cachedIndex = index
        }

        /** Reads up to [len] bytes at plaintext [position]; returns -1 at end of file. */
        fun readAt(position: Long, buf: ByteArray, off: Int, len: Int): Int {
            if (position >= size) return -1
            var done = 0
            var pos = position
            while (done < len && pos < size) {
                load(pos / chunkSize)
                val inChunk = (pos % chunkSize).toInt()
                val n = minOf(len - done, cached.size - inChunk)
                System.arraycopy(cached, inChunk, buf, off + done, n)
                done += n; pos += n
            }
            return done
        }

        fun openStream(): InputStream = object : InputStream() {
            var pos = 0L
            override fun read(): Int { val b = ByteArray(1); return if (read(b, 0, 1) < 0) -1 else b[0].toInt() and 0xFF }
            override fun read(b: ByteArray, off: Int, len: Int): Int =
                readAt(pos, b, off, len).also { if (it > 0) pos += it }
        }

        override fun close() { cached.fill(0); raf.close() }
    }
}
