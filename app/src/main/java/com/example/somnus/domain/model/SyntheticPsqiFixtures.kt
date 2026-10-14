package com.example.somnus.domain.model

/**
 * Standard synthetic test fixtures for the Pittsburgh Sleep Quality Index (PSQI).
 *
 * Used for automated verification and test runner execution prior to authorized
 * instrument configuration, adhering to copyright compliance guidelines.
 */
object SyntheticPsqiFixtures {

    /**
     * Perfect score fixture (all optimal parameters).
     * Expected: All components = 0, Global Score = 0.
     */
    val PERFECT_SLEEP = PsqiResponses(
        bedTime = "22:30",
        latencyMinutes = 10,
        wakeTime = "06:30",
        actualSleepHours = 8.0,
        q5a = 0,
        q5b = 0,
        q5c = 0,
        q5d = 0,
        q5e = 0,
        q5f = 0,
        q5g = 0,
        q5h = 0,
        q5i = 0,
        q5j = 0,
        q6 = 0,
        q7 = 0,
        q8 = 0,
        q9 = 0
    )

    /**
     * Maximal severity fixture (worst possible outcomes on all parameters).
     * Expected: All components = 3, Global Score = 21.
     */
    val MAXIMAL_SEVERITY = PsqiResponses(
        bedTime = "22:00",
        latencyMinutes = 80,
        wakeTime = "06:00",
        actualSleepHours = 3.5, // Efficiency = (3.5 / 8.0) * 100 = 43.75% (< 65% -> C4=3)
        q5a = 3, // C2: Q2(3) + Q5a(3) = 6 -> C2=3
        q5b = 3,
        q5c = 3,
        q5d = 3,
        q5e = 3,
        q5f = 3,
        q5g = 3,
        q5h = 3,
        q5i = 3,
        q5j = 3, // C5: sum = 27 -> C5=3
        q6 = 3,  // C6 = 3
        q7 = 3,
        q8 = 3,  // C7: sum = 6 -> C7=3
        q9 = 3   // C1 = 3
    )

    /**
     * Moderate sleep disturbance fixture representing a typical clinical screening case.
     * Expected: Global Score = 8 (Disturbed sleep > 5).
     */
    val MODERATE_DISTURBANCE = PsqiResponses(
        bedTime = "23:30",
        latencyMinutes = 25, // Q2 score = 1
        wakeTime = "07:00",  // Bed duration = 7.5 hours
        actualSleepHours = 6.0, // Efficiency = (6.0 / 7.5) * 100 = 80.0% -> C4=1, Duration 6.0h -> C3=1
        q5a = 1, // C2: 1 + 1 = 2 -> C2=1
        q5b = 2,
        q5c = 1,
        q5d = 0,
        q5e = 0,
        q5f = 1,
        q5g = 0,
        q5h = 1,
        q5i = 0,
        q5j = 0, // C5: sum = 5 -> C5=1
        q6 = 1,  // C6 = 1 (took meds less than once a week)
        q7 = 1,
        q8 = 1,  // C7: 1 + 1 = 2 -> C7=1
        q9 = 2   // C1 = 2 (Fairly bad)
        // Expected Global: 2 + 1 + 1 + 1 + 1 + 1 + 1 = 8
    )

    /**
     * Incomplete fixture with null responses.
     * Expected: Scoring fails with PsqiScoringResult.Incomplete without silently converting to zero.
     */
    val INCOMPLETE_FIXTURE = PsqiResponses(
        bedTime = "23:00",
        latencyMinutes = 20,
        wakeTime = "07:00",
        actualSleepHours = 6.5,
        q5a = 1,
        q5b = null, // Missing!
        q5c = 1,
        q6 = null,  // Missing!
        q9 = null   // Missing!
    )
}
