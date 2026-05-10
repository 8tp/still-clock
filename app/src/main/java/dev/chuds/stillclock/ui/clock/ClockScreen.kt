package dev.chuds.stillclock.ui.clock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.chuds.stillclock.data.ClockSettings
import dev.chuds.stillclock.data.TimeFormat
import dev.chuds.stillclock.ui.components.SettingsHeader
import dev.chuds.stillclock.ui.components.StillDivider
import dev.chuds.stillclock.ui.theme.StillColors
import dev.chuds.stillclock.ui.theme.StillTypography
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun ClockScreen(
    settings: ClockSettings,
    is24HourSystem: Boolean,
    onOpenSettings: () -> Unit,
) {
    val now by produceState(initialValue = LocalDateTime.now(), key1 = settings.secondsOnClock) {
        while (true) {
            value = LocalDateTime.now()
            delay(if (settings.secondsOnClock) 200L else 5_000L)
        }
    }

    val use24h = when (settings.timeFormat) {
        TimeFormat.Twelve -> false
        TimeFormat.TwentyFour -> true
        TimeFormat.System -> is24HourSystem
    }

    val pattern = buildString {
        append(if (use24h) "HH:mm" else "h:mm")
        if (settings.secondsOnClock) append(":ss")
    }
    val timeFmt = remember(pattern) { DateTimeFormatter.ofPattern(pattern) }
    val ampmFmt = remember { DateTimeFormatter.ofPattern("a", Locale.US) }

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

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 96.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = now.format(timeFmt),
                    style = StillTypography.ClockFace,
                    color = StillColors.SoftWhite,
                    textAlign = TextAlign.Center,
                )
                if (!use24h) {
                    Text(
                        text = now.format(ampmFmt).lowercase(Locale.US),
                        style = StillTypography.Caption,
                        color = StillColors.MutedWhite,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    text = formatDate(now.toLocalDate()),
                    style = StillTypography.Title,
                    color = StillColors.MutedWhite,
                )

                if (settings.secondZone.isNotBlank()) {
                    Spacer(Modifier.height(28.dp))
                    StillDivider(modifier = Modifier.padding(horizontal = 96.dp))
                    Spacer(Modifier.height(12.dp))
                    val zoneText = remember(settings.secondZone, now) {
                        formatSecondZone(settings.secondZone, use24h)
                    }
                    Text(
                        text = zoneText,
                        style = StillTypography.Caption,
                        color = StillColors.Gray,
                    )
                }
            }
        }
    }
}

private fun formatDate(date: LocalDate): String {
    val day = date.dayOfWeek.name.lowercase(Locale.US)
    val month = date.month.name.lowercase(Locale.US)
    return "$day, $month ${date.dayOfMonth}"
}

private fun formatSecondZone(zoneId: String, use24h: Boolean): String {
    val zone = runCatching { ZoneId.of(zoneId) }.getOrNull() ?: return ""
    val zdt = ZonedDateTime.now(zone)
    val pattern = if (use24h) "HH:mm" else "h:mm a"
    val text = zdt.format(DateTimeFormatter.ofPattern(pattern, Locale.US))
    val city = zoneId.substringAfterLast('/').replace('_', ' ').lowercase(Locale.US)
    return "$city  ${text.lowercase(Locale.US)}"
}
