package com.example.somnus.domain.analysis

import com.example.somnus.domain.model.StepRecord
import com.example.somnus.domain.model.StepReconciliationRationale
import com.example.somnus.domain.model.StepSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class StepReconciliationEngineTest {

    private val testDate = LocalDate.of(2026, 10, 14)
    private val zoneId = ZoneId.of("UTC")

    @Test
    fun reconcileDailySteps_whenEmpty_returnsZeroStepsAndSingleSource() {
        val summary = StepReconciliationEngine.reconcileDailySteps(
            date = testDate,
            records = emptyList(),
            goalSteps = 8000,
            zoneId = zoneId
        )

        assertEquals(0L, summary.totalSteps)
        assertEquals(8000, summary.goalSteps)
        assertFalse(summary.isGoalReached)
        assertEquals(0f, summary.goalProgressPercentage, 0.01f)
        assertEquals(StepReconciliationRationale.SINGLE_SOURCE, summary.rationale)
        assertTrue(summary.hourlyDistribution.isEmpty())
    }

    @Test
    fun reconcileDailySteps_singlePhoneSource_sumsCorrectly() {
        val start = Instant.parse("2026-10-14T09:00:00Z")
        val end = Instant.parse("2026-10-14T10:00:00Z")

        val record1 = StepRecord(
            id = "r1",
            startTime = start,
            endTime = end,
            localDate = testDate,
            zoneId = zoneId,
            stepCount = 1500,
            source = StepSource.PHONE_HARDWARE,
            sourceIdentifier = "sensor:type_step_counter"
        )

        val summary = StepReconciliationEngine.reconcileDailySteps(
            date = testDate,
            records = listOf(record1),
            goalSteps = 1000,
            zoneId = zoneId
        )

        assertEquals(1500L, summary.totalSteps)
        assertEquals(1500L, summary.phoneSteps)
        assertEquals(0L, summary.healthConnectSteps)
        assertTrue(summary.isGoalReached)
        assertEquals(StepReconciliationRationale.SINGLE_SOURCE, summary.rationale)
        assertEquals(1500L, summary.hourlyDistribution[9])
    }

    @Test
    fun reconcileDailySteps_overlappingPhoneAndWearable_prefersWearableWithoutDoubleCounting() {
        // Both phone and wearable recorded steps in hour 14 (2 PM)
        val start = Instant.parse("2026-10-14T14:00:00Z")
        val end = Instant.parse("2026-10-14T15:00:00Z")

        val phoneRecord = StepRecord(
            id = "phone-1",
            startTime = start,
            endTime = end,
            localDate = testDate,
            zoneId = zoneId,
            stepCount = 2000,
            source = StepSource.PHONE_HARDWARE,
            sourceIdentifier = "sensor:counter"
        )

        val wearableRecord = StepRecord(
            id = "wearable-1",
            startTime = start,
            endTime = end,
            localDate = testDate,
            zoneId = zoneId,
            stepCount = 2150,
            source = StepSource.HEALTH_CONNECT_WEARABLE,
            sourceIdentifier = "com.google.android.apps.fitness",
            deviceModel = "Pixel Watch 2"
        )

        val summary = StepReconciliationEngine.reconcileDailySteps(
            date = testDate,
            records = listOf(phoneRecord, wearableRecord),
            goalSteps = 8000,
            zoneId = zoneId
        )

        // NEVER 4150 (which would be blind double counting)!
        assertEquals(2150L, summary.totalSteps)
        assertEquals(2000L, summary.phoneSteps)
        assertEquals(2150L, summary.healthConnectSteps)
        assertEquals(StepReconciliationRationale.DEDUPLICATED_WEARABLE_PREFERRED, summary.rationale)
        assertEquals(2150L, summary.hourlyDistribution[14])
    }

    @Test
    fun reconcileDailySteps_nonOverlappingIntervals_combinesBothSourcesSafely() {
        // Phone recorded at 10 AM (watch was on charger)
        val phoneStart = Instant.parse("2026-10-14T10:00:00Z")
        val phoneEnd = Instant.parse("2026-10-14T11:00:00Z")
        val phoneRecord = StepRecord(
            id = "phone-1",
            startTime = phoneStart,
            endTime = phoneEnd,
            localDate = testDate,
            zoneId = zoneId,
            stepCount = 1200,
            source = StepSource.PHONE_HARDWARE,
            sourceIdentifier = "sensor:counter"
        )

        // Wearable recorded at 18:00 (user ran outside without phone)
        val wearStart = Instant.parse("2026-10-14T18:00:00Z")
        val wearEnd = Instant.parse("2026-10-14T19:00:00Z")
        val wearableRecord = StepRecord(
            id = "wearable-1",
            startTime = wearStart,
            endTime = wearEnd,
            localDate = testDate,
            zoneId = zoneId,
            stepCount = 3500,
            source = StepSource.HEALTH_CONNECT_WEARABLE,
            sourceIdentifier = "com.garmin.connect",
            deviceModel = "Garmin Forerunner 965"
        )

        val summary = StepReconciliationEngine.reconcileDailySteps(
            date = testDate,
            records = listOf(phoneRecord, wearableRecord),
            goalSteps = 8000,
            zoneId = zoneId
        )

        // Non-overlapping intervals should be combined: 1200 + 3500 = 4700
        assertEquals(4700L, summary.totalSteps)
        assertEquals(1200L, summary.phoneSteps)
        assertEquals(3500L, summary.healthConnectSteps)
        assertEquals(StepReconciliationRationale.NON_OVERLAPPING_SUMMED, summary.rationale)
        assertEquals(1200L, summary.hourlyDistribution[10])
        assertEquals(3500L, summary.hourlyDistribution[18])
    }

    @Test
    fun reconcileDailySteps_multiHourRecord_distributesAcrossHours() {
        // Record spanning 8:00 to 10:00 (2 hours) with 1000 steps
        val start = Instant.parse("2026-10-14T08:00:00Z")
        val end = Instant.parse("2026-10-14T10:00:00Z")

        val record = StepRecord(
            id = "multi-hour",
            startTime = start,
            endTime = end,
            localDate = testDate,
            zoneId = zoneId,
            stepCount = 1000,
            source = StepSource.PHONE_HARDWARE,
            sourceIdentifier = "sensor"
        )

        val hourly = StepReconciliationEngine.computeHourlyDistribution(listOf(record), zoneId)
        val totalDistributed = hourly.values.sum()
        assertEquals(1000L, totalDistributed)
        assertTrue((hourly[8] ?: 0L) > 0)
        assertTrue((hourly[9] ?: 0L) > 0)
    }

    @Test
    fun reconcileDateRange_computesSummariesForAllDates() {
        val date1 = LocalDate.of(2026, 10, 13)
        val date2 = LocalDate.of(2026, 10, 14)

        val record1 = StepRecord(
            id = "d1",
            startTime = Instant.parse("2026-10-13T10:00:00Z"),
            endTime = Instant.parse("2026-10-13T11:00:00Z"),
            localDate = date1,
            zoneId = zoneId,
            stepCount = 5000,
            source = StepSource.PHONE_HARDWARE,
            sourceIdentifier = "sensor"
        )

        val summaries = StepReconciliationEngine.reconcileDateRange(
            startDate = date1,
            endDate = date2,
            records = listOf(record1),
            goalSteps = 8000,
            zoneId = zoneId
        )

        assertEquals(2, summaries.size)
        assertEquals(5000L, summaries[date1]?.totalSteps)
        assertEquals(0L, summaries[date2]?.totalSteps)
    }
}
