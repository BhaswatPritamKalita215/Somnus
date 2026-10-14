package com.example.somnus.domain.analysis

import com.example.somnus.domain.model.SolMeasurementMethod
import com.example.somnus.domain.model.SolQualityFlag
import com.example.somnus.domain.model.SolRecord
import java.time.Duration
import java.time.Instant

/**
 * Summary metrics for a collection of Sleep Onset Latency records over a specific time window.
 */
data class SolSummary(
    val periodLabel: String,
    val totalDaysInPeriod: Int,
    val recordedNightsCount: Int,
    val averageLatencyMinutes: Double?,
    val medianLatencyMinutes: Double?,
    val minLatencyMinutes: Int?,
    val maxLatencyMinutes: Int?,
    val selfReportedCount: Int,
    val importedOrEstimatedCount: Int,
    val missingNightsCount: Int
)

/**
 * Pure calculation and validation logic for Sleep Onset Latency (SOL).
 *
 * Implements strict physiological sanity checks and summary calculations without
 * inventing or interpolating missing physiological onset data.
 */
object SolCalculator {

    /** Maximum realistic latency duration before considering the record an error (24 hours = 1440 min). */
    const val MAX_ALLOWABLE_LATENCY_MINUTES = 1440

    /** Threshold above which latency is flagged as an outlier (2 hours = 120 min). */
    const val OUTLIER_LATENCY_THRESHOLD_MINUTES = 120

    /**
     * Calculates SOL in integer minutes from an explicit attempt-to-sleep timestamp
     * and a verified/estimated sleep-onset timestamp.
     *
     * @param attemptToSleep The instant the user actively attempted to initiate sleep (lights out / in-bed).
     * @param sleepOnset The instant physiological sleep onset was estimated or confirmed.
     * @return [Result.success] containing the duration in minutes, or [Result.failure] if invalid.
     */
    fun calculateSolMinutes(
        attemptToSleep: Instant,
        sleepOnset: Instant
    ): Result<Int> {
        if (sleepOnset.isBefore(attemptToSleep)) {
            return Result.failure(
                IllegalArgumentException("Sleep onset cannot occur before attempt-to-sleep time.")
            )
        }

        val durationMinutes = Duration.between(attemptToSleep, sleepOnset).toMinutes()

        if (durationMinutes > MAX_ALLOWABLE_LATENCY_MINUTES) {
            return Result.failure(
                IllegalArgumentException(
                    "Calculated latency ($durationMinutes min) exceeds physiological limit of $MAX_ALLOWABLE_LATENCY_MINUTES min."
                )
            )
        }

        return Result.success(durationMinutes.toInt())
    }

    /**
     * Determines appropriate quality flags based on latency magnitude and entry method.
     */
    fun evaluateQualityFlags(
        latencyMinutes: Int,
        hasExplicitTimestamps: Boolean,
        isManuallyEdited: Boolean,
        existingFlags: Set<SolQualityFlag> = emptySet()
    ): Set<SolQualityFlag> {
        val flags = existingFlags.toMutableSet()

        if (latencyMinutes >= OUTLIER_LATENCY_THRESHOLD_MINUTES) {
            flags.add(SolQualityFlag.OUTLIER_EXCESSIVE_LATENCY)
        }
        if (!hasExplicitTimestamps) {
            flags.add(SolQualityFlag.MISSING_SLEEP_ATTEMPT)
        }
        if (isManuallyEdited) {
            flags.add(SolQualityFlag.EDITED_MANUALLY)
        }

        return flags
    }

    /**
     * Calculates weekly, monthly, or arbitrary window statistics for a list of records.
     *
     * When calculating the median and mean, each distinct night's primary or user-selected
     * record is utilized.
     */
    fun calculateSummary(
        records: List<SolRecord>,
        totalDaysInPeriod: Int,
        periodLabel: String
    ): SolSummary {
        if (records.isEmpty()) {
            return SolSummary(
                periodLabel = periodLabel,
                totalDaysInPeriod = totalDaysInPeriod,
                recordedNightsCount = 0,
                averageLatencyMinutes = null,
                medianLatencyMinutes = null,
                minLatencyMinutes = null,
                maxLatencyMinutes = null,
                selfReportedCount = 0,
                importedOrEstimatedCount = 0,
                missingNightsCount = totalDaysInPeriod
            )
        }

        val latencies = records.map { it.latencyMinutes }.sorted()
        val count = latencies.size

        val average = latencies.average()

        val median = if (count % 2 == 1) {
            latencies[count / 2].toDouble()
        } else {
            (latencies[count / 2 - 1] + latencies[count / 2]) / 2.0
        }

        val min = latencies.first()
        val max = latencies.last()

        val selfReported = records.count { it.method == SolMeasurementMethod.SELF_REPORTED }
        val importedOrEstimated = records.count { it.method != SolMeasurementMethod.SELF_REPORTED }

        // Number of unique nights recorded in this period
        val uniqueNights = records.map { it.reportingDate }.distinct().size
        val missingNights = maxOf(0, totalDaysInPeriod - uniqueNights)

        return SolSummary(
            periodLabel = periodLabel,
            totalDaysInPeriod = totalDaysInPeriod,
            recordedNightsCount = count,
            averageLatencyMinutes = Math.round(average * 10.0) / 10.0,
            medianLatencyMinutes = Math.round(median * 10.0) / 10.0,
            minLatencyMinutes = min,
            maxLatencyMinutes = max,
            selfReportedCount = selfReported,
            importedOrEstimatedCount = importedOrEstimated,
            missingNightsCount = missingNights
        )
    }
}
