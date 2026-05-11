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
    fun pauseIfRunning_setsPausedRemainingFromCurrentTime() = runBlocking {
        TimerRepository(dataStore).save(
            TimerState(
                deadlineEpochMs = 30_000L,
                deadlineElapsedRealtimeMs = 30_000L,
                totalDurationMs = 30_000L,
                pausedRemainingMs = null,
                startedBootCount = 42,
            ),
        )

        val paused = TimerRepository(dataStore).pauseIfRunning(
            nowEpochMs = 10_000L,
            nowElapsedRealtimeMs = 10_000L,
            currentBootCount = 42,
        )

        assertEquals(true, paused)
        val saved = TimerRepository(dataStore).snapshot()
        assertEquals(20_000L, saved.pausedRemainingMs)
        assertEquals(null, saved.deadlineEpochMs)
    }

    @Test
    fun pauseIfRunning_isNoOpWhenIdle() = runBlocking {
        TimerRepository(dataStore).clear()

        val paused = TimerRepository(dataStore).pauseIfRunning(
            nowEpochMs = 1L,
            nowElapsedRealtimeMs = 1L,
            currentBootCount = 1,
        )

        assertEquals(false, paused)
        assertEquals(TimerState.Idle, TimerRepository(dataStore).snapshot())
    }

    @Test
    fun pauseIfRunning_doesNotResurrectClearedStateAfterFire() = runBlocking {
        // Simulate the race: timer fired and AlarmReceiver cleared the state. A pause
        // call that read snapshot() before the clear used to overwrite Idle with a
        // ~0 paused state, producing a zombie. pauseIfRunning's edit { } CAS makes the
        // check-and-write atomic, so a late pause is a no-op.
        TimerRepository(dataStore).clear()

        val paused = TimerRepository(dataStore).pauseIfRunning(
            nowEpochMs = 1_000_000L,
            nowElapsedRealtimeMs = 1_000_000L,
            currentBootCount = 1,
        )

        assertEquals(false, paused)
        assertEquals(TimerState.Idle, TimerRepository(dataStore).snapshot())
    }

    @Test
    fun pauseIfRunning_refusesToPauseExpiredTimer() = runBlocking {
        // A pause attempted after the deadline has been reached (but before AlarmReceiver
        // has cleared the state) should not land a 0-remaining paused state.
        TimerRepository(dataStore).save(
            TimerState(
                deadlineEpochMs = 1_000L,
                deadlineElapsedRealtimeMs = 1_000L,
                totalDurationMs = 30_000L,
                pausedRemainingMs = null,
                startedBootCount = 42,
            ),
        )

        val paused = TimerRepository(dataStore).pauseIfRunning(
            nowEpochMs = 5_000L,
            nowElapsedRealtimeMs = 5_000L,
            currentBootCount = 42,
        )

        assertEquals(false, paused)
        val saved = TimerRepository(dataStore).snapshot()
        assertEquals(null, saved.pausedRemainingMs)
        assertEquals(1_000L, saved.deadlineEpochMs)
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
