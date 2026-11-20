package com.kryptsafe.crypto

/** Tiny key-value abstraction so the policy can be unit-tested without Android. */
interface LockoutStorage {
    fun getLong(key: String, default: Long): Long
    fun putAll(vararg pairs: Pair<String, Long>)
}

/**
 * Escalating lockout after wrong PINs: free tries 1–4, then 30s, 60s, 120s ... (doubling, capped at 2^8 × 30s).
 *
 * The wait is measured with the *monotonic* clock, not the wall clock, so changing the phone's date/time can't skip it.
 * A monotonic clock restarts at reboot, so if the boot id changed we re-start the full wait from that moment —
 * rebooting never shortens a lockout (it can only restart it).
 */
class LockoutPolicy(
    private val storage: LockoutStorage,
    private val elapsedNow: () -> Long,
    private val bootId: () -> Long,
) {
    fun failures(): Int = storage.getLong(FAILS, 0).toInt()

    fun remainingMs(): Long {
        val delay = storage.getLong(DELAY, 0)
        if (delay <= 0) return 0
        if (storage.getLong(BOOT, -1) != bootId()) {
            storage.putAll(BOOT to bootId(), UNTIL to elapsedNow() + delay)
            return delay
        }
        return (storage.getLong(UNTIL, 0) - elapsedNow()).coerceIn(0, delay)
    }

    fun recordFailure() {
        val fails = failures() + 1
        val delay = if (fails >= 5) 30_000L shl minOf(fails - 5, 8) else 0L
        storage.putAll(FAILS to fails.toLong(), DELAY to delay, UNTIL to elapsedNow() + delay, BOOT to bootId())
    }

    fun clear() = storage.putAll(FAILS to 0, DELAY to 0, UNTIL to 0, BOOT to bootId())

    private companion object { const val FAILS = "fails"; const val DELAY = "delay_ms"; const val UNTIL = "until_elapsed"; const val BOOT = "boot" }
}
