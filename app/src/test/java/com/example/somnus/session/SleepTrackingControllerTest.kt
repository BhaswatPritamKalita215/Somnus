package com.example.somnus.session

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.somnus.data.local.SomnusDatabase
import com.example.somnus.data.repository.SleepSessionRepositoryImpl
import com.example.somnus.ui.home.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

class FakeSleepTrackingController : SleepTrackingController {
    val _isServiceRunning = MutableStateFlow(false)
    override val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    val _isTrackingInterrupted = MutableStateFlow(false)
    override val isTrackingInterrupted: StateFlow<Boolean> = _isTrackingInterrupted.asStateFlow()

    val _currentSessionId = MutableStateFlow<String?>(null)
    override val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()

    var startTrackingCallCount = 0
    var stopTrackingCallCount = 0
    var discardTrackingCallCount = 0
    var lastStartedSessionId: String? = null

    override fun startTracking(sessionId: String, startTime: Instant, notes: String?) {
        startTrackingCallCount++
        lastStartedSessionId = sessionId
        _isServiceRunning.value = true
        _isTrackingInterrupted.value = false
        _currentSessionId.value = sessionId
    }

    override fun stopTracking(notes: String?) {
        stopTrackingCallCount++
        _isServiceRunning.value = false
        _currentSessionId.value = null
    }

    override fun discardTracking() {
        discardTrackingCallCount++
        _isServiceRunning.value = false
        _currentSessionId.value = null
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SleepTrackingControllerTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var db: SomnusDatabase
    private lateinit var repository: SleepSessionRepositoryImpl
    private lateinit var sessionManager: SessionManager
    private lateinit var fakeController: FakeSleepTrackingController
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = SomnusDatabase.buildInMemoryDatabase(context)
        repository = SleepSessionRepositoryImpl(db.sleepSessionDao(), testDispatcher)
        sessionManager = SessionManager(repository, testScope)
        fakeController = FakeSleepTrackingController()
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun startSession_triggersSleepTrackingControllerStartTracking() = runTest(testDispatcher) {
        val viewModel = HomeViewModel(
            sessionManager = sessionManager,
            repository = repository,
            sleepTrackingController = fakeController
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        viewModel.startSession("Testing overnight tracking")
        advanceUntilIdle()

        assertEquals(1, fakeController.startTrackingCallCount)
        assertTrue(fakeController.isServiceRunning.value)
        val active = repository.getActiveSessionOneShot()
        assertEquals(active?.id, fakeController.lastStartedSessionId)

        val state = viewModel.uiState.value
        assertTrue(state.isTrackingServiceRunning)
        assertFalse(state.isTrackingInterrupted)
    }

    @Test
    fun confirmEndSession_triggersSleepTrackingControllerStopTracking() = runTest(testDispatcher) {
        val viewModel = HomeViewModel(
            sessionManager = sessionManager,
            repository = repository,
            sleepTrackingController = fakeController
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        viewModel.startSession()
        advanceUntilIdle()
        assertTrue(fakeController.isServiceRunning.value)

        viewModel.confirmEndSession("Completed overnight session")
        advanceUntilIdle()

        assertEquals(1, fakeController.stopTrackingCallCount)
        assertFalse(fakeController.isServiceRunning.value)
    }

    @Test
    fun discardActiveSession_triggersSleepTrackingControllerDiscardTracking() = runTest(testDispatcher) {
        val viewModel = HomeViewModel(
            sessionManager = sessionManager,
            repository = repository,
            sleepTrackingController = fakeController
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        viewModel.startSession()
        advanceUntilIdle()

        viewModel.discardActiveSession()
        advanceUntilIdle()

        assertEquals(1, fakeController.discardTrackingCallCount)
        assertFalse(fakeController.isServiceRunning.value)
    }

    @Test
    fun activeSessionWithoutRunningService_isFlaggedAsInterrupted() = runTest(testDispatcher) {
        // Simulate a session created previously in Room, but service died or device rebooted
        sessionManager.startSession("Pre-reboot session")
        advanceUntilIdle()

        // Service is NOT running
        assertFalse(fakeController.isServiceRunning.value)

        val viewModel = HomeViewModel(
            sessionManager = sessionManager,
            repository = repository,
            sleepTrackingController = fakeController
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isTrackingServiceRunning)
        assertTrue(state.isTrackingInterrupted)
    }

    @Test
    fun resumeTracking_restartsServiceForActiveInterruptedSession() = runTest(testDispatcher) {
        // Start session directly in session manager without controller to simulate pre-existing active session
        sessionManager.startSession("Interrupted session")
        advanceUntilIdle()

        val viewModel = HomeViewModel(
            sessionManager = sessionManager,
            repository = repository,
            sleepTrackingController = fakeController
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isTrackingInterrupted)

        viewModel.resumeTracking()
        advanceUntilIdle()

        assertEquals(1, fakeController.startTrackingCallCount)
        assertTrue(viewModel.uiState.value.isTrackingServiceRunning)
        assertFalse(viewModel.uiState.value.isTrackingInterrupted)
    }
}
