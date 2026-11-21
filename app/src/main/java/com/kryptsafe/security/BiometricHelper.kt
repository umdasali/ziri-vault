package com.kryptsafe.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.kryptsafe.vault.VaultStore
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Biometric unlock: the master key is also wrapped under a hardware-backed Keystore key that
 * can only be used after a successful strong-biometric prompt. The PIN stays the fallback.
 */
class BiometricHelper(private val activity: FragmentActivity, private val store: VaultStore) {
    private val alias = ALIAS

    companion object {
        private const val ALIAS = "kryptsafe_bio_key"
        fun deleteKey() { runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(ALIAS) } }
    }

    fun isAvailable() = BiometricManager.from(activity)
        .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    private fun ks() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun key(create: Boolean): SecretKey? {
        val ks = ks()
        (ks.getKey(alias, null) as? SecretKey)?.let { if (!create) return it }
        if (!create) return null
        ks.deleteEntry(alias)
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            // setUserAuthenticationParameters is API 30+; on 26-29 -1 means "authenticate for every use".
            .apply {
                if (android.os.Build.VERSION.SDK_INT >= 30) setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                else @Suppress("DEPRECATION") setUserAuthenticationValidityDurationSeconds(-1)
            }
            .setInvalidatedByBiometricEnrollment(true)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply { init(spec) }.generateKey()
    }

    private fun prompt(cipher: Cipher, title: String, onOk: (Cipher) -> Unit, onFail: (String) -> Unit) {
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title).setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG).build()
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(r: BiometricPrompt.AuthenticationResult) {
                r.cryptoObject?.cipher?.let(onOk) ?: onFail("No cipher")
            }
            override fun onAuthenticationError(code: Int, msg: CharSequence) {
                // The user tapping "Use PIN" / backing out isn't an error: stay quiet and show the PIN pad.
                val quiet = code == BiometricPrompt.ERROR_NEGATIVE_BUTTON || code == BiometricPrompt.ERROR_USER_CANCELED || code == BiometricPrompt.ERROR_CANCELED
                onFail(if (quiet) "" else msg.toString())
            }
        }).authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }

    /** Call while unlocked (master key in Session). Wraps it under the biometric-gated key. */
    fun enable(masterKey: ByteArray, onDone: (Boolean) -> Unit) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key(true)) }
        // Copy: if the vault locks while the prompt is up, Session zeroes the original and we'd wrap an all-zero key.
        val keyCopy = masterKey.copyOf()
        prompt(cipher, "Enable biometric unlock", { c ->
            try { store.writeBio(byteArrayOf(c.iv.size.toByte()) + c.iv + c.doFinal(keyCopy)); onDone(true) } finally { keyCopy.fill(0) }
        }, { onDone(false) })
    }

    fun unlock(onKey: (ByteArray) -> Unit, onFail: (String) -> Unit) {
        val blob = store.readBio() ?: return onFail("Biometric not set up")
        val k = runCatching { key(false) }.getOrNull() ?: return onFail("Biometric key missing").also { store.clearBio() }
        val ivLen = blob[0].toInt()
        val iv = blob.copyOfRange(1, 1 + ivLen)
        val ct = blob.copyOfRange(1 + ivLen, blob.size)
        val cipher = try {
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, iv)) }
        } catch (e: KeyPermanentlyInvalidatedException) {
            // New fingerprint enrolled: biometric unlock is revoked, PIN required.
            store.clearBio(); ks().deleteEntry(alias)
            return onFail("Biometrics changed - use your PIN")
        } catch (e: Exception) {
            store.clearBio(); runCatching { ks().deleteEntry(alias) }
            return onFail("Biometric unlock is unavailable - use your PIN")
        }
        prompt(cipher, "Unlock KryptSafe", { c -> onKey(c.doFinal(ct)) }, onFail)
    }

    fun disable() { store.clearBio(); ks().deleteEntry(alias) }
}
