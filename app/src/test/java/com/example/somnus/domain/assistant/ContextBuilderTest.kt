package com.example.somnus.domain.assistant

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.somnus.data.local.SomnusDatabase
import com.example.somnus.data.preferences.UserPreferencesRepository
import com.example.somnus.data.repository.DashboardRepositoryImpl
import com.example.somnus.data.repository.FoodLogRepositoryImpl
import com.example.somnus.data.repository.PsqiRepositoryImpl
import com.example.somnus.data.repository.SleepSessionRepositoryImpl
import com.example.somnus.data.repository.SolRepositoryImpl
import com.example.somnus.data.repository.StepRepositoryImpl
import com.example.somnus.domain.model.FoodLogEntry
import com.example.somnus.domain.model.MealType
import com.example.somnus.domain.model.ServingUnit
import com.example.somnus.domain.model.SessionSource
import com.example.somnus.domain.model.SessionStatus
import com.example.somnus.domain.model.SleepSession
import com.example.somnus.domain.model.StepRecord
import com.example.somnus.domain.model.StepSource
import com.example.somnus.session.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
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
class ContextBuilderTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val tmpFolder: TemporaryFolder = TemporaryFolder()

    private lateinit var db: SomnusDatabase
    private lateinit var sleepSessionRepository: SleepSessionRepositoryImpl
    private lateinit var solRepository: SolRepositoryImpl
    private lateinit var psqiRepository: PsqiRepositoryImpl
    private lateinit var stepRepository: StepRepositoryImpl
    private lateinit var foodLogRepository: FoodLogRepositoryImpl
    private lateinit var preferencesRepository: UserPreferencesRepository
    private lateinit var sessionManager: SessionManager
    private lateinit var dashboardRepository: DashboardRepositoryImpl
    private lateinit var contextBuilder: ContextBuilder

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val testDate = LocalDate.of(2026, 10, 10)
    private val zoneId = ZoneId.of("UTC")

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = SomnusDatabase.buildInMemoryDatabase(context)

        sleepSessionRepository = SleepSessionRepositoryImpl(db.sleepSessionDao(), testDispatcher)
        solRepository = SolRepositoryImpl(db.solRecordDao())
        psqiRepository = PsqiRepositoryImpl(db.psqiDao())
        stepRepository = StepRepositoryImpl(db.stepRecordDao(), db.stepCheckpointDao(), testDispatcher)
        foodLogRepository = FoodLogRepositoryImpl(db.foodLogDao(), testDispatcher)

        val testDataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { File(tmpFolder.newFolder(), "context_prefs.preferences_pb") }
        )
        preferencesRepository = UserPreferencesRepository(testDataStore)
        sessionManager = SessionManager(sleepSessionRepository, testScope)

        dashboardRepository = DashboardRepositoryImpl(
            sleepSessionRepository = sleepSessionRepository,
            solRepository = solRepository,
            psqiRepository = psqiRepository,
            stepRepository = stepRepository,
            foodLogRepository = foodLogRepository,
            userPreferencesRepository = preferencesRepository,
            sessionManager = sessionManager,
            healthConnectManager = null,
            ioDispatcher = testDispatcher
        )

        contextBuilder = ContextBuilder(
            sleepSessionRepository = sleepSessionRepository,
            solRepository = solRepository,
            stepRepository = stepRepository,
            foodLogRepository = foodLogRepository,
            dashboardRepository = dashboardRepository,
            userPreferencesRepository = preferencesRepository
        )
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun buildPromptContext_domainPruning_includesOnlyQueriedDomain() = runTest(testDispatcher) {
        // Insert step data
        stepRepository.insertRecord(
            StepRecord(
                id = "step-1",
                startTime = Instant.parse("2026-10-10T08:00:00Z"),
                endTime = Instant.parse("2026-10-10T18:00:00Z"),
                localDate = testDate,
                zoneId = zoneId,
                stepCount = 7500L,
                source = StepSource.PHONE_HARDWARE,
                sourceIdentifier = "sensor:step_detector"
            )
        )

        // Insert food log
        foodLogRepository.logFood(
            FoodLogEntry(
                id = "food-1",
                barcode = "8901234567890",
                productName = "Whole Grain Oats",
                mealType = MealType.BREAKFAST,
                loggedAt = Instant.parse("2026-10-10T08:30:00Z"),
                localDate = testDate,
                quantity = 50.0,
                unit = ServingUnit.GRAMS,
                calculatedCalories = 190.0,
                calculatedProtein = 6.0,
                calculatedCarbs = 33.0,
                calculatedFat = 3.5
            )
        )

        // Query exclusively about steps
        val stepContext = contextBuilder.buildPromptContext(
            query = "How was my step count and walking activity?",
            currentDate = testDate
        )

        assertTrue(stepContext.fullPrompt.contains("7,500 steps"))
        assertFalse(stepContext.fullPrompt.contains("Whole Grain Oats"))
        assertTrue(stepContext.estimatedInputTokens <= 1000)
    }

    @Test
    fun buildPromptContext_strictTokenBudget_remainsWithinLimit() = runTest(testDispatcher) {
        // Insert multiple sessions and steps
        for (i in 0 until 7) {
            val date = testDate.minusDays(i.toLong())
            stepRepository.insertRecord(
                StepRecord(
                    id = "step-$i",
                    startTime = Instant.parse("2026-10-04T08:00:00Z").plusSeconds(i * 86400L),
                    endTime = Instant.parse("2026-10-04T18:00:00Z").plusSeconds(i * 86400L),
                    localDate = date,
                    zoneId = zoneId,
                    stepCount = (8000L + i * 100L),
                    source = StepSource.PHONE_HARDWARE,
                    sourceIdentifier = "sensor:step_detector"
                )
            )
            sleepSessionRepository.insertCompletedSession(
                SleepSession(
                    id = "sleep-$i",
                    startTime = Instant.parse("2026-10-03T22:00:00Z").plusSeconds(i * 86400L),
                    endTime = Instant.parse("2026-10-04T06:00:00Z").plusSeconds(i * 86400L),
                    status = SessionStatus.COMPLETED,
                    source = SessionSource.MANUAL
                )
            )
        }

        val promptContext = contextBuilder.buildPromptContext(
            query = "Compare my sleep and steps over the past week and provide comprehensive insights",
            currentDate = testDate
        )

        assertTrue(
            "Estimated tokens must be <= 1,000, got ${promptContext.estimatedInputTokens}",
            promptContext.estimatedInputTokens <= 1000
        )
    }

    @Test
    fun buildPromptContext_truncatesConversationHistory() = runTest(testDispatcher) {
        val manyMessages = (1..10).map { i ->
            AssistantMessage(
                id = "m-$i",
                role = if (i % 2 == 1) MessageRole.USER else MessageRole.ASSISTANT,
                content = "turn $i: user message about health data.",
                timestamp = Instant.now()
            )
        }

        val promptContext = contextBuilder.buildPromptContext(
            query = "Why do I feel tired in the afternoons?",
            recentMessages = manyMessages,
            currentDate = testDate
        )

        // Only the last 2-3 turns should be retained
        assertFalse(promptContext.fullPrompt.contains("turn 1:"))
        assertFalse(promptContext.fullPrompt.contains("turn 2:"))
        assertTrue(promptContext.fullPrompt.contains("turn 10:"))
    }
}
