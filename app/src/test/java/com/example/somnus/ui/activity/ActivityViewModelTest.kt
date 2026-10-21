package com.example.somnus.ui.activity

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.somnus.data.healthconnect.FakeHealthConnectClientWrapper
import com.example.somnus.data.healthconnect.HealthConnectAvailability
import com.example.somnus.data.healthconnect.HealthConnectManager
import com.example.somnus.data.healthconnect.HealthConnectStepRecord
import com.example.somnus.data.local.SomnusDatabase
import com.example.somnus.data.preferences.UserPreferencesRepository
import com.example.somnus.data.repository.SleepSessionRepositoryImpl
import com.example.somnus.data.repository.StepRepositoryImpl
import com.example.somnus.data.sensor.PhoneStepTracker
import com.example.somnus.domain.model.StepRecord
import com.example.somnus.domain.model.StepSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ActivityViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val tmpFolder: TemporaryFolder = TemporaryFolder()

    private lateinit var db: SomnusDatabase
    private lateinit var stepRepository: StepRepositoryImpl
    private lateinit var sessionRepository: SleepSessionRepositoryImpl
    private lateinit var fakeClient: FakeHealthConnectClientWrapper
    private lateinit var healthConnectManager: HealthConnectManager
    private lateinit var preferencesRepository: UserPreferencesRepository
    private lateinit var phoneStepTracker: PhoneStepTracker
    private lateinit var viewModel: ActivityViewModel

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = SomnusDatabase.buildInMemoryDatabase(context)
        stepRepository = StepRepositoryImpl(db.stepRecordDao(), db.stepCheckpointDao(), testDispatcher)
        sessionRepository = SleepSessionRepositoryImpl(db.sleepSessionDao(), testDispatcher)
        fakeClient = FakeHealthConnectClientWrapper()
        healthConnectManager = HealthConnectManager(fakeClient, sessionRepository, testDispatcher, stepRepository)

        val testDataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { File(tmpFolder.newFolder(), "activity_test_preferences.preferences_pb") }
        )
        preferencesRepository = UserPreferencesRepository(testDataStore)

        phoneStepTracker = PhoneStepTracker(
            context = context,
            stepRepository = stepRepository,
            scope = testScope,
            ioDispatcher = testDispatcher
        )

        viewModel = ActivityViewModel(
            stepRepository = stepRepository,
            phoneStepTracker = phoneStepTracker,
            healthConnectManager = healthConnectManager,
            userPreferencesRepository = preferencesRepository
        )
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun initialUiState_loadsDefaultGoalAndZeroSteps() = runTest(testDispatcher) {
        val collectJob = launch(testDispatcher) { viewModel.uiState.collect() }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(8000, state.todaySummary.goalSteps)
        assertEquals(0L, state.todaySummary.totalSteps)
        assertFalse(state.todaySummary.isGoalReached)

        collectJob.cancel()
    }

    @Test
    fun stepRecordsInRepository_updateUiStateTotalAndProgress() = runTest(testDispatcher) {
        val collectJob = launch(testDispatcher) { viewModel.uiState.collect() }
        advanceUntilIdle()

        val today = LocalDate.now()
        val record = StepRecord(
            id = "rec-1",
            startTime = Instant.now().minusSeconds(1800),
            endTime = Instant.now(),
            localDate = today,
            zoneId = ZoneId.systemDefault(),
            stepCount = 4000,
            source = StepSource.PHONE_HARDWARE,
            sourceIdentifier = "hardware"
        )
        stepRepository.insertRecord(record)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(4000L, state.todaySummary.totalSteps)
        assertEquals(50f, state.todaySummary.goalProgressPercentage, 0.1f)
        assertFalse(state.todaySummary.isGoalReached)

        collectJob.cancel()
    }

    @Test
    fun saveGoal_updatesPreferencesAndRecalculatesProgress() = runTest(testDispatcher) {
        val collectJob = launch(testDispatcher) { viewModel.uiState.collect() }
        advanceUntilIdle()

        val today = LocalDate.now()
        stepRepository.insertRecord(
            StepRecord(
                id = "rec-2",
                startTime = Instant.now().minusSeconds(3600),
                endTime = Instant.now(),
                localDate = today,
                zoneId = ZoneId.systemDefault(),
                stepCount = 6000,
                source = StepSource.PHONE_HARDWARE,
                sourceIdentifier = "hardware"
            )
        )
        advanceUntilIdle()

        viewModel.onOpenGoalDialog()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showGoalDialog)

        viewModel.onGoalInputChange("5000")
        viewModel.onSaveGoal()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.showGoalDialog)
        assertEquals(5000, state.todaySummary.goalSteps)
        assertEquals(6000L, state.todaySummary.totalSteps)
        assertTrue(state.todaySummary.isGoalReached) // 6,000 >= 5,000!

        collectJob.cancel()
    }

    @Test
    fun syncHealthConnect_importsRecordsAndShowsSyncMessage() = runTest(testDispatcher) {
        val collectJob = launch(testDispatcher) { viewModel.uiState.collect() }
        advanceUntilIdle()

        val todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
        fakeClient.stubbedAvailability = HealthConnectAvailability.AVAILABLE
        fakeClient.stepPermissionsGranted = true
        fakeClient.stepRecordsToReturn.add(
            HealthConnectStepRecord(
                id = "hc_vm_step_1",
                startTime = todayStart.plusSeconds(3600),
                endTime = todayStart.plusSeconds(7200),
                count = 3200,
                deviceModel = "Pixel Watch"
            )
        )

        viewModel.syncHealthConnect()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.syncMessage != null)
        assertTrue(state.syncMessage?.contains("Step sync complete") == true)
        assertEquals(3200L, state.todaySummary.totalSteps)

        viewModel.clearSyncMessage()
        advanceUntilIdle()
        assertEquals(null, viewModel.uiState.value.syncMessage)

        collectJob.cancel()
    }
}
