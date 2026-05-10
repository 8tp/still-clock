package dev.chuds.stillclock.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

enum class TimeFormat { Twelve, TwentyFour, System }
enum class Tab { Clock, Alarms, Timer, Stopwatch }

data class ClockSettings(
    val fontPreset: FontPreset = FontPreset.System,
    val timeFormat: TimeFormat = TimeFormat.TwentyFour,
    val secondsOnClock: Boolean = false,
    val secondZone: String = "",
    val defaultTab: Tab = Tab.Clock,
    val alarmSoundUri: String = "",
    val alarmSoundDisplayName: String = "",
    val snoozeMinutes: Int = 5,
)

private val FONT_PRESET_KEY = stringPreferencesKey("pref_font")
private val TIME_FORMAT_KEY = stringPreferencesKey("pref_time_format")
private val SECONDS_ON_CLOCK_KEY = booleanPreferencesKey("pref_seconds_on_clock")
private val SECOND_ZONE_KEY = stringPreferencesKey("pref_second_zone")
private val DEFAULT_TAB_KEY = stringPreferencesKey("pref_default_tab")
private val ALARM_SOUND_URI_KEY = stringPreferencesKey("pref_alarm_sound_uri")
private val ALARM_SOUND_DISPLAY_NAME_KEY = stringPreferencesKey("pref_alarm_sound_display_name")
private val SNOOZE_MINUTES_KEY = intPreferencesKey("pref_snooze_minutes")

class PreferencesRepository(private val context: Context) {

    val settings: Flow<ClockSettings> = context.stillClockDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            ClockSettings(
                fontPreset = prefs[FONT_PRESET_KEY]
                    ?.let { runCatching { FontPreset.valueOf(it) }.getOrNull() }
                    ?: FontPreset.System,
                timeFormat = prefs[TIME_FORMAT_KEY]
                    ?.let { runCatching { TimeFormat.valueOf(it) }.getOrNull() }
                    ?: TimeFormat.TwentyFour,
                secondsOnClock = prefs[SECONDS_ON_CLOCK_KEY] ?: false,
                secondZone = prefs[SECOND_ZONE_KEY] ?: "",
                defaultTab = prefs[DEFAULT_TAB_KEY]
                    ?.let { runCatching { Tab.valueOf(it) }.getOrNull() }
                    ?: Tab.Clock,
                alarmSoundUri = prefs[ALARM_SOUND_URI_KEY] ?: "",
                alarmSoundDisplayName = prefs[ALARM_SOUND_DISPLAY_NAME_KEY] ?: "",
                snoozeMinutes = prefs[SNOOZE_MINUTES_KEY] ?: 5,
            )
        }

    suspend fun setFontPreset(value: FontPreset) =
        context.stillClockDataStore.edit { it[FONT_PRESET_KEY] = value.name }

    suspend fun setTimeFormat(value: TimeFormat) =
        context.stillClockDataStore.edit { it[TIME_FORMAT_KEY] = value.name }

    suspend fun setSecondsOnClock(value: Boolean) =
        context.stillClockDataStore.edit { it[SECONDS_ON_CLOCK_KEY] = value }

    suspend fun setSecondZone(zone: String) =
        context.stillClockDataStore.edit { it[SECOND_ZONE_KEY] = zone }

    suspend fun setDefaultTab(tab: Tab) =
        context.stillClockDataStore.edit { it[DEFAULT_TAB_KEY] = tab.name }

    suspend fun setAlarmSound(uri: String, displayName: String) =
        context.stillClockDataStore.edit {
            it[ALARM_SOUND_URI_KEY] = uri
            it[ALARM_SOUND_DISPLAY_NAME_KEY] = displayName
        }

    suspend fun setSnoozeMinutes(minutes: Int) =
        context.stillClockDataStore.edit { it[SNOOZE_MINUTES_KEY] = minutes.coerceIn(1, 60) }
}
