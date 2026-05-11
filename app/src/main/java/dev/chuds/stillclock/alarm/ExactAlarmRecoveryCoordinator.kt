package dev.chuds.stillclock.alarm

import android.content.Context
import android.os.SystemClock
import dev.chuds.stillclock.data.AlarmsRepository
import dev.chuds.stillclock.data.TimerRepository

/**
 * Process-wide debounce for exact-alarm recovery sweeps. A single user grant fans out into
 * the system permission broadcast (ExactAlarmPermissionReceiver), the in-app ActivityResult
 * callback, and the lifecycle ON_RESUME observer — without this guard, each callsite would
 * trigger its own full setAlarmClock sweep and a recoverRunningTimer pass.
 *
 * The coordinator is safe to call from multiple coroutines: tryClaim is an atomic compare-set
 * on a single AtomicLong, so only the first caller within MIN_INTERVAL_MS proceeds.
 */
object ExactAlarmRecoveryCoordinator {

    const val MIN_INTERVAL_MS: Long = 5_000L

    private val lastSweepElapsedMs = java.util.concurrent.atomic.AtomicLong(0L)

    /**
     * Returns true if the caller should perform a sweep right now; false if a sweep was already
     * performed within MIN_INTERVAL_MS. Wrap any callsite with `if (tryClaim()) sweep(...)`.
     */
    fun tryClaim(nowElapsedMs: Long = SystemClock.elapsedRealtime()): Boolean {
        while (true) {
            val previous = lastSweepElapsedMs.get()
            if (previous != 0L && (nowElapsedMs - previous) < MIN_INTERVAL_MS) return false
            if (lastSweepElapsedMs.compareAndSet(previous, nowElapsedMs)) return true
        }
    }

    /** Reset for tests. */
    fun resetForTests() {
        lastSweepElapsedMs.set(0L)
    }

    suspend fun sweep(context: Context) {
        if (!AlarmsScheduler.canScheduleExactAlarms(context)) return
        AlarmsRepository(context).snapshot()
            .filter { it.enabled }
            .forEach { alarm -> AlarmsScheduler.schedule(context, alarm) }
        TimerScheduler(context, TimerRepository(context)).recoverRunningTimer()
    }
}
