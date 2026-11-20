package com.kryptsafe.vault

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.kryptsafe.crypto.CredentialKind
import com.kryptsafe.crypto.KeyManager
import com.kryptsafe.crypto.LockoutPolicy
import com.kryptsafe.crypto.WrongPinException
import com.kryptsafe.security.BiometricHelper
import java.io.File

/** In-memory session. The master key exists only while the vault is unlocked. */
object Session {
    @Volatile var masterKey: ByteArray? = null
        private set
    val isUnlocked get() = masterKey != null
    fun open(key: ByteArray) { masterKey = key }
    fun lock() { masterKey?.fill(0); masterKey = null; ThumbCache.clear() }
}

class LockedOutException(val remainingMs: Long) : Exception("Too many attempts")
/** Thrown after the opt-in "erase after N wrong attempts" limit is hit. By then everything is already destroyed. */
class VaultWipedException : Exception("Vault erased after too many wrong attempts")

class VaultStore(private val context: Context) {
    private val dir = File(context.filesDir, "vault").apply { mkdirs() }
    private val keyFile = File(dir, "keys.bin")
    private val bioFile = File(dir, "bio.bin")
    private val settings = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val elapsed = { SystemClock.elapsedRealtime() }
    private val boot = { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0).toLong() }
    private val lockout = LockoutPolicy(SecureLockoutStorage(File(dir, "lock.bin"), elapsed, boot), elapsed, boot)

    /** 0 = lock as soon as the app leaves the foreground (default, most secure). */
    var autoLockMs: Long
        get() = settings.getLong("autolock_ms", 0L)
        set(v) { settings.edit().putLong("autolock_ms", v).apply() }

    /** What the user unlocks with. Not secret (it only decides which keypad/field to show). */
    var credentialKind: CredentialKind
        get() = CredentialKind.fromId(settings.getInt("cred_kind", CredentialKind.PIN6.id))
        private set(v) { settings.edit().putInt("cred_kind", v.id).apply() }

    /** 0 = never. Otherwise the vault is destroyed after this many wrong attempts in a row. Opt-in, off by default. */
    var wipeAfter: Int
        get() = settings.getInt("wipe_after", 0)
        set(v) { settings.edit().putInt("wipe_after", v).apply() }

    val isSetUp get() = keyFile.exists()
    val biometricEnabled get() = bioFile.exists()

    fun setUp(secret: CharArray, kind: CredentialKind) {
        val c = KeyManager.create(secret)
        writeBundle(c.bundle)
        credentialKind = kind
        clearFailures()
        Session.open(c.dek)
    }

    private fun checkNotLockedOut() {
        val wait = lockout.remainingMs()
        if (wait > 0) throw LockedOutException(wait)
    }
    private fun clearFailures() = lockout.clear()

    /** Records a wrong attempt. If the opt-in erase limit is reached, destroys the vault and throws instead. */
    private fun failure(e: WrongPinException): Nothing {
        lockout.recordFailure()
        if (wipeAfter > 0 && lockout.failures() >= wipeAfter) { wipeAll(); throw VaultWipedException() }
        throw e
    }

    /** How many more wrong attempts until the vault is erased, or null if that option is off. */
    fun attemptsLeftBeforeWipe(): Int? = wipeAfter.takeIf { it > 0 }?.let { maxOf(0, it - lockout.failures()) }

    /** Reads the key bundle, removing the device-bound layer. A legacy PIN-only file is bound to this phone on first read. */
    private fun readBundle(): ByteArray {
        val raw = keyFile.readBytes()
        if (DeviceKey.isWrapped(raw)) return DeviceKey.unwrap(raw)
        writeBundle(raw)
        return raw
    }

    /** Wraps with the device key and swaps the file in atomically, so a crash can't leave the vault without a key file. */
    private fun writeBundle(plain: ByteArray) {
        val wrapped = DeviceKey.wrap(plain)
        val tmp = File(dir, "keys.bin.tmp")
        tmp.writeBytes(wrapped)
        if (!tmp.renameTo(keyFile)) { keyFile.writeBytes(wrapped); tmp.delete() }
        keyFile.setLastModified(0) // don't leak when the vault was created or the PIN last changed
    }

    /** Throws WrongPinException / LockedOutException / VaultWipedException. */
    fun unlockWithPin(pin: CharArray) {
        checkNotLockedOut()
        val bundle = readBundle()
        val dek = try { KeyManager.unlock(pin, bundle) } catch (e: WrongPinException) { failure(e) }
        Session.open(dek)
        clearFailures()
        // Silent upgrade: vaults made with an older (weaker) work factor are re-wrapped now that we know the PIN.
        if (!KeyManager.isCurrent(bundle))
            runCatching { writeBundle(KeyManager.changePin(pin, pin, bundle)) }
                .onSuccess { android.util.Log.i("KryptSafe", "Key stretching upgraded to Argon2id") }
    }

    /** Checks a credential without opening a session (used before sensitive changes). Same rules as unlocking. */
    fun verifyPin(pin: CharArray) {
        checkNotLockedOut()
        try { KeyManager.unlock(pin, readBundle()).fill(0) } catch (e: WrongPinException) { failure(e) }
        clearFailures()
    }

    /**
     * Re-wraps the master key under a new PIN/passphrase (possibly of a different kind). Files are NOT re-encrypted, so
     * this is instant regardless of vault size, and biometric unlock keeps working (same master key).
     */
    fun changeCredential(old: CharArray, new: CharArray, newKind: CredentialKind) {
        checkNotLockedOut()
        val updated = try { KeyManager.changePin(old, new, readBundle()) } catch (e: WrongPinException) { failure(e) }
        clearFailures()
        writeBundle(updated)
        credentialKind = newKind
    }

    /**
     * Crypto-erase: destroys the key file, both Keystore keys and every vault file. Even if some flash blocks survive,
     * the ciphertext can no longer be decrypted because the keys are gone.
     */
    fun wipeAll() {
        Session.lock()
        File(dir, "items").deleteRecursively()
        File(dir, "thumbs").deleteRecursively()
        dir.listFiles()?.forEach { it.deleteRecursively() }
        settings.edit().clear().apply()
        DeviceKey.delete()
        BiometricHelper.deleteKey()
        dir.mkdirs()
    }

    fun failedAttempts() = lockout.failures()
    fun lockoutRemainingMs() = lockout.remainingMs()

    fun readBio(): ByteArray? = bioFile.takeIf { it.exists() }?.readBytes()
    fun writeBio(data: ByteArray) = bioFile.writeBytes(data)
    fun clearBio() { bioFile.delete() }
}
