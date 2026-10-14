package com.example.somnus.data.repository

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.somnus.data.healthconnect.FakeHealthConnectClientWrapper
import com.example.somnus.data.healthconnect.HealthConnectEnergyExpenditure
import com.example.somnus.data.healthconnect.HealthConnectManager
import com.example.somnus.data.local.SomnusDatabase
import com.example.somnus.data.preferences.UserPreferencesRepository
import com.example.somnus.domain.dashboard.CoverageCompletenessTier
import com.example.somnus.domain.model.FoodLogEntry
import com.example.somnus.domain.model.MealType
import com.example.somnus.domain.model.SessionSource
import com.example.somnus.domain.model.SessionStatus
import com.example.somnus.domain.model.SleepSession
import com.example.somnus.domain.model.SolMeasurementMethod
import com.example.somnus.domain.model.SolRecord
import com.example.somnus.domain.model.StepRecord
import com.example.somnus.domain.model.StepSource
import com.example.somnus.session.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
class DashboardRepositoryTest {

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
    private lateinit var fakeHealthConnectClient: FakeHealthConnectClientWrapper
    private lateinit var healthConnectManager: HealthConnectManager
    private lateinit var dashboardRepository: DashboardRepositoryImpl

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
            produceFile = { File(tmpFolder.newFolder(), "dashboard_prefs.preferences_pb") }
        )
        preferencesRepository = UserPreferencesRepository(testDataStore)

        sessionManager = SessionManager(sleepSessionRepository, testScope)

        fakeHealthConnectClient = FakeHealthConnectClientWrapper()
        healthConnectManager = HealthConnectManager(
            fakeHealthConnectClient,
            sleepSessionRepository,
            testDispatcher,
            stepRepository
        )

        dashboardRepository = DashboardRepositoryImpl(
            sleepSessionRepository = sleepSessionRepository,
            solRepository = solRepository,
            psqiRepository = psqiRepository,
            stepRepository = stepRepository,
            foodLogRepository = foodLogRepository,
            userPreferencesRepository = preferencesRepository,
            sessionManager = sessionManager,
            healthConnectManager = healthConnectManager,
            ioDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun observeDashboardDay_emptyDay_returnsDefaultValues() = runTest(testDispatcher) {
        val dayData = dashboardRepository.observeDashboardDay(testDate).first()

        assertEquals(testDate, dayData.selectedDate)
        assertNull(dayData.sleepSummary)
        assertNull(dayData.primarySleepSession)
        assertTrue(dayData.allSleepSessionsForDate.isEmpty())
        assertNull(dayData.solRecord)
        assertEquals(0L, dayData.stepSummary?.totalSteps ?: -1L)
        assertEquals(0.0, dayData.nutritionSummary?.totalCalories ?: -1.0, 0.01)
        assertFalse(dayData.hasAnyData)
    }

    @Test
    fun observeDashboardDay_midnightCrossingSleep_attributedToClinicalDate() = runTest(testDispatcher) {
        // Sleep session from Oct 10 23:00 to Oct 11 07:00
        val session = SleepSession(
            id = "session_oct10",
            startTime = Instant.parse("2026-10-10T23:00:00Z"),
            endTime = Instant.parse("2026-10-11T07:00:00Z"),
            zoneId = zoneId,
            status = SessionStatus.COMPLETED,
            source = SessionSource.PHONE_ESTIMATE
        )
        sleepSessionRepository.insertCompletedSession(session)

        // Sol record for Oct 10
        val sol = SolRecord(
            id = "sol_oct10",
            sessionId = session.id,
            reportingDate = testDate,
            latencyMinutes = 20,
            method = SolMeasurementMethod.SELF_REPORTED,
            sourceIdentifier = "manual"
        )
        solRepository.saveRecord(sol)

        // Observe Oct 10 (evening clinical date)
        val oct10Data = dashboardRepository.observeDashboardDay(testDate).first()
        assertNotNull(oct10Data.sleepSummary)
        assertEquals(480L, oct10Data.sleepSummary?.totalSleepDurationMinutes)
        assertEquals("session_oct10", oct10Data.primarySleepSession?.id)
        assertNotNull(oct10Data.solRecord)
        assertEquals(20, oct10Data.solRecord?.latencyMinutes)
        assertTrue(oct10Data.hasAnyData)

        // Observe Oct 11 - should not double-count Oct 10's overnight sleep
        val oct11Data = dashboardRepository.observeDashboardDay(testDate.plusDays(1)).first()
        assertNull(oct11Data.sleepSummary)
    }

    @Test
    fun observeDashboardDay_withStepsAndNutrition_aggregatesCorrectly() = runTest(testDispatcher) {
        // Add step record
        val stepRecord = StepRecord(
            id = "step_1",
            startTime = Instant.parse("2026-10-10T08:00:00Z"),
            endTime = Instant.parse("2026-10-10T09:00:00Z"),
            localDate = testDate,
            zoneId = zoneId,
            stepCount = 3500,
            source = StepSource.PHONE_HARDWARE,
            sourceIdentifier = "hardware"
        )
        stepRepository.insertRecord(stepRecord)

        // Add food log entry
        val foodEntry = FoodLogEntry(
            id = "food_1",
            productName = "Steamed Rice & Dal",
            localDate = testDate,
            mealType = MealType.LUNCH,
            quantity = 200.0,
            unit = com.example.somnus.domain.model.ServingUnit.GRAMS,
            calculatedCalories = 320.0,
            calculatedProtein = 12.0,
            calculatedCarbs = 60.0,
            calculatedFat = 4.0
        )
        foodLogRepository.logFood(foodEntry)

        // Setup Health Connect energy return
        fakeHealthConnectClient.energyExpenditureToReturn = HealthConnectEnergyExpenditure(
            activeCaloriesKcal = 280.0,
            totalCaloriesKcal = 1850.0
        )

        val dayData = dashboardRepository.observeDashboardDay(testDate).first()

        assertEquals(3500L, dayData.stepSummary?.totalSteps)
        assertEquals(320.0, dayData.nutritionSummary?.totalCalories ?: 0.0, 0.01)
        assertEquals(1, dayData.nutritionSummary?.entries?.size)
        assertEquals(280.0, dayData.energyExpenditure?.activeCaloriesKcal ?: 0.0, 0.01)
        assertEquals(1850.0, dayData.energyExpenditure?.totalCaloriesKcal ?: 0.0, 0.01)
        assertTrue(dayData.hasHealthConnectEnergyPermissions)
        assertTrue(dayData.hasAnyData)
    }

    @Test
    fun observeDataCoverage_calculatesPercentagesAndTiersCorrectly() = runTest(testDispatcher) {
        val startDate = LocalDate.of(2026, 10, 8)
        val endDate = LocalDate.of(2026, 10, 14) // 7 days: Oct 8..14

        // Insert sleep on Oct 8, 9, 10 (3 days)
        for (i in 0..2) {
            val date = startDate.plusDays(i.toLong())
            sleepSessionRepository.insertCompletedSession(
                SleepSession(
                    id = "s_$i",
                    startTime = date.atTime(22, 0).atZone(zoneId).toInstant(),
                    endTime = date.plusDays(1).atTime(6, 0).atZone(zoneId).toInstant(),
                    zoneId = zoneId,
                    status = SessionStatus.COMPLETED,
                    source = SessionSource.PHONE_ESTIMATE
                )
            )
        }

        // Insert steps on Oct 8, 9, 10, 11 (4 days)
        for (i in 0..3) {
            val date = startDate.plusDays(i.toLong())
            stepRepository.insertRecord(
                StepRecord(
                    id = "step_$i",
                    startTime = date.atTime(8, 0).atZone(zoneId).toInstant(),
                    endTime = date.atTime(9, 0).atZone(zoneId).toInstant(),
                    localDate = date,
                    zoneId = zoneId,
                    stepCount = 4000,
                    source = StepSource.PHONE_HARDWARE,
                    sourceIdentifier = "hardware"
                )
            )
        }

        // Insert food on Oct 8, 9 (2 days)
        for (i in 0..1) {
            val date = startDate.plusDays(i.toLong())
            foodLogRepository.logFood(
                FoodLogEntry(
                    id = "f_$i",
                    productName = "Oatmeal",
                    localDate = date,
                    mealType = MealType.BREAKFAST,
                    quantity = 100.0,
                    unit = com.example.somnus.domain.model.ServingUnit.GRAMS,
                    calculatedCalories = 200.0,
                    calculatedProtein = 6.0,
                    calculatedCarbs = 35.0,
                    calculatedFat = 3.0
                )
            )
        }

        val coverage = dashboardRepository.observeDataCoverage(startDate, endDate).first()

        assertEquals(7, coverage.totalDays)
        assertEquals(3, coverage.sleepDaysWithData)
        assertEquals(4, coverage.stepDaysWithData)
        assertEquals(2, coverage.nutritionDaysWithData)
        // Total slots = 7 * 4 = 28. Filled: 3 + 4 + 2 + 0 (energy) = 9. 9/28 = ~32.14% -> SPARSE tier
        assertEquals(CoverageCompletenessTier.SPARSE, coverage.completenessTier)
        assertTrue(coverage.overallCoveragePercentage in 30f..35f)
    }

    @Test
    fun observeCrossMetricInsights_withSufficientPairedDays_returnsInsights() = runTest(testDispatcher) {
        val startDate = LocalDate.of(2026, 10, 1)
        val endDate = LocalDate.of(2026, 10, 5)

        // Add 5 paired days of sleep and steps
        for (i in 0..4) {
            val date = startDate.plusDays(i.toLong())
            sleepSessionRepository.insertCompletedSession(
                SleepSession(
                    id = "paired_sleep_$i",
                    startTime = date.atTime(23, 0).atZone(zoneId).toInstant(),
                    endTime = date.plusDays(1).atTime(7, 0).atZone(zoneId).toInstant(),
                    zoneId = zoneId,
                    status = SessionStatus.COMPLETED,
                    source = SessionSource.PHONE_ESTIMATE
                )
            )
            stepRepository.insertRecord(
                StepRecord(
                    id = "paired_step_$i",
                    startTime = date.atTime(8, 0).atZone(zoneId).toInstant(),
                    endTime = date.atTime(9, 0).atZone(zoneId).toInstant(),
                    localDate = date,
                    zoneId = zoneId,
                    stepCount = (5000 + i * 2000).toLong(),
                    source = StepSource.PHONE_HARDWARE,
                    sourceIdentifier = "hardware"
                )
            )
        }

        val insights = dashboardRepository.observeCrossMetricInsights(startDate, endDate).first()

        assertFalse(insights.isEmpty())
        val activityVsSleep = insights.find { it.type == com.example.somnus.domain.dashboard.CrossMetricType.ACTIVITY_VS_SLEEP_DURATION }
        assertNotNull(activityVsSleep)
    }
}
