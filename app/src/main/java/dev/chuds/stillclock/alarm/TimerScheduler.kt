package dev.chuds.stillclock.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.chuds.stillclock.data.TimerRepository
import dev.chuds.stillclock.data.TimerState

/**
 * Wraps the timer pipeline. Uses setExactAndAllowWhileIdle (no system "next-timer" UI to honor).
 */
class TimerScheduler(private val context: Context, private val repository: TimerRepository) {

    suspend fun start(durationMs: Long): Boolean {
        val now = System.currentTimeMillis()
        val deadline = now + durationMs.coerceAtLeast(0L)
        if (!canArmExact()) return false
        repository.save(TimerState(deadlineEpochMs = deadline, totalDurationMs = durationMs, pausedRemainingMs = null))
        armAt(deadline)
        return true
    }

    suspend fun pause() {
        val state = repository.snapshot()
        if (!state.isRunning) return
        val now = System.currentTimeMillis()
        val remaining = ((state.deadlineEpochMs ?: now) - now).coerceAtLeast(0L)
        cancelArm()
        repository.save(state.copy(deadlineEpochMs = null, pausedRemainingMs = remaining))
    }

    suspend fun resume(): Boolean {
        val state = repository.snapshot()
        val remaining = state.pausedRemainingMs ?: return false
        val now = System.currentTimeMillis()
        val deadline = now + remaining
        if (!canArmExact()) return false
        repository.save(state.copy(deadlineEpochMs = deadline, pausedRemainingMs = null))
        armAt(deadline)
        return true
    }

    suspend fun cancel() {
        cancelArm()
        repository.clear()
    }

    suspend fun recoverRunningTimer() {
        val state = repository.snapshot()
        val deadline = state.deadlineEpochMs ?: return
        val now = System.currentTimeMillis()
        if (deadline > now) {
            if (canArmExact()) armAt(deadline)
            return
        }

        if (!repository.consumeExpiredRunningTimer(now)) return
        val fire = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmsScheduler.ACTION_FIRE_TIMER
            putExtra(AlarmsScheduler.EXTRA_KIND, AlarmsScheduler.KIND_TIMER)
        }
        context.sendBroadcast(fire)
    }

    private fun armAt(deadlineEpochMs: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            context,
            AlarmScheduling.timerRequestCode(),
            Intent(context, AlarmReceiver::class.java).apply {
                action = AlarmsScheduler.ACTION_FIRE_TIMER
                putExtra(AlarmsScheduler.EXTRA_KIND, AlarmsScheduler.KIND_TIMER)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deadlineEpochMs, pi)
    }

    private fun canArmExact(): Boolean {
        if (AlarmsScheduler.canScheduleExactAlarms(context)) return true
        Log.w("still-clock/timer", "exact-alarm permission missing; timer will not arm")
        return false
    }

    private fun cancelArm() {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            context,
            AlarmScheduling.timerRequestCode(),
            Intent(context, AlarmReceiver::class.java).apply {
                action = AlarmsScheduler.ACTION_FIRE_TIMER
            },
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pi != null) {
            am.cancel(pi)
            pi.cancel()
        }
    }
}
