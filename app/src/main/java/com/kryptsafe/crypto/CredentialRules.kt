package com.kryptsafe.crypto

/** What the user unlocks with. Stronger kinds trade typing speed for far more combinations. */
enum class CredentialKind(
    val id: Int, val title: String, val blurb: String, val noun: String,
    val minLen: Int, val maxLen: Int, val digitsOnly: Boolean, val autoSubmit: Boolean,
) {
    PIN6(0, "6-digit PIN", "Quickest to type. 1 million combinations.", "PIN", 6, 6, true, true),
    PIN_LONG(1, "Longer PIN", "8 to 16 digits. At least 100 million combinations.", "PIN", 8, 16, true, false),
    PASSPHRASE(2, "Passphrase", "Letters, numbers and symbols. The strongest option.", "passphrase", 8, 64, false, false);

    companion object { fun fromId(id: Int) = entries.firstOrNull { it.id == id } ?: PIN6 }
}

object CredentialRules {
    private val COMMON = setOf(
        "123456", "654321", "123123", "112233", "121212", "159753", "696969", "654123", "789456", "123654", "123321", "147258",
        "12345678", "87654321", "11223344", "123456789", "1234567890", "0987654321", "1357924680", "12341234", "13579246",
        "password", "passw0rd", "password1", "qwertyui", "qwertyuiop", "iloveyou", "letmein1", "admin123", "welcome1",
    )

    /** Returns a user-facing reason the credential is rejected, or null if it is acceptable. */
    fun validate(kind: CredentialKind, c: CharArray): String? {
        if (c.size < kind.minLen) return "Use at least ${kind.minLen} ${if (kind.digitsOnly) "digits" else "characters"}."
        if (c.size > kind.maxLen) return "Use at most ${kind.maxLen} ${if (kind.digitsOnly) "digits" else "characters"}."
        if (kind.digitsOnly && c.any { it !in '0'..'9' }) return "Digits only, please."
        val s = String(c)
        val weak = "That's too easy to guess. Choose something less predictable."
        if (s.all { it == s[0] }) return weak
        if (s.lowercase() in COMMON) return weak
        if (kind.digitsOnly && (isSequence(s) || isRepeatingUnit(s))) return weak
        if (!kind.digitsOnly && s.trim().isEmpty()) return weak
        return null
    }

    private fun isSequence(s: String): Boolean {
        val up = s.zipWithNext().all { (a, b) -> b - a == 1 || (a == '9' && b == '0') }
        val down = s.zipWithNext().all { (a, b) -> a - b == 1 || (a == '0' && b == '9') }
        return up || down
    }

    /** "121212", "123123", "00110011": the whole string is one short unit repeated. */
    private fun isRepeatingUnit(s: String): Boolean {
        for (len in 1..s.length / 2) if (s.length % len == 0 && s.substring(0, len).repeat(s.length / len) == s) return true
        return false
    }
}
