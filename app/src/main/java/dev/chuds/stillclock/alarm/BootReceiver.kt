package dev.chuds.stillclock.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.chuds.stillclock.data.AlarmsRepository
import dev.chuds.stillclock.data.TimerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * AlarmManager forgets every armed alarm across reboots. This receiver re-arms them on
 * BOOT_COMPLETED after credential-protected app storage is available.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED) return

        NotificationChannels.ensure(context)
        val pending = goAsync()
        val app = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val alarmsRepo = AlarmsRepository(app)
                val alarms = alarmsRepo.snapshot()
                for (alarm in alarms.filter { it.enabled }) {
                    AlarmsScheduler.schedule(app, alarm)
                }

                TimerScheduler(app, TimerRepository(app)).recoverRunningTimer()
            } finally {
                pending.finish()
            }
        }
    }
}
