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
    val deadlineElapsedRealtimeMs: Long? = null,
    val totalDurationMs: Long = 0L,
    val pausedRemainingMs: Long? = null,
    val startedBootCount: Int? = null,
) {
    val isRunning: Boolean get() = deadlineEpochMs != null
    val isPaused: Boolean get() = deadlineEpochMs == null && pausedRemainingMs != null
    val isIdle: Boolean get() = deadlineEpochMs == null && pausedRemainingMs == null

    fun remainingMs(
        nowEpochMs: Long,
        nowElapsedRealtimeMs: Long? = null,
        currentBootCount: Int? = null,
        allowUnknownBootForElapsedRealtime: Boolean = false,
    ): Long = when {
        deadlineEpochMs != null && canUseElapsedRealtime(
            currentBootCount = currentBootCount,
            allowUnknownBoot = allowUnknownBootForElapsedRealtime,
        ) && nowElapsedRealtimeMs != null -> (deadlineElapsedRealtimeMs!! - nowElapsedRealtimeMs).coerceAtLeast(0L)
        deadlineEpochMs != null -> (deadlineEpochMs - nowEpochMs).coerceAtLeast(0L)
        pausedRemainingMs != null -> pausedRemainingMs
        else -> 0L
    }

    fun isExpired(
        nowEpochMs: Long,
        nowElapsedRealtimeMs: Long? = null,
        currentBootCount: Int? = null,
    ): Boolean = isRunning && remainingMs(
        nowEpochMs = nowEpochMs,
        nowElapsedRealtimeMs = nowElapsedRealtimeMs,
        currentBootCount = currentBootCount,
    ) <= 0L

    fun canUseElapsedRealtime(currentBootCount: Int?, allowUnknownBoot: Boolean = false): Boolean {
        if (deadlineElapsedRealtimeMs == null) return false
        if (allowUnknownBoot && currentBootCount == null) return true
        return startedBootCount != null && currentBootCount != null && startedBootCount == currentBootCount
    }

    companion object {
        val Idle = TimerState()
    }
}
