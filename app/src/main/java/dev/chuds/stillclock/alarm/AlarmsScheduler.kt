package dev.chuds.stillclock.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import dev.chuds.stillclock.MainActivity
import dev.chuds.stillclock.data.Alarm
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Single source of truth for arming and disarming alarms via AlarmManager.setAlarmClock.
 * setAlarmClock is mandatory: it fills the OS's user-facing "next alarm" slot, draws the
 * status-bar alarm icon, and survives Doze without a battery-optimization allowlist entry.
 */
object AlarmsScheduler {

    private const val TAG = "still-clock/scheduler"

    fun canScheduleExactAlarms(context: Context): Boolean {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
    }

    fun schedule(context: Context, alarm: Alarm): Long? {
        if (!alarm.enabled) {
            cancel(context, alarm.id)
            return null
        }
        if (!canScheduleExactAlarms(context)) {
            Log.w(TAG, "exact-alarm permission missing; alarm ${alarm.id} will not be scheduled")
            return null
        }
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val zone = ZoneId.systemDefault()
        val next = AlarmScheduling.nextFire(alarm, ZonedDateTime.now(zone), zone)
        val triggerMs = next.toInstant().toEpochMilli()

        val firePendingIntent = buildFirePendingIntent(context, alarm.id)
            ?: error("PendingIntent.getBroadcast returned null without FLAG_NO_CREATE")
        val showPendingIntent = buildShowPendingIntent(context, alarm.id)

        val info = AlarmManager.AlarmClockInfo(triggerMs, showPendingIntent)
        am.setAlarmClock(info, firePendingIntent)
        Log.i(TAG, "scheduled alarm ${alarm.id} for $next")
        return triggerMs
    }

    fun cancel(context: Context, alarmId: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = buildFirePendingIntent(context, alarmId, flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        if (pi != null) {
            am.cancel(pi)
            pi.cancel()
        }
    }

    /** Returns true if the snooze was actually armed. False means exact-alarm permission is missing. */
    fun scheduleSnooze(context: Context, alarmId: String, triggerMs: Long): Boolean {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (!canScheduleExactAlarms(context)) {
            Log.w(TAG, "exact-alarm permission missing; snooze for $alarmId will not arm")
            return false
        }
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_FIRE_ALARM
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_IS_SNOOZE, true)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            AlarmScheduling.snoozeRequestCodeFor(alarmId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
        return true
    }

    fun cancelSnooze(context: Context, alarmId: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply { action = ACTION_FIRE_ALARM }
        val pi = PendingIntent.getBroadcast(
            context,
            AlarmScheduling.snoozeRequestCodeFor(alarmId),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pi != null) {
            am.cancel(pi)
            pi.cancel()
        }
    }

    private fun buildFirePendingIntent(
        context: Context,
        alarmId: String,
        flags: Int = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    ): PendingIntent? {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_FIRE_ALARM
            putExtra(EXTRA_ALARM_ID, alarmId)
        }
        return if (flags and PendingIntent.FLAG_NO_CREATE != 0) {
            PendingIntent.getBroadcast(context, AlarmScheduling.requestCodeFor(alarmId), intent, flags)
        } else {
            PendingIntent.getBroadcast(context, AlarmScheduling.requestCodeFor(alarmId), intent, flags)
        }
    }

    private fun buildShowPendingIntent(context: Context, alarmId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_ALARM_EDIT
            putExtra(EXTRA_ALARM_ID, alarmId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            ("show:$alarmId").hashCode() and 0x7FFFFFFF,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    const val ACTION_FIRE_ALARM = "dev.chuds.stillclock.action.FIRE_ALARM"
    const val ACTION_FIRE_TIMER = "dev.chuds.stillclock.action.FIRE_TIMER"
    const val EXTRA_ALARM_ID = "alarm_id"
    const val EXTRA_IS_SNOOZE = "is_snooze"
    const val EXTRA_KIND = "kind"
    const val EXTRA_SOUND_URI = "sound_uri"
    const val KIND_ALARM = "alarm"
    const val KIND_TIMER = "timer"
}
