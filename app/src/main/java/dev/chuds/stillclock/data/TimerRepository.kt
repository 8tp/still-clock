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

    suspend fun consumeExpiredRunningTimer(
        nowMs: Long,
        nowElapsedRealtimeMs: Long? = null,
        currentBootCount: Int? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        var consumed = false
        dataStore.edit { prefs ->
            val state = decode(prefs[TIMER_STATE_JSON_KEY])
            if (state.isExpired(
                    nowEpochMs = nowMs,
                    nowElapsedRealtimeMs = nowElapsedRealtimeMs,
                    currentBootCount = currentBootCount,
                )
            ) {
                prefs[TIMER_STATE_JSON_KEY] = encode(TimerState.Idle)
                consumed = true
            }
        }
        consumed
    }

    /**
     * Atomic pause: the running-check and the write happen inside one DataStore.edit, so a
     * concurrent fire-and-clear (AlarmReceiver) can never race a pause read-modify-write
     * into a zombie paused state with ~0 remaining. Returns true if the pause took effect.
     */
    suspend fun pauseIfRunning(
        nowEpochMs: Long,
        nowElapsedRealtimeMs: Long,
        currentBootCount: Int?,
    ): Boolean = withContext(Dispatchers.IO) {
        var paused = false
        dataStore.edit { prefs ->
            val state = decode(prefs[TIMER_STATE_JSON_KEY])
            if (!state.isRunning) return@edit
            val remaining = state.remainingMs(
                nowEpochMs = nowEpochMs,
                nowElapsedRealtimeMs = nowElapsedRealtimeMs,
                currentBootCount = currentBootCount,
            )
            if (remaining <= 0L) return@edit
            val next = state.copy(
                deadlineEpochMs = null,
                deadlineElapsedRealtimeMs = null,
                pausedRemainingMs = remaining,
                startedBootCount = null,
            )
            prefs[TIMER_STATE_JSON_KEY] = encode(next)
            paused = true
        }
        paused
    }

    private fun encode(state: TimerState): String {
        val o = JSONObject()
        if (state.deadlineEpochMs != null) o.put("deadlineEpochMs", state.deadlineEpochMs)
        if (state.deadlineElapsedRealtimeMs != null) {
            o.put("deadlineElapsedRealtimeMs", state.deadlineElapsedRealtimeMs)
        }
        o.put("totalDurationMs", state.totalDurationMs)
        if (state.pausedRemainingMs != null) o.put("pausedRemainingMs", state.pausedRemainingMs)
        if (state.startedBootCount != null) o.put("startedBootCount", state.startedBootCount)
        return o.toString()
    }

    private fun decode(json: String?): TimerState {
        if (json.isNullOrBlank()) return TimerState.Idle
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return TimerState.Idle
        val deadline = if (o.has("deadlineEpochMs")) o.optLong("deadlineEpochMs") else null
        val elapsedDeadline = if (o.has("deadlineElapsedRealtimeMs")) o.optLong("deadlineElapsedRealtimeMs") else null
        val total = o.optLong("totalDurationMs", 0L)
        val paused = if (o.has("pausedRemainingMs")) o.optLong("pausedRemainingMs") else null
        val bootCount = if (o.has("startedBootCount")) o.optInt("startedBootCount") else null
        return TimerState(
            deadlineEpochMs = deadline,
            deadlineElapsedRealtimeMs = elapsedDeadline,
            totalDurationMs = total,
            pausedRemainingMs = paused,
            startedBootCount = bootCount,
        )
    }
}
