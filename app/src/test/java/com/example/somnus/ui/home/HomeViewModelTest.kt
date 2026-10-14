package com.example.somnus.ui.home

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.somnus.data.local.SomnusDatabase
import com.example.somnus.data.repository.DashboardRepository
import com.example.somnus.data.repository.SleepSessionRepositoryImpl
import com.example.somnus.domain.dashboard.CoverageCompletenessTier
import com.example.somnus.domain.dashboard.CrossMetricInsight
import com.example.somnus.domain.dashboard.CrossMetricType
import com.example.somnus.domain.dashboard.DashboardDayData
import com.example.somnus.domain.dashboard.DataCoverageSummary
import com.example.somnus.domain.dashboard.InsightConfidence
import com.example.somnus.session.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
import java.time.LocalDate

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

    @Test
    fun dateNavigation_adjustsSelectedDateInState() = runTest(testDispatcher) {
        val viewModel = HomeViewModel(sessionManager, repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        val today = LocalDate.now()
        assertEquals(today, viewModel.uiState.value.selectedDate)

        viewModel.stepDate(-1)
        advanceUntilIdle()
        assertEquals(today.minusDays(1), viewModel.uiState.value.selectedDate)

        viewModel.stepDate(1)
        advanceUntilIdle()
        assertEquals(today, viewModel.uiState.value.selectedDate)

        val targetDate = LocalDate.of(2026, 5, 20)
        viewModel.selectDate(targetDate)
        advanceUntilIdle()
        assertEquals(targetDate, viewModel.uiState.value.selectedDate)

        viewModel.selectToday()
        advanceUntilIdle()
        assertEquals(today, viewModel.uiState.value.selectedDate)
    }

    @Test
    fun dashboardRepository_streamsDataCoverageAndInsights() = runTest(testDispatcher) {
        val targetDate = LocalDate.of(2026, 10, 14)
        val fakeDayData = DashboardDayData(
            selectedDate = targetDate,
            stepGoal = 10000,
            calorieTarget = 2200
        )
        val fakeInsight = CrossMetricInsight(
            id = "test_insight",
            type = CrossMetricType.ACTIVITY_VS_SLEEP_DURATION,
            title = "Activity & Sleep",
            headline = "Insight headline",
            explanation = "Insight explanation",
            validDaysCount = 5,
            minimumDaysRequired = 3,
            confidence = InsightConfidence.SUFFICIENT_DATA
        )
        val fakeCoverage = DataCoverageSummary(
            startDate = targetDate.minusDays(6),
            endDate = targetDate,
            totalDays = 7,
            sleepDaysWithData = 5,
            stepDaysWithData = 6,
            nutritionDaysWithData = 4,
            energyDaysWithData = 2,
            completenessTier = CoverageCompletenessTier.MODERATE,
            summaryText = "Moderate coverage"
        )

        val fakeDashboardRepo = object : DashboardRepository {
            val dayFlow = MutableStateFlow(fakeDayData)
            val insightsFlow = MutableStateFlow(listOf(fakeInsight))
            val coverageFlow = MutableStateFlow(fakeCoverage)

            override fun observeDashboardDay(date: LocalDate): Flow<DashboardDayData> = dayFlow
            override fun observeCrossMetricInsights(startDate: LocalDate, endDate: LocalDate): Flow<List<CrossMetricInsight>> = insightsFlow
            override fun observeDataCoverage(startDate: LocalDate, endDate: LocalDate): Flow<DataCoverageSummary> = coverageFlow
        }

        val viewModel = HomeViewModel(
            sessionManager = sessionManager,
            repository = repository,
            dashboardRepository = fakeDashboardRepo
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        viewModel.selectDate(targetDate)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(targetDate, state.selectedDate)
        assertEquals(10000, state.dayData.stepGoal)
        assertEquals(2200, state.dayData.calorieTarget)
        assertEquals(1, state.crossMetricInsights.size)
        assertEquals("test_insight", state.crossMetricInsights[0].id)
        assertEquals(CoverageCompletenessTier.MODERATE, state.coverageSummary?.completenessTier)
    }
}
