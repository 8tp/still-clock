package dev.chuds.stillclock.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject

private val TIMER_STATE_JSON_KEY = stringPreferencesKey("timer_state_json")

class TimerRepository internal constructor(
    private val dataStore: DataStore<Preferences>,
) {

    constructor(context: Context) : this(context.stillClockDataStore)

    val state: Flow<TimerState> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs -> decode(prefs[TIMER_STATE_JSON_KEY]) }

    suspend fun snapshot(): TimerState = withContext(Dispatchers.IO) {
        decode(dataStore.data.first()[TIMER_STATE_JSON_KEY])
    }

    suspend fun save(state: TimerState) = withContext(Dispatchers.IO) {
        dataStore.edit { it[TIMER_STATE_JSON_KEY] = encode(state) }
    }

    suspend fun clear() = save(TimerState.Idle)

    suspend fun consumeExpiredRunningTimer(nowMs: Long): Boolean = withContext(Dispatchers.IO) {
        var consumed = false
        dataStore.edit { prefs ->
            val state = decode(prefs[TIMER_STATE_JSON_KEY])
            val deadline = state.deadlineEpochMs ?: return@edit
            if (deadline <= nowMs) {
                prefs[TIMER_STATE_JSON_KEY] = encode(TimerState.Idle)
                consumed = true
            }
        }
        consumed
    }

    private fun encode(state: TimerState): String {
        val o = JSONObject()
        if (state.deadlineEpochMs != null) o.put("deadlineEpochMs", state.deadlineEpochMs)
        o.put("totalDurationMs", state.totalDurationMs)
        if (state.pausedRemainingMs != null) o.put("pausedRemainingMs", state.pausedRemainingMs)
        return o.toString()
    }

    private fun decode(json: String?): TimerState {
        if (json.isNullOrBlank()) return TimerState.Idle
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return TimerState.Idle
        val deadline = if (o.has("deadlineEpochMs")) o.optLong("deadlineEpochMs") else null
        val total = o.optLong("totalDurationMs", 0L)
        val paused = if (o.has("pausedRemainingMs")) o.optLong("pausedRemainingMs") else null
        return TimerState(
            deadlineEpochMs = deadline,
            totalDurationMs = total,
            pausedRemainingMs = paused,
        )
    }
}
