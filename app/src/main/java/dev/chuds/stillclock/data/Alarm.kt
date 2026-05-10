package dev.chuds.stillclock.data

import java.time.DayOfWeek

/**
 * One alarm. UID stable across edits. daysOfWeek empty = one-shot — fires once and disables.
 */
data class Alarm(
    val id: String,
    val hour: Int,
    val minute: Int,
    val label: String,
    val daysOfWeek: Set<DayOfWeek>,
    val enabled: Boolean,
    val soft: Boolean,
)
