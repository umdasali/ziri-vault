package com.kryptsafe.vault

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec

class DeviceKeyLostException(cause: Throwable? = null) : Exception("Device key unavailable", cause)

/**
 * A second lock on the key file, bound to THIS phone's Android Keystore (hardware-backed where available).
 *
 * Without it, anyone who copies `keys.bin` could try all 1,000,000 six-digit PINs offline at full speed. With it,
 * the file is encrypted by a key that cannot leave the phone's secure hardware, so a copy is useless anywhere else.
 * The key is not tied to the screen lock or biometrics, so it never gets invalidated by the user changing those.
 *
 * File layout: "KSK2" | iv[12] | AES-GCM(ciphertext of the PIN-wrapped key bundle).
 * Legacy files (no magic) are PIN-only and get migrated on first read.
 */
object DeviceKey {
    private const val ALIAS = "kryptsafe_device_wrap_v1"
    private val MAGIC = byteArrayOf('K'.code.toByte(), 'S'.code.toByte(), 'K'.code.toByte(), '2'.code.toByte())
    private const val IV_LEN = 12

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun key(): SecretKey {
        (keyStore().getKey(ALIAS, null) as? SecretKey)?.let { return it }
        fun spec(strongBox: Boolean) = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .apply { if (strongBox && Build.VERSION.SDK_INT >= 28) setIsStrongBoxBacked(true) }
            .build()
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        return try { gen.init(spec(true)); gen.generateKey() } catch (e: Exception) { gen.init(spec(false)); gen.generateKey() }
    }

    /** Destroys the device key. Anything wrapped with it (the key file, lockout counters) becomes permanently unreadable. */
    fun delete() { runCatching { keyStore().deleteEntry(ALIAS) } }

    fun isWrapped(data: ByteArray) = data.size > MAGIC.size + IV_LEN && data.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    fun wrap(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return MAGIC + c.iv + c.doFinal(plain)
    }

    fun unwrap(data: ByteArray): ByteArray = try {
        val iv = data.copyOfRange(MAGIC.size, MAGIC.size + IV_LEN)
        val ct = data.copyOfRange(MAGIC.size + IV_LEN, data.size)
        Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }.doFinal(ct)
    } catch (e: Exception) { throw DeviceKeyLostException(e) }

    /** Honest description of where the device key lives, for the Settings > About screen. */
    fun protectionLabel(): String = runCatching {
        val k = key()
        val info = SecretKeyFactory.getInstance(k.algorithm, "AndroidKeyStore").getKeySpec(k, KeyInfo::class.java) as KeyInfo
        if (Build.VERSION.SDK_INT >= 31) when (info.securityLevel) {
            KeyProperties.SECURITY_LEVEL_STRONGBOX -> "Hardware (StrongBox)"
            KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> "Hardware (secure chip)"
            else -> "Software only"
        } else if (info.isInsideSecureHardware) "Hardware (secure chip)" else "Software only"
    }.getOrDefault("Unavailable")
}
