// Hand-rolled router — sealed Route, no NavCompose. Same shape as still-notes' StillNotesApp.
package dev.chuds.stillclock

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.chuds.stillclock.alarm.AlarmsScheduler
import dev.chuds.stillclock.alarm.NotificationChannels
import dev.chuds.stillclock.alarm.TimerScheduler
import dev.chuds.stillclock.data.Alarm
import dev.chuds.stillclock.data.AlarmsRepository
import dev.chuds.stillclock.data.ClockSettings
import dev.chuds.stillclock.data.FontPreset
import dev.chuds.stillclock.data.PreferencesRepository
import dev.chuds.stillclock.data.StopwatchRepository
import dev.chuds.stillclock.data.StopwatchState
import dev.chuds.stillclock.data.Tab
import dev.chuds.stillclock.data.TimeFormat
import dev.chuds.stillclock.data.TimerRepository
import dev.chuds.stillclock.data.TimerState
import dev.chuds.stillclock.ui.alarms.AlarmActionSheet
import dev.chuds.stillclock.ui.alarms.AlarmEditScreen
import dev.chuds.stillclock.ui.alarms.AlarmsScreen
import dev.chuds.stillclock.ui.clock.ClockScreen
import dev.chuds.stillclock.ui.components.LocalHaptics
import dev.chuds.stillclock.ui.components.StillTabBar
import dev.chuds.stillclock.ui.components.rememberHapticsPerformer
import dev.chuds.stillclock.ui.settings.SettingsScreen
import dev.chuds.stillclock.ui.stopwatch.StopwatchScreen
import dev.chuds.stillclock.ui.theme.LocalStillTypography
import dev.chuds.stillclock.ui.theme.stillTypographyFor
import dev.chuds.stillclock.ui.timer.TimerScreen
import kotlinx.coroutines.launch

private sealed interface Route {
    data class Tabs(val tab: Tab) : Route
    data class AlarmEdit(val id: String?) : Route
    data object Settings : Route
}

