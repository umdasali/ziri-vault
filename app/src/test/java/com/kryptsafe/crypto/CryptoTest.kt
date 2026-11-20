package com.kryptsafe.crypto

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.SecureRandom

class CryptoTest {
    private val meta = FileMeta("holiday.jpg", "image/jpeg", MediaKind.IMAGE, 1234L)
    private val key = ByteArray(32).also(SecureRandom()::nextBytes)

    private fun enc(data: ByteArray): File {
        val f = File.createTempFile("ksf", ".bin").apply { deleteOnExit() }
        f.outputStream().use { VaultFormat.encrypt(data.inputStream(), it, meta, key) }
        return f
    }

    private fun roundTrip(n: Int) {
        val data = ByteArray(n).also(SecureRandom()::nextBytes)
        val f = enc(data)
        VaultFormat.Reader(f, key).use { r ->
            assertEquals(meta, r.meta)
            assertEquals(n.toLong(), r.size)
            assertArrayEquals(data, r.openStream().readBytes())
        }
    }

    @Test fun roundTripSizes() {
        val c = VaultFormat.CHUNK_SIZE
        listOf(0, 1, 100, c - 1, c, c + 1, 3 * c, 3 * c + 7).forEach(::roundTrip)
    }

    @Test fun randomAccessSeek() {
        val c = VaultFormat.CHUNK_SIZE
        val data = ByteArray(5 * c + 99).also(SecureRandom()::nextBytes)
        VaultFormat.Reader(enc(data), key).use { r ->
            val pos = 2L * c - 10
            val buf = ByteArray(50)
            assertEquals(50, r.readAt(pos, buf, 0, 50))
            assertArrayEquals(data.copyOfRange(pos.toInt(), pos.toInt() + 50), buf)
            assertEquals(-1, r.readAt(data.size.toLong(), buf, 0, 1))
        }
    }

    @Test fun ciphertextHidesPlaintextAndName() {
        val data = "SECRET-FAMILY-PHOTO".repeat(100).toByteArray()
        val raw = enc(data).readBytes()
        assertFalse(String(raw, Charsets.ISO_8859_1).contains("SECRET-FAMILY"))
        assertFalse(String(raw, Charsets.ISO_8859_1).contains("holiday"))
    }

    @Test fun wrongKeyRejected() {
        val f = enc(ByteArray(1000))
        assertThrows(TamperedFileException::class.java) { VaultFormat.Reader(f, ByteArray(32)) }
    }

    @Test fun bitFlipDetected() {
        val c = VaultFormat.CHUNK_SIZE
        val f = enc(ByteArray(3 * c))
        val b = f.readBytes(); b[b.size - 5] = (b[b.size - 5].toInt() xor 1).toByte(); f.writeBytes(b)
        VaultFormat.Reader(f, key).use { r ->
            assertThrows(TamperedFileException::class.java) { r.openStream().readBytes() }
        }
    }

    @Test fun truncationDetected() {
        val c = VaultFormat.CHUNK_SIZE
        val f = enc(ByteArray(3 * c))
        val stride = c + VaultFormat.TAG_LEN
        f.writeBytes(f.readBytes().let { it.copyOf(it.size - stride) }) // drop last whole chunk
        VaultFormat.Reader(f, key).use { r ->
            assertThrows(TamperedFileException::class.java) { r.openStream().readBytes() }
        }
    }

    @Test fun pinFlow() {
        installTestArgon()
        val created = KeyManager.create("1234".toCharArray())
        assertArrayEquals(created.dek, KeyManager.unlock("1234".toCharArray(), created.bundle))
        assertThrows(WrongPinException::class.java) { KeyManager.unlock("0000".toCharArray(), created.bundle) }
        val nb = KeyManager.changePin("1234".toCharArray(), "9999".toCharArray(), created.bundle)
        assertArrayEquals(created.dek, KeyManager.unlock("9999".toCharArray(), nb))
        assertThrows(WrongPinException::class.java) { KeyManager.unlock("1234".toCharArray(), nb) }
    }
}
