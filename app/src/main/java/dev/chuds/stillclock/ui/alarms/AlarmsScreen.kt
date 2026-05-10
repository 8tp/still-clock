package dev.chuds.stillclock.ui.alarms

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.chuds.stillclock.alarm.AlarmScheduling
import dev.chuds.stillclock.data.Alarm
import dev.chuds.stillclock.data.ClockSettings
import dev.chuds.stillclock.data.TimeFormat
import dev.chuds.stillclock.ui.components.SettingsHeader
import dev.chuds.stillclock.ui.components.StillDivider
import dev.chuds.stillclock.ui.components.StillToggle
import dev.chuds.stillclock.ui.theme.StillColors
import dev.chuds.stillclock.ui.theme.StillTypography
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlarmsScreen(
    alarms: List<Alarm>,
    settings: ClockSettings,
    is24HourSystem: Boolean,
    onOpenSettings: () -> Unit,
    onTapAlarm: (String) -> Unit,
    onLongPressAlarm: (String) -> Unit,
    onToggleEnabled: (String) -> Unit,
    onNew: () -> Unit,
) {
    val use24h = when (settings.timeFormat) {
        TimeFormat.Twelve -> false
        TimeFormat.TwentyFour -> true
        TimeFormat.System -> is24HourSystem
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(StillColors.OledBlack),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            SettingsHeader(onSettings = onOpenSettings)
            Text(
                text = "alarms",
                style = StillTypography.Title,
                color = StillColors.SoftWhite,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
            )

            if (alarms.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(bottom = 160.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "no alarms",
                        style = StillTypography.Caption,
                        color = StillColors.DimGray,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp),
                ) {
                    items(items = alarms, key = { it.id }) { alarm ->
                        AlarmRow(
                            alarm = alarm,
                            use24h = use24h,
                            onClick = { onTapAlarm(alarm.id) },
                            onLongClick = { onLongPressAlarm(alarm.id) },
                            onToggle = { onToggleEnabled(alarm.id) },
                        )
                        StillDivider()
                    }
                    items(items = listOf(Unit)) {
                        Spacer(Modifier.height(140.dp))
                    }
                }
            }
        }

        FooterNew(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            onNew = onNew,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlarmRow(
    alarm: Alarm,
    use24h: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggle: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = source,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f, fill = true)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = formatTime(alarm.hour, alarm.minute, use24h),
                    style = StillTypography.Time,
                    color = if (alarm.enabled) StillColors.SoftWhite else StillColors.DimGray,
                )
                if (!use24h) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (alarm.hour < 12) "am" else "pm",
                        style = StillTypography.Caption,
                        color = StillColors.MutedWhite,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
            if (alarm.label.isNotBlank()) {
                Text(
                    text = alarm.label,
                    style = StillTypography.Title,
                    color = if (alarm.enabled) StillColors.MutedWhite else StillColors.DimGray,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(
                text = if (alarm.daysOfWeek.isEmpty()) "once" else AlarmScheduling.daysOfWeekLine(alarm.daysOfWeek),
                style = StillTypography.Caption,
                color = if (alarm.enabled) StillColors.Gray else StillColors.DimGray,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        StillToggle(on = alarm.enabled, onClick = onToggle)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FooterNew(
    modifier: Modifier = Modifier,
    onNew: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    Column(modifier = modifier.padding(bottom = 92.dp)) {
        StillDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "new",
                style = StillTypography.Menu,
                color = StillColors.SoftWhite,
                modifier = Modifier.combinedClickable(
                    interactionSource = source,
                    indication = null,
                    onClick = onNew,
                ),
            )
        }
    }
}

private fun formatTime(hour: Int, minute: Int, use24h: Boolean): String {
    return if (use24h) {
        "%02d:%02d".format(hour, minute)
    } else {
        val h12 = ((hour % 12).let { if (it == 0) 12 else it })
        "$h12:%02d".format(minute)
    }
}
