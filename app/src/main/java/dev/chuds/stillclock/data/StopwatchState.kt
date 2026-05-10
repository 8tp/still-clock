package dev.chuds.stillclock.data

/**
 * Single source of truth for the stopwatch. elapsed = accumulatedMs + (now - startedAtEpochMs ?: 0).
 */
data class StopwatchState(
    val startedAtEpochMs: Long? = null,
    val accumulatedMs: Long = 0L,
    val laps: List<Long> = emptyList(),
) {
    val isRunning: Boolean get() = startedAtEpochMs != null

    fun elapsedMs(now: Long): Long =
        accumulatedMs + (startedAtEpochMs?.let { (now - it).coerceAtLeast(0L) } ?: 0L)

    companion object {
        val Idle = StopwatchState()
    }
}
