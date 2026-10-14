package com.example.somnus.data.local

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.somnus.domain.model.SessionSource
import com.example.somnus.domain.model.SessionStatus
import com.example.somnus.domain.model.SolMeasurementMethod
import com.example.somnus.domain.model.SolQualityFlag
import com.example.somnus.domain.model.SolRecord
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SolDaoTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var db: SomnusDatabase
    private lateinit var solDao: SolRecordDao
    private lateinit var sessionDao: SleepSessionDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = SomnusDatabase.buildInMemoryDatabase(context)
        solDao = db.solRecordDao()
        sessionDao = db.sleepSessionDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAndRetrieve_preservesAllSolAttributes() = runBlocking {
        val record = SolRecord(
            id = "sol-101",
            sessionId = null,
            reportingDate = LocalDate.of(2026, 10, 14),
            zoneId = ZoneId.of("America/New_York"),
            latencyMinutes = 22,
            method = SolMeasurementMethod.SELF_REPORTED,
            sourceIdentifier = "manual_entry",
            sourceDeviceMetadata = "Pixel 8",
            qualityFlags = setOf(SolQualityFlag.UNCERTAIN_TIMING, SolQualityFlag.EDITED_MANUALLY),
            notes = "User fell asleep reading"
        )

        val entity = SolRecordEntity.fromDomain(record)
        solDao.insert(entity)

        val retrieved = solDao.getRecordById("sol-101").first()
        assertNotNull(retrieved)

        val domain = retrieved!!.toDomain()
        assertEquals("sol-101", domain.id)
        assertEquals(LocalDate.of(2026, 10, 14), domain.reportingDate)
        assertEquals(ZoneId.of("America/New_York"), domain.zoneId)
        assertEquals(22, domain.latencyMinutes)
        assertEquals(SolMeasurementMethod.SELF_REPORTED, domain.method)
        assertEquals("manual_entry", domain.sourceIdentifier)
        assertEquals("Pixel 8", domain.sourceDeviceMetadata)
        assertTrue(domain.qualityFlags.contains(SolQualityFlag.UNCERTAIN_TIMING))
        assertTrue(domain.qualityFlags.contains(SolQualityFlag.EDITED_MANUALLY))
        assertEquals("User fell asleep reading", domain.notes)
    }

    @Test
    fun getRecordsForDateRange_filtersCorrectly() = runBlocking {
        solDao.insert(
            SolRecordEntity.fromDomain(
                SolRecord(id = "r1", reportingDate = LocalDate.of(2026, 10, 1), latencyMinutes = 15)
            )
        )
        solDao.insert(
            SolRecordEntity.fromDomain(
                SolRecord(id = "r2", reportingDate = LocalDate.of(2026, 10, 5), latencyMinutes = 25)
            )
        )
        solDao.insert(
            SolRecordEntity.fromDomain(
                SolRecord(id = "r3", reportingDate = LocalDate.of(2026, 10, 10), latencyMinutes = 35)
            )
        )

        val rangeRecords = solDao.getRecordsForDateRange("2026-10-03", "2026-10-08").first()
        assertEquals(1, rangeRecords.size)
        assertEquals("r2", rangeRecords[0].id)
    }

    @Test
    fun foreignKey_setNullWhenSessionDeleted() = runBlocking {
        val sessionEntity = SleepSessionEntity(
            id = "sess-1",
            startTime = Instant.now().toEpochMilli(),
            endTime = null,
            timezoneId = "UTC",
            status = SessionStatus.ACTIVE.name,
            source = SessionSource.MANUAL.name,
            createdAt = Instant.now().toEpochMilli(),
            updatedAt = Instant.now().toEpochMilli(),
            notes = null,
            isCorrected = false
        )
        sessionDao.insert(sessionEntity)

        val solEntity = SolRecordEntity.fromDomain(
            SolRecord(id = "sol-sess", sessionId = "sess-1", reportingDate = LocalDate.now(), latencyMinutes = 18)
        )
        solDao.insert(solEntity)

        // Delete parent session
        sessionDao.deleteById("sess-1")

        // Child record should remain with sessionId set to null (ON DELETE SET NULL)
        val afterDelete = solDao.getRecordById("sol-sess").first()
        assertNotNull(afterDelete)
        assertNull(afterDelete!!.sessionId)
    }

    @Test
    fun deleteByIdAndCount_worksProperly() = runBlocking {
        solDao.insert(
            SolRecordEntity.fromDomain(
                SolRecord(id = "del-1", reportingDate = LocalDate.now(), latencyMinutes = 10)
            )
        )
        assertEquals(1, solDao.count())

        solDao.deleteById("del-1")
        assertEquals(0, solDao.count())
    }
}