@Composable
fun StillClockApp(initialAlarmEditId: String? = null) {
    val context = LocalContext.current.applicationContext
    val activityContext = LocalContext.current

    val alarmsRepository = remember(context) { AlarmsRepository(context) }
    val timerRepository = remember(context) { TimerRepository(context) }
    val stopwatchRepository = remember(context) { StopwatchRepository(context) }
    val preferencesRepository = remember(context) { PreferencesRepository(context) }
    val timerScheduler = remember(context) { TimerScheduler(context, timerRepository) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        NotificationChannels.ensure(context)
    }

    val loadedSettings by preferencesRepository.settings.collectAsState(initial = null)
    val settings = loadedSettings ?: ClockSettings()

    val alarms by alarmsRepository.alarmsFlow.collectAsState(initial = emptyList())
    val timerState by timerRepository.state.collectAsState(initial = TimerState.Idle)
    val stopwatchState by stopwatchRepository.state.collectAsState(initial = StopwatchState.Idle)

    var route by remember(initialAlarmEditId) {
        mutableStateOf<Route>(
            if (initialAlarmEditId != null) Route.AlarmEdit(initialAlarmEditId)
            else Route.Tabs(settings.defaultTab),
        )
    }
    // Once settings loads (async), respect default tab on cold start.
    var initialTabApplied by remember { mutableStateOf(false) }
    LaunchedEffect(loadedSettings?.defaultTab) {
        if (!initialTabApplied && initialAlarmEditId == null && loadedSettings != null) {
            route = Route.Tabs(loadedSettings!!.defaultTab)
            initialTabApplied = true
        }
    }

    var actionTarget by remember { mutableStateOf<String?>(null) }

    // Notification permission ask — first time we need it.
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* fall through; user can deny — alarms still fire over lockscreen */ }

    BackHandler(enabled = route !is Route.Tabs || (route as Route.Tabs).tab != Tab.Clock) {
        route = when (route) {
            is Route.AlarmEdit, Route.Settings -> Route.Tabs(Tab.Alarms.takeIf { route is Route.AlarmEdit } ?: settings.defaultTab)
            is Route.Tabs -> Route.Tabs(Tab.Clock)
        }
    }

    val typography = remember(settings.fontPreset) { stillTypographyFor(settings.fontPreset) }

    fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val hapticsPerformer = rememberHapticsPerformer(settings.hapticsEnabled)

    CompositionLocalProvider(
        LocalStillTypography provides typography,
        LocalHaptics provides hapticsPerformer,
    ) {
        Box(modifier = Modifier.fillMaxSize().background(dev.chuds.stillclock.ui.theme.StillColors.OledBlack)) {
            AnimatedContent(
                targetState = route,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) },
                label = "route",
            ) { current ->
                when (current) {
                    is Route.Tabs -> TabsContent(
                        tab = current.tab,
                        settings = settings,
                        alarms = alarms,
                        timerState = timerState,
                        stopwatchState = stopwatchState,
                        is24HourSystem = android.text.format.DateFormat.is24HourFormat(activityContext),
                        onSwitchTab = { route = Route.Tabs(it) },
                        onOpenSettings = { route = Route.Settings },
                        onTapAlarm = { id -> route = Route.AlarmEdit(id) },
                        onLongPressAlarm = { id -> actionTarget = id },
                        onToggleEnabled = { id ->
                            scope.launch {
                                ensureNotificationPermission()
                                val nextEnabled = !(alarms.firstOrNull { it.id == id }?.enabled ?: false)
                                if (nextEnabled && !AlarmsScheduler.canScheduleExactAlarms(activityContext)) {
                                    Toast.makeText(activityContext, "enable exact alarms in settings", Toast.LENGTH_SHORT).show()
                                    return@launch
                                }
                                val updated = alarmsRepository.setEnabled(id, nextEnabled)
                                if (updated != null) {
                                    if (updated.enabled) AlarmsScheduler.schedule(activityContext, updated)
                                    else AlarmsScheduler.cancel(activityContext, id)
                                }
                            }
                        },
                        onNew = { route = Route.AlarmEdit(null) },
                        onStartTimer = { ms ->
                            scope.launch {
                                ensureNotificationPermission()
                                if (!timerScheduler.start(ms)) {
                                    Toast.makeText(activityContext, "enable exact alarms in settings", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onPauseTimer = { scope.launch { timerScheduler.pause() } },
                        onResumeTimer = {
                            scope.launch {
                                if (!timerScheduler.resume()) {
                                    Toast.makeText(activityContext, "enable exact alarms in settings", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onCancelTimer = { scope.launch { timerScheduler.cancel() } },
                        onDismissTimer = { scope.launch { timerScheduler.cancel() } },
                        onStopwatchStartStop = {
                            scope.launch {
                                val s = stopwatchRepository.snapshot()
                                if (s.isRunning) {
                                    val now = System.currentTimeMillis()
                                    val accumulated = s.elapsedMs(now)
                                    stopwatchRepository.save(s.copy(startedAtEpochMs = null, accumulatedMs = accumulated))
                                } else {
                                    stopwatchRepository.save(s.copy(startedAtEpochMs = System.currentTimeMillis()))
                                }
                            }
                        },
                        onStopwatchLap = {
                            scope.launch {
                                val s = stopwatchRepository.snapshot()
                                if (!s.isRunning) return@launch
                                val now = System.currentTimeMillis()
                                val total = s.elapsedMs(now)
                                val priorTotal = s.laps.sum()
                                val split = (total - priorTotal).coerceAtLeast(0L)
                                stopwatchRepository.save(s.copy(laps = s.laps + split))
                            }
                        },
                        onStopwatchReset = {
                            scope.launch { stopwatchRepository.clear() }
                        },
                    )

                    is Route.AlarmEdit -> {
                        val targetAlarm = remember(current.id, alarms) {
                            current.id?.let { id -> alarms.firstOrNull { it.id == id } }
                        }
                        if (current.id != null && targetAlarm == null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(dev.chuds.stillclock.ui.theme.StillColors.OledBlack),
                            )
                            return@AnimatedContent
                        }

                        val initial = targetAlarm ?: remember(current.id) {
                            Alarm(
                                id = AlarmsRepository.newId(),
                                hour = 7,
                                minute = 0,
                                label = "",
                                daysOfWeek = emptySet(),
                                enabled = true,
                                soft = false,
                            )
                        }
                        AlarmEditScreen(
                            initial = initial,
                            isNew = current.id == null,
                            settings = settings,
                            is24HourSystem = android.text.format.DateFormat.is24HourFormat(activityContext),
                            onSave = { updated ->
                                scope.launch {
                                    ensureNotificationPermission()
                                    if (updated.enabled && !AlarmsScheduler.canScheduleExactAlarms(activityContext)) {
                                        Toast.makeText(activityContext, "enable exact alarms in settings", Toast.LENGTH_SHORT).show()
                                        return@launch
                                    }
                                    alarmsRepository.upsert(updated)
                                    AlarmsScheduler.schedule(activityContext, updated)
                                    route = Route.Tabs(Tab.Alarms)
                                }
                            },
                            onDelete = {
                                scope.launch {
                                    AlarmsScheduler.cancel(activityContext, initial.id)
                                    alarmsRepository.delete(initial.id)
                                    route = Route.Tabs(Tab.Alarms)
                                }
                            },
                            onCancel = { route = Route.Tabs(Tab.Alarms) },
                        )
                    }

                    Route.Settings -> SettingsScreen(
                        settings = settings,
                        onCycleFont = {
                            scope.launch {
                                preferencesRepository.setFontPreset(
                                    when (settings.fontPreset) {
                                        FontPreset.System -> FontPreset.Editorial
                                        FontPreset.Editorial -> FontPreset.Terminal
                                        FontPreset.Terminal -> FontPreset.Grotesk
                                        FontPreset.Grotesk -> FontPreset.System
                                    },
                                )
                            }
                        },
                        onCycleTimeFormat = {
                            scope.launch {
                                preferencesRepository.setTimeFormat(
                                    when (settings.timeFormat) {
                                        TimeFormat.TwentyFour -> TimeFormat.Twelve
                                        TimeFormat.Twelve -> TimeFormat.System
                                        TimeFormat.System -> TimeFormat.TwentyFour
                                    },
                                )
                            }
                        },
                        onToggleSeconds = {
                            scope.launch {
                                preferencesRepository.setSecondsOnClock(!settings.secondsOnClock)
                            }
                        },
                        onSetSecondZone = { value ->
                            scope.launch { preferencesRepository.setSecondZone(value) }
                        },
                        onCycleDefaultTab = {
                            scope.launch {
                                preferencesRepository.setDefaultTab(
                                    when (settings.defaultTab) {
                                        Tab.Clock -> Tab.Alarms
                                        Tab.Alarms -> Tab.Timer
                                        Tab.Timer -> Tab.Stopwatch
                                        Tab.Stopwatch -> Tab.Clock
                                    },
                                )
                            }
                        },
                        onSetAlarmSound = { uri, name ->
                            scope.launch { preferencesRepository.setAlarmSound(uri, name) }
                        },
                        onSetTimerSound = { uri, name ->
                            scope.launch { preferencesRepository.setTimerSound(uri, name) }
                        },
                        onCycleSnooze = {
                            scope.launch {
                                val next = when (settings.snoozeMinutes) {
                                    1 -> 3
                                    3 -> 5
                                    5 -> 10
                                    else -> 1
                                }
                                preferencesRepository.setSnoozeMinutes(next)
                            }
                        },
                        onToggleHaptics = {
                            scope.launch {
                                preferencesRepository.setHapticsEnabled(!settings.hapticsEnabled)
                            }
                        },
                        onBack = { route = Route.Tabs(settings.defaultTab) },
                    )
                }
            }

            actionTarget?.let { id ->
                AlarmActionSheet(
                    onEdit = { actionTarget = null; route = Route.AlarmEdit(id) },
                    onDelete = {
                        actionTarget = null
                        scope.launch {
                            AlarmsScheduler.cancel(activityContext, id)
                            alarmsRepository.delete(id)
                        }
                    },
                    onDismiss = { actionTarget = null },
                )
            }
        }
    }
}

@Composable
private fun TabsContent(
    tab: Tab,
    settings: ClockSettings,
    alarms: List<Alarm>,
    timerState: TimerState,
    stopwatchState: StopwatchState,
    is24HourSystem: Boolean,
    onSwitchTab: (Tab) -> Unit,
    onOpenSettings: () -> Unit,
    onTapAlarm: (String) -> Unit,
    onLongPressAlarm: (String) -> Unit,
    onToggleEnabled: (String) -> Unit,
    onNew: () -> Unit,
    onStartTimer: (Long) -> Unit,
    onPauseTimer: () -> Unit,
    onResumeTimer: () -> Unit,
    onCancelTimer: () -> Unit,
    onDismissTimer: () -> Unit,
    onStopwatchStartStop: () -> Unit,
    onStopwatchLap: () -> Unit,
    onStopwatchReset: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
            label = "tab",
        ) { current ->
            when (current) {
                Tab.Clock -> ClockScreen(
                    settings = settings,
                    is24HourSystem = is24HourSystem,
                    onOpenSettings = onOpenSettings,
                )
                Tab.Alarms -> AlarmsScreen(
                    alarms = alarms,
                    settings = settings,
                    is24HourSystem = is24HourSystem,
                    onOpenSettings = onOpenSettings,
                    onTapAlarm = onTapAlarm,
                    onLongPressAlarm = onLongPressAlarm,
                    onToggleEnabled = onToggleEnabled,
                    onNew = onNew,
                )
                Tab.Timer -> TimerScreen(
                    state = timerState,
                    onOpenSettings = onOpenSettings,
                    onStart = onStartTimer,
                    onPause = onPauseTimer,
                    onResume = onResumeTimer,
                    onCancel = onCancelTimer,
                    onDismiss = onDismissTimer,
                )
                Tab.Stopwatch -> StopwatchScreen(
                    state = stopwatchState,
                    onOpenSettings = onOpenSettings,
                    onStartStop = onStopwatchStartStop,
                    onLap = onStopwatchLap,
                    onReset = onStopwatchReset,
                )
            }
        }

        StillTabBar(
            current = tab,
            onTabSelected = onSwitchTab,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        )
    }
}

private fun tween(ms: Int) = androidx.compose.animation.core.tween<Float>(ms)
