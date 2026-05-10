package dev.chuds.stillclock.data

import java.time.DayOfWeek

/**
 * One alarm. UID stable across edits. daysOfWeek empty = one-shot — fires once and disables.
 *
 * soundUri/soundDisplayName are nullable: null means "use the app-wide default alarm sound"
 * (`ClockSettings.alarmSoundUri`). Non-null overrides per-alarm.
 */
data class Alarm(
    val id: String,
    val hour: Int,
    val minute: Int,
    val label: String,
    val daysOfWeek: Set<DayOfWeek>,
    val enabled: Boolean,
    val soft: Boolean,
    val soundUri: String? = null,
    val soundDisplayName: String? = null,
)
