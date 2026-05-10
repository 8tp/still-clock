package dev.chuds.stillclock.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import dev.chuds.stillclock.R

/**
 * Two channels: alarms (high importance, full-screen-intent eligible) and timer (high too,
 * because the user did just ask for a thing to fire).
 */
object NotificationChannels {

    const val ALARMS = "alarms"
    const val TIMER = "timer"

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (nm.getNotificationChannel(ALARMS) == null) {
            val ch = NotificationChannel(
                ALARMS,
                context.getString(R.string.alarm_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.alarm_channel_description)
                setBypassDnd(false)
                setSound(null, null) // The fires-Activity owns the sound; channel stays quiet.
                enableVibration(false)
            }
            nm.createNotificationChannel(ch)
        }
        if (nm.getNotificationChannel(TIMER) == null) {
            val ch = NotificationChannel(
                TIMER,
                context.getString(R.string.timer_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.timer_channel_description)
                setSound(null, null)
                enableVibration(false)
            }
            nm.createNotificationChannel(ch)
        }
    }
}
