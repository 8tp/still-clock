package dev.chuds.stillclock.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.chuds.stillclock.ui.theme.StillColors
import dev.chuds.stillclock.ui.theme.StillTypography

/**
 * Tiny numeric stepper: tap up to increment, tap down to decrement, with a prominent
 * centered current value. Mirrors still-cal's text-list picker shape but for a numeric range.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StillNumberPicker(
    value: Int,
    range: IntRange,
    label: String,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    pad: Int = 2,
) {
    val upSource = remember { MutableInteractionSource() }
    val downSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier.padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = StillTypography.Caption,
            color = StillColors.DimGray,
        )
        Text(
            text = "+",
            style = StillTypography.Title,
            color = StillColors.MutedWhite,
            modifier = Modifier
                .combinedClickable(
                    interactionSource = upSource,
                    indication = null,
                    onClick = {
                        val next = if (value >= range.last) range.first else value + 1
                        onValueChange(next)
                    },
                )
                .padding(vertical = 4.dp, horizontal = 16.dp),
        )
        Text(
            text = value.toString().padStart(pad, '0'),
            style = StillTypography.Time,
            color = StillColors.SoftWhite,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(72.dp),
        )
        Text(
            text = "−",
            style = StillTypography.Title,
            color = StillColors.MutedWhite,
            modifier = Modifier
                .combinedClickable(
                    interactionSource = downSource,
                    indication = null,
                    onClick = {
                        val next = if (value <= range.first) range.last else value - 1
                        onValueChange(next)
                    },
                )
                .padding(vertical = 4.dp, horizontal = 16.dp),
        )
    }
}
