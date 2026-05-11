package dev.chuds.stillclock.ui.alarms

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import dev.chuds.stillclock.data.Alarm
import dev.chuds.stillclock.data.ClockSettings
import dev.chuds.stillclock.data.TimeFormat
import dev.chuds.stillclock.data.bundledToneFor
import dev.chuds.stillclock.ui.components.StillDivider
import dev.chuds.stillclock.ui.components.StillMenuItem
import dev.chuds.stillclock.ui.components.StillNumberPicker
import dev.chuds.stillclock.ui.components.StillToggle
import dev.chuds.stillclock.ui.components.StillVerb
import dev.chuds.stillclock.ui.sound.SoundPickerScreen
import dev.chuds.stillclock.ui.theme.StillColors
import dev.chuds.stillclock.ui.theme.StillTypography
import java.time.DayOfWeek
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlarmEditScreen(
    initial: Alarm,
    isNew: Boolean,
    settings: ClockSettings,
    is24HourSystem: Boolean,
    onSave: (Alarm) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    var hour by remember(initial.id) { mutableStateOf(initial.hour) }
    var minute by remember(initial.id) { mutableStateOf(initial.minute) }
    var label by remember(initial.id) { mutableStateOf(initial.label) }
    var days by remember(initial.id) { mutableStateOf(initial.daysOfWeek) }
    var soft by remember(initial.id) { mutableStateOf(initial.soft) }
    var soundUri by remember(initial.id) { mutableStateOf(initial.soundUri ?: "") }
    var soundDisplayName by remember(initial.id) { mutableStateOf(initial.soundDisplayName ?: "") }
    var pickerOpen by remember { mutableStateOf(false) }
    var soundPickerOpen by remember { mutableStateOf(false) }

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
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 120.dp),
        ) {
            Text(
                text = if (isNew) "new alarm" else "edit alarm",
                style = StillTypography.Caption,
                color = StillColors.DimGray,
            )
            Spacer(Modifier.height(24.dp))

            // Time row, tap to reveal picker.
            val source = remember { MutableInteractionSource() }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        interactionSource = source,
                        indication = null,
                        onClick = { pickerOpen = !pickerOpen },
                    )
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = formatTime(hour, minute, use24h),
                    style = StillTypography.ClockFace,
                    color = StillColors.SoftWhite,
                )
                if (!use24h) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (hour < 12) "am" else "pm",
                        style = StillTypography.Caption,
                        color = StillColors.MutedWhite,
                        modifier = Modifier.padding(bottom = 18.dp),
                    )
                }
            }

            if (pickerOpen) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val displayHour = if (use24h) hour else ((hour % 12).let { if (it == 0) 12 else it })
                    StillNumberPicker(
                        value = if (use24h) hour else displayHour,
                        range = if (use24h) 0..23 else 1..12,
                        label = "hour",
                        onValueChange = { v ->
                            hour = if (use24h) v else {
                                val isPm = hour >= 12
                                val twentyFour = if (v == 12) 0 else v
                                if (isPm) (twentyFour + 12) % 24 else twentyFour
                            }
                        },
                    )
                    Spacer(Modifier.width(20.dp))
                    StillNumberPicker(
                        value = minute,
                        range = 0..59,
                        label = "min",
                        onValueChange = { minute = it },
                    )
                    if (!use24h) {
                        Spacer(Modifier.width(20.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("am/pm", style = StillTypography.Caption, color = StillColors.DimGray)
                            Spacer(Modifier.height(4.dp))
                            val toggleSource = remember { MutableInteractionSource() }
                            Text(
                                text = if (hour < 12) "am" else "pm",
                                style = StillTypography.Title,
                                color = StillColors.SoftWhite,
                                modifier = Modifier.combinedClickable(
                                    interactionSource = toggleSource,
                                    indication = null,
                                    onClick = { hour = (hour + 12) % 24 },
                                ).padding(8.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            StillDivider()
            Spacer(Modifier.height(16.dp))

            Text(text = "label", style = StillTypography.Caption, color = StillColors.DimGray)
            Spacer(Modifier.height(4.dp))
            BasicTextField(
                value = label,
                onValueChange = { label = it },
                singleLine = true,
                textStyle = StillTypography.Title.copy(color = StillColors.SoftWhite),
                cursorBrush = SolidColor(StillColors.SoftWhite),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Box {
                        if (label.isEmpty()) {
                            Text(
                                text = "untitled",
                                style = StillTypography.Title,
                                color = StillColors.DimGray,
                            )
                        }
                        inner()
                    }
                },
            )

            Spacer(Modifier.height(24.dp))
            StillDivider()
            Spacer(Modifier.height(16.dp))

            Text(text = "days", style = StillTypography.Caption, color = StillColors.DimGray)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val map = listOf(
                    DayOfWeek.SUNDAY to "s",
                    DayOfWeek.MONDAY to "m",
                    DayOfWeek.TUESDAY to "t",
                    DayOfWeek.WEDNESDAY to "w",
                    DayOfWeek.THURSDAY to "t",
                    DayOfWeek.FRIDAY to "f",
                    DayOfWeek.SATURDAY to "s",
                )
                for ((d, l) in map) {
                    val active = d in days
                    val sourceDay = remember(d) { MutableInteractionSource() }
                    Text(
                        text = l,
                        style = StillTypography.Title,
                        color = if (active) StillColors.SoftWhite else StillColors.DimGray,
                        modifier = Modifier
                            .combinedClickable(
                                interactionSource = sourceDay,
                                indication = null,
                                onClick = {
                                    days = if (active) days - d else days + d
                                },
                            )
                            .padding(8.dp),
                    )
                }
            }
            Text(
                text = if (days.isEmpty()) "fires once, then disables"
                else "repeats every " + days.sortedBy { it.value }.joinToString(", ") { it.name.lowercase(Locale.US) },
                style = StillTypography.Caption,
                color = StillColors.Gray,
                modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(Modifier.height(24.dp))
            StillDivider()
            Spacer(Modifier.height(16.dp))

            StillMenuItem(
                title = "sound",
                subtitle = alarmSoundSubtitle(soundUri, soundDisplayName, settings),
                onClick = { soundPickerOpen = true },
            )

            Spacer(Modifier.height(8.dp))
            StillDivider()
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "soft", style = StillTypography.Menu, color = StillColors.SoftWhite)
                StillToggle(on = soft, onClick = { soft = !soft })
            }
            if (soft) {
                Text(
                    text = "tone ramps from silent over 30 seconds",
                    style = StillTypography.Caption,
                    color = StillColors.Gray,
                )
            }
        }

        FooterActions(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            isNew = isNew,
            onSave = {
                onSave(
                    initial.copy(
                        hour = hour,
                        minute = minute,
                        label = label,
                        daysOfWeek = days,
                        soft = soft,
                        enabled = true,
                        soundUri = soundUri.takeIf { it.isNotBlank() },
                        soundDisplayName = soundDisplayName.takeIf { it.isNotBlank() },
                    ),
                )
            },
            onDelete = onDelete,
            onCancel = onCancel,
        )

        if (soundPickerOpen) {
            SoundPickerScreen(
                title = "alarm sound",
                currentUri = soundUri,
                onPick = { uri, name ->
                    soundUri = uri
                    soundDisplayName = name
                    soundPickerOpen = false
                },
                onBack = { soundPickerOpen = false },
            )
        }
    }
}

