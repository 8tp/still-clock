package dev.chuds.stillclock.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.chuds.stillclock.ui.theme.StillColors
import dev.chuds.stillclock.ui.theme.StillTypography

/** Text-toggle: `[ on ]` / `[ off ]`. Color is the only emphasis. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StillToggle(
    on: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    Text(
        text = if (on) "[ on ]" else "[ off ]",
        style = StillTypography.Caption,
        color = if (on) StillColors.SoftWhite else StillColors.Gray,
        modifier = modifier
            .combinedClickable(
                interactionSource = source,
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 6.dp, horizontal = 4.dp),
    )
}
