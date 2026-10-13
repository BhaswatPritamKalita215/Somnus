package com.example.somnus.ui.detail

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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
class SessionDetailViewModelTest {

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
    fun correctSession_validTimestamps_updatesDatabaseAndSetsFlag() = runTest(testDispatcher) {
        val start = Instant.now().minus(Duration.ofHours(8)).truncatedTo(ChronoUnit.MILLIS)
        val end = Instant.now().minus(Duration.ofMinutes(30)).truncatedTo(ChronoUnit.MILLIS)

        repository.startSession("test-session-1", start, ZoneId.of("UTC"), "Original notes")
        repository.endSession("test-session-1", end, "Original notes")
        advanceUntilIdle()

        val viewModel = SessionDetailViewModel("test-session-1", repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        val newStart = start.minus(Duration.ofMinutes(30)).truncatedTo(ChronoUnit.MILLIS)
        val newEnd = end.plus(Duration.ofMinutes(15)).truncatedTo(ChronoUnit.MILLIS)
        viewModel.correctSession(newStart, newEnd, "Corrected notes")
        advanceUntilIdle()

        val updated = repository.getSessionByIdOneShot("test-session-1")
        assertNotNull(updated)
        assertEquals(newStart, updated?.startTime)
        assertEquals(newEnd, updated?.endTime)
        assertEquals("Corrected notes", updated?.notes)
        assertTrue(updated?.isCorrected == true)
        assertTrue(viewModel.uiState.value.isSaved)
    }

    @Test
    fun correctSession_invalidTimestamps_setsErrorMessage() = runTest(testDispatcher) {
        val start = Instant.now().minus(Duration.ofHours(8)).truncatedTo(ChronoUnit.MILLIS)
        val end = Instant.now().minus(Duration.ofMinutes(30)).truncatedTo(ChronoUnit.MILLIS)

        repository.startSession("test-session-1", start, ZoneId.of("UTC"))
        repository.endSession("test-session-1", end)
        advanceUntilIdle()

        val viewModel = SessionDetailViewModel("test-session-1", repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        // Attempting to set end time earlier than start time
        val invalidEnd = start.minus(Duration.ofHours(1))
        viewModel.correctSession(start, invalidEnd, null)
        advanceUntilIdle()

        assertEquals("End time cannot be before start time", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun deleteSession_removesSessionAndSetsDeletedState() = runTest(testDispatcher) {
        val start = Instant.now().minus(Duration.ofHours(8)).truncatedTo(ChronoUnit.MILLIS)
        val end = Instant.now().minus(Duration.ofMinutes(30)).truncatedTo(ChronoUnit.MILLIS)

        repository.startSession("delete-me", start, ZoneId.of("UTC"))
        repository.endSession("delete-me", end)
        advanceUntilIdle()

        val viewModel = SessionDetailViewModel("delete-me", repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        viewModel.deleteSession()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isDeleted)
        assertNull(repository.getSessionByIdOneShot("delete-me"))
    }
}
