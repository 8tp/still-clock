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

    @Test
    fun consumeExpiredRunningTimer_isSingleConsumerAcrossColdRepositories() = runBlocking {
        val warmRepository = TimerRepository(dataStore)
        warmRepository.save(
            TimerState(
                deadlineEpochMs = 1_000L,
                totalDurationMs = 30_000L,
                pausedRemainingMs = null,
            ),
        )

        val first = TimerRepository(dataStore).consumeExpiredRunningTimer(nowMs = 2_000L)
        val second = TimerRepository(dataStore).consumeExpiredRunningTimer(nowMs = 2_000L)

        assertEquals(true, first)
        assertEquals(false, second)
        assertEquals(TimerState.Idle, TimerRepository(dataStore).snapshot())
    }

    @Test
    fun consumeExpiredRunningTimer_leavesFutureTimerArmed() = runBlocking {
        val state = TimerState(
            deadlineEpochMs = 5_000L,
            deadlineElapsedRealtimeMs = 7_000L,
            totalDurationMs = 30_000L,
            pausedRemainingMs = null,
            startedBootCount = 42,
        )
        TimerRepository(dataStore).save(state)

        val consumed = TimerRepository(dataStore).consumeExpiredRunningTimer(
            nowMs = 2_000L,
            nowElapsedRealtimeMs = 3_000L,
            currentBootCount = 42,
        )

        assertEquals(false, consumed)
        assertEquals(state, TimerRepository(dataStore).snapshot())
    }

    @Test
    fun consumeExpiredRunningTimer_usesElapsedRealtimeOnSameBoot() = runBlocking {
        TimerRepository(dataStore).save(
            TimerState(
                deadlineEpochMs = 100_000L,
                deadlineElapsedRealtimeMs = 5_000L,
                totalDurationMs = 30_000L,
                pausedRemainingMs = null,
                startedBootCount = 42,
            ),
        )

        val consumed = TimerRepository(dataStore).consumeExpiredRunningTimer(
            nowMs = 2_000L,
            nowElapsedRealtimeMs = 6_000L,
            currentBootCount = 42,
        )

        assertEquals(true, consumed)
        assertEquals(TimerState.Idle, TimerRepository(dataStore).snapshot())
    }

    @Test
    fun runningTimerRemainingFallsBackToWallClockAcrossBoots() {
        val state = TimerState(
            deadlineEpochMs = 30_000L,
            deadlineElapsedRealtimeMs = 5_000L,
            totalDurationMs = 30_000L,
            pausedRemainingMs = null,
            startedBootCount = 41,
        )

        assertEquals(
            20_000L,
            state.remainingMs(
                nowEpochMs = 10_000L,
                nowElapsedRealtimeMs = 6_000L,
                currentBootCount = 42,
            ),
        )
    }
}
