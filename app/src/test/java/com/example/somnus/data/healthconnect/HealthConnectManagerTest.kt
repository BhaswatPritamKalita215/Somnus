package com.example.somnus.data.healthconnect

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.somnus.data.local.SomnusDatabase
import com.example.somnus.data.repository.SleepSessionRepositoryImpl
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

/**
 * Test double for [HealthConnectClientWrapper] to verify capability detection,
 * permissions, sync, and deduplication without requiring physical device or Google Play Services.
 */
class FakeHealthConnectClientWrapper : HealthConnectClientWrapper {
    var stubbedAvailability: HealthConnectAvailability = HealthConnectAvailability.AVAILABLE
    var permissionsGranted: Boolean = true
    var sleepRecordsToReturn: MutableList<HealthConnectSleepRecord> = mutableListOf()
    var shouldThrowException: Exception? = null

    override fun getAvailability(): HealthConnectAvailability = stubbedAvailability

    override suspend fun hasPermissions(): Boolean = permissionsGranted

    override suspend fun readSleepRecords(
        startTime: Instant,
        endTime: Instant
    ): List<HealthConnectSleepRecord> {
        shouldThrowException?.let { throw it }
        return sleepRecordsToReturn
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HealthConnectManagerTest {

    private lateinit var database: SomnusDatabase
    private lateinit var repository: SleepSessionRepositoryImpl
    private lateinit var fakeClient: FakeHealthConnectClientWrapper
    private lateinit var manager: HealthConnectManager
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SomnusDatabase::class.java
        ).allowMainThreadQueries().build()

        repository = SleepSessionRepositoryImpl(database.sleepSessionDao(), testDispatcher)
        fakeClient = FakeHealthConnectClientWrapper()
        manager = HealthConnectManager(fakeClient, repository, testDispatcher)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun checkAvailability_reportsCorrectStatus() {
        fakeClient.stubbedAvailability = HealthConnectAvailability.AVAILABLE
        assertEquals(HealthConnectAvailability.AVAILABLE, manager.checkAvailability())

        fakeClient.stubbedAvailability = HealthConnectAvailability.NOT_INSTALLED
        assertEquals(HealthConnectAvailability.NOT_INSTALLED, manager.checkAvailability())

        fakeClient.stubbedAvailability = HealthConnectAvailability.UPDATE_REQUIRED
        assertEquals(HealthConnectAvailability.UPDATE_REQUIRED, manager.checkAvailability())

        fakeClient.stubbedAvailability = HealthConnectAvailability.NOT_SUPPORTED
        assertEquals(HealthConnectAvailability.NOT_SUPPORTED, manager.checkAvailability())
    }

    @Test
    fun sync_whenUnavailable_failsWithIllegalStateException() = runTest(testDispatcher) {
        fakeClient.stubbedAvailability = HealthConnectAvailability.NOT_INSTALLED

        val result = manager.syncSleepRecords(Instant.now().minus(1, ChronoUnit.DAYS), Instant.now())
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun sync_whenPermissionDenied_failsWithSecurityException() = runTest(testDispatcher) {
        fakeClient.stubbedAvailability = HealthConnectAvailability.AVAILABLE
        fakeClient.permissionsGranted = false

        val result = manager.syncSleepRecords(Instant.now().minus(1, ChronoUnit.DAYS), Instant.now())
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
    }

    @Test
    fun sync_whenNoRecords_returnsEmptySummary() = runTest(testDispatcher) {
        fakeClient.stubbedAvailability = HealthConnectAvailability.AVAILABLE
        fakeClient.permissionsGranted = true
        fakeClient.sleepRecordsToReturn.clear()

        val result = manager.syncSleepRecords(Instant.now().minus(1, ChronoUnit.DAYS), Instant.now())
        assertTrue(result.isSuccess)
        val summary = result.getOrThrow()
        assertEquals(0, summary.importedCount)
        assertEquals(0, summary.duplicateCount)
        assertEquals(0, summary.conflictCount)
    }

    @Test
    fun sync_importsCleanRecord_withWearableSourceAndDeviceMetadata() = runTest(testDispatcher) {
        fakeClient.stubbedAvailability = HealthConnectAvailability.AVAILABLE
        fakeClient.permissionsGranted = true

        val start = Instant.parse("2026-03-01T23:00:00Z")
        val end = Instant.parse("2026-03-02T07:00:00Z")

        fakeClient.sleepRecordsToReturn.add(
            HealthConnectSleepRecord(
                id = "ext_12345",
                startTime = start,
                endTime = end,
                title = "Deep Sleep Night",
                notes = "Oura Ring Gen 3 Sync",
                sourcePackage = "com.ouraring.oura",
                deviceModel = "Oura Ring Gen3"
            )
        )

        val result = manager.syncSleepRecords(start.minus(1, ChronoUnit.HOURS), end.plus(1, ChronoUnit.HOURS))
        assertTrue(result.isSuccess)
        val summary = result.getOrThrow()
        assertEquals(1, summary.importedCount)
        assertEquals(0, summary.duplicateCount)
        assertEquals(0, summary.conflictCount)

        // Verify stored session in Room
        val count = repository.getSessionCount()
        assertEquals(1, count)

        val stored = repository.getActiveSessionOneShot() // null because it's completed
        val completed = repository.getSessionByIdOneShot("hc_ext_12345")
        assertTrue(completed != null)
        assertEquals(SessionSource.WEARABLE_IMPORT, completed?.source)
        assertEquals(SessionStatus.COMPLETED, completed?.status)
        assertTrue(completed?.notes?.contains("Oura Ring Gen3") == true)
        assertTrue(completed?.notes?.contains("com.ouraring.oura") == true)
    }

    @Test
    fun sync_deduplicatesExactTimeIntervals() = runTest(testDispatcher) {
        fakeClient.stubbedAvailability = HealthConnectAvailability.AVAILABLE
        fakeClient.permissionsGranted = true

        val start = Instant.parse("2026-03-01T23:00:00Z")
        val end = Instant.parse("2026-03-02T07:00:00Z")

        // Pre-insert session in repository
        val existingSession = SleepSession(
            id = "manual_1",
            startTime = start,
            endTime = end,
            zoneId = ZoneId.of("UTC"),
            status = SessionStatus.COMPLETED,
            source = SessionSource.MANUAL
        )
        repository.insertCompletedSession(existingSession)

        // Health Connect returns record with EXACT same start and end
        fakeClient.sleepRecordsToReturn.add(
            HealthConnectSleepRecord(
                id = "hc_duplicate",
                startTime = start,
                endTime = end,
                deviceModel = "Pixel Watch 2"
            )
        )

        val result = manager.syncSleepRecords(start.minus(1, ChronoUnit.HOURS), end.plus(1, ChronoUnit.HOURS))
        assertTrue(result.isSuccess)
        val summary = result.getOrThrow()
        assertEquals(0, summary.importedCount)
        assertEquals(1, summary.duplicateCount)
        assertEquals(0, summary.conflictCount)

        // Database should still only have 1 session
        assertEquals(1, repository.getSessionCount())
    }

    @Test
    fun sync_flagsConflictWhenOverlappingDifferentWindow_andDoesNotOverwriteManualSession() = runTest(testDispatcher) {
        fakeClient.stubbedAvailability = HealthConnectAvailability.AVAILABLE
        fakeClient.permissionsGranted = true

        val manualStart = Instant.parse("2026-03-01T22:30:00Z")
        val manualEnd = Instant.parse("2026-03-02T06:30:00Z")

        // Pre-insert manual session
        val existingManual = SleepSession(
            id = "manual_session_1",
            startTime = manualStart,
            endTime = manualEnd,
            zoneId = ZoneId.of("UTC"),
            status = SessionStatus.COMPLETED,
            source = SessionSource.MANUAL,
            notes = "User recorded bedtime"
        )
        repository.insertCompletedSession(existingManual)

        // Health Connect returns overlapping but DIFFERENT interval
        val importedStart = Instant.parse("2026-03-01T23:00:00Z")
        val importedEnd = Instant.parse("2026-03-02T07:15:00Z")

        fakeClient.sleepRecordsToReturn.add(
            HealthConnectSleepRecord(
                id = "hc_conflicting",
                startTime = importedStart,
                endTime = importedEnd,
                deviceModel = "Garmin Fenix"
            )
        )

        val result = manager.syncSleepRecords(manualStart.minus(1, ChronoUnit.HOURS), importedEnd.plus(1, ChronoUnit.HOURS))
        assertTrue(result.isSuccess)
        val summary = result.getOrThrow()
        assertEquals(0, summary.importedCount)
        assertEquals(0, summary.duplicateCount)
        assertEquals(1, summary.conflictCount)

        // Ensure manual session was preserved unaltered
        val storedManual = repository.getSessionByIdOneShot("manual_session_1")
        assertTrue(storedManual != null)
        assertEquals(manualStart, storedManual?.startTime)
        assertEquals(manualEnd, storedManual?.endTime)
        assertEquals(SessionSource.MANUAL, storedManual?.source)
    }

    @Test
    fun sync_handlesClientExceptionGracefully() = runTest(testDispatcher) {
        fakeClient.stubbedAvailability = HealthConnectAvailability.AVAILABLE
        fakeClient.permissionsGranted = true
        fakeClient.shouldThrowException = RuntimeException("Remote IPC failure")

        val result = manager.syncSleepRecords(Instant.now().minus(1, ChronoUnit.DAYS), Instant.now())
        assertTrue(result.isFailure)
        assertEquals("Remote IPC failure", result.exceptionOrNull()?.message)
    }
}
