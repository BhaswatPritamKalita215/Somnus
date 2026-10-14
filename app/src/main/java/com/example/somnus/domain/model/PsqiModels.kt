package com.example.somnus.domain.model

import java.time.Instant
import java.util.UUID

enum class PsqiStatus {
    INCOMPLETE,
    COMPLETED
}

/**
 * Encapsulates the responses to the 19 individual items of the PSQI.
 *
 * Missing responses are explicitly represented as null and are NEVER
 * silently coerced to zero.
 */
data class PsqiResponses(
    // Item 1: Usual bed time (e.g., "23:00")
    val bedTime: String? = null,
    // Item 2: Usual minutes to fall asleep
    val latencyMinutes: Int? = null,
    // Item 3: Usual wake up time (e.g., "07:00")
    val wakeTime: String? = null,
    // Item 4: Actual hours of sleep
    val actualSleepHours: Double? = null,
    // Item 5a: Trouble sleeping because cannot get to sleep within 30 min (0-3)
    val q5a: Int? = null,
    // Item 5b: Wake up in middle of night or early morning (0-3)
    val q5b: Int? = null,
    // Item 5c: Have to get up to use bathroom (0-3)
    val q5c: Int? = null,
    // Item 5d: Cannot breathe comfortably (0-3)
    val q5d: Int? = null,
    // Item 5e: Cough or snore loudly (0-3)
    val q5e: Int? = null,
    // Item 5f: Feel too cold (0-3)
    val q5f: Int? = null,
    // Item 5g: Feel too hot (0-3)
    val q5g: Int? = null,
    // Item 5h: Had bad dreams (0-3)
    val q5h: Int? = null,
    // Item 5i: Have pain (0-3)
    val q5i: Int? = null,
    // Item 5j: Other reason(s) (0-3)
    val q5j: Int? = null,
    // Item 6: Taken medicine to help sleep (0-3)
    val q6: Int? = null,
    // Item 7: Trouble staying awake driving/meals/social (0-3)
    val q7: Int? = null,
    // Item 8: Problem keeping up enthusiasm (0-3)
    val q8: Int? = null,
    // Item 9: Overall sleep quality rating (0-3)
    val q9: Int? = null
)

/**
 * Seven clinical component scores (0–3 each) and global score (0–21).
 */
data class PsqiComponentScores(
    val c1SubjectiveQuality: Int,
    val c2SleepLatency: Int,
    val c3SleepDuration: Int,
    val c4SleepEfficiency: Int,
    val c5SleepDisturbance: Int,
    val c6UseOfMedication: Int,
    val c7DaytimeDysfunction: Int,
    val globalScore: Int
) {
    init {
        require(c1SubjectiveQuality in 0..3) { "C1 must be 0..3" }
        require(c2SleepLatency in 0..3) { "C2 must be 0..3" }
        require(c3SleepDuration in 0..3) { "C3 must be 0..3" }
        require(c4SleepEfficiency in 0..3) { "C4 must be 0..3" }
        require(c5SleepDisturbance in 0..3) { "C5 must be 0..3" }
        require(c6UseOfMedication in 0..3) { "C6 must be 0..3" }
        require(c7DaytimeDysfunction in 0..3) { "C7 must be 0..3" }
        val sum = c1SubjectiveQuality + c2SleepLatency + c3SleepDuration +
                c4SleepEfficiency + c5SleepDisturbance + c6UseOfMedication + c7DaytimeDysfunction
        require(globalScore == sum) { "Global score ($globalScore) must equal sum of components ($sum)" }
        require(globalScore in 0..21) { "Global score must be in 0..21" }
    }

    /**
     * Diagnostic interpretation note based on validated clinical literature.
     * Note: PSQI is a screening instrument, not an automatic medical diagnosis.
     */
    val isDisturbedSleep: Boolean
        get() = globalScore > 5
}

/**
 * Domain entity representing a full monthly PSQI Assessment record.
 */
data class PsqiAssessment(
    val id: String = UUID.randomUUID().toString(),
    val startedAt: Instant = Instant.now(),
    val completedAt: Instant? = null,
    val version: String = "Authorized-PSQI-v1",
    val language: String = "en-US",
    val reportingPeriod: String = "Past 1 Month",
    val status: PsqiStatus = PsqiStatus.INCOMPLETE,
    val scoringVersion: String = "PSQI_BUYSSE_1989_V1",
    val responses: PsqiResponses = PsqiResponses(),
    val componentScores: PsqiComponentScores? = null,
    val notes: String? = null
)
