package com.kryptsafe.vault

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.kryptsafe.crypto.VaultFormat
import java.io.File
import java.io.IOException

/**
 * Lets ExoPlayer play an encrypted vault file directly. Chunks are decrypted on demand in memory and handed
 * straight to the decoder: no plaintext temp file, no cache, and seeking works because every chunk is independent.
 * URIs look like `ksf://<itemId>`.
 */
@UnstableApi
class EncryptedFileDataSource(private val itemFile: (String) -> File) : BaseDataSource(/* isNetwork = */ false) {
    private var reader: VaultFormat.Reader? = null
    private var uri: Uri? = null
    private var position = 0L
    private var remaining = 0L

    override fun open(dataSpec: DataSpec): Long {
        val key = Session.masterKey ?: throw IOException("Vault is locked")
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        val id = dataSpec.uri.host ?: throw IOException("Bad vault URI")
        val r = try { VaultFormat.Reader(itemFile(id), key) } catch (e: Exception) { throw IOException(e) }
        reader = r
        position = dataSpec.position
        if (position > r.size) throw IOException("Position beyond end")
        remaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.length else r.size - position
        transferStarted(dataSpec)
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val r = reader ?: throw IOException("Not open")
        val n = try {
            r.readAt(position, buffer, offset, minOf(length.toLong(), remaining).toInt())
        } catch (e: Exception) { throw IOException(e) }
        if (n < 0) return C.RESULT_END_OF_INPUT
        position += n; remaining -= n
        bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        try { reader?.close() } finally {
            reader = null
            if (uri != null) { uri = null; transferEnded() }
        }
    }

    companion object {
        fun factory(itemFile: (String) -> File) = DataSource.Factory { EncryptedFileDataSource(itemFile) }
    }
}
