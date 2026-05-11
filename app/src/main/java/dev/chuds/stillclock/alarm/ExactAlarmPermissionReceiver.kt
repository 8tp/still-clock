package dev.chuds.stillclock.alarm

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import dev.chuds.stillclock.data.AlarmsRepository
import dev.chuds.stillclock.data.TimerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ExactAlarmPermissionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (intent.action != AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) return

        val appContext = context.applicationContext
        if (!AlarmsScheduler.canScheduleExactAlarms(appContext)) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                AlarmsRepository(appContext).snapshot()
                    .filter { it.enabled }
                    .forEach { alarm -> AlarmsScheduler.schedule(appContext, alarm) }
                TimerScheduler(appContext, TimerRepository(appContext)).recoverRunningTimer()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
