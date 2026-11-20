package com.kryptsafe.vault

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import com.kryptsafe.crypto.FileMeta
import com.kryptsafe.crypto.MediaKind
import com.kryptsafe.crypto.VaultFormat
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

data class VaultItem(val id: String, val meta: FileMeta, val size: Long)

/**
 * Encrypted files live in app-private storage under random UUID names; the original name,
 * type and thumbnail are all stored inside encrypted data. Nothing is written to the gallery.
 */
class VaultRepository(private val context: Context) {
    private val itemsDir get() = File(context.filesDir, "vault/items").apply { mkdirs() }
    private val thumbsDir get() = File(context.filesDir, "vault/thumbs").apply { mkdirs() }

    private fun key() = Session.masterKey ?: error("Vault is locked")
    private fun itemFile(id: String) = File(itemsDir, "$id.ksf")
    private fun thumbFile(id: String) = File(thumbsDir, "$id.ksf")

    fun list(): List<VaultItem> = (itemsDir.listFiles { f -> f.extension == "ksf" } ?: emptyArray())
        .mapNotNull { f ->
            runCatching { VaultFormat.Reader(f, key()).use { VaultItem(f.nameWithoutExtension, it.meta, it.size) } }.getOrNull()
        }
        .sortedByDescending { it.meta.importedAt }

    fun openReader(id: String) = VaultFormat.Reader(itemFile(id), key())
    fun itemFileFor(id: String): File = itemFile(id)

    /** Encrypts [uri] into the vault. Returns the new item. Throws on failure, leaving nothing behind. */
    fun import(uri: Uri): VaultItem {
        val (name, mime) = describe(uri)
        val kind = kindOf(mime, name)
        val meta = FileMeta(name, mime, kind, System.currentTimeMillis())
        val id = UUID.randomUUID().toString()
        val tmp = File(itemsDir, "$id.tmp")
        try {
            val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open $name")
            input.use { inp -> tmp.outputStream().buffered().use { VaultFormat.encrypt(inp, it, meta, key()) } }
            if (!tmp.renameTo(itemFile(id))) error("Cannot store $name")
            itemFile(id).setLastModified(0) // don't leak when each file was added
        } catch (e: Throwable) {
            tmp.delete(); itemFile(id).delete(); throw e
        }
        runCatching { makeThumb(uri, kind)?.let { writeThumb(id, it, name) } }
        val size = itemFile(id).let { f -> VaultFormat.Reader(f, key()).use { it.size } }
        return VaultItem(id, meta, size)
    }

    fun delete(id: String) {
        // Overwrite before unlinking so ciphertext isn't trivially recoverable from freed blocks.
        listOf(itemFile(id), thumbFile(id)).forEach { f ->
            if (f.exists()) runCatching { java.io.RandomAccessFile(f, "rw").use { r -> r.write(ByteArray(r.length().coerceAtMost(1 shl 20).toInt())) } }
            f.delete()
        }
        ThumbCache.remove(id)
    }

    fun exportTo(id: String, out: OutputStream) {
        openReader(id).use { r -> r.openStream().use { it.copyTo(out, 64 * 1024) } }
    }

    fun thumbnail(id: String): Bitmap? {
        ThumbCache.get(id)?.let { return it }
        val f = thumbFile(id)
        if (!f.exists()) return null
        return runCatching {
            VaultFormat.Reader(f, key()).use { r ->
                BitmapFactory.decodeStream(r.openStream())
            }
        }.getOrNull()?.also { ThumbCache.put(id, it) }
    }

    private fun writeThumb(id: String, bmp: Bitmap, name: String) {
        val jpeg = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 82, it) }.toByteArray()
        thumbFile(id).outputStream().buffered().use {
            VaultFormat.encrypt(jpeg.inputStream(), it, FileMeta("thumb", "image/jpeg", MediaKind.IMAGE, 0), key())
        }
        thumbFile(id).setLastModified(0)
    }

    private fun describe(uri: Uri): Pair<String, String> {
        var name = "file"
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0)?.let { n -> name = n }
        }
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        return name to mime
    }

    private fun kindOf(mime: String, name: String) = when {
        mime.startsWith("image/") -> MediaKind.IMAGE
        mime.startsWith("video/") -> MediaKind.VIDEO
        mime == "application/pdf" || name.endsWith(".pdf", true) || mime.startsWith("text/") -> MediaKind.DOCUMENT
        else -> MediaKind.OTHER
    }

    private fun makeThumb(uri: Uri, kind: MediaKind): Bitmap? = when (kind) {
        MediaKind.IMAGE -> decodeImage(uri, 384)
        MediaKind.VIDEO -> MediaMetadataRetriever().run {
            try {
                setDataSource(context, uri)
                getFrameAtTime(0)?.let { scaleDown(it, 384) }
            } finally { release() }
        }
        else -> null
    }

    private fun decodeImage(uri: Uri, max: Int): Bitmap? {
        val cr = context.contentResolver
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        var sample = 1
        while (opts.outWidth / (sample * 2) >= max && opts.outHeight / (sample * 2) >= max) sample *= 2
        val bmp = cr.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val rotation = cr.openInputStream(uri)?.use { exifRotation(it) } ?: 0
        return scaleDown(rotate(bmp, rotation), max)
    }

    companion object {
        fun exifRotation(input: InputStream): Int = runCatching {
            when (ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        }.getOrDefault(0)

        fun rotate(b: Bitmap, deg: Int): Bitmap =
            if (deg == 0) b else Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(deg.toFloat()) }, true)

        fun scaleDown(b: Bitmap, max: Int): Bitmap {
            val s = max.toFloat() / maxOf(b.width, b.height)
            return if (s >= 1f) b else Bitmap.createScaledBitmap(b, (b.width * s).toInt().coerceAtLeast(1), (b.height * s).toInt().coerceAtLeast(1), true)
        }
    }
}

/** Decrypted thumbnails are kept in RAM only (never on disk) and dropped when the vault locks. */
object ThumbCache {
    private val cache = object : android.util.LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    fun get(id: String): Bitmap? = cache.get(id)
    fun put(id: String, b: Bitmap) { cache.put(id, b) }
    fun remove(id: String) { cache.remove(id) }
    fun clear() = cache.evictAll()
}
