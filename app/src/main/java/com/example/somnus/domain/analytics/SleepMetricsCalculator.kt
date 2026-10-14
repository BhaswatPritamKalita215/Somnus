package com.example.somnus.domain.analytics

import com.example.somnus.domain.model.SleepSession
import com.example.somnus.domain.model.SolRecord
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.sqrt

/**
 * Pure Kotlin calculation engine for core sleep analytics.
 *
 * Implements interval merging to prevent double-counting overlapping sources,
 * circular time averaging for bedtime and wake times, and rigorous variability metrics.
 */
object SleepMetricsCalculator {

    /** Maximum duration in minutes to classify a session as a daytime nap. */
    const val NAP_MAX_DURATION_MINUTES = 180L

    /**
     * Determines the clinical sleep date for a session.
     *
     * Clinical convention: A session starting between 00:00 and 06:00 AM represents an
     * overnight session begun after midnight and is attributed to the preceding evening's sleep date.
     */
    fun determineSleepDate(session: SleepSession): LocalDate {
        val zdt = session.startTime.atZone(session.zoneId)
        val localDate = zdt.toLocalDate()
        val hour = zdt.hour
        return if (hour in 0..5) {
            localDate.minusDays(1)
        } else {
            localDate
        }
    }

    /**
     * Aggregates raw sleep sessions and nightly SOL records into daily sleep summaries.
     */
    fun aggregateDailySummaries(
        sessions: List<SleepSession>,
        solRecords: List<SolRecord> = emptyList()
    ): List<DaySleepSummary> {
        val completedSessions = sessions.filter { it.endTime != null }
        if (completedSessions.isEmpty() && solRecords.isEmpty()) return emptyList()

        // Group sessions by clinical sleep date
        val sessionsByDate = completedSessions.groupBy { determineSleepDate(it) }
        val solByDate = solRecords.groupBy { it.reportingDate }

        val allDates = (sessionsByDate.keys + solByDate.keys).distinct().sorted()

        return allDates.map { date ->
            val dateSessions = sessionsByDate[date].orEmpty()
            val dateSol = solByDate[date]?.firstOrNull() // Take primary SOL for the date

            if (dateSessions.isEmpty()) {
                // SOL record exists without a full session
                DaySleepSummary(
                    sleepDate = date,
                    totalSleepDurationMinutes = 0,
                    totalTimeInBedMinutes = 0,
                    bedtime = null,
                    wakeTime = null,
                    solMinutes = dateSol?.latencyMinutes,
                    isNap = false,
                    sources = emptySet(),
                    sessionCount = 0,
                    hasConflictingOverlaps = false,
                    notes = dateSol?.notes
                )
            } else {
                // Calculate non-overlapping total time in bed
                val intervals = dateSessions.map {
                    it.startTime.toEpochMilli() to it.endTime!!.toEpochMilli()
                }
                val mergedDurationMillis = mergeIntervals(intervals)
                val totalDurationMinutes = mergedDurationMillis / 60_000L

                // Detect if overlapping sessions from different sources exist
                val hasOverlap = intervals.size > 1 &&
                        mergedDurationMillis < dateSessions.sumOf { Duration.between(it.startTime, it.endTime).toMillis() }

                // Identify primary (longest) overnight session vs naps
                val primarySession = dateSessions.maxByOrNull {
                    Duration.between(it.startTime, it.endTime).toMillis()
                }!!

                val isNap = dateSessions.size == 1 &&
                        totalDurationMinutes <= NAP_MAX_DURATION_MINUTES &&
                        isDaytimeSession(primarySession)

                val bedtime = primarySession.startTime.atZone(primarySession.zoneId).toLocalTime()
                val wakeTime = primarySession.endTime!!.atZone(primarySession.zoneId).toLocalTime()

                DaySleepSummary(
                    sleepDate = date,
                    totalSleepDurationMinutes = totalDurationMinutes,
                    totalTimeInBedMinutes = totalDurationMinutes,
                    bedtime = bedtime,
                    wakeTime = wakeTime,
                    solMinutes = dateSol?.latencyMinutes,
                    isNap = isNap,
                    sources = dateSessions.map { it.source }.toSet(),
                    sessionCount = dateSessions.size,
                    hasConflictingOverlaps = hasOverlap,
                    notes = dateSessions.mapNotNull { it.notes }.joinToString("; ").ifBlank { null }
                )
            }
        }
    }

