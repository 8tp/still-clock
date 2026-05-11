package dev.chuds.stillclock.alarm

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ExactAlarmRecoveryCoordinatorTest {

    @Before
    fun setUp() {
        ExactAlarmRecoveryCoordinator.resetForTests()
    }

    @After
    fun tearDown() {
        ExactAlarmRecoveryCoordinator.resetForTests()
    }

    @Test
    fun firstClaim_succeeds() {
        assertEquals(true, ExactAlarmRecoveryCoordinator.tryClaim(nowElapsedMs = 1_000L))
    }

    @Test
    fun secondClaimWithinWindow_isRejected() {
        assertEquals(true, ExactAlarmRecoveryCoordinator.tryClaim(nowElapsedMs = 1_000L))
        // A single user grant fans out to 2-3 callsites within milliseconds. All but
        // the first must be rejected so we don't re-arm every alarm 2-3 times in a row.
        assertEquals(false, ExactAlarmRecoveryCoordinator.tryClaim(nowElapsedMs = 1_010L))
        assertEquals(false, ExactAlarmRecoveryCoordinator.tryClaim(nowElapsedMs = 2_500L))
    }

    @Test
    fun claimAfterMinIntervalElapses_succeeds() {
        assertEquals(true, ExactAlarmRecoveryCoordinator.tryClaim(nowElapsedMs = 1_000L))
        val later = 1_000L + ExactAlarmRecoveryCoordinator.MIN_INTERVAL_MS + 1L
        assertEquals(true, ExactAlarmRecoveryCoordinator.tryClaim(nowElapsedMs = later))
    }

    @Test
    fun claimsFromMultipleThreads_singleWinnerPerInterval() {
        // ExactAlarmPermissionReceiver runs on the main thread; the in-app launcher runs
        // on the main thread; both observe the same AtomicLong, so concurrent calls must
        // converge to one winner.
        val threads = 16
        val barrier = java.util.concurrent.CyclicBarrier(threads)
        val wins = java.util.concurrent.atomic.AtomicInteger(0)
        val ts = (0 until threads).map {
            Thread {
                barrier.await()
                if (ExactAlarmRecoveryCoordinator.tryClaim(nowElapsedMs = 5_000L)) {
                    wins.incrementAndGet()
                }
            }
        }
        ts.forEach { it.start() }
        ts.forEach { it.join() }
        assertEquals(1, wins.get())
    }
}
