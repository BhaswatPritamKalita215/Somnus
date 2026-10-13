package com.example.somnus.ui.history

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.somnus.data.local.SomnusDatabase
import com.example.somnus.data.repository.SleepSessionRepositoryImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class HistoryViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var db: SomnusDatabase
    private lateinit var repository: SleepSessionRepositoryImpl
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = SomnusDatabase.buildInMemoryDatabase(context)
        repository = SleepSessionRepositoryImpl(db.sleepSessionDao(), testDispatcher)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun historyUiState_whenEmpty_showsZeroCounts() = runTest(testDispatcher) {
        val viewModel = HistoryViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(0, state.sessions.size)
        assertEquals(Duration.ZERO, state.totalTimeInBed)
        assertEquals(Duration.ZERO, state.averageTimeInBed)
        assertFalse(state.isLoading)
    }

    @Test
    fun historyUiState_withSessions_calculatesAggregateDurations() = runTest(testDispatcher) {
        val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        // Night 1: 8 hours
        val start1 = now.minus(Duration.ofDays(2)).minus(Duration.ofHours(8))
        val end1 = now.minus(Duration.ofDays(2))
        repository.startSession("s1", start1, ZoneId.of("UTC"))
        repository.endSession("s1", end1)

        // Night 2: 6 hours
        val start2 = now.minus(Duration.ofDays(1)).minus(Duration.ofHours(6))
        val end2 = now.minus(Duration.ofDays(1))
        repository.startSession("s2", start2, ZoneId.of("UTC"))
        repository.endSession("s2", end2)
        advanceUntilIdle()

        val viewModel = HistoryViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.sessions.size)
        assertEquals(Duration.ofHours(14), state.totalTimeInBed)
        assertEquals(Duration.ofHours(7), state.averageTimeInBed)
    }
}
