package com.kryptsafe.crypto

import org.junit.Assert.assertEquals
import org.junit.Test

class LockoutPolicyTest {
    private class Mem : LockoutStorage {
        val m = mutableMapOf<String, Long>()
        override fun getLong(key: String, default: Long) = m[key] ?: default
        override fun putAll(vararg pairs: Pair<String, Long>) { pairs.forEach { m[it.first] = it.second } }
    }
    private var now = 1_000_000L
    private var boot = 7L
    private val policy = LockoutPolicy(Mem(), { now }, { boot })

    @Test fun firstFourFailuresAreFree() {
        repeat(4) { policy.recordFailure(); assertEquals(0L, policy.remainingMs()) }
    }

    @Test fun fifthFailureLocksThirtySecondsThenDoubles() {
        repeat(5) { policy.recordFailure() }
        assertEquals(30_000L, policy.remainingMs())
        now += 30_000; assertEquals(0L, policy.remainingMs())
        policy.recordFailure(); assertEquals(60_000L, policy.remainingMs())
        now += 60_000; policy.recordFailure(); assertEquals(120_000L, policy.remainingMs())
    }

    @Test fun waitCountsDownWithTime() {
        repeat(5) { policy.recordFailure() }
        now += 12_000
        assertEquals(18_000L, policy.remainingMs())
    }

    @Test fun rebootNeverShortensTheWaitAndItStillCountsDown() {
        repeat(5) { policy.recordFailure() }
        now += 25_000          // 5s left...
        boot += 1              // ...then the phone reboots: monotonic clock restarts
        now = 100              // (a fresh monotonic clock)
        assertEquals(30_000L, policy.remainingMs())  // full wait restarts, not shortened
        now += 10_000
        assertEquals(20_000L, policy.remainingMs())  // and counts down normally afterwards
    }

    @Test fun successClearsEverything() {
        repeat(6) { policy.recordFailure() }
        policy.clear()
        assertEquals(0L, policy.remainingMs()); assertEquals(0, policy.failures())
        policy.recordFailure(); assertEquals(0L, policy.remainingMs())
    }
}
