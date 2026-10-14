package com.example.somnus.domain.analysis

import com.example.somnus.domain.model.PsqiComponentScores
import com.example.somnus.domain.model.PsqiResponses
import java.time.LocalTime
import java.time.format.DateTimeFormatter

sealed interface PsqiScoringResult {
    data class Complete(
        val componentScores: PsqiComponentScores,
        val globalScore: Int,
        val sleepEfficiencyPercentage: Double,
        val hoursInBed: Double
    ) : PsqiScoringResult

    data class Incomplete(
        val missingItems: List<String>,
        val reason: String = "Assessment is incomplete. Missing items cannot be coerced to zero."
    ) : PsqiScoringResult
}

/**
 * Pure Kotlin scoring engine implementing the validated Pittsburgh Sleep Quality Index
 * scoring rules (Buysse, Reynolds, Monk, Berman, & Kupfer, 1989).
 *
 * Scoring version: "PSQI_BUYSSE_1989_V1"
 *
 * Enforces all invariants:
 * - Each component score is strictly 0..3
 * - The global score is strictly 0..21 and equals the exact sum of all 7 components
 * - Missing responses are NEVER converted to zero
 * - Incomplete questionnaires do not produce a completed score
 */
object PsqiScoringEngine {

    const val SCORING_VERSION = "PSQI_BUYSSE_1989_V1"

    private val TIME_FORMATTERS = listOf(
        DateTimeFormatter.ofPattern("HH:mm"),
        DateTimeFormatter.ofPattern("H:mm"),
        DateTimeFormatter.ofPattern("hh:mm a"),
        DateTimeFormatter.ofPattern("h:mm a")
    )

