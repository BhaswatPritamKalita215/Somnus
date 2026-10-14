package com.example.somnus.domain.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantSafetyPolicyTest {

    @Test
    fun checkUserQuerySafety_medicationQuery_returnsProhibited() {
        val result = AssistantSafetyPolicy.checkUserQuerySafety("How much medication or sleeping pill dose should I take?")
        assertTrue(result is QuerySafetyResult.Prohibited)
        val text = (result as QuerySafetyResult.Prohibited).refusalReason
        assertTrue(text.contains("cannot provide medication dosage recommendations"))
    }

    @Test
    fun checkUserQuerySafety_normalHealthQuery_returnsSafe() {
        val result = AssistantSafetyPolicy.checkUserQuerySafety("How many steps did I take on Tuesday?")
        assertTrue(result is QuerySafetyResult.Safe)
    }

    @Test
    fun sanitizeResponse_extremeDiet_filtersHarmfulAdvice() {
        val raw = "You should eat under 500 calories to lose weight rapidly."
        val result = AssistantSafetyPolicy.sanitizeResponse(raw)
        assertTrue(result.wasModified)
        assertTrue(result.safeContent.contains("sustainable, nourishing nutrition"))
    }

    @Test
    fun sanitizeResponse_prescriptionAdvice_filtersMedication() {
        val raw = "You should take ambien before bed tonight."
        val result = AssistantSafetyPolicy.sanitizeResponse(raw)
        assertTrue(result.wasModified)
        assertTrue(result.safeContent.contains("cannot recommend medications"))
    }

    @Test
    fun sanitizeResponse_softensCausationClaims() {
        val raw = "Your late heavy meal caused your sleep to suffer."
        val result = AssistantSafetyPolicy.sanitizeResponse(raw)
        assertFalse(result.safeContent.contains("caused your sleep to"))
        assertTrue(result.safeContent.contains("is associated with"))
        assertTrue(result.safeContent.contains("does not establish biological causation"))
    }

    @Test
    fun sanitizeResponse_capsExcessiveWordLength() {
        val longText = (1..250).joinToString(" ") { "word$it" }
        val result = AssistantSafetyPolicy.sanitizeResponse(longText)
        val wordCount = result.safeContent.split(Regex("\\s+")).size
        assertTrue("Expected word count <= 190, got $wordCount", wordCount <= 190)
    }

    @Test
    fun containsDisallowedDatabaseOperations_detectsSqlWriteAttempts() {
        assertTrue(AssistantSafetyPolicy.containsDisallowedDatabaseOperations("DROP TABLE sleep_sessions;"))
        assertTrue(AssistantSafetyPolicy.containsDisallowedDatabaseOperations("UPDATE user_preferences SET themeMode = 'LIGHT'"))
        assertTrue(AssistantSafetyPolicy.containsDisallowedDatabaseOperations("DELETE FROM step_records WHERE 1=1"))
        assertFalse(AssistantSafetyPolicy.containsDisallowedDatabaseOperations("Show my sleep sessions from last week"))
    }
}