    /**
     * Calculates period-level aggregate metrics from daily sleep summaries.
     */
    fun calculatePeriodMetrics(
        dailySummaries: List<DaySleepSummary>,
        dateRange: SleepDateRange
    ): PeriodSleepMetrics {
        val totalDays = dateRange.totalDays
        val trackedNights = dailySummaries.filter { it.totalSleepDurationMinutes > 0 }
        val trackedCount = trackedNights.size
        val coveragePct = if (totalDays > 0) (trackedCount.toDouble() / totalDays) * 100.0 else 0.0

        if (trackedCount == 0) {
            return PeriodSleepMetrics(
                averageDurationMinutes = null,
                averageTibMinutes = null,
                typicalBedtime = null,
                typicalWakeTime = null,
                bedtimeVariabilityMinutes = null,
                wakeTimeVariabilityMinutes = null,
                durationVariabilityMinutes = null,
                averageSolMinutes = null,
                totalNightsTracked = 0,
                totalDaysInRange = totalDays,
                coveragePercentage = 0.0
            )
        }

        val durations = trackedNights.map { it.totalSleepDurationMinutes.toDouble() }
        val avgDuration = durations.average()

        val bedtimes = trackedNights.mapNotNull { it.bedtime }
        val wakeTimes = trackedNights.mapNotNull { it.wakeTime }

        val circularMeanBedtime = CircularTimeCalculator.calculateCircularMean(bedtimes)
        val circularMeanWakeTime = CircularTimeCalculator.calculateCircularMean(wakeTimes)

        val bedtimeVar = CircularTimeCalculator.calculateCircularStandardDeviationMinutes(bedtimes)
        val wakeTimeVar = CircularTimeCalculator.calculateCircularStandardDeviationMinutes(wakeTimes)

        // Duration standard deviation
        val durationVar = if (durations.size > 1) {
            val mean = durations.average()
            val sumSquaredDiff = durations.sumOf { (it - mean) * (it - mean) }
            val stdDev = sqrt(sumSquaredDiff / (durations.size - 1))
            Math.round(stdDev * 10.0) / 10.0
        } else {
            null
        }

        // SOL average
        val validSols = dailySummaries.mapNotNull { it.solMinutes }
        val avgSol = if (validSols.isNotEmpty()) {
            Math.round(validSols.average() * 10.0) / 10.0
        } else {
            null
        }

        return PeriodSleepMetrics(
            averageDurationMinutes = Math.round(avgDuration * 10.0) / 10.0,
            averageTibMinutes = Math.round(avgDuration * 10.0) / 10.0,
            typicalBedtime = circularMeanBedtime,
            typicalWakeTime = circularMeanWakeTime,
            bedtimeVariabilityMinutes = bedtimeVar,
            wakeTimeVariabilityMinutes = wakeTimeVar,
            durationVariabilityMinutes = durationVar,
            averageSolMinutes = avgSol,
            totalNightsTracked = trackedCount,
            totalDaysInRange = totalDays,
            coveragePercentage = Math.round(coveragePct * 10.0) / 10.0
        )
    }

    /**
     * Merges a list of start/end intervals in milliseconds to prevent double-counting overlaps.
     *
     * @return Total non-overlapping duration in milliseconds.
     */
    fun mergeIntervals(intervals: List<Pair<Long, Long>>): Long {
        if (intervals.isEmpty()) return 0L
        val sorted = intervals.filter { it.second > it.first }.sortedBy { it.first }
        if (sorted.isEmpty()) return 0L

        var totalMillis = 0L
        var currentStart = sorted[0].first
        var currentEnd = sorted[0].second

        for (i in 1 until sorted.size) {
            val (start, end) = sorted[i]
            if (start <= currentEnd) {
                // Overlaps or contiguous: extend current interval
                currentEnd = maxOf(currentEnd, end)
            } else {
                // Disjoint interval: commit previous and start new
                totalMillis += (currentEnd - currentStart)
                currentStart = start
                currentEnd = end
            }
        }
        totalMillis += (currentEnd - currentStart)
        return totalMillis
    }

    private fun isDaytimeSession(session: SleepSession): Boolean {
        val zdt = session.startTime.atZone(session.zoneId)
        val hour = zdt.hour
        return hour in 9..17
    }
}
