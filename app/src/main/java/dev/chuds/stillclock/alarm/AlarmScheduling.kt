// Pure functions for alarm scheduling math. Kept Android-free so they're trivially testable.
package dev.chuds.stillclock.alarm

import dev.chuds.stillclock.data.Alarm
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object AlarmScheduling {

    /**
     * Returns the next firing instant for [alarm] strictly after [now] (in [zone]).
     * Empty daysOfWeek = one-shot — fires the soonest hour:minute >= now.
     */
    fun nextFire(alarm: Alarm, now: ZonedDateTime, zone: ZoneId = now.zone): ZonedDateTime {
        val today = now.toLocalDate()
        val targetTime = LocalTime.of(alarm.hour, alarm.minute)

        if (alarm.daysOfWeek.isEmpty()) {
            for (offset in 0..1) {
                val date = today.plusDays(offset.toLong())
                for (candidate in candidatesFor(date, targetTime, zone)) {
                    if (candidate.isAfter(now)) return candidate
                }
            }
            return candidatesFor(today.plusDays(1), targetTime, zone).first()
        }

        // Recurring: find soonest day-of-week match in [today, today+7].
        for (offset in 0..7) {
            val date: LocalDate = today.plusDays(offset.toLong())
            if (date.dayOfWeek !in alarm.daysOfWeek) continue
            for (candidate in candidatesFor(date, targetTime, zone)) {
                if (candidate.isAfter(now)) return candidate
            }
        }
        // Should not reach: with non-empty days the loop always returns within 7 iterations.
        return candidatesFor(today.plusDays(7), targetTime, zone).first()
    }

    private fun candidatesFor(date: LocalDate, time: LocalTime, zone: ZoneId): List<ZonedDateTime> {
        val local = LocalDateTime.of(date, time)
        val offsets = zone.rules.getValidOffsets(local)
        return if (offsets.isEmpty()) {
            // DST spring-forward gap: java.time resolves to the first valid local time after the gap.
            listOf(ZonedDateTime.of(local, zone))
        } else {
            offsets.map { offset -> ZonedDateTime.ofLocal(local, zone, offset) }
                .sortedBy { it.toInstant() }
        }
    }

    /** Snooze offset N minutes from [now]. */
    fun snoozeAt(now: ZonedDateTime, minutes: Int): ZonedDateTime =
        now.plusMinutes(minutes.toLong())

    /**
     * Linear ramp coefficient at the [stepIndex]-th 250ms tick of a 30s ramp.
     * 120 ticks total. Returns a value in [0f, 1f].
     */
    fun softRampCoefficient(stepIndex: Int, totalSteps: Int = 120): Float {
        if (totalSteps <= 0) return 1f
        val frac = (stepIndex.coerceAtLeast(0).toFloat()) / totalSteps.toFloat()
        return frac.coerceIn(0f, 1f)
    }

    /** Stable, positive request code for a per-alarm PendingIntent. */
    fun requestCodeFor(alarmId: String): Int {
        // Hash to non-negative int so it round-trips through every PendingIntent API.
        return (alarmId.hashCode() and 0x7FFFFFFF)
    }

    fun snoozeRequestCodeFor(alarmId: String): Int {
        return (("snooze:$alarmId").hashCode() and 0x7FFFFFFF)
    }

    fun timerRequestCode(): Int = 0x0F1E2D3C

    /** Lowercase day-letter row, e.g. "s m t w t f s" with selected days uppercase. */
    fun daysOfWeekLine(selected: Set<DayOfWeek>): String {
        val all = listOf(
            DayOfWeek.SUNDAY to "s",
            DayOfWeek.MONDAY to "m",
            DayOfWeek.TUESDAY to "t",
            DayOfWeek.WEDNESDAY to "w",
            DayOfWeek.THURSDAY to "t",
            DayOfWeek.FRIDAY to "f",
            DayOfWeek.SATURDAY to "s",
        )
        return all.joinToString("  ") { (d, l) -> if (d in selected) l else "·" }
    }
}
