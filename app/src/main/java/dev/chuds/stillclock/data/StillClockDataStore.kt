package dev.chuds.stillclock.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * Single DataStore for everything: alarms blob, timer state, stopwatch state, prefs.
 * One file, one source of truth.
 */
internal val Context.stillClockDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "stillclock",
)
