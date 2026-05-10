package dev.chuds.stillclock

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.chuds.stillclock.alarm.AlarmsScheduler
import dev.chuds.stillclock.ui.theme.StillTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        val initialAlarmId = consumeAlarmEditExtra(intent)

        setContent {
            StillTheme {
                StillClockApp(initialAlarmEditId = initialAlarmId)
            }
        }
    }

    private fun consumeAlarmEditExtra(intent: Intent?): String? {
        if (intent?.action != ACTION_OPEN_ALARM_EDIT) return null
        val id = intent.getStringExtra(AlarmsScheduler.EXTRA_ALARM_ID)
        intent.action = null
        intent.removeExtra(AlarmsScheduler.EXTRA_ALARM_ID)
        return id
    }

    companion object {
        const val ACTION_OPEN_ALARM_EDIT = "dev.chuds.stillclock.action.OPEN_ALARM_EDIT"
    }
}
