package com.example.somnus.data.repository

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.somnus.data.local.SomnusDatabase
import com.example.somnus.data.preferences.UserPreferencesRepository
import com.example.somnus.domain.assistant.AssistantCache
import com.example.somnus.domain.assistant.ContextBuilder
import com.example.somnus.domain.assistant.DeterministicLocalAssistantModelProvider
import com.example.somnus.domain.assistant.IntentType
import com.example.somnus.domain.assistant.LocalDataQueryService
import com.example.somnus.domain.assistant.MessageRole
import com.example.somnus.domain.model.SessionSource
import com.example.somnus.domain.model.SessionStatus
import com.example.somnus.domain.model.SleepSession
import com.example.somnus.domain.model.StepRecord
import com.example.somnus.domain.model.StepSource
import com.example.somnus.session.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AssistantRepositoryTest {

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
    private lateinit var localDataQueryService: LocalDataQueryService
    private lateinit var contextBuilder: ContextBuilder
    private lateinit var assistantCache: AssistantCache
    private lateinit var assistantRepository: AssistantRepositoryImpl

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val testDate = LocalDate.of(2026, 10, 10)

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
            produceFile = { File(tmpFolder.newFolder(), "assistant_repo_prefs.preferences_pb") }
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

        localDataQueryService = LocalDataQueryService(
            sleepSessionRepository = sleepSessionRepository,
            solRepository = solRepository,
            stepRepository = stepRepository,
            foodLogRepository = foodLogRepository,
            dashboardRepository = dashboardRepository,
            userPreferencesRepository = preferencesRepository,
            healthConnectManager = null
        )

        contextBuilder = ContextBuilder(
            sleepSessionRepository = sleepSessionRepository,
            solRepository = solRepository,
            stepRepository = stepRepository,
            foodLogRepository = foodLogRepository,
            dashboardRepository = dashboardRepository,
            userPreferencesRepository = preferencesRepository
        )

        assistantCache = AssistantCache(ttlMs = 300_000L)

        assistantRepository = AssistantRepositoryImpl(
            dao = db.assistantMessageDao(),
            localDataQueryService = localDataQueryService,
            contextBuilder = contextBuilder,
            modelProvider = DeterministicLocalAssistantModelProvider(),
            assistantCache = assistantCache,
            ioDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun processQuery_deterministicQuery_bypassesModelAndSavesToDb() = runTest(testDispatcher) {
        // Insert a sleep session
        sleepSessionRepository.insertCompletedSession(
            SleepSession(
                id = "session-100",
                startTime = Instant.parse("2026-10-09T23:00:00Z"),
                endTime = Instant.parse("2026-10-10T07:00:00Z"),
                status = SessionStatus.COMPLETED,
                source = SessionSource.MANUAL
            )
        )

        val flow = assistantRepository.processQuery("How did I sleep last night?")
        val results = flow.toList()
        assertTrue(results.isNotEmpty())

        val lastEmission = results.last()
        assertTrue(lastEmission.isModelBypass)
        assertEquals(0, lastEmission.inputTokens)
        assertEquals(0, lastEmission.outputTokens)
        assertEquals(IntentType.LATEST_SLEEP, lastEmission.intentType)
        assertTrue(lastEmission.content.contains("8h 0m"))

        // Check Room persistence
        val messages = assistantRepository.conversationMessages.first()
        assertEquals(2, messages.size) // User message + Assistant response
        assertEquals(MessageRole.USER, messages[0].role)
        assertEquals(MessageRole.ASSISTANT, messages[1].role)
    }

    @Test
    fun processQuery_cacheHit_reusesCachedResponse() = runTest(testDispatcher) {
        // First query
        assistantRepository.processQuery("Hello, what can you do?").toList()

        // Diagnostics after query 1
        var diag = assistantRepository.diagnosticsFlow.first()
        assertEquals(1, diag.totalRequests)
        assertEquals(0, diag.cacheHits)

        // Repeat identical query
        val secondResults = assistantRepository.processQuery("Hello, what can you do?").toList()
        assertEquals(1, secondResults.size)

        diag = assistantRepository.diagnosticsFlow.first()
        assertEquals(2, diag.totalRequests)
        assertEquals(1, diag.cacheHits)
    }

    @Test
    fun processQuery_medicalRefusal_returnsSafeRefusalWithoutCallingModel() = runTest(testDispatcher) {
        val flow = assistantRepository.processQuery("What dosage of prescription sleeping pills should I take?")
        val results = flow.toList()
        val response = results.last()

        assertTrue(response.isModelBypass)
        assertEquals(IntentType.OUT_OF_SCOPE_MEDICAL, response.intentType)
        assertTrue(response.content.contains("cannot diagnose"))
    }

    @Test
    fun clearConversation_removesAllMessagesFromDatabase() = runTest(testDispatcher) {
        assistantRepository.processQuery("How many steps today?").toList()
        var messages = assistantRepository.conversationMessages.first()
        assertTrue(messages.isNotEmpty())

        assistantRepository.clearConversation()
        messages = assistantRepository.conversationMessages.first()
        assertTrue(messages.isEmpty())
    }
}
