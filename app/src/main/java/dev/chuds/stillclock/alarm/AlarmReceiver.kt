package dev.chuds.stillclock.alarm

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.chuds.stillclock.R
import dev.chuds.stillclock.data.AlarmsRepository
import dev.chuds.stillclock.data.BUNDLED_TONES
import dev.chuds.stillclock.data.PreferencesRepository
import dev.chuds.stillclock.data.TimerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver that fires when an alarm or timer's PendingIntent triggers.
 *
 * For alarms: post a high-importance notification with full-screen intent (the OS uses this
 * when we can't startActivity from background) AND startActivity directly. Then schedule the
 * next occurrence (recurring) or disable (one-shot).
 *
 * For timers: post a notification, start the fires-Activity in timer mode, clear the timer state.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        NotificationChannels.ensure(context)
        when (intent.action) {
            AlarmsScheduler.ACTION_FIRE_ALARM -> handleAlarm(context, intent)
            AlarmsScheduler.ACTION_FIRE_TIMER -> handleTimer(context)
        }
    }

    private fun handleAlarm(context: Context, intent: Intent) {
        val alarmId = intent.getStringExtra(AlarmsScheduler.EXTRA_ALARM_ID) ?: return
        val isSnooze = intent.getBooleanExtra(AlarmsScheduler.EXTRA_IS_SNOOZE, false)
        val pending = goAsync()
        val app = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = AlarmsRepository(app)
                val alarm = repo.snapshot().firstOrNull { it.id == alarmId }
                val label = alarm?.label.orEmpty()
                val soft = alarm?.soft == true

                val prefs = runCatching { PreferencesRepository(app).settings.first() }.getOrNull()
                val resolvedSoundUri = listOfNotNull(
                    alarm?.soundUri?.takeIf { it.isNotBlank() },
                    prefs?.alarmSoundUri?.takeIf { it.isNotBlank() },
                    BUNDLED_TONES.first().uri,
                ).first()

                postFiringNotification(app, alarmId, label, soft, isSnooze, resolvedSoundUri)
                startFiresActivity(app, alarmId, label, soft, isSnooze, kind = AlarmsScheduler.KIND_ALARM, soundUri = resolvedSoundUri)

                if (alarm != null && !isSnooze) {
                    if (alarm.daysOfWeek.isEmpty()) {
                        // One-shot: disable so the list reflects the spent state.
                        repo.upsert(alarm.copy(enabled = false))
                    } else {
                        // Recurring: arm the next occurrence right away.
                        AlarmsScheduler.schedule(app, alarm)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun handleTimer(context: Context) {
        val pending = goAsync()
        val app = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val preferencesRepo = PreferencesRepository(app)
                val prefs = runCatching { preferencesRepo.settings.first() }.getOrNull()
                val resolvedSoundUri = listOfNotNull(
                    prefs?.timerSoundUri?.takeIf { it.isNotBlank() },
                    BUNDLED_TONES.first().uri,
                ).first()

                val notificationPosted = postTimerNotification(app, resolvedSoundUri)
                startFiresActivity(app, "", "", soft = false, isSnooze = false, kind = AlarmsScheduler.KIND_TIMER, soundUri = resolvedSoundUri)
                if (!notificationPosted) {
                    runCatching { preferencesRepo.markSilentTimerFire(System.currentTimeMillis()) }
                }
                TimerRepository(app).clear()
            } finally {
                pending.finish()
            }
        }
    }

    private fun postFiringNotification(
        context: Context,
        alarmId: String,
        label: String,
        soft: Boolean,
        isSnooze: Boolean,
        soundUri: String,
    ) {
        if (!hasPostNotifications(context)) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val activityIntent = Intent(context, AlarmFiresActivity::class.java).apply {
            putExtra(AlarmsScheduler.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmsScheduler.EXTRA_KIND, AlarmsScheduler.KIND_ALARM)
            putExtra(AlarmsScheduler.EXTRA_SOUND_URI, soundUri)
            putExtra(AlarmFiresActivity.EXTRA_LABEL, label)
            putExtra(AlarmFiresActivity.EXTRA_SOFT, soft)
            putExtra(AlarmFiresActivity.EXTRA_IS_SNOOZE, isSnooze)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION
        }
        val full = PendingIntent.getActivity(
            context,
            ("full:$alarmId").hashCode() and 0x7FFFFFFF,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, NotificationChannels.ALARMS)
            .setSmallIcon(R.drawable.ic_still_clock_notification)
            .setContentTitle("alarm")
            .setContentText(label.ifBlank { "" })
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .build()

        nm.notify(NOTIF_ID_ALARM, notification)
    }

    private fun postTimerNotification(context: Context, soundUri: String): Boolean {
        if (!hasPostNotifications(context)) return false
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val activityIntent = Intent(context, AlarmFiresActivity::class.java).apply {
            putExtra(AlarmsScheduler.EXTRA_KIND, AlarmsScheduler.KIND_TIMER)
            putExtra(AlarmsScheduler.EXTRA_SOUND_URI, soundUri)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION
        }
        val full = PendingIntent.getActivity(
            context,
            ("full:timer").hashCode() and 0x7FFFFFFF,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, NotificationChannels.TIMER)
            .setSmallIcon(R.drawable.ic_still_clock_notification)
            .setContentTitle("time up")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .build()

        return runCatching { nm.notify(NOTIF_ID_TIMER, notification) }.isSuccess
    }

    private fun startFiresActivity(
        context: Context,
        alarmId: String,
        label: String,
        soft: Boolean,
        isSnooze: Boolean,
        kind: String,
        soundUri: String,
    ) {
        val intent = Intent(context, AlarmFiresActivity::class.java).apply {
            putExtra(AlarmsScheduler.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmsScheduler.EXTRA_KIND, kind)
            putExtra(AlarmsScheduler.EXTRA_SOUND_URI, soundUri)
            putExtra(AlarmFiresActivity.EXTRA_LABEL, label)
            putExtra(AlarmFiresActivity.EXTRA_SOFT, soft)
            putExtra(AlarmFiresActivity.EXTRA_IS_SNOOZE, isSnooze)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION
        }
        runCatching { context.startActivity(intent) }
    }

    private fun hasPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val NOTIF_ID_ALARM = 0xA1
        const val NOTIF_ID_TIMER = 0xB1
    }
}
