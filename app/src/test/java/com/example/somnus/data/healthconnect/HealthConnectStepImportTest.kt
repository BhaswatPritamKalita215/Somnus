package com.example.somnus.data.healthconnect

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.somnus.data.local.SomnusDatabase
import com.example.somnus.data.repository.SleepSessionRepositoryImpl
import com.example.somnus.data.repository.StepRepositoryImpl
import com.example.somnus.domain.model.StepSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HealthConnectStepImportTest {

    private lateinit var database: SomnusDatabase
    private lateinit var sleepRepository: SleepSessionRepositoryImpl
    private lateinit var stepRepository: StepRepositoryImpl
    private lateinit var fakeClient: FakeHealthConnectClientWrapper
    private lateinit var manager: HealthConnectManager
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SomnusDatabase::class.java
        ).allowMainThreadQueries().build()

        sleepRepository = SleepSessionRepositoryImpl(database.sleepSessionDao(), testDispatcher)
        stepRepository = StepRepositoryImpl(database.stepRecordDao(), database.stepCheckpointDao(), testDispatcher)
        fakeClient = FakeHealthConnectClientWrapper()
        manager = HealthConnectManager(fakeClient, sleepRepository, testDispatcher, stepRepository)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun syncStepRecords_whenUnavailable_failsWithIllegalStateException() = runTest(testDispatcher) {
        fakeClient.stubbedAvailability = HealthConnectAvailability.NOT_INSTALLED

        val result = manager.syncStepRecords(Instant.now().minus(1, ChronoUnit.DAYS), Instant.now())
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun syncStepRecords_whenPermissionDenied_failsWithSecurityException() = runTest(testDispatcher) {
        fakeClient.stubbedAvailability = HealthConnectAvailability.AVAILABLE
        fakeClient.stepPermissionsGranted = false

        val result = manager.syncStepRecords(Instant.now().minus(1, ChronoUnit.DAYS), Instant.now())
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
    }

    @Test
    fun syncStepRecords_whenNoRecords_returnsEmptySummary() = runTest(testDispatcher) {
        fakeClient.stubbedAvailability = HealthConnectAvailability.AVAILABLE
        fakeClient.stepPermissionsGranted = true
        fakeClient.stepRecordsToReturn.clear()

        val result = manager.syncStepRecords(Instant.now().minus(1, ChronoUnit.DAYS), Instant.now())
        assertTrue(result.isSuccess)
        val summary = result.getOrThrow()
        assertEquals(0, summary.importedCount)
        assertEquals(0, summary.duplicateCount)
    }

    @Test
    fun syncStepRecords_importsWearableRecordAndAttributesCorrectly() = runTest(testDispatcher) {
        fakeClient.stubbedAvailability = HealthConnectAvailability.AVAILABLE
        fakeClient.stepPermissionsGranted = true

        val start = Instant.parse("2026-10-14T10:00:00Z")
        val end = Instant.parse("2026-10-14T11:00:00Z")

        fakeClient.stepRecordsToReturn.add(
            HealthConnectStepRecord(
                id = "hc_step_101",
                startTime = start,
                endTime = end,
                count = 1420,
                sourcePackage = "com.google.android.apps.fitness",
                deviceModel = "Pixel Watch 3"
            )
        )

        val result = manager.syncStepRecords(start.minus(1, ChronoUnit.HOURS), end.plus(1, ChronoUnit.HOURS))
        assertTrue(result.isSuccess)
        val summary = result.getOrThrow()
        assertEquals(1, summary.importedCount)
        assertEquals(0, summary.duplicateCount)

        val stored = stepRepository.getStepRecordsForDateSync(LocalDate.of(2026, 10, 14))
        assertEquals(1, stored.size)
        val stepRecord = stored.first()
        assertEquals(1420L, stepRecord.stepCount)
        assertEquals(StepSource.HEALTH_CONNECT_WEARABLE, stepRecord.source)
        assertEquals("Pixel Watch 3", stepRecord.deviceModel)
        assertEquals("hc_step_101", stepRecord.externalRecordId)
    }

    @Test
    fun syncStepRecords_deduplicatesExistingExternalRecords() = runTest(testDispatcher) {
        fakeClient.stubbedAvailability = HealthConnectAvailability.AVAILABLE
        fakeClient.stepPermissionsGranted = true

        val start = Instant.parse("2026-10-14T10:00:00Z")
        val end = Instant.parse("2026-10-14T11:00:00Z")

        fakeClient.stepRecordsToReturn.add(
            HealthConnectStepRecord(
                id = "hc_step_duplicate_1",
                startTime = start,
                endTime = end,
                count = 850,
                deviceModel = "Garmin Venu 3"
            )
        )

        // First sync: imports 1
        val firstResult = manager.syncStepRecords(start.minus(1, ChronoUnit.HOURS), end.plus(1, ChronoUnit.HOURS))
        assertTrue(firstResult.isSuccess)
        assertEquals(1, firstResult.getOrThrow().importedCount)

        // Second sync: should detect duplicate and skip
        val secondResult = manager.syncStepRecords(start.minus(1, ChronoUnit.HOURS), end.plus(1, ChronoUnit.HOURS))
        assertTrue(secondResult.isSuccess)
        val summary = secondResult.getOrThrow()
        assertEquals(0, summary.importedCount)
        assertEquals(1, summary.duplicateCount)

        // Total records in database must still be 1
        val stored = stepRepository.getStepRecordsForDateSync(LocalDate.of(2026, 10, 14))
        assertEquals(1, stored.size)
    }
}
