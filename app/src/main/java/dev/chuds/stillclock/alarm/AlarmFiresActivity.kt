// Full-screen alarm-fires Activity. The sound URI is resolved by AlarmReceiver
// (which already runs on Dispatchers.IO and can read prefs without main-thread
// gymnastics) and passed in via EXTRA_SOUND_URI. We fall back to bundled chime
// then system default if playback fails.
// `still://<slug>` URIs resolve to res/raw via data/SoundCatalog.kt.
package dev.chuds.stillclock.alarm

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.chuds.stillclock.data.BUNDLED_TONES
import dev.chuds.stillclock.data.PreferencesRepository
import dev.chuds.stillclock.data.bundledToneFor
import dev.chuds.stillclock.ui.theme.LocalStillTypography
import dev.chuds.stillclock.ui.theme.StillColors
import dev.chuds.stillclock.ui.theme.StillTheme
import dev.chuds.stillclock.ui.theme.StillTypography
import dev.chuds.stillclock.ui.theme.stillTypographyFor
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AlarmFiresActivity : ComponentActivity() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var rampJob: Job? = null
    private val scope = MainScope()
    private var timerAutoDismissJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { c ->
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
        }

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "stillclock:fires",
        ).apply { setReferenceCounted(false); acquire(15 * 60_000L) }

        val kind = intent.getStringExtra(AlarmsScheduler.EXTRA_KIND) ?: AlarmsScheduler.KIND_ALARM
        val alarmId = intent.getStringExtra(AlarmsScheduler.EXTRA_ALARM_ID).orEmpty()
        val label = intent.getStringExtra(EXTRA_LABEL).orEmpty()
        val soft = intent.getBooleanExtra(EXTRA_SOFT, false)
        val isSnooze = intent.getBooleanExtra(EXTRA_IS_SNOOZE, false)
        val requestedSoundUri = intent.getStringExtra(AlarmsScheduler.EXTRA_SOUND_URI).orEmpty()

        startSoundAndVibration(soft, requestedSoundUri)

        if (kind == AlarmsScheduler.KIND_TIMER) {
            timerAutoDismissJob = scope.launch {
                delay(10_000L)
                finishAndQuiet()
            }
        }

        setContent {
            StillTheme {
                val context = LocalContext.current.applicationContext
                val preferences = remember(context) { PreferencesRepository(context) }
                val settingsState = remember(preferences) {
                    preferences.settings.stateIn(
                        scope = scope,
                        started = SharingStarted.Eagerly,
                        initialValue = dev.chuds.stillclock.data.ClockSettings(),
                    )
                }
                val settings by settingsState.collectAsState()
                val typography = remember(settings.fontPreset) { stillTypographyFor(settings.fontPreset) }

                CompositionLocalProvider(LocalStillTypography provides typography) {
                    FiresUi(
                        kind = kind,
                        label = label,
                        is24Hour = when (settings.timeFormat) {
                            dev.chuds.stillclock.data.TimeFormat.Twelve -> false
                            dev.chuds.stillclock.data.TimeFormat.TwentyFour -> true
                            dev.chuds.stillclock.data.TimeFormat.System ->
                                android.text.format.DateFormat.is24HourFormat(this@AlarmFiresActivity)
                        },
                        onDismiss = {
                            handleDismiss(alarmId, isSnooze, kind)
                        },
                        onSnooze = {
                            handleSnooze(alarmId, kind, settings.snoozeMinutes)
                        },
                    )
                }
            }
        }
    }

    private fun handleDismiss(alarmId: String, isSnooze: Boolean, kind: String) {
        if (isSnooze) AlarmsScheduler.cancelSnooze(this, alarmId)
        finishAndQuiet()
    }

    private fun handleSnooze(alarmId: String, kind: String, snoozeMinutes: Int) {
        if (kind == AlarmsScheduler.KIND_ALARM && alarmId.isNotEmpty()) {
            val triggerMs = System.currentTimeMillis() + snoozeMinutes.coerceAtLeast(1) * 60_000L
            val armed = AlarmsScheduler.scheduleSnooze(this, alarmId, triggerMs)
            if (!armed) {
                android.widget.Toast.makeText(
                    this,
                    "snooze unavailable — grant exact alarm",
                    android.widget.Toast.LENGTH_LONG,
                ).show()
                // Don't dismiss: keep the alarm ringing so the user has another chance
                // to act after seeing the toast.
                return
            }
        }
        finishAndQuiet()
    }

    private fun finishAndQuiet() {
        rampJob?.cancel()
        rampJob = null
        timerAutoDismissJob?.cancel()
        timerAutoDismissJob = null
        runCatching {
            mediaPlayer?.let { it.stop(); it.release() }
        }
        mediaPlayer = null
        runCatching { vibrator?.cancel() }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(AlarmReceiver.NOTIF_ID_ALARM)
        nm.cancel(AlarmReceiver.NOTIF_ID_TIMER)
        finish()
    }

    private fun startSoundAndVibration(soft: Boolean, requestedSoundUri: String) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val target = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            .coerceAtLeast(1)
            .toFloat()
            .let { it / audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM).coerceAtLeast(1) }

        val initialVolume = if (soft) 0f else target

        val attrs = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_ALARM)
            .build()

        val tryOrder = listOf(requestedSoundUri, BUNDLED_TONES.first().uri)
            .filter { it.isNotBlank() }
            .distinct()

        var player: MediaPlayer? = null
        for (uri in tryOrder) {
            player = playFromUri(uri, attrs, initialVolume)
            if (player != null) break
        }
        if (player == null) player = playSystemDefault(attrs, initialVolume)

        if (player == null) {
            Log.w(TAG, "no playable audio source — vibration only")
            startVibration()
            return
        }
        mediaPlayer = player

        if (soft) {
            rampJob = scope.launch(Dispatchers.Main) {
                val totalSteps = 120
                for (i in 1..totalSteps) {
                    delay(250L)
                    val frac = AlarmScheduling.softRampCoefficient(i, totalSteps) * target
                    runCatching { player.setVolume(frac, frac) }
                }
            }
        }

        startVibration()
    }

    private fun playFromUri(
        uriString: String,
        attrs: AudioAttributes,
        initialVolume: Float,
    ): MediaPlayer? = runCatching {
        val mp = MediaPlayer()
        mp.setAudioAttributes(attrs)
        val bundled = bundledToneFor(uriString)
        if (bundled != null) {
            val afd = resources.openRawResourceFd(bundled.rawResId)
                ?: error("missing raw resource for ${bundled.slug}")
            try {
                mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            } finally {
                runCatching { afd.close() }
            }
        } else {
            mp.setDataSource(this@AlarmFiresActivity, Uri.parse(uriString))
        }
        mp.isLooping = true
        mp.prepare()
        mp.setVolume(initialVolume, initialVolume)
        mp.start()
        Log.i(TAG, "playing $uriString")
        mp
    }.getOrElse {
        Log.w(TAG, "playFromUri failed for $uriString: ${it.message}")
        null
    }

    private fun playSystemDefault(attrs: AudioAttributes, initialVolume: Float): MediaPlayer? {
        val def = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
            ?: return null
        return runCatching {
            MediaPlayer().apply {
                setAudioAttributes(attrs)
                setDataSource(this@AlarmFiresActivity, def)
                isLooping = true
                prepare()
                setVolume(initialVolume, initialVolume)
                start()
            }
        }.getOrElse {
            Log.w(TAG, "playSystemDefault failed: ${it.message}")
            null
        }
    }

    private fun startVibration() {
        val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator = vib
        runCatching {
            vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0L, 800L, 600L), 0))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        rampJob?.cancel()
        timerAutoDismissJob?.cancel()
        runCatching {
            mediaPlayer?.let { it.stop(); it.release() }
        }
        mediaPlayer = null
        runCatching { vibrator?.cancel() }
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
        scope.cancel()
    }

    companion object {
        private const val TAG = "still-clock/fires"
        const val EXTRA_LABEL = "label"
        const val EXTRA_SOFT = "soft"
        const val EXTRA_IS_SNOOZE = "is_snooze"
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FiresUi(
    kind: String,
    label: String,
    is24Hour: Boolean,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit,
) {
    val isTimer = kind == AlarmsScheduler.KIND_TIMER

    BackHandler(enabled = true) { /* no-op — must dismiss or snooze */ }

    val now by produceMinuteFlow().collectAsState(initial = System.currentTimeMillis())
    val pattern = if (is24Hour) "HH:mm" else "h:mm"
    val timeText = remember(now, pattern) {
        DateTimeFormatter.ofPattern(pattern).format(LocalTime.now())
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(StillColors.OledBlack),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (isTimer) {
                Text(
                    text = "time",
                    style = StillTypography.ClockFace,
                    color = StillColors.SoftWhite,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "up",
                    style = StillTypography.Title,
                    color = StillColors.MutedWhite,
                    textAlign = TextAlign.Center,
                )
            } else {
                Text(
                    text = timeText,
                    style = StillTypography.ClockFace,
                    color = StillColors.SoftWhite,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = label.ifBlank { "alarm" },
                    style = StillTypography.Title,
                    color = StillColors.MutedWhite,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // Bottom verbs: dismiss + snooze (alarm) or just dismiss (timer).
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 48.dp),
            horizontalArrangement = if (isTimer) Arrangement.Center else Arrangement.SpaceBetween,
        ) {
            FiresVerb("dismiss", onDismiss)
            if (!isTimer) FiresVerb("snooze", onSnooze)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FiresVerb(text: String, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Text(
        text = text,
        style = StillTypography.ClockFace.copy(fontSize = androidx.compose.ui.unit.TextUnit(36f, androidx.compose.ui.unit.TextUnitType.Sp)),
        color = StillColors.SoftWhite,
        modifier = Modifier
            .combinedClickable(
                interactionSource = source,
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 12.dp, horizontal = 12.dp),
    )
}

@Composable
private fun produceMinuteFlow() = remember {
    flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(15_000L)
        }
    }
}
