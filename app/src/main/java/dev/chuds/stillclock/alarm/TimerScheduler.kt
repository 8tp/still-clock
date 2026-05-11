package dev.chuds.stillclock.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import dev.chuds.stillclock.data.TimerRepository
import dev.chuds.stillclock.data.TimerState

/**
 * Wraps the timer pipeline. Uses setExactAndAllowWhileIdle (no system "next-timer" UI to honor).
 */
class TimerScheduler(private val context: Context, private val repository: TimerRepository) {

    suspend fun start(durationMs: Long): Boolean {
        val duration = durationMs.coerceAtLeast(0L)
        val nowEpoch = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        val bootCount = currentBootCount()
        if (!canArmExact()) return false
        repository.save(
            TimerState(
                deadlineEpochMs = nowEpoch + duration,
                deadlineElapsedRealtimeMs = nowElapsed + duration,
                totalDurationMs = duration,
                pausedRemainingMs = null,
                startedBootCount = bootCount,
            ),
        )
        armAtElapsed(nowElapsed + duration)
        return true
    }

    suspend fun pause() {
        val paused = repository.pauseIfRunning(
            nowEpochMs = System.currentTimeMillis(),
            nowElapsedRealtimeMs = SystemClock.elapsedRealtime(),
            currentBootCount = currentBootCount(),
        )
        // Cancel the AlarmManager arm only when we committed the paused state. If the timer
        // already fired (AlarmReceiver cleared it inside the same DataStore boundary),
        // pauseIfRunning returns false and we leave the cleared state alone.
        if (paused) cancelArm()
    }

    suspend fun resume(): Boolean {
        val state = repository.snapshot()
        val remaining = (state.pausedRemainingMs ?: return false).coerceAtLeast(0L)
        val nowEpoch = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        val bootCount = currentBootCount()
        if (!canArmExact()) return false
        repository.save(
            state.copy(
                deadlineEpochMs = nowEpoch + remaining,
                deadlineElapsedRealtimeMs = nowElapsed + remaining,
                pausedRemainingMs = null,
                startedBootCount = bootCount,
            ),
        )
        armAtElapsed(nowElapsed + remaining)
        return true
    }

    suspend fun cancel() {
        cancelArm()
        repository.clear()
    }

    suspend fun recoverRunningTimer() {
        val state = repository.snapshot()
        val deadline = state.deadlineEpochMs ?: return
        val nowEpoch = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        val bootCount = currentBootCount()
        val elapsedDeadline = state.deadlineElapsedRealtimeMs

        if (state.canUseElapsedRealtime(bootCount) && elapsedDeadline != null) {
            if (elapsedDeadline > nowElapsed) {
                if (canArmExact()) armAtElapsed(elapsedDeadline)
                return
            }
            fireIfConsumed(nowEpoch, nowElapsed, bootCount)
            return
        }

        when (val decision = AlarmScheduling.wallFallbackDecision(
            deadlineEpochMs = deadline,
            nowEpochMs = nowEpoch,
            totalDurationMs = state.totalDurationMs,
        )) {
            AlarmScheduling.WallFallbackDecision.FireNow -> forceFire()
            is AlarmScheduling.WallFallbackDecision.Reschedule -> {
                if (canArmExact()) {
                    val refreshed = state.copy(
                        deadlineElapsedRealtimeMs = nowElapsed + decision.remainingMs,
                        startedBootCount = bootCount,
                    )
                    repository.save(refreshed)
                    armAtElapsed(nowElapsed + decision.remainingMs)
                }
            }
            AlarmScheduling.WallFallbackDecision.Expired -> fireIfConsumed(nowEpoch, nowElapsed, bootCount)
        }
    }

    private suspend fun fireIfConsumed(nowEpoch: Long, nowElapsed: Long, bootCount: Int?) {
        if (!repository.consumeExpiredRunningTimer(nowEpoch, nowElapsed, bootCount)) return
        broadcastFire()
    }

    /**
     * Force-fire bypasses isExpired() — used when the wall clock has moved backward enough
     * that the persisted deadlineEpochMs would otherwise read as still in the future.
     */
    private suspend fun forceFire() {
        repository.clear()
        broadcastFire()
    }

    private fun broadcastFire() {
        val fire = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmsScheduler.ACTION_FIRE_TIMER
            putExtra(AlarmsScheduler.EXTRA_KIND, AlarmsScheduler.KIND_TIMER)
        }
        context.sendBroadcast(fire)
    }

    private fun armAtElapsed(deadlineElapsedRealtimeMs: Long) {
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
        am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, deadlineElapsedRealtimeMs, pi)
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

    private fun currentBootCount(): Int? =
        runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT) }.getOrNull()
}
