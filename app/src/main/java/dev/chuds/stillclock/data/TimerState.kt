package dev.chuds.stillclock.data

/**
 * Single source of truth for the timer.
 *
 * - idle:    deadlineEpochMs == null && pausedRemainingMs == null
 * - running: deadlineEpochMs != null
 * - paused:  deadlineEpochMs == null && pausedRemainingMs != null
 */
data class TimerState(
    val deadlineEpochMs: Long? = null,
    val totalDurationMs: Long = 0L,
    val pausedRemainingMs: Long? = null,
) {
    val isRunning: Boolean get() = deadlineEpochMs != null
    val isPaused: Boolean get() = deadlineEpochMs == null && pausedRemainingMs != null
    val isIdle: Boolean get() = deadlineEpochMs == null && pausedRemainingMs == null

    fun remainingMs(now: Long): Long = when {
        deadlineEpochMs != null -> (deadlineEpochMs - now).coerceAtLeast(0L)
        pausedRemainingMs != null -> pausedRemainingMs
        else -> 0L
    }

    companion object {
        val Idle = TimerState()
    }
}
