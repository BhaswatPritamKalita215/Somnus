package com.example.somnus.ui.home

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.somnus.data.local.SomnusDatabase
import com.example.somnus.data.repository.SleepSessionRepositoryImpl
import com.example.somnus.session.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class HomeViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var db: SomnusDatabase
    private lateinit var repository: SleepSessionRepositoryImpl
    private lateinit var sessionManager: SessionManager
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = SomnusDatabase.buildInMemoryDatabase(context)
        repository = SleepSessionRepositoryImpl(db.sleepSessionDao(), testDispatcher)
        sessionManager = SessionManager(repository, testScope)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun startSession_updatesActiveSessionInState() = runTest(testDispatcher) {
        val viewModel = HomeViewModel(sessionManager, repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        viewModel.startSession("Test notes")
        advanceUntilIdle()

        val active = repository.getActiveSessionOneShot()
        assertNotNull(active)
        assertEquals("Test notes", active?.notes)
        assertNotNull(viewModel.uiState.value.activeSession)
    }

    @Test
    fun promptAndConfirmEndSession_completesSession() = runTest(testDispatcher) {
        val viewModel = HomeViewModel(sessionManager, repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        viewModel.startSession()
        advanceUntilIdle()

        viewModel.promptEndSession()
        viewModel.confirmEndSession("Finished session")
        advanceUntilIdle()

        val active = repository.getActiveSessionOneShot()
        assertNull(active)
        assertNull(viewModel.uiState.value.activeSession)

        assertEquals(1, viewModel.uiState.value.recentSessions.size)
        assertEquals("Finished session", viewModel.uiState.value.recentSessions[0].notes)
    }

    @Test
    fun discardActiveSession_cancelsWithoutSaving() = runTest(testDispatcher) {
        val viewModel = HomeViewModel(sessionManager, repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        viewModel.startSession()
        advanceUntilIdle()
        assertNotNull(repository.getActiveSessionOneShot())

        viewModel.discardActiveSession()
        advanceUntilIdle()

        assertNull(repository.getActiveSessionOneShot())
        assertEquals(0, repository.getSessionCount())
        assertNull(viewModel.uiState.value.activeSession)
    }
}
