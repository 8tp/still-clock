package dev.chuds.stillclock.ui.sound

import android.content.Intent
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.chuds.stillclock.data.BUNDLED_TONES
import dev.chuds.stillclock.data.BundledTone
import dev.chuds.stillclock.data.bundledToneFor
import dev.chuds.stillclock.ui.components.StillDivider
import dev.chuds.stillclock.ui.components.StillMenuItem
import dev.chuds.stillclock.ui.components.StillVerb
import dev.chuds.stillclock.ui.theme.StillColors
import dev.chuds.stillclock.ui.theme.StillTypography

/**
 * One-screen sound picker. Lists the four bundled CC0 tones with an inline
 * `preview` verb, plus a `system sounds…` row that falls through to Android's
 * RingtoneManager.ACTION_RINGTONE_PICKER for users who want their own file.
 *
 * `onPick` is invoked with (uri, displayName) on selection. The caller is
 * expected to hide the picker; this screen does not auto-dismiss.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SoundPickerScreen(
    title: String,
    currentUri: String,
    onPick: (uri: String, displayName: String) -> Unit,
    onBack: () -> Unit,
) {
    val activityContext = LocalContext.current

    var preview by remember { mutableStateOf<MediaPlayer?>(null) }
    var previewingSlug by remember { mutableStateOf<String?>(null) }

    fun stopPreview() {
        preview?.let {
            runCatching { if (it.isPlaying) it.stop() }
            runCatching { it.release() }
        }
        preview = null
        previewingSlug = null
    }

    fun playPreview(tone: BundledTone) {
        stopPreview()
        val mp = MediaPlayer.create(activityContext, tone.rawResId) ?: return
        mp.setOnCompletionListener {
            previewingSlug = null
            runCatching { it.release() }
            if (preview === it) preview = null
        }
        mp.start()
        preview = mp
        previewingSlug = tone.slug
    }

    DisposableEffect(Unit) {
        onDispose { stopPreview() }
    }

    val ringtoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        if (data != null) {
            val uri: Uri? = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (uri != null) {
                val displayName = runCatching {
                    RingtoneManager.getRingtone(activityContext, uri).getTitle(activityContext)
                }.getOrNull().orEmpty()
                stopPreview()
                onPick(uri.toString(), displayName)
            }
        }
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
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 96.dp),
        ) {
            Text(text = title, style = StillTypography.Title, color = StillColors.SoftWhite)
            Text(
                text = "tap to choose · preview to hear",
                style = StillTypography.Caption,
                color = StillColors.DimGray,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
            )

            val currentTone = bundledToneFor(currentUri)
            val currentIsSystem = currentUri.isNotBlank() && currentTone == null

            BUNDLED_TONES.forEach { tone ->
                BundledToneRow(
                    tone = tone,
                    selected = currentTone?.slug == tone.slug,
                    previewing = previewingSlug == tone.slug,
                    onSelect = {
                        stopPreview()
                        onPick(tone.uri, tone.label)
                    },
                    onPreview = {
                        if (previewingSlug == tone.slug) stopPreview() else playPreview(tone)
                    },
                )
                StillDivider()
            }

            val systemSubtitle = if (currentIsSystem) {
                val name = runCatching {
                    RingtoneManager.getRingtone(activityContext, Uri.parse(currentUri))
                        .getTitle(activityContext)
                }.getOrNull()
                if (!name.isNullOrBlank()) "currently: $name" else "currently selected"
            } else {
                "pick any ringtone on this device"
            }
            StillMenuItem(
                title = "system sounds…",
                subtitle = systemSubtitle,
                onClick = {
                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        putExtra(
                            RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                            currentUri.takeIf { it.isNotBlank() && currentTone == null }
                                ?.let { Uri.parse(it) }
                                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                        )
                    }
                    runCatching { ringtoneLauncher.launch(intent) }
                        .onFailure {
                            Toast.makeText(
                                activityContext,
                                "no ringtone picker available",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                },
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StillVerb(
                label = "back",
                onClick = { stopPreview(); onBack() },
                bordered = true,
                color = StillColors.MutedWhite,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BundledToneRow(
    tone: BundledTone,
    selected: Boolean,
    previewing: Boolean,
    onSelect: () -> Unit,
    onPreview: () -> Unit,
) {
    val rowSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = rowSource,
                indication = null,
                onClick = onSelect,
            )
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selected) {
                    Text(
                        text = "·  ",
                        style = StillTypography.Title,
                        color = StillColors.SoftWhite,
                    )
                }
                Text(
                    text = tone.label,
                    style = StillTypography.Title,
                    color = StillColors.SoftWhite,
                )
            }
            Text(
                text = tone.description,
                style = StillTypography.Caption,
                color = StillColors.DimGray,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        StillVerb(
            label = if (previewing) "stop" else "preview",
            onClick = onPreview,
            bordered = true,
            color = StillColors.MutedWhite,
        )
    }
}
