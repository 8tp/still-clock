package dev.chuds.stillclock.ui.timer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.chuds.stillclock.data.TimerState
import dev.chuds.stillclock.ui.components.SettingsHeader
import dev.chuds.stillclock.ui.components.StillVerb
import dev.chuds.stillclock.ui.theme.StillColors
import dev.chuds.stillclock.ui.theme.StillTypography
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TimerScreen(
    state: TimerState,
    onOpenSettings: () -> Unit,
    onStart: (Long) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val now by produceState(initialValue = System.currentTimeMillis(), key1 = state.deadlineEpochMs) {
        while (true) {
            value = System.currentTimeMillis()
            delay(100L)
        }
    }

    val remainingMs = state.remainingMs(now)
    val finished = state.isRunning && remainingMs <= 0L

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

            Box(
                modifier = Modifier.fillMaxSize().padding(bottom = 96.dp),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    finished -> FinishedUi(onDismiss = onDismiss)
                    state.isIdle -> IdleUi(onStart = onStart)
                    state.isPaused -> RunningUi(remainingMs = remainingMs, paused = true, onPause = onPause, onResume = onResume, onCancel = onCancel)
                    state.isRunning -> RunningUi(remainingMs = remainingMs, paused = false, onPause = onPause, onResume = onResume, onCancel = onCancel)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IdleUi(onStart: (Long) -> Unit) {
    var typed by remember { mutableStateOf("") }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 32.dp),
    ) {
        Text(text = "timer", style = StillTypography.Caption, color = StillColors.DimGray)
        Spacer(Modifier.height(20.dp))

        // Two rows of chips.
        ChipRow(durations = listOf("1m" to 1, "3m" to 3, "5m" to 5, "10m" to 10), onStart = onStart)
        Spacer(Modifier.height(12.dp))
        ChipRow(durations = listOf("15m" to 15, "25m" to 25, "1h" to 60), onStart = onStart)

        Spacer(Modifier.height(28.dp))
        Text(text = "or type", style = StillTypography.Small, color = StillColors.DimGray)
        Spacer(Modifier.height(8.dp))

        BasicTextField(
            value = typed,
            onValueChange = { typed = it },
            singleLine = true,
            textStyle = StillTypography.Title.copy(color = StillColors.SoftWhite, textAlign = TextAlign.Center),
            cursorBrush = SolidColor(StillColors.SoftWhite),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                val ms = parseDuration(typed)
                if (ms != null && ms > 0L) onStart(ms)
            }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.Center) {
                    if (typed.isEmpty()) {
                        Text(
                            text = "mm  /  mm:ss  /  hh:mm:ss",
                            style = StillTypography.Title,
                            color = StillColors.DimGray,
                        )
                    }
                    inner()
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChipRow(
    durations: List<Pair<String, Int>>,
    onStart: (Long) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        for ((label, mins) in durations) {
            val source = remember(label) { MutableInteractionSource() }
            Text(
                text = label,
                style = StillTypography.Caption,
                color = StillColors.SoftWhite,
                modifier = Modifier
                    .border(1.dp, StillColors.Hairline, RoundedCornerShape(2.dp))
                    .combinedClickable(
                        interactionSource = source,
                        indication = null,
                        onClick = { onStart(mins.toLong() * 60_000L) },
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun RunningUi(
    remainingMs: Long,
    paused: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = formatTimerReadout(remainingMs),
            style = StillTypography.ClockFace,
            color = StillColors.SoftWhite,
        )
        Spacer(Modifier.height(40.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StillVerb(
                label = if (paused) "resume" else "pause",
                onClick = if (paused) onResume else onPause,
            )
            StillVerb(
                label = "cancel",
                onClick = onCancel,
                color = StillColors.MutedWhite,
            )
        }
    }
}

@Composable
private fun FinishedUi(onDismiss: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "0:00", style = StillTypography.ClockFace, color = StillColors.SoftWhite)
        Text(text = "time", style = StillTypography.Title, color = StillColors.MutedWhite)
        Text(text = "up", style = StillTypography.Title, color = StillColors.MutedWhite)
        Spacer(Modifier.height(32.dp))
        StillVerb(label = "dismiss", onClick = onDismiss)
    }
}

internal fun parseDuration(input: String): Long? {
    val s = input.trim()
    if (s.isEmpty()) return null
    val parts = s.split(":")
    return try {
        when (parts.size) {
            1 -> parts[0].toLong() * 60_000L
            2 -> parts[0].toLong() * 60_000L + parts[1].toLong() * 1_000L
            3 -> parts[0].toLong() * 3_600_000L + parts[1].toLong() * 60_000L + parts[2].toLong() * 1_000L
            else -> null
        }
    } catch (_: NumberFormatException) {
        null
    }
}

internal fun formatTimerReadout(remainingMs: Long): String {
    val totalSec = (remainingMs + 999L) / 1000L
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
