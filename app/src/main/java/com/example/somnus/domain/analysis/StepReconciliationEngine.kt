package com.example.somnus.domain.analysis

import com.example.somnus.domain.model.DailyStepSummary
import com.example.somnus.domain.model.StepRecord
import com.example.somnus.domain.model.StepReconciliationRationale
import com.example.somnus.domain.model.StepSource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Pure domain engine responsible for reconciling physical step records from multiple sources.
 *
 * Principles:
 * 1. Never blindly sum phone and wearable steps spanning overlapping intervals.
 * 2. Deduplicate overlapping intervals and prefer continuous on-body wearable records.
 * 3. Combine non-overlapping intervals (e.g. phone steps while wearable was charging).
 * 4. Transparently report source contributions and reconciliation rationale.
 * 5. Compute honest hourly step distributions (0-23) for daily activity breakdown.
 */
object StepReconciliationEngine {

    /**
     * Reconciles all [StepRecord]s for a given [date] against the user's [goalSteps].
     */
    fun reconcileDailySteps(
        date: LocalDate,
        records: List<StepRecord>,
        goalSteps: Int = 8000,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): DailyStepSummary {
        if (records.isEmpty()) {
            return DailyStepSummary(
                date = date,
                totalSteps = 0L,
                goalSteps = goalSteps,
                phoneSteps = 0L,
                healthConnectSteps = 0L,
                rationale = StepReconciliationRationale.SINGLE_SOURCE,
                isGoalReached = false,
                goalProgressPercentage = 0f,
                hourlyDistribution = emptyMap(),
                sourcesPresent = emptySet(),
                hasUncertainty = false,
                rawRecordsCount = 0
            )
        }

        val sourcesPresent = records.map { it.source }.toSet()
        val phoneRecords = records.filter { it.source == StepSource.PHONE_HARDWARE }
        val wearableRecords = records.filter {
            it.source == StepSource.HEALTH_CONNECT_WEARABLE || it.source == StepSource.HEALTH_CONNECT_PHONE
        }
        val manualRecords = records.filter { it.source == StepSource.MANUAL_ENTRY }

        val rawPhoneTotal = phoneRecords.sumOf { it.stepCount }
        val rawWearableTotal = wearableRecords.sumOf { it.stepCount }
        val rawManualTotal = manualRecords.sumOf { it.stepCount }

        // Single source scenario: if only one category of source is present
        if (sourcesPresent.size == 1) {
            val singleSource = sourcesPresent.first()
            val total = records.sumOf { it.stepCount }
            val hourlyMap = computeHourlyDistribution(records, zoneId)
            return DailyStepSummary(
                date = date,
                totalSteps = total,
                goalSteps = goalSteps,
                phoneSteps = if (singleSource == StepSource.PHONE_HARDWARE) total else 0L,
                healthConnectSteps = if (singleSource != StepSource.PHONE_HARDWARE && singleSource != StepSource.MANUAL_ENTRY) total else 0L,
                rationale = StepReconciliationRationale.SINGLE_SOURCE,
                isGoalReached = total >= goalSteps,
                goalProgressPercentage = calculateProgress(total, goalSteps),
                hourlyDistribution = hourlyMap,
                sourcesPresent = sourcesPresent,
                hasUncertainty = false,
                rawRecordsCount = records.size
            )
        }

        // Multi-source reconciliation:
        // Distribute records per source into 24 hourly buckets
        val phoneHourly = computeHourlyDistribution(phoneRecords, zoneId)
        val wearableHourly = computeHourlyDistribution(wearableRecords, zoneId)
        val manualHourly = computeHourlyDistribution(manualRecords, zoneId)

        val reconciledHourly = mutableMapOf<Int, Long>()
        var overlappingHoursCount = 0
        var phonePreferredCount = 0
        var wearablePreferredCount = 0

        for (hour in 0..23) {
            val pSteps = phoneHourly[hour] ?: 0L
            val wSteps = wearableHourly[hour] ?: 0L
            val mSteps = manualHourly[hour] ?: 0L

            val hourTotal = when {
                mSteps > 0L -> {
                    // Manual entry takes explicit user priority if provided for that hour
                    mSteps
                }
                pSteps > 0L && wSteps > 0L -> {
                    // Overlap detected!
                    overlappingHoursCount++
                    if (pSteps > (wSteps * 1.5f).toLong()) {
                        // Phone recorded significantly higher coverage (e.g. wearable off-wrist or delayed sync)
                        phonePreferredCount++
                        pSteps
                    } else {
                        // Wearable is on-body and preferred for movement fidelity
                        wearablePreferredCount++
                        wSteps
                    }
                }
                wSteps > 0L -> {
                    wSteps
                }
                pSteps > 0L -> {
                    pSteps
                }
                else -> 0L
            }

            if (hourTotal > 0L) {
                reconciledHourly[hour] = hourTotal
            }
        }

        val totalSteps = reconciledHourly.values.sum()

        // Determine reconciliation rationale
        val rationale = when {
            overlappingHoursCount == 0 -> {
                StepReconciliationRationale.NON_OVERLAPPING_SUMMED
            }
            phonePreferredCount > wearablePreferredCount -> {
                StepReconciliationRationale.DEDUPLICATED_PHONE_HIGHER_COVERAGE
            }
            else -> {
                StepReconciliationRationale.DEDUPLICATED_WEARABLE_PREFERRED
            }
        }

        return DailyStepSummary(
            date = date,
            totalSteps = totalSteps,
            goalSteps = goalSteps,
            phoneSteps = rawPhoneTotal,
            healthConnectSteps = rawWearableTotal + rawManualTotal,
            rationale = rationale,
            isGoalReached = totalSteps >= goalSteps,
            goalProgressPercentage = calculateProgress(totalSteps, goalSteps),
            hourlyDistribution = reconciledHourly,
            sourcesPresent = sourcesPresent,
            hasUncertainty = overlappingHoursCount > 0 && Math.abs(rawPhoneTotal - rawWearableTotal) > 3000,
            rawRecordsCount = records.size
        )
    }

