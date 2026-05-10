package dev.chuds.stillclock.ui.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.chuds.stillclock.data.ClockSettings
import dev.chuds.stillclock.data.FontPreset
import dev.chuds.stillclock.data.Tab
import dev.chuds.stillclock.data.TimeFormat
import dev.chuds.stillclock.data.bundledToneFor
import dev.chuds.stillclock.ui.components.StillMenuItem
import dev.chuds.stillclock.ui.components.StillVerb
import dev.chuds.stillclock.ui.sound.SoundPickerScreen
import dev.chuds.stillclock.ui.theme.StillColors
import dev.chuds.stillclock.ui.theme.StillTypography
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class SoundTarget { Alarm, Timer }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    settings: ClockSettings,
    onCycleFont: () -> Unit,
    onCycleTimeFormat: () -> Unit,
    onToggleSeconds: () -> Unit,
    onSetSecondZone: (String) -> Unit,
    onCycleDefaultTab: () -> Unit,
    onSetAlarmSound: (uri: String, displayName: String) -> Unit,
    onSetTimerSound: (uri: String, displayName: String) -> Unit,
    onCycleSnooze: () -> Unit,
    onToggleHaptics: () -> Unit,
    onBack: () -> Unit,
) {
    var zoneInput by remember(settings.secondZone) { mutableStateOf(settings.secondZone) }
    var zoneOpen by remember { mutableStateOf(false) }
    var soundPicker by remember { mutableStateOf<SoundTarget?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(StillColors.OledBlack),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 96.dp),
        ) {
            Text(text = "settings", style = StillTypography.Title, color = StillColors.SoftWhite)
            Text(
                text = "still clock · v0.1.0",
                style = StillTypography.Caption,
                color = StillColors.DimGray,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
            )

            StillMenuItem(
                title = "font",
                subtitle = settings.fontPreset.label(),
                onClick = onCycleFont,
            )
            StillMenuItem(
                title = "time format",
                subtitle = settings.timeFormat.label(),
                onClick = onCycleTimeFormat,
            )
            StillMenuItem(
                title = "seconds on clock",
                subtitle = if (settings.secondsOnClock) "on" else "off",
                onClick = onToggleSeconds,
            )
            val zoneSubtitle = if (settings.secondZone.isBlank()) "no second zone" else resolveZone(settings.secondZone)
            StillMenuItem(
                title = "second zone",
                subtitle = zoneSubtitle,
                onClick = { zoneOpen = !zoneOpen },
            )
            if (zoneOpen) {
                BasicTextField(
                    value = zoneInput,
                    onValueChange = { zoneInput = it },
                    singleLine = true,
                    textStyle = StillTypography.Title.copy(color = StillColors.SoftWhite),
                    cursorBrush = SolidColor(StillColors.SoftWhite),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 8.dp),
                    decorationBox = { inner ->
                        Box {
                            if (zoneInput.isEmpty()) {
                                Text(
                                    text = "iana id, e.g. europe/berlin",
                                    style = StillTypography.Title,
                                    color = StillColors.DimGray,
                                )
                            }
                            inner()
                        }
                    },
                )
                Row {
                    val saveSource = remember { MutableInteractionSource() }
                    val clearSource = remember { MutableInteractionSource() }
                    Text(
                        text = "save",
                        style = StillTypography.Caption,
                        color = StillColors.SoftWhite,
                        modifier = Modifier
                            .combinedClickable(
                                interactionSource = saveSource,
                                indication = null,
                                onClick = {
                                    onSetSecondZone(zoneInput.trim())
                                    zoneOpen = false
                                },
                            )
                            .padding(top = 8.dp, bottom = 8.dp, end = 16.dp),
                    )
                    Text(
                        text = "clear",
                        style = StillTypography.Caption,
                        color = StillColors.MutedWhite,
                        modifier = Modifier
                            .combinedClickable(
                                interactionSource = clearSource,
                                indication = null,
                                onClick = {
                                    zoneInput = ""
                                    onSetSecondZone("")
                                    zoneOpen = false
                                },
                            )
                            .padding(vertical = 8.dp),
                    )
                }
            }
            StillMenuItem(
                title = "default tab",
                subtitle = settings.defaultTab.label(),
                onClick = onCycleDefaultTab,
            )
            StillMenuItem(
                title = "default alarm sound",
                subtitle = soundSubtitle(settings.alarmSoundUri, settings.alarmSoundDisplayName, "still chime"),
                onClick = { soundPicker = SoundTarget.Alarm },
            )
            StillMenuItem(
                title = "timer sound",
                subtitle = soundSubtitle(settings.timerSoundUri, settings.timerSoundDisplayName, "still chime"),
                onClick = { soundPicker = SoundTarget.Timer },
            )
            StillMenuItem(
                title = "snooze duration",
                subtitle = "${settings.snoozeMinutes} ${if (settings.snoozeMinutes == 1) "minute" else "minutes"}",
                onClick = onCycleSnooze,
            )
            StillMenuItem(
                title = "haptic feedback",
                subtitle = if (settings.hapticsEnabled) "subtle vibration on taps" else "off",
                onClick = onToggleHaptics,
            )
        }

        FooterBar(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .navigationBarsPadding(),
            onBack = onBack,
        )

        soundPicker?.let { target ->
            val isAlarm = target == SoundTarget.Alarm
            SoundPickerScreen(
                title = if (isAlarm) "default alarm sound" else "timer sound",
                currentUri = if (isAlarm) settings.alarmSoundUri else settings.timerSoundUri,
                onPick = { uri, name ->
                    if (isAlarm) onSetAlarmSound(uri, name) else onSetTimerSound(uri, name)
                    soundPicker = null
                },
                onBack = { soundPicker = null },
            )
        }
    }
}

@Composable
private fun FooterBar(modifier: Modifier = Modifier, onBack: () -> Unit) {
    Row(
        modifier = modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StillVerb(
            label = "back",
            onClick = onBack,
            bordered = true,
            color = StillColors.MutedWhite,
        )
    }
}

private fun soundSubtitle(uri: String, displayName: String, defaultLabel: String): String {
    val bundled = bundledToneFor(uri)
    return when {
        bundled != null -> "still ${bundled.label}"
        uri.isBlank() -> defaultLabel
        displayName.isNotBlank() -> displayName
        else -> "system sound"
    }
}

private fun FontPreset.label(): String = when (this) {
    FontPreset.System -> "system — serif + sans + mono"
    FontPreset.Editorial -> "editorial — cormorant + inter + plex"
    FontPreset.Terminal -> "terminal — plex mono throughout"
    FontPreset.Grotesk -> "grotesk — instrument serif + space"
}

private fun TimeFormat.label(): String = when (this) {
    TimeFormat.Twelve -> "12-hour"
    TimeFormat.TwentyFour -> "24-hour"
    TimeFormat.System -> "system"
}

private fun Tab.label(): String = when (this) {
    Tab.Clock -> "clock"
    Tab.Alarms -> "alarms"
    Tab.Timer -> "timer"
    Tab.Stopwatch -> "stopwatch"
}

private fun resolveZone(zoneId: String): String {
    val zone = runCatching { ZoneId.of(zoneId) }.getOrNull()
        ?: return "$zoneId — invalid"
    val zdt = ZonedDateTime.now(zone)
    val text = zdt.format(DateTimeFormatter.ofPattern("HH:mm", Locale.US))
    return "$zoneId  ·  $text"
}
