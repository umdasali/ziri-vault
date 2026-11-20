package com.kryptsafe.crypto

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class WrongPinException : Exception("Wrong PIN")

/**
 * The vault has one random 256-bit master key (DEK). It is never stored in the clear;
 * it is stored wrapped (AES-GCM) under a key derived from the user's PIN via Argon2id (legacy: PBKDF2).
 * A wrong PIN fails the GCM tag check, so no separate PIN hash is stored.
 *
 * (Biometric unlock will wrap the same DEK under an Android Keystore key — added with the lock screen.)
 */
object KeyManager {
    /** Argon2id work factor (memory-hard): 64 MiB, 3 passes, 1 lane. */
    const val ARGON_MEM_KIB = 65_536
    const val ARGON_PASSES = 3
    const val ARGON_LANES = 1
    /** Legacy PBKDF2 work factor, only used to read/create old-format bundles. */
    const val LEGACY_ITERATIONS = 600_000
    /** First int of a bundle: positive = PBKDF2 iterations (legacy); this marker = Argon2id params follow. */
    private const val ARGON2ID_MARK = -2
    private const val SALT_LEN = 16
    private const val NONCE_LEN = 12
    private val rng = SecureRandom()

    /** Raw Argon2id: (password utf8, salt, memKiB, passes, lanes) -> 32 bytes. Swappable so JVM unit tests can supply a pure-Java one. */
    @JvmStatic var argon2id: (ByteArray, ByteArray, Int, Int, Int) -> ByteArray = { pw, salt, mem, t, p ->
        com.lambdapioneer.argon2kt.Argon2Kt().hash(
            com.lambdapioneer.argon2kt.Argon2Mode.ARGON2_ID, pw, salt,
            tCostInIterations = t, mCostInKibibyte = mem, parallelism = p, hashLengthInBytes = 32,
        ).rawHashAsByteArray()
    }

    class Created(val dek: ByteArray, val bundle: ByteArray)

    /** legacyIterations != null creates an old PBKDF2 bundle (used by tests to prove migration). */
    fun create(pin: CharArray, legacyIterations: Int? = null): Created {
        val dek = ByteArray(32).also(rng::nextBytes)
        return Created(dek, wrap(dek, pin, legacyIterations))
    }

    /** True when the bundle is stretched with the current scheme (Argon2id). */
    fun isCurrent(bundle: ByteArray): Boolean = DataInputStream(ByteArrayInputStream(bundle)).readInt() == ARGON2ID_MARK

    /** PBKDF2 rounds for a legacy bundle, or 0 for Argon2id. */
    fun iterationsOf(bundle: ByteArray): Int = DataInputStream(ByteArrayInputStream(bundle)).readInt().let { if (it == ARGON2ID_MARK) 0 else it }

    fun unlock(pin: CharArray, bundle: ByteArray): ByteArray {
        val inp = DataInputStream(ByteArrayInputStream(bundle))
        val head = inp.readInt()
        val mem: Int; val passes: Int; val lanes: Int
        if (head == ARGON2ID_MARK) { mem = inp.readInt(); passes = inp.readInt(); lanes = inp.readInt() } else { mem = 0; passes = 0; lanes = 0 }
        val salt = ByteArray(SALT_LEN).also(inp::readFully)
        val nonce = ByteArray(NONCE_LEN).also(inp::readFully)
        val ct = inp.readBytes()
        val kek = if (head == ARGON2ID_MARK) deriveArgon(pin, salt, mem, passes, lanes) else derivePbkdf2(pin, salt, head)
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(128, nonce))
                doFinal(ct)
            }
        } catch (e: GeneralSecurityException) {
            throw WrongPinException()
        } finally {
            kek.fill(0)
        }
    }

    /** Re-wrap the same DEK under a new PIN, always with the current scheme (files don't need re-encryption). */
    fun changePin(oldPin: CharArray, newPin: CharArray, bundle: ByteArray): ByteArray {
        val dek = unlock(oldPin, bundle)
        return try { wrap(dek, newPin, null) } finally { dek.fill(0) }
    }

    private fun wrap(dek: ByteArray, pin: CharArray, legacyIterations: Int?): ByteArray {
        val salt = ByteArray(SALT_LEN).also(rng::nextBytes)
        val nonce = ByteArray(NONCE_LEN).also(rng::nextBytes)
        val kek = if (legacyIterations != null) derivePbkdf2(pin, salt, legacyIterations)
                  else deriveArgon(pin, salt, ARGON_MEM_KIB, ARGON_PASSES, ARGON_LANES)
        val ct = try {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(128, nonce))
                doFinal(dek)
            }
        } finally { kek.fill(0) }
        return ByteArrayOutputStream().also {
            DataOutputStream(it).apply {
                if (legacyIterations != null) writeInt(legacyIterations)
                else { writeInt(ARGON2ID_MARK); writeInt(ARGON_MEM_KIB); writeInt(ARGON_PASSES); writeInt(ARGON_LANES) }
                write(salt); write(nonce); write(ct)
            }
        }.toByteArray()
    }

    private fun deriveArgon(pin: CharArray, salt: ByteArray, mem: Int, passes: Int, lanes: Int): ByteArray {
        val bb = Charsets.UTF_8.encode(java.nio.CharBuffer.wrap(pin))
        val pw = ByteArray(bb.remaining()).also { bb.get(it) }
        return try { argon2id(pw, salt, mem, passes, lanes) } finally { pw.fill(0); if (bb.hasArray()) bb.array().fill(0) }
    }

    private fun derivePbkdf2(pin: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin, salt, iterations, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally { spec.clearPassword() }
    }
}