    /**
     * Scores the provided 19 self-rated PSQI responses.
     */
    fun score(responses: PsqiResponses): PsqiScoringResult {
        val missing = mutableListOf<String>()

        if (responses.bedTime.isNullOrBlank()) missing.add("Item 1 (Bed time)")
        if (responses.latencyMinutes == null) missing.add("Item 2 (Latency minutes)")
        if (responses.wakeTime.isNullOrBlank()) missing.add("Item 3 (Wake time)")
        if (responses.actualSleepHours == null) missing.add("Item 4 (Actual sleep hours)")
        if (responses.q5a == null) missing.add("Item 5a (Trouble falling asleep <=30m)")
        if (responses.q5b == null) missing.add("Item 5b (Middle/early awakenings)")
        if (responses.q5c == null) missing.add("Item 5c (Bathroom visits)")
        if (responses.q5d == null) missing.add("Item 5d (Breathing discomfort)")
        if (responses.q5e == null) missing.add("Item 5e (Cough/snore)")
        if (responses.q5f == null) missing.add("Item 5f (Cold)")
        if (responses.q5g == null) missing.add("Item 5g (Hot)")
        if (responses.q5h == null) missing.add("Item 5h (Bad dreams)")
        if (responses.q5i == null) missing.add("Item 5i (Pain)")
        if (responses.q5j == null) missing.add("Item 5j (Other reasons)")
        if (responses.q6 == null) missing.add("Item 6 (Sleep medication)")
        if (responses.q7 == null) missing.add("Item 7 (Daytime sleepiness)")
        if (responses.q8 == null) missing.add("Item 8 (Enthusiasm)")
        if (responses.q9 == null) missing.add("Item 9 (Subjective sleep quality)")

        if (missing.isNotEmpty()) {
            return PsqiScoringResult.Incomplete(missingItems = missing)
        }

        // Parse hours in bed
        val hoursInBed = try {
            calculateHoursInBed(responses.bedTime!!, responses.wakeTime!!)
        } catch (e: Exception) {
            return PsqiScoringResult.Incomplete(
                missingItems = listOf("Invalid bed/wake time format: ${e.message}")
            )
        }

        if (hoursInBed <= 0.0) {
            return PsqiScoringResult.Incomplete(
                missingItems = listOf("Calculated time in bed is non-positive")
            )
        }

        val actualSleep = responses.actualSleepHours!!
        if (actualSleep < 0.0) {
            return PsqiScoringResult.Incomplete(
                missingItems = listOf("Actual sleep hours cannot be negative")
            )
        }

        // Component 1: Subjective Sleep Quality (Item 9)
        val c1 = scoreComponent1(responses.q9!!)

        // Component 2: Sleep Latency (Item 2 + Item 5a)
        val c2 = scoreComponent2(responses.latencyMinutes!!, responses.q5a!!)

        // Component 3: Sleep Duration (Item 4)
        val c3 = scoreComponent3(actualSleep)

        // Component 4: Habitual Sleep Efficiency (Item 4 / hours in bed)
        val efficiencyPct = calculateSleepEfficiency(actualSleep, hoursInBed)
        val c4 = scoreComponent4(efficiencyPct)

        // Component 5: Sleep Disturbances (Items 5b through 5j)
        val disturbanceScores = listOf(
            responses.q5b!!, responses.q5c!!, responses.q5d!!, responses.q5e!!,
            responses.q5f!!, responses.q5g!!, responses.q5h!!, responses.q5i!!, responses.q5j!!
        )
        val c5 = scoreComponent5(disturbanceScores)

        // Component 6: Use of Sleep Medication (Item 6)
        val c6 = scoreComponent6(responses.q6!!)

        // Component 7: Daytime Dysfunction (Items 7 + 8)
        val c7 = scoreComponent7(responses.q7!!, responses.q8!!)

        val globalScore = c1 + c2 + c3 + c4 + c5 + c6 + c7

        val componentScores = PsqiComponentScores(
            c1SubjectiveQuality = c1,
            c2SleepLatency = c2,
            c3SleepDuration = c3,
            c4SleepEfficiency = c4,
            c5SleepDisturbance = c5,
            c6UseOfMedication = c6,
            c7DaytimeDysfunction = c7,
            globalScore = globalScore
        )

        return PsqiScoringResult.Complete(
            componentScores = componentScores,
            globalScore = globalScore,
            sleepEfficiencyPercentage = Math.round(efficiencyPct * 10.0) / 10.0,
            hoursInBed = Math.round(hoursInBed * 10.0) / 10.0
        )
    }

    /**
     * Component 1: Subjective sleep quality
     * Direct score from Item 9 (0 to 3).
     */
    fun scoreComponent1(q9: Int): Int {
        require(q9 in 0..3) { "Item 9 must be in range 0..3" }
        return q9
    }

    /**
     * Component 2: Sleep latency
     * Combines usual latency minutes (Item 2) and frequency of difficulty <= 30 min (Item 5a).
     */
    fun scoreComponent2(latencyMinutes: Int, q5a: Int): Int {
        require(latencyMinutes >= 0) { "Latency minutes cannot be negative" }
        require(q5a in 0..3) { "Item 5a must be in range 0..3" }

        val q2Score = when {
            latencyMinutes <= 15 -> 0
            latencyMinutes in 16..30 -> 1
            latencyMinutes in 31..60 -> 2
            else -> 3
        }

        val sum = q2Score + q5a
        return when (sum) {
            0 -> 0
            1, 2 -> 1
            3, 4 -> 2
            else -> 3
        }
    }

    /**
     * Component 3: Sleep duration
     * Hours of actual sleep (Item 4).
     * > 7 hours = 0
     * 6-7 hours = 1 (i.e. 6.0 <= hours <= 7.0)
     * 5-6 hours = 2 (i.e. 5.0 <= hours < 6.0)
     * < 5 hours = 3
     */
    fun scoreComponent3(actualSleepHours: Double): Int {
        return when {
            actualSleepHours > 7.0 -> 0
            actualSleepHours >= 6.0 -> 1
            actualSleepHours >= 5.0 -> 2
            else -> 3
        }
    }

