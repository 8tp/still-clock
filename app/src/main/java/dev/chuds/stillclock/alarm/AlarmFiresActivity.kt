// Full-screen alarm-fires Activity.
//
// Resolution of spec.md §15 q1 (bundled tone): the runtime gracefully no-ops when
// res/raw/still_tone.ogg is absent (RawRes lookup falls back to the user's chosen ringtone
// or to silence-with-vibration). Drop a CC0 .ogg into res/raw/ before release; see
// res/raw/STILL_TONE_LICENSE.txt for the candidate license text.
package dev.chuds.stillclock.alarm

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.res.Resources
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
import dev.chuds.stillclock.data.PreferencesRepository
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
import kotlinx.coroutines.runBlocking

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
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "stillclock:fires",
        ).apply { setReferenceCounted(false); acquire(15 * 60_000L) }

        val kind = intent.getStringExtra(AlarmsScheduler.EXTRA_KIND) ?: AlarmsScheduler.KIND_ALARM
        val alarmId = intent.getStringExtra(AlarmsScheduler.EXTRA_ALARM_ID).orEmpty()
        val label = intent.getStringExtra(EXTRA_LABEL).orEmpty()
        val soft = intent.getBooleanExtra(EXTRA_SOFT, false)
        val isSnooze = intent.getBooleanExtra(EXTRA_IS_SNOOZE, false)

        startSoundAndVibration(soft, kind == AlarmsScheduler.KIND_TIMER)

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
            AlarmsScheduler.scheduleSnooze(this, alarmId, triggerMs)
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

    private fun startSoundAndVibration(soft: Boolean, isTimer: Boolean) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val target = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            .coerceAtLeast(1)
            .toFloat()
            .let { it / audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM).coerceAtLeast(1) }

        val initialVolume = if (soft) 0f else target

        val player = createMediaPlayer(initialVolume) ?: run {
            // Tone unavailable; vibration alone still wakes the user.
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

    private fun createMediaPlayer(initialVolume: Float): MediaPlayer? {
        val attrs = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_ALARM)
            .build()

        // Prefer the user's selected ringtone URI; fall back to bundled tone; then to default alarm.
        val prefRepo = PreferencesRepository(applicationContext)
        val settings = runCatching {
            runBlocking { prefRepo.settings.let { flow -> flow.stateIn(scope).value } }
        }.getOrNull() ?: dev.chuds.stillclock.data.ClockSettings()

        val candidates: List<() -> MediaPlayer?> = listOfNotNull(
            settings.alarmSoundUri.takeIf { it.isNotBlank() }?.let { uriString ->
                {
                    runCatching {
                        MediaPlayer().apply {
                            setAudioAttributes(attrs)
                            setDataSource(this@AlarmFiresActivity, Uri.parse(uriString))
                            isLooping = true
                            setVolume(initialVolume, initialVolume)
                            prepare()
                            start()
                        }
                    }.getOrNull()
                }
            },
            {
                runCatching {
                    val resId = resources.getIdentifier("still_tone", "raw", packageName)
                    if (resId == 0) return@runCatching null
                    MediaPlayer.create(this, resId)?.apply {
                        setAudioAttributes(attrs)
                        isLooping = true
                        setVolume(initialVolume, initialVolume)
                        start()
                    }
                }.getOrNull()
            },
            {
                runCatching {
                    val def = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
                        ?: return@runCatching null
                    MediaPlayer().apply {
                        setAudioAttributes(attrs)
                        setDataSource(this@AlarmFiresActivity, def)
                        isLooping = true
                        setVolume(initialVolume, initialVolume)
                        prepare()
                        start()
                    }
                }.getOrNull()
            },
        )
        for (factory in candidates) {
            val mp = factory()
            if (mp != null) return mp
        }
        return null
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
