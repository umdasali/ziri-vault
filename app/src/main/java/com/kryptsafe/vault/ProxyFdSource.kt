package com.kryptsafe.vault

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.os.ProxyFileDescriptorCallback
import android.os.storage.StorageManager
import android.system.ErrnoException
import android.system.OsConstants
import com.kryptsafe.crypto.VaultFormat
import java.io.Closeable

/**
 * Gives PdfRenderer the *seekable file descriptor* it insists on, without ever creating a plaintext file:
 * the OS asks us for byte ranges (onRead) and we decrypt exactly those bytes from the vault file in memory.
 */
class ProxyFdSource(context: Context, private val reader: VaultFormat.Reader) : Closeable {
    private val thread = HandlerThread("ksf-pdf-io").apply { start() }
    val descriptor: ParcelFileDescriptor

    init {
        val sm = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        descriptor = sm.openProxyFileDescriptor(ParcelFileDescriptor.MODE_READ_ONLY, object : ProxyFileDescriptorCallback() {
            override fun onGetSize(): Long = reader.size
            override fun onRead(offset: Long, size: Int, data: ByteArray): Int = try {
                val n = reader.readAt(offset, data, 0, size)
                if (n < 0) 0 else n
            } catch (e: Exception) { throw ErrnoException("onRead", OsConstants.EIO) }
            override fun onRelease() {}
        }, Handler(thread.looper))
    }

    override fun close() {
        runCatching { descriptor.close() }
        reader.close()
        thread.quitSafely()
    }
}
