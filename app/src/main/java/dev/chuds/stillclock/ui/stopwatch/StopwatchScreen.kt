package dev.chuds.stillclock.ui.stopwatch

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.chuds.stillclock.data.StopwatchState
import dev.chuds.stillclock.ui.components.SettingsHeader
import dev.chuds.stillclock.ui.components.StillDivider
import dev.chuds.stillclock.ui.components.StillVerb
import dev.chuds.stillclock.ui.theme.StillColors
import dev.chuds.stillclock.ui.theme.StillTypography
import kotlinx.coroutines.delay

@Composable
fun StopwatchScreen(
    state: StopwatchState,
    onOpenSettings: () -> Unit,
    onStartStop: () -> Unit,
    onLap: () -> Unit,
    onReset: () -> Unit,
) {
    val now by produceState(initialValue = System.currentTimeMillis(), key1 = state.startedAtEpochMs) {
        if (state.isRunning) {
            while (true) {
                value = System.currentTimeMillis()
                delay(50L)
            }
        }
    }
    val elapsedMs = state.elapsedMs(now)
    val running = state.isRunning
    val canReset = !running && elapsedMs > 0L

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
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = formatStopwatch(elapsedMs),
                    style = StillTypography.ClockFace,
                    color = StillColors.SoftWhite,
                )

                Spacer(Modifier.height(28.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    StillVerb(
                        label = if (running) "stop" else "start",
                        onClick = onStartStop,
                    )
                    StillVerb(
                        label = "lap",
                        enabled = running,
                        onClick = if (running) onLap else ({}),
                    )
                    StillVerb(
                        label = "reset",
                        enabled = canReset,
                        onClick = if (canReset) onReset else ({}),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            StillDivider(modifier = Modifier.padding(horizontal = 24.dp))

            // Lap list — newest first.
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, bottom = 96.dp),
            ) {
                val laps = state.laps
                val withTotals: List<Triple<Int, Long, Long>> = run {
                    var running = 0L
                    laps.mapIndexed { idx, split ->
                        running += split
                        Triple(idx + 1, split, running)
                    }
                }.reversed()

                items(items = withTotals, key = { it.first }) { (n, split, total) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(text = "lap $n", style = StillTypography.Caption, color = StillColors.Gray)
                        Text(text = formatStopwatch(split), style = StillTypography.Time, color = StillColors.SoftWhite)
                        Text(text = formatStopwatch(total), style = StillTypography.Caption, color = StillColors.MutedWhite)
                    }
                    StillDivider()
                }
            }
        }
    }
}

private fun formatStopwatch(ms: Long): String {
    val centis = (ms / 10) % 100
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d.%02d".format(h, m, s, centis) else "%02d:%02d.%02d".format(m, s, centis)
}
