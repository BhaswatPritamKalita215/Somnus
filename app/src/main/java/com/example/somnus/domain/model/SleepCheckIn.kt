package com.example.somnus.domain.model

import java.time.Instant
import java.util.UUID

/**
 * Domain entity representing an original, self-reported morning check-in
 * completed after ending a sleep tracking session.
 *
 * Guarantees:
 * 1. Linked directly to the corresponding [SleepSession.id].
 * 2. Unambiguously self-reported (never conflated with sensor-measured sleep stages).
 * 3. Skipped check-ins leave no fabricated values.
 */
data class SleepCheckIn(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val moodScore: Int? = null, // 1 (Exhausted) .. 5 (Great)
    val moodLabel: String? = null, // e.g. "Exhausted", "Tired", "Okay", "Good", "Great"
    val restedScore: Int? = null, // 1 (Not at all rested) .. 5 (Fully refreshed)
    val notes: String? = null,
    val createdAt: Instant = Instant.now(),
    val isSelfReported: Boolean = true
)
