package com.example.somnus.data.local

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.somnus.domain.model.PsqiAssessment
import com.example.somnus.domain.model.PsqiComponentScores
import com.example.somnus.domain.model.PsqiResponses
import com.example.somnus.domain.model.PsqiStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PsqiDaoTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var db: SomnusDatabase
    private lateinit var psqiDao: PsqiDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = SomnusDatabase.buildInMemoryDatabase(context)
        psqiDao = db.psqiDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAndRetrieve_completedAssessment_roundtripsCorrectly() = runBlocking {
        val responses = PsqiResponses(
            bedTime = "23:00",
            latencyMinutes = 25,
            wakeTime = "07:00",
            actualSleepHours = 7.0,
            q5a = 1,
            q5b = 1,
            q6 = 0,
            q7 = 1,
            q8 = 0,
            q9 = 1
        )

        val scores = PsqiComponentScores(
            c1SubjectiveQuality = 1,
            c2SleepLatency = 1,
            c3SleepDuration = 1,
            c4SleepEfficiency = 0,
            c5SleepDisturbance = 1,
            c6UseOfMedication = 0,
            c7DaytimeDysfunction = 1,
            globalScore = 5
        )

        val assessment = PsqiAssessment(
            id = "psqi-test-1",
            startedAt = Instant.parse("2026-10-14T10:00:00Z"),
            completedAt = Instant.parse("2026-10-14T10:15:00Z"),
            version = "Authorized-PSQI-v1",
            language = "en-US",
            reportingPeriod = "October 2026",
            status = PsqiStatus.COMPLETED,
            scoringVersion = "PSQI_BUYSSE_1989_V1",
            responses = responses,
            componentScores = scores,
            notes = "Routine monthly test"
        )

        psqiDao.insert(PsqiAssessmentEntity.fromDomain(assessment))

        val retrievedEntity = psqiDao.getAssessmentById("psqi-test-1").first()
        assertNotNull(retrievedEntity)

        val domain = retrievedEntity!!.toDomain()
        assertEquals("psqi-test-1", domain.id)
        assertEquals(PsqiStatus.COMPLETED, domain.status)
        assertEquals(5, domain.componentScores?.globalScore)
        assertEquals(1, domain.componentScores?.c1SubjectiveQuality)
        assertEquals(1, domain.componentScores?.c2SleepLatency)
        assertEquals("23:00", domain.responses.bedTime)
        assertEquals(25, domain.responses.latencyMinutes)
        assertEquals("October 2026", domain.reportingPeriod)
        assertEquals("Routine monthly test", domain.notes)
    }

    @Test
    fun getLatestCompletedAssessment_ignoresIncomplete() = runBlocking {
        val incomplete = PsqiAssessment(
            id = "inc-1",
            startedAt = Instant.parse("2026-10-14T12:00:00Z"),
            status = PsqiStatus.INCOMPLETE,
            componentScores = null
        )
        psqiDao.insert(PsqiAssessmentEntity.fromDomain(incomplete))

        val latestBefore = psqiDao.getLatestCompletedAssessment().first()
        assertNull(latestBefore)

        val completed = PsqiAssessment(
            id = "comp-1",
            startedAt = Instant.parse("2026-10-14T08:00:00Z"),
            completedAt = Instant.parse("2026-10-14T08:10:00Z"),
            status = PsqiStatus.COMPLETED,
            componentScores = PsqiComponentScores(0, 0, 0, 0, 0, 0, 0, 0)
        )
        psqiDao.insert(PsqiAssessmentEntity.fromDomain(completed))

        val latestAfter = psqiDao.getLatestCompletedAssessment().first()
        assertNotNull(latestAfter)
        assertEquals("comp-1", latestAfter!!.id)
    }

    @Test
    fun deleteByIdAndCount_cleansDatabase() = runBlocking {
        val assessment = PsqiAssessment(id = "psqi-del", status = PsqiStatus.INCOMPLETE)
        psqiDao.insert(PsqiAssessmentEntity.fromDomain(assessment))
        assertEquals(1, psqiDao.count())

        psqiDao.deleteById("psqi-del")
        assertEquals(0, psqiDao.count())
    }
}
