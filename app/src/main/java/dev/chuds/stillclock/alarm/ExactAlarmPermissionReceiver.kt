package dev.chuds.stillclock.alarm

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
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
        if (!ExactAlarmRecoveryCoordinator.tryClaim()) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                ExactAlarmRecoveryCoordinator.sweep(appContext)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
