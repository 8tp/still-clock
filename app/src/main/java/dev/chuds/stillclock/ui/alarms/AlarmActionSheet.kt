package dev.chuds.stillclock.ui.alarms

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import dev.chuds.stillclock.ui.theme.StillColors
import dev.chuds.stillclock.ui.theme.StillTypography

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlarmActionSheet(
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dismissSource = remember { MutableInteractionSource() }
    val editSource = remember { MutableInteractionSource() }
    val deleteSource = remember { MutableInteractionSource() }
    val cancelSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(StillColors.OledBlack.copy(alpha = 0.94f))
            .combinedClickable(
                interactionSource = dismissSource,
                indication = null,
                onClick = onDismiss,
            ),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 28.dp),
        ) {
            Text(
                text = "edit",
                style = StillTypography.Menu,
                color = StillColors.SoftWhite,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        interactionSource = editSource,
                        indication = null,
                        onClick = onEdit,
                    )
                    .padding(vertical = 10.dp),
            )
            Text(
                text = "delete",
                style = StillTypography.Menu,
                color = StillColors.SoftWhite,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        interactionSource = deleteSource,
                        indication = null,
                        onClick = onDelete,
                    )
                    .padding(vertical = 10.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "cancel",
                style = StillTypography.Menu,
                color = StillColors.Gray,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        interactionSource = cancelSource,
                        indication = null,
                        onClick = onDismiss,
                    )
                    .padding(vertical = 10.dp),
            )
        }
    }
}