    /**
     * Reconciles a date range into a daily map of summaries.
     */
    fun reconcileDateRange(
        startDate: LocalDate,
        endDate: LocalDate,
        records: List<StepRecord>,
        goalSteps: Int = 8000,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Map<LocalDate, DailyStepSummary> {
        val recordsByDate = records.groupBy { it.localDate }
        val summaries = mutableMapOf<LocalDate, DailyStepSummary>()

        var current = startDate
        while (!current.isAfter(endDate)) {
            val dailyRecords = recordsByDate[current] ?: emptyList()
            summaries[current] = reconcileDailySteps(current, dailyRecords, goalSteps, zoneId)
            current = current.plusDays(1)
        }
        return summaries
    }

    /**
     * Computes the hourly distribution (hour 0-23 -> step count) for a list of records.
     * Accurately splits records spanning multiple hours.
     */
    fun computeHourlyDistribution(
        records: List<StepRecord>,
        zoneId: ZoneId
    ): Map<Int, Long> {
        val distribution = mutableMapOf<Int, Long>()

        for (record in records) {
            val count = record.stepCount
            if (count <= 0L) continue

            val startZdt = record.startTime.atZone(zoneId)
            val effectiveEndTime = if (record.endTime.isAfter(record.startTime)) {
                record.endTime.minusNanos(1)
            } else {
                record.startTime
            }
            val endZdt = effectiveEndTime.atZone(zoneId)

            val startHour = startZdt.hour
            val endHour = endZdt.hour

            if (startHour == endHour && startZdt.toLocalDate() == endZdt.toLocalDate()) {
                distribution[startHour] = (distribution[startHour] ?: 0L) + count
            } else {
                // Spans multiple hours: distribute proportionally across duration
                val totalDurationMs = record.duration.toMillis().coerceAtLeast(1L)
                val hoursSpan = (endHour - startHour + 24) % 24
                if (hoursSpan == 0) {
                    distribution[startHour] = (distribution[startHour] ?: 0L) + count
                } else {
                    val stepsPerHour = count / (hoursSpan + 1).coerceAtLeast(1)
                    val remainder = count % (hoursSpan + 1).coerceAtLeast(1)

                    for (hOffset in 0..hoursSpan) {
                        val h = (startHour + hOffset) % 24
                        val allocated = stepsPerHour + if (hOffset == 0) remainder else 0L
                        distribution[h] = (distribution[h] ?: 0L) + allocated
                    }
                }
            }
        }

        return distribution
    }

    private fun calculateProgress(total: Long, goal: Int): Float {
        if (goal <= 0) return 0f
        return ((total.toFloat() / goal.toFloat()) * 100f).coerceIn(0f, 1000f)
    }
}
