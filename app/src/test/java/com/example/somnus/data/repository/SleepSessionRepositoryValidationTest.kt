package com.example.somnus.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.somnus.data.local.PsqiAssessmentEntity
import com.example.somnus.data.local.SleepSessionEntity
import com.example.somnus.data.local.SolRecordEntity
import com.example.somnus.data.local.SomnusDatabase
import com.example.somnus.domain.model.SessionSource
import com.example.somnus.domain.model.SessionStatus
import com.example.somnus.domain.model.SleepSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SleepSessionRepositoryValidationTest {

    private lateinit var database: SomnusDatabase
    private lateinit var repository: SleepSessionRepositoryImpl
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        // Build database with the DB_CALLBACK that runs PRAGMA foreign_keys = ON;
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SomnusDatabase::class.java
        )
            .addCallback(object : androidx.room.RoomDatabase.Callback() {
                override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    super.onOpen(db)
                    db.execSQL("PRAGMA foreign_keys = ON;")
                }
            })
            .allowMainThreadQueries()
            .build()

        repository = SleepSessionRepositoryImpl(database.sleepSessionDao(), testDispatcher)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun foreignKeyConstraint_isEnforced_onInvalidParentReference() = runTest(testDispatcher) {
        val nonExistentSessionId = "non_existent_session_id"

        val solRecord = SolRecordEntity(
            id = UUID.randomUUID().toString(),
            sessionId = nonExistentSessionId,
            reportingDate = "2026-03-01",
            zoneId = "UTC",
            latencyMinutes = 15,
            method = "SELF_REPORTED",
            sourceIdentifier = "somnus_manual",
            sourceDeviceMetadata = null,
            qualityFlags = "",
            createdAt = Instant.now().toEpochMilli(),
            updatedAt = Instant.now().toEpochMilli(),
            attemptToSleepTime = null,
            sleepOnsetTime = null,
            notes = null
        )

        var caughtFkException = false
        try {
            database.solRecordDao().insert(solRecord)
        } catch (e: Exception) {
            caughtFkException = true
        }

        assertTrue("Inserting child with invalid foreign key must fail when PRAGMA foreign_keys = ON", caughtFkException)
    }

    @Test
    fun clearAllDataTables_atomicallyClearsDatabaseWithoutFkViolation() = runTest(testDispatcher) {
        val sessionId = UUID.randomUUID().toString()
        val session = SleepSession(
            id = sessionId,
            startTime = Instant.now().minus(8, ChronoUnit.HOURS),
            endTime = Instant.now(),
            zoneId = ZoneId.of("UTC"),
            status = SessionStatus.COMPLETED
        )
        database.sleepSessionDao().insert(SleepSessionEntity.fromDomain(session))

        val solRecord = SolRecordEntity(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            reportingDate = "2026-03-01",
            zoneId = "UTC",
            latencyMinutes = 15,
            method = "SELF_REPORTED",
            sourceIdentifier = "somnus_manual",
            sourceDeviceMetadata = null,
            qualityFlags = "",
            createdAt = Instant.now().toEpochMilli(),
            updatedAt = Instant.now().toEpochMilli(),
            attemptToSleepTime = null,
            sleepOnsetTime = null,
            notes = null
        )
        database.solRecordDao().insert(solRecord)

        val psqiAssessment = PsqiAssessmentEntity(
            id = UUID.randomUUID().toString(),
            startedAt = Instant.now().toEpochMilli(),
            completedAt = Instant.now().toEpochMilli(),
            version = "1.0",
            language = "en",
            reportingPeriod = "LAST_MONTH",
            status = "COMPLETED",
            scoringVersion = "standard_1989",
            globalScore = 4,
            component1 = 1,
            component2 = 1,
            component3 = 0,
            component4 = 0,
            component5 = 1,
            component6 = 0,
            component7 = 1,
            responsesJson = "{}",
            notes = null
        )
        database.psqiDao().insert(psqiAssessment)

        // Run atomic wipe
        database.clearAllDataTables()

        assertEquals(0, database.sleepSessionDao().getSessionCount())
        assertEquals(0, database.solRecordDao().count())
        assertEquals(0, database.psqiDao().count())
    }

    @Test
    fun findOverlappingSessions_detectsAllOverlapTopologies() = runTest(testDispatcher) {
        val baseStart = Instant.parse("2026-03-01T22:00:00Z")
        val baseEnd = Instant.parse("2026-03-02T06:00:00Z")

        val baseSession = SleepSession(
            id = "base_session",
            startTime = baseStart,
            endTime = baseEnd,
            zoneId = ZoneId.of("UTC"),
            status = SessionStatus.COMPLETED
        )
        repository.insertCompletedSession(baseSession)

        // 1. Partial overlap at start: 21:00 to 23:00
        val overlapStart = repository.findOverlappingSessions(
            Instant.parse("2026-03-01T21:00:00Z"),
            Instant.parse("2026-03-01T23:00:00Z")
        )
        assertEquals(1, overlapStart.size)

        // 2. Partial overlap at end: 05:00 to 07:00
        val overlapEnd = repository.findOverlappingSessions(
            Instant.parse("2026-03-02T05:00:00Z"),
            Instant.parse("2026-03-02T07:00:00Z")
        )
        assertEquals(1, overlapEnd.size)

        // 3. Enclosed session: 23:00 to 05:00
        val enclosed = repository.findOverlappingSessions(
            Instant.parse("2026-03-01T23:00:00Z"),
            Instant.parse("2026-03-02T05:00:00Z")
        )
        assertEquals(1, enclosed.size)

        // 4. Spanning session: 21:00 to 07:00
        val spanning = repository.findOverlappingSessions(
            Instant.parse("2026-03-01T21:00:00Z"),
            Instant.parse("2026-03-02T07:00:00Z")
        )
        assertEquals(1, spanning.size)

        // 5. Strictly before: 20:00 to 22:00 (abutting at boundary)
        val before = repository.findOverlappingSessions(
            Instant.parse("2026-03-01T20:00:00Z"),
            Instant.parse("2026-03-01T22:00:00Z")
        )
        assertEquals(0, before.size)

        // 6. Strictly after: 06:00 to 08:00 (abutting at boundary)
        val after = repository.findOverlappingSessions(
            Instant.parse("2026-03-02T06:00:00Z"),
            Instant.parse("2026-03-02T08:00:00Z")
        )
        assertEquals(0, after.size)

        // 7. Exclude own ID
        val selfExcluded = repository.findOverlappingSessions(
            baseStart,
            baseEnd,
            excludeId = "base_session"
        )
        assertEquals(0, selfExcluded.size)
    }

    @Test
    fun updateSession_rejectsOverlappingCollision_butAllowsSelfUpdate() = runTest(testDispatcher) {
        val s1 = SleepSession(
            id = "s1",
            startTime = Instant.parse("2026-03-01T22:00:00Z"),
            endTime = Instant.parse("2026-03-02T06:00:00Z"),
            status = SessionStatus.COMPLETED
        )
        val s2 = SleepSession(
            id = "s2",
            startTime = Instant.parse("2026-03-02T22:00:00Z"),
            endTime = Instant.parse("2026-03-03T06:00:00Z"),
            status = SessionStatus.COMPLETED
        )
        repository.insertCompletedSession(s1)
        repository.insertCompletedSession(s2)

        // Update s2 with new notes without moving timestamps -> Success
        val selfUpdate = repository.updateSession(s2.copy(notes = "Updated notes"))
        assertTrue(selfUpdate.isSuccess)

        // Attempt to move s2's time window so that it collides with s1
        val collidingUpdate = s2.copy(
            startTime = Instant.parse("2026-03-02T04:00:00Z"),
            endTime = Instant.parse("2026-03-02T10:00:00Z")
        )
        val result = repository.updateSession(collidingUpdate)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("overlaps") == true)
    }

    @Test
    fun recoverStaleActiveSession_successfullyRecoversSessionWithCorrectedEndTime() = runTest(testDispatcher) {
        val start = Instant.now().minus(48, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS) // 48h ago (> 36h)
        val sessionId = "stale_1"

        repository.startSession(
            id = sessionId,
            startTime = start,
            zoneId = ZoneId.of("UTC")
        )

        // Confirm it's active
        val active = repository.getActiveSessionOneShot()
        assertEquals(sessionId, active?.id)

        // Normal endSession at Instant.now() would fail because duration is 48 hours (> 36h)
        val normalEnd = repository.endSession(sessionId, Instant.now())
        assertTrue(normalEnd.isFailure)

        // Recover with realistic wake time: 8 hours after start
        val correctedWakeTime = start.plus(8, ChronoUnit.HOURS)
        val recoverResult = repository.recoverStaleActiveSession(
            id = sessionId,
            correctedEndTime = correctedWakeTime,
            notes = "Woke up at 7am, phone left on bed"
        )

        assertTrue(recoverResult.isSuccess)
        val recovered = recoverResult.getOrThrow()
        assertEquals(SessionStatus.COMPLETED, recovered.status)
        assertEquals(correctedWakeTime, recovered.endTime)
        assertTrue(recovered.isCorrected)
        assertTrue(recovered.notes?.contains("[Recovered stale session]") == true)

        // Active session should now be null
        assertEquals(null, repository.getActiveSessionOneShot())
    }
}
