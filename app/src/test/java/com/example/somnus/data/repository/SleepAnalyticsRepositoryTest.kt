package com.example.somnus.data.repository

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.somnus.data.local.SomnusDatabase
import com.example.somnus.domain.analytics.DataQualityLevel
import com.example.somnus.domain.analytics.DateRangeOption
import com.example.somnus.domain.analytics.SleepDateRange
import com.example.somnus.domain.model.SessionSource
import com.example.somnus.domain.model.SessionStatus
import com.example.somnus.domain.model.SleepSession
import com.example.somnus.domain.model.SolMeasurementMethod
import com.example.somnus.domain.model.SolRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SleepAnalyticsRepositoryTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var db: SomnusDatabase
    private lateinit var sessionRepository: SleepSessionRepositoryImpl
    private lateinit var solRepository: SolRepositoryImpl
    private lateinit var psqiRepository: PsqiRepositoryImpl
    private lateinit var analyticsRepository: SleepAnalyticsRepositoryImpl
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = SomnusDatabase.buildInMemoryDatabase(context)
        sessionRepository = SleepSessionRepositoryImpl(db.sleepSessionDao())
        solRepository = SolRepositoryImpl(db.solRecordDao())
        psqiRepository = PsqiRepositoryImpl(db.psqiDao())

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
    fun getAnalyticsReport_emptyDatabase_returnsEmptyMetricsAndBuildingBaselineInsight() = runTest(testDispatcher) {
        val range = SleepDateRange(
            startDate = LocalDate.now().minusDays(6),
            endDate = LocalDate.now(),
            label = "Last 7 Days",
            option = DateRangeOption.LAST_7_DAYS
        )

        val report = analyticsRepository.getAnalyticsReport(range).first()

        assertNotNull(report)
        assertEquals(0, report.periodMetrics.totalNightsTracked)
        assertEquals(DataQualityLevel.INSUFFICIENT, report.dataQuality.qualityLevel)
        assertTrue(report.insights.isNotEmpty())
        assertTrue(report.insights.first().title.contains("Building Your Baseline"))
    }

    @Test
    fun getAnalyticsReport_withSessionsAndSol_correctlyAggregatesMetrics() = runTest(testDispatcher) {
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

        val sol = SolRecord(
            id = "sol-1",
            reportingDate = yesterday,
            latencyMinutes = 20,
            method = SolMeasurementMethod.SELF_REPORTED,
            sourceIdentifier = "manual"
        )
        solRepository.saveRecord(sol)

        val range = SleepDateRange(
            startDate = today.minusDays(6),
            endDate = today,
            label = "Last 7 Days",
            option = DateRangeOption.LAST_7_DAYS
        )

        val report = analyticsRepository.getAnalyticsReport(range).first()

        assertEquals(1, report.periodMetrics.totalNightsTracked)
        assertEquals(480.0, report.periodMetrics.averageDurationMinutes!!, 0.01)
        val targetDay = report.dailySummaries.find { it.sleepDate == yesterday }
        assertNotNull(targetDay)
        assertEquals(480L, targetDay!!.totalSleepDurationMinutes)
        assertEquals(20, targetDay.solMinutes)
    }
}
