// JSON-blob alarm store. Same posture as still-notes' index.json — org.json, no extra
// serialization dependency. Alarms are small; one blob is enough.
package dev.chuds.stillclock.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import java.time.DayOfWeek
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private val ALARMS_JSON_KEY = stringPreferencesKey("alarms_json")

class AlarmsRepository internal constructor(
    private val dataStore: DataStore<Preferences>,
) {

    constructor(context: Context) : this(context.stillClockDataStore)

    private val mutex = Mutex()
    private val _alarms = MutableStateFlow<List<Alarm>>(emptyList())
    val alarms: StateFlow<List<Alarm>> = _alarms.asStateFlow()

    val alarmsFlow: Flow<List<Alarm>> = dataStore.data
        .catch { e -> if (e is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw e }
        .map { prefs -> decode(prefs[ALARMS_JSON_KEY] ?: "[]") }

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            _alarms.value = readPersisted()
        }
    }

    suspend fun snapshot(): List<Alarm> = withContext(Dispatchers.IO) {
        readPersisted()
    }

    suspend fun upsert(alarm: Alarm): Alarm = withContext(Dispatchers.IO) {
        mutex.withLock {
            persistMutation { current ->
                if (current.any { it.id == alarm.id }) {
                    current.map { if (it.id == alarm.id) alarm else it }
                } else {
                    current + alarm
                }
            }
            alarm
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            persistMutation { current -> current.filterNot { it.id == id } }
        }
    }

    suspend fun setEnabled(id: String, enabled: Boolean): Alarm? = withContext(Dispatchers.IO) {
        mutex.withLock {
            var updatedAlarm: Alarm? = null
            persistMutation { current ->
                val target = current.firstOrNull { it.id == id }
                if (target == null) {
                    current
                } else {
                    val updated = target.copy(enabled = enabled)
                    updatedAlarm = updated
                    current.map { if (it.id == id) updated else it }
                }
            }
            updatedAlarm
        }
    }

    suspend fun get(id: String): Alarm? = withContext(Dispatchers.IO) {
        _alarms.value.firstOrNull { it.id == id } ?: snapshot().firstOrNull { it.id == id }
    }

    private suspend fun readPersisted(): List<Alarm> {
        val prefs = dataStore.data.first()
        return decode(prefs[ALARMS_JSON_KEY] ?: "[]")
    }

    private suspend fun persistMutation(transform: (List<Alarm>) -> List<Alarm>): List<Alarm> {
        var updated = emptyList<Alarm>()
        dataStore.edit { prefs ->
            val current = decode(prefs[ALARMS_JSON_KEY] ?: "[]")
            updated = transform(current)
            prefs[ALARMS_JSON_KEY] = encode(updated)
        }
        _alarms.value = updated
        return updated
    }

    companion object {
        fun newId(): String = UUID.randomUUID().toString()

        fun encode(list: List<Alarm>): String {
            val arr = JSONArray()
            for (a in list) {
                val o = JSONObject()
                o.put("id", a.id)
                o.put("hour", a.hour)
                o.put("minute", a.minute)
                o.put("label", a.label)
                val days = JSONArray()
                for (d in a.daysOfWeek) days.put(d.value)
                o.put("daysOfWeek", days)
                o.put("enabled", a.enabled)
                o.put("soft", a.soft)
                if (!a.soundUri.isNullOrBlank()) o.put("soundUri", a.soundUri)
                if (!a.soundDisplayName.isNullOrBlank()) o.put("soundDisplayName", a.soundDisplayName)
                arr.put(o)
            }
            return arr.toString()
        }

        fun decode(json: String): List<Alarm> {
            val out = mutableListOf<Alarm>()
            val arr = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val days = o.optJSONArray("daysOfWeek")
                val daySet = mutableSetOf<DayOfWeek>()
                if (days != null) {
                    for (j in 0 until days.length()) {
                        val v = days.optInt(j, -1)
                        if (v in 1..7) daySet.add(DayOfWeek.of(v))
                    }
                }
                out.add(
                    Alarm(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        hour = o.optInt("hour", 7).coerceIn(0, 23),
                        minute = o.optInt("minute", 0).coerceIn(0, 59),
                        label = o.optString("label", ""),
                        daysOfWeek = daySet,
                        enabled = o.optBoolean("enabled", true),
                        soft = o.optBoolean("soft", false),
                        soundUri = o.optString("soundUri", "").takeIf { it.isNotBlank() },
                        soundDisplayName = o.optString("soundDisplayName", "").takeIf { it.isNotBlank() },
                    ),
                )
            }
            return out
        }
    }
}
