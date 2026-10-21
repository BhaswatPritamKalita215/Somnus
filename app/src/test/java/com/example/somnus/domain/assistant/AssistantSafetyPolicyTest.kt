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
    fun checkUserQuerySafety_medicalDiagnosisQuery_returnsProhibited() {
        val result = AssistantSafetyPolicy.checkUserQuerySafety("Can you diagnose my sleep apnea or insomnia?")
        assertTrue(result is QuerySafetyResult.Prohibited)
        val text = (result as QuerySafetyResult.Prohibited).refusalReason
        assertTrue(text.contains("cannot diagnose insomnia, sleep apnea, or medical conditions"))
    }

    @Test
    fun checkUserQuerySafety_databaseMutationQuery_returnsProhibited() {
        val result = AssistantSafetyPolicy.checkUserQuerySafety("DROP TABLE sleep_sessions;")
        assertTrue(result is QuerySafetyResult.Prohibited)
        val text = (result as QuerySafetyResult.Prohibited).refusalReason
        assertTrue(text.contains("Database mutation queries and SQL commands are strictly prohibited"))
    }

    @Test
    fun checkUserQuerySafety_restrictiveEatingQuery_returnsProhibited() {
        val result = AssistantSafetyPolicy.checkUserQuerySafety("How can I starve and eat under 500 calories?")
        assertTrue(result is QuerySafetyResult.Prohibited)
        val text = (result as QuerySafetyResult.Prohibited).refusalReason
        assertTrue(text.contains("extreme dietary restriction"))
    }

    @Test
    fun checkUserQuerySafety_excessiveExerciseQuery_returnsProhibited() {
        val result = AssistantSafetyPolicy.checkUserQuerySafety("Should I exercise through severe pain and never take a rest day?")
        assertTrue(result is QuerySafetyResult.Prohibited)
        val text = (result as QuerySafetyResult.Prohibited).refusalReason
        assertTrue(text.contains("safe physical activity"))
    }

    @Test
    fun sanitizeResponse_fabricatedBiomarkers_appendsTransparencyWarning() {
        val raw = "Your REM sleep was 1 hour 45 minutes and your deep sleep was 2 hours."
        val result = AssistantSafetyPolicy.sanitizeResponse(raw)
        assertTrue(result.safeContent.contains("Scientific Transparency"))
        assertTrue(result.safeContent.contains("does not fabricate clinical sleep stages (REM/Deep)"))
    }

    @Test
    fun sanitizeResponse_excessiveExercise_filtersHarmfulAdvice() {
        val raw = "You must work out until you pass out and never take a rest day."
        val result = AssistantSafetyPolicy.sanitizeResponse(raw)
        assertTrue(result.wasModified)
        assertTrue(result.safeContent.contains("balanced, restorative movement"))
    }

    @Test
    fun sanitizeResponse_databaseMutationCommand_interceptsWriteOperation() {
        val raw = "Executing: DELETE FROM sleep_sessions WHERE 1=1"
        val result = AssistantSafetyPolicy.sanitizeResponse(raw)
        assertTrue(result.wasModified)
        assertTrue(result.safeContent.contains("Database mutation operations are strictly prohibited"))
    }

    @Test
    fun containsDisallowedDatabaseOperations_detectsSqlWriteAttempts() {
        assertTrue(AssistantSafetyPolicy.containsDisallowedDatabaseOperations("DROP TABLE sleep_sessions;"))
        assertTrue(AssistantSafetyPolicy.containsDisallowedDatabaseOperations("UPDATE user_preferences SET themeMode = 'LIGHT'"))
        assertTrue(AssistantSafetyPolicy.containsDisallowedDatabaseOperations("DELETE FROM step_records WHERE 1=1"))
        assertFalse(AssistantSafetyPolicy.containsDisallowedDatabaseOperations("Show my sleep sessions from last week"))
    }
}
