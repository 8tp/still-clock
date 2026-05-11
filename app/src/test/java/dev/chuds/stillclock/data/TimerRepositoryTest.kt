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
import org.junit.Before
import org.junit.Test

class TimerRepositoryTest {

    private lateinit var storeJob: Job
    private lateinit var storeFile: File
    private lateinit var dataStore: DataStore<Preferences>

    @Before
    fun setUp() {
        storeJob = SupervisorJob()
        storeFile = File.createTempFile("timer-repository-test", ".preferences_pb")
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
    fun clear_persistsIdleStateForColdRepository() = runBlocking {
        val warmRepository = TimerRepository(dataStore)
        warmRepository.save(
            TimerState(
                deadlineEpochMs = 1_000L,
                totalDurationMs = 30_000L,
                pausedRemainingMs = null,
            ),
        )

        val coldRepository = TimerRepository(dataStore)
        coldRepository.clear()

        assertEquals(TimerState.Idle, TimerRepository(dataStore).snapshot())
    }
}
