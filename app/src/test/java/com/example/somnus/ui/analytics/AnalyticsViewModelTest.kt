package com.example.somnus.ui.analytics

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.somnus.data.local.SomnusDatabase
import com.example.somnus.data.repository.PsqiRepositoryImpl
import com.example.somnus.data.repository.SleepAnalyticsRepositoryImpl
import com.example.somnus.data.repository.SleepSessionRepositoryImpl
import com.example.somnus.data.repository.SolRepositoryImpl
import com.example.somnus.domain.analytics.DateRangeOption
import com.example.somnus.domain.analytics.DaySleepSummary
import com.example.somnus.domain.model.SessionSource
import com.example.somnus.domain.model.SessionStatus
import com.example.somnus.domain.model.SleepSession
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AnalyticsViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var db: SomnusDatabase
    private lateinit var sessionRepository: SleepSessionRepositoryImpl
    private lateinit var analyticsRepository: SleepAnalyticsRepositoryImpl
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = SomnusDatabase.buildInMemoryDatabase(context)
        sessionRepository = SleepSessionRepositoryImpl(db.sleepSessionDao())
        val solRepository = SolRepositoryImpl(db.solRecordDao())
        val psqiRepository = PsqiRepositoryImpl(db.psqiDao())

        analyticsRepository = SleepAnalyticsRepositoryImpl(
            sessionRepository = sessionRepository,
            solRepository = solRepository,
            psqiRepository = psqiRepository,
            dispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_hasLast7DaysOptionAndEmitsReport() = runTest(testDispatcher) {
        val viewModel = AnalyticsViewModel(analyticsRepository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        assertEquals(DateRangeOption.LAST_7_DAYS, viewModel.uiState.value.selectedOption)
        assertNotNull(viewModel.uiState.value.report)
        assertEquals("Last 7 Days", viewModel.uiState.value.selectedRange.label)
    }

    @Test
    fun selectDateRangeOption_updatesOptionAndRange() = runTest(testDispatcher) {
        val viewModel = AnalyticsViewModel(analyticsRepository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        viewModel.selectDateRangeOption(DateRangeOption.LAST_30_DAYS)
        advanceUntilIdle()

        assertEquals(DateRangeOption.LAST_30_DAYS, viewModel.uiState.value.selectedOption)
        assertEquals("Last 30 Days", viewModel.uiState.value.selectedRange.label)

        viewModel.selectDateRangeOption(DateRangeOption.LAST_90_DAYS)
        advanceUntilIdle()

        assertEquals(DateRangeOption.LAST_90_DAYS, viewModel.uiState.value.selectedOption)
        assertEquals("Last 90 Days", viewModel.uiState.value.selectedRange.label)
    }

    @Test
    fun setCustomDateRange_setsCustomOptionWithCorrectDays() = runTest(testDispatcher) {
        val viewModel = AnalyticsViewModel(analyticsRepository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        val start = LocalDate.now().minusDays(13)
        val end = LocalDate.now()
        viewModel.setCustomDateRange(start, end)
        advanceUntilIdle()

        assertEquals(DateRangeOption.CUSTOM, viewModel.uiState.value.selectedOption)
        assertEquals(start, viewModel.uiState.value.selectedRange.startDate)
        assertEquals(end, viewModel.uiState.value.selectedRange.endDate)
        assertEquals(14, viewModel.uiState.value.selectedRange.totalDays)
    }

    @Test
    fun selectDayDetail_updatesSelectedDayAndDeselects() = runTest(testDispatcher) {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val start = yesterday.atTime(23, 0).atZone(ZoneId.systemDefault()).toInstant()
        val end = today.atTime(7, 0).atZone(ZoneId.systemDefault()).toInstant()
        db.sleepSessionDao().insert(
            com.example.somnus.data.local.SleepSessionEntity(
                id = "s-1",
                startTime = start.toEpochMilli(),
                endTime = end.toEpochMilli(),
                timezoneId = ZoneId.systemDefault().id,
                status = "COMPLETED",
                source = "MANUAL",
                createdAt = start.toEpochMilli(),
                updatedAt = end.toEpochMilli(),
                notes = null,
                isCorrected = false
            )
        )

        val viewModel = AnalyticsViewModel(analyticsRepository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        val firstDay = viewModel.uiState.value.report?.dailySummaries?.firstOrNull()
        assertNotNull(firstDay)

        viewModel.selectDayDetail(firstDay)
        advanceUntilIdle()
        assertEquals(firstDay, viewModel.uiState.value.selectedDayDetail)

        viewModel.selectDayDetail(null)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.selectedDayDetail)
    }
}
