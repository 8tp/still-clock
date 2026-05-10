package dev.chuds.stillclock.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.chuds.stillclock.data.Tab
import dev.chuds.stillclock.ui.theme.StillColors
import dev.chuds.stillclock.ui.theme.StillTypography

/**
 * Bottom tab strip — four lowercase mono labels, color-only emphasis. No background fill,
 * no underline, no chip. Active tab is SoftWhite, others are Gray. No ripple.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StillTabBar(
    current: Tab,
    onTabSelected: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
    ) {
        StillDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 12.dp, start = 8.dp, end = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabCell("clock", current == Tab.Clock) { onTabSelected(Tab.Clock) }
            TabCell("alarms", current == Tab.Alarms) { onTabSelected(Tab.Alarms) }
            TabCell("timer", current == Tab.Timer) { onTabSelected(Tab.Timer) }
            TabCell("stopwatch", current == Tab.Stopwatch) { onTabSelected(Tab.Stopwatch) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabCell(label: String, active: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .combinedClickable(
                interactionSource = source,
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 6.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = StillTypography.Kicker,
            color = if (active) StillColors.SoftWhite else StillColors.Gray,
        )
    }
}
