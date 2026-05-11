package dev.chuds.stillclock.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AlarmsRepositoryTest {

    private lateinit var storeJob: Job
    private lateinit var storeFile: File
    private lateinit var dataStore: DataStore<Preferences>

    @Before
    fun setUp() {
        storeJob = SupervisorJob()
        storeFile = File.createTempFile("alarms-repository-test", ".preferences_pb")
        check(storeFile.delete())
        dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + storeJob),
            produceFile = { storeFile },
        )
    }

    @After
    fun tearDown() {
        storeJob.cancel()
        storeFile.delete()
    }

    @Test
    fun coldUpsertPreservesPersistedSiblings() = runBlocking {
        seedPersistedAlarms()
        val coldRepository = AlarmsRepository(dataStore)

        coldRepository.upsert(firedAlarm.copy(enabled = false))

        val persisted = coldRepository.snapshot()
        assertEquals(2, persisted.size)
        assertFalse(persisted.first { it.id == firedAlarm.id }.enabled)
        assertTrue(persisted.first { it.id == siblingAlarm.id }.enabled)
    }

    @Test
    fun coldSetEnabledPreservesPersistedSiblings() = runBlocking {
        seedPersistedAlarms()
        val coldRepository = AlarmsRepository(dataStore)

        val updated = coldRepository.setEnabled(firedAlarm.id, enabled = false)

        val persisted = coldRepository.snapshot()
        assertNotNull(updated)
        assertEquals(2, persisted.size)
        assertFalse(persisted.first { it.id == firedAlarm.id }.enabled)
        assertTrue(persisted.first { it.id == siblingAlarm.id }.enabled)
    }

    @Test
    fun coldDeletePreservesPersistedSiblings() = runBlocking {
        seedPersistedAlarms()
        val coldRepository = AlarmsRepository(dataStore)

        coldRepository.delete(firedAlarm.id)

        val persisted = coldRepository.snapshot()
        assertEquals(listOf(siblingAlarm.id), persisted.map { it.id })
    }

    @Test
    fun coldSetEnabledReturnsNullForMissingAlarmWithoutDroppingPersistedAlarms() = runBlocking {
        seedPersistedAlarms()
        val coldRepository = AlarmsRepository(dataStore)

        val updated = coldRepository.setEnabled("missing", enabled = false)

        assertNull(updated)
        assertEquals(setOf(firedAlarm.id, siblingAlarm.id), coldRepository.snapshot().map { it.id }.toSet())
    }

    private suspend fun seedPersistedAlarms() {
        val warmRepository = AlarmsRepository(dataStore)
        warmRepository.upsert(firedAlarm)
        warmRepository.upsert(siblingAlarm)
    }

    private companion object {
        val firedAlarm = Alarm(
            id = "fired",
            hour = 7,
            minute = 0,
            label = "fired",
            daysOfWeek = emptySet(),
            enabled = true,
            soft = false,
        )

        val siblingAlarm = Alarm(
            id = "sibling",
            hour = 8,
            minute = 30,
            label = "sibling",
            daysOfWeek = emptySet(),
            enabled = true,
            soft = false,
        )
    }
}
