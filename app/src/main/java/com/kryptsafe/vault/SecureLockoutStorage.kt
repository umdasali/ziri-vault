package com.kryptsafe.vault

import com.kryptsafe.crypto.LockoutCodec
import com.kryptsafe.crypto.LockoutStorage
import java.io.File

/**
 * Lockout counters stored encrypted + authenticated with the device key (not as editable plain preferences).
 * Editing or forging the file makes decryption fail; we then assume the worst (as if 9 wrong PINs had just been
 * entered) instead of trusting it. Limit: without a hardware monotonic counter nothing can stop someone *restoring
 * an older valid copy* of the file — this defends against edits and forgeries, not rollbacks.
 */
class SecureLockoutStorage(
    private val file: File,
    private val elapsedNow: () -> Long,
    private val bootId: () -> Long,
) : LockoutStorage {
    private var cache: MutableMap<String, Long>? = null

    @Synchronized
    private fun map(): MutableMap<String, Long> {
        cache?.let { return it }
        val loaded: MutableMap<String, Long> =
            if (!file.exists()) mutableMapOf()
            else runCatching { LockoutCodec.decode(DeviceKey.unwrap(file.readBytes())) }.getOrNull()?.toMutableMap() ?: tampered()
        cache = loaded
        return loaded
    }

    private fun tampered(): MutableMap<String, Long> {
        val delay = 30_000L shl 4
        return mutableMapOf("fails" to 9L, "delay_ms" to delay, "until_elapsed" to elapsedNow() + delay, "boot" to bootId()).also(::persist)
    }

    private fun persist(m: Map<String, Long>) {
        val wrapped = DeviceKey.wrap(LockoutCodec.encode(m))
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeBytes(wrapped)
        if (!tmp.renameTo(file)) { file.writeBytes(wrapped); tmp.delete() }
        file.setLastModified(0)
    }

    @Synchronized override fun getLong(key: String, default: Long) = map()[key] ?: default
    @Synchronized override fun putAll(vararg pairs: Pair<String, Long>) { val m = map(); pairs.forEach { m[it.first] = it.second }; persist(m) }
}
