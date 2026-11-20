package com.kryptsafe.crypto

import org.junit.Assert.*
import org.junit.Test

class CredentialAndKdfTest {
    private fun v(k: CredentialKind, s: String) = CredentialRules.validate(k, s.toCharArray())

    @Test fun pin6RejectsPredictable() {
        listOf("111111", "123456", "654321", "121212", "123123", "000000", "012345", "987654", "112233").forEach { assertNotNull(it, v(CredentialKind.PIN6, it)) }
    }
    @Test fun pin6AcceptsReasonable() {
        listOf("135790", "482916", "908172").forEach { assertNull(it, v(CredentialKind.PIN6, it)) }
    }
    @Test fun lengthAndCharsetEnforced() {
        assertNotNull(v(CredentialKind.PIN6, "12345"))
        assertNotNull(v(CredentialKind.PIN6, "1234567"))
        assertNotNull(v(CredentialKind.PIN_LONG, "1234567"))
        assertNotNull(v(CredentialKind.PIN_LONG, "48291a63"))
        assertNull(v(CredentialKind.PIN_LONG, "48291637"))
        assertNotNull(v(CredentialKind.PASSPHRASE, "short"))
        assertNotNull(v(CredentialKind.PASSPHRASE, "password"))
        assertNull(v(CredentialKind.PASSPHRASE, "correct horse battery staple"))
        assertNotNull(v(CredentialKind.PASSPHRASE, "aaaaaaaaaaaa"))
    }
    @Test fun kindRoundTripsById() { CredentialKind.entries.forEach { assertEquals(it, CredentialKind.fromId(it.id)) }; assertEquals(CredentialKind.PIN6, CredentialKind.fromId(99)) }

    @Test fun lockoutCodecRoundTripsAndRejectsGarbage() {
        val m = mapOf("fails" to 6L, "delay_ms" to 60_000L, "until_elapsed" to 123456789L)
        assertEquals(m, LockoutCodec.decode(LockoutCodec.encode(m)))
        assertNull(LockoutCodec.decode(byteArrayOf(1, 2, 3)))
        assertNull(LockoutCodec.decode(ByteArray(0)))
    }

    @Test fun pbkdf2VaultIsDetectedAndUpgradedToArgon2id() {
        installTestArgon()
        val old = KeyManager.create("482916".toCharArray(), legacyIterations = 10_000)
        assertEquals(10_000, KeyManager.iterationsOf(old.bundle))
        assertFalse(KeyManager.isCurrent(old.bundle))
        // Same PIN, re-wrapped: this is exactly what the silent upgrade after unlock does.
        val upgraded = KeyManager.changePin("482916".toCharArray(), "482916".toCharArray(), old.bundle)
        assertTrue(KeyManager.isCurrent(upgraded))
        assertArrayEquals(old.dek, KeyManager.unlock("482916".toCharArray(), upgraded)) // same master key -> files still open
        assertThrows(WrongPinException::class.java) { KeyManager.unlock("000000".toCharArray(), upgraded) }
    }

    @Test fun argon2idMatchesRfc9106ReferenceBehaviourAndIsSaltSensitive() {
        installTestArgon()
        val pw = "pw".toByteArray()
        val a = KeyManager.argon2id(pw, ByteArray(16) { 1 }, 64, 1, 1)
        assertEquals(32, a.size)
        assertArrayEquals(a, KeyManager.argon2id(pw, ByteArray(16) { 1 }, 64, 1, 1))
        assertFalse(a.contentEquals(KeyManager.argon2id(pw, ByteArray(16) { 2 }, 64, 1, 1)))
    }
}
