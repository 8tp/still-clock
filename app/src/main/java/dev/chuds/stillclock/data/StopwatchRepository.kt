package dev.chuds.stillclock.data

import android.content.Context
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
import org.json.JSONArray
import org.json.JSONObject

private val STOPWATCH_STATE_JSON_KEY = stringPreferencesKey("stopwatch_state_json")

class StopwatchRepository(private val context: Context) {

    val state: Flow<StopwatchState> = context.stillClockDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs -> decode(prefs[STOPWATCH_STATE_JSON_KEY]) }

    suspend fun snapshot(): StopwatchState = withContext(Dispatchers.IO) {
        decode(context.stillClockDataStore.data.first()[STOPWATCH_STATE_JSON_KEY])
    }

    suspend fun save(state: StopwatchState) = withContext(Dispatchers.IO) {
        context.stillClockDataStore.edit { it[STOPWATCH_STATE_JSON_KEY] = encode(state) }
    }

    suspend fun clear() = save(StopwatchState.Idle)

    private fun encode(state: StopwatchState): String {
        val o = JSONObject()
        if (state.startedAtEpochMs != null) o.put("startedAtEpochMs", state.startedAtEpochMs)
        o.put("accumulatedMs", state.accumulatedMs)
        val arr = JSONArray()
        for (l in state.laps) arr.put(l)
        o.put("laps", arr)
        return o.toString()
    }

    private fun decode(json: String?): StopwatchState {
        if (json.isNullOrBlank()) return StopwatchState.Idle
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return StopwatchState.Idle
        val start = if (o.has("startedAtEpochMs")) o.optLong("startedAtEpochMs") else null
        val accumulated = o.optLong("accumulatedMs", 0L)
        val arr = o.optJSONArray("laps")
        val laps = mutableListOf<Long>()
        if (arr != null) for (i in 0 until arr.length()) laps.add(arr.optLong(i, 0L))
        return StopwatchState(
            startedAtEpochMs = start,
            accumulatedMs = accumulated,
            laps = laps,
        )
    }
}