    /**
     * Component 4: Habitual sleep efficiency
     * (Actual sleep hours / Hours in bed) * 100%.
     * >= 85% = 0
     * 75–84.99% = 1
     * 65–74.99% = 2
     * < 65% = 3
     */
    fun scoreComponent4(efficiencyPercentage: Double): Int {
        return when {
            efficiencyPercentage >= 85.0 -> 0
            efficiencyPercentage >= 75.0 -> 1
            efficiencyPercentage >= 65.0 -> 2
            else -> 3
        }
    }

    /**
     * Component 5: Sleep disturbances
     * Sum of items 5b through 5j (sum 0 to 27).
     * 0 -> 0
     * 1–9 -> 1
     * 10–18 -> 2
     * 19–27 -> 3
     */
    fun scoreComponent5(items5bThrough5j: List<Int>): Int {
        require(items5bThrough5j.size == 9) { "Component 5 requires exactly 9 disturbance items (5b-5j)" }
        items5bThrough5j.forEach { require(it in 0..3) { "Disturbance items must be 0..3" } }

        val sum = items5bThrough5j.sum()
        return when (sum) {
            0 -> 0
            in 1..9 -> 1
            in 10..18 -> 2
            else -> 3
        }
    }

    /**
     * Component 6: Use of sleep medication
     * Direct score from Item 6 (0 to 3).
     */
    fun scoreComponent6(q6: Int): Int {
        require(q6 in 0..3) { "Item 6 must be in range 0..3" }
        return q6
    }

    /**
     * Component 7: Daytime dysfunction
     * Sum of Item 7 and Item 8 (sum 0 to 6).
     * 0 -> 0
     * 1–2 -> 1
     * 3–4 -> 2
     * 5–6 -> 3
     */
    fun scoreComponent7(q7: Int, q8: Int): Int {
        require(q7 in 0..3) { "Item 7 must be in range 0..3" }
        require(q8 in 0..3) { "Item 8 must be in range 0..3" }

        val sum = q7 + q8
        return when (sum) {
            0 -> 0
            1, 2 -> 1
            3, 4 -> 2
            else -> 3
        }
    }

    /**
     * Calculates time in bed in hours from wall-clock strings.
     * Accurately handles crossing midnight.
     */
    fun calculateHoursInBed(bedTimeStr: String, wakeTimeStr: String): Double {
        val bed = parseTime(bedTimeStr)
        val wake = parseTime(wakeTimeStr)

        val bedMinuteOfDay = bed.hour * 60 + bed.minute
        val wakeMinuteOfDay = wake.hour * 60 + wake.minute

        val diffMinutes = if (wakeMinuteOfDay > bedMinuteOfDay) {
            wakeMinuteOfDay - bedMinuteOfDay
        } else if (wakeMinuteOfDay < bedMinuteOfDay) {
            (wakeMinuteOfDay + 1440) - bedMinuteOfDay
        } else {
            1440 // 24 hours if identical
        }

        return diffMinutes / 60.0
    }

    /**
     * Calculates sleep efficiency percentage: (sleep hours / bed hours) * 100.
     */
    fun calculateSleepEfficiency(actualSleepHours: Double, hoursInBed: Double): Double {
        if (hoursInBed <= 0.0) return 0.0
        val percentage = (actualSleepHours / hoursInBed) * 100.0
        return percentage.coerceIn(0.0, 100.0)
    }

    private fun parseTime(str: String): LocalTime {
        val trimmed = str.trim()
        for (formatter in TIME_FORMATTERS) {
            try {
                return LocalTime.parse(trimmed, formatter)
            } catch (_: Exception) {
            }
        }
        // Fallback split
        val parts = trimmed.split(":")
        if (parts.size >= 2) {
            val h = parts[0].trim().toInt()
            val m = parts[1].trim().take(2).toInt()
            return LocalTime.of(h, m)
        }
        throw IllegalArgumentException("Unable to parse time: $str")
    }
}