private fun alarmSoundSubtitle(
    uri: String,
    displayName: String,
    settings: ClockSettings,
): String {
    if (uri.isBlank()) {
        val defaultBundled = bundledToneFor(settings.alarmSoundUri)
        val defaultName = when {
            defaultBundled != null -> "still ${defaultBundled.label}"
            settings.alarmSoundDisplayName.isNotBlank() -> settings.alarmSoundDisplayName
            settings.alarmSoundUri.isNotBlank() -> "system sound"
            else -> "still chime"
        }
        return "default · $defaultName"
    }
    val bundled = bundledToneFor(uri)
    return when {
        bundled != null -> "still ${bundled.label}"
        displayName.isNotBlank() -> displayName
        else -> "system sound"
    }
}

@Composable
private fun FooterActions(
    modifier: Modifier = Modifier,
    isNew: Boolean,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(modifier = modifier.padding(bottom = 32.dp).background(StillColors.OledBlack)) {
        StillDivider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StillVerb(
                label = "cancel",
                onClick = onCancel,
                bordered = true,
                color = StillColors.MutedWhite,
            )
            if (!isNew) {
                StillVerb(
                    label = "delete",
                    onClick = onDelete,
                    bordered = true,
                    color = StillColors.Gray,
                )
            }
            StillVerb(
                label = "save",
                onClick = onSave,
                bordered = true,
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
