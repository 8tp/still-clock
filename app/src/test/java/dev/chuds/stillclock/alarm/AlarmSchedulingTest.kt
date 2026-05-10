package dev.chuds.stillclock.alarm

import dev.chuds.stillclock.data.Alarm
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmSchedulingTest {

    private val zone = ZoneId.of("UTC")

    @Test
    fun oneShot_today_future_firesToday() {
        val now = ZonedDateTime.of(LocalDateTime.of(2026, 5, 10, 6, 0), zone) // a sunday
        val alarm = make(hour = 7, minute = 30, days = emptySet())
        val next = AlarmScheduling.nextFire(alarm, now, zone)
        assertEquals(7, next.hour)
        assertEquals(30, next.minute)
        assertEquals(now.toLocalDate(), next.toLocalDate())
    }

    @Test
    fun oneShot_today_past_firesTomorrow() {
        val now = ZonedDateTime.of(LocalDateTime.of(2026, 5, 10, 8, 0), zone)
        val alarm = make(hour = 7, minute = 30, days = emptySet())
        val next = AlarmScheduling.nextFire(alarm, now, zone)
        assertEquals(now.toLocalDate().plusDays(1), next.toLocalDate())
    }

    @Test
    fun recurring_picksNextArmedDay() {
        val now = ZonedDateTime.of(LocalDateTime.of(2026, 5, 10, 6, 0), zone) // sunday
        val alarm = make(hour = 7, minute = 0, days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))
        val next = AlarmScheduling.nextFire(alarm, now, zone)
        assertEquals(DayOfWeek.MONDAY, next.dayOfWeek)
    }

    @Test
    fun recurring_sameDayLater_firesToday() {
        val now = ZonedDateTime.of(LocalDateTime.of(2026, 5, 11, 6, 0), zone) // monday
        val alarm = make(hour = 7, minute = 0, days = setOf(DayOfWeek.MONDAY))
        val next = AlarmScheduling.nextFire(alarm, now, zone)
        assertEquals(now.toLocalDate(), next.toLocalDate())
    }

    @Test
    fun recurring_sameDayMissed_jumpsToNextWeek() {
        val now = ZonedDateTime.of(LocalDateTime.of(2026, 5, 11, 8, 0), zone) // monday
        val alarm = make(hour = 7, minute = 0, days = setOf(DayOfWeek.MONDAY))
        val next = AlarmScheduling.nextFire(alarm, now, zone)
        assertEquals(now.toLocalDate().plusDays(7), next.toLocalDate())
    }

    @Test
    fun snooze_offsetsByMinutes() {
        val now = ZonedDateTime.of(LocalDateTime.of(2026, 5, 10, 7, 0), zone)
        val later = AlarmScheduling.snoozeAt(now, 5)
        assertEquals(7, later.hour)
        assertEquals(5, later.minute)
    }

    @Test
    fun softRamp_isLinear() {
        assertEquals(0f, AlarmScheduling.softRampCoefficient(0), 1e-6f)
        val mid = AlarmScheduling.softRampCoefficient(60)
        assertTrue(mid > 0.49f && mid < 0.51f)
        assertEquals(1f, AlarmScheduling.softRampCoefficient(120), 1e-6f)
        assertEquals(1f, AlarmScheduling.softRampCoefficient(200), 1e-6f) // capped
    }

    @Test
    fun requestCode_isStableAndNonNegative() {
        val code = AlarmScheduling.requestCodeFor("abc-123")
        assertEquals(code, AlarmScheduling.requestCodeFor("abc-123"))
        assertTrue(code >= 0)
    }

    private fun make(hour: Int, minute: Int, days: Set<DayOfWeek>): Alarm = Alarm(
        id = "x",
        hour = hour,
        minute = minute,
        label = "",
        daysOfWeek = days,
        enabled = true,
        soft = false,
    )
}
