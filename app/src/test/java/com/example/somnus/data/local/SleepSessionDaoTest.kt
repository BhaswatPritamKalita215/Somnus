package com.example.somnus.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SleepSessionDaoTest {

    private lateinit var db: SomnusDatabase
    private lateinit var dao: SleepSessionDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = SomnusDatabase.buildInMemoryDatabase(context)
        dao = db.sleepSessionDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun startNewSession_insertsActiveSession() = runTest {
        val entity = SleepSessionEntity(
            id = "session-1",
            startTime = 1000000L,
            endTime = null,
            timezoneId = "UTC",
            status = "ACTIVE",
            source = "MANUAL",
            createdAt = 1000000L,
            updatedAt = 1000000L,
            notes = "Test night",
            isCorrected = false
        )

        dao.startNewSession(entity)

        val active = dao.getActiveSession()
        assertNotNull(active)
        assertEquals("session-1", active?.id)
        assertEquals("ACTIVE", active?.status)
    }

    @Test
    fun startNewSession_whenActiveExists_throwsIllegalStateException() = runTest {
        val firstActive = SleepSessionEntity(
            id = "session-1",
            startTime = 1000000L,
            endTime = null,
            timezoneId = "UTC",
            status = "ACTIVE",
            source = "MANUAL",
            createdAt = 1000000L,
            updatedAt = 1000000L,
            notes = null,
            isCorrected = false
        )
        dao.startNewSession(firstActive)

        val secondActive = SleepSessionEntity(
            id = "session-2",
            startTime = 1005000L,
            endTime = null,
            timezoneId = "UTC",
            status = "ACTIVE",
            source = "MANUAL",
            createdAt = 1005000L,
            updatedAt = 1005000L,
            notes = null,
            isCorrected = false
        )

        try {
            dao.startNewSession(secondActive)
            fail("Expected IllegalStateException when an active session already exists")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("already in progress") == true)
        }
    }

    @Test
    fun completeSession_atomicallyTransitionsToCompleted() = runTest {
        val active = SleepSessionEntity(
            id = "session-1",
            startTime = 1000000L,
            endTime = null,
            timezoneId = "UTC",
            status = "ACTIVE",
            source = "MANUAL",
            createdAt = 1000000L,
            updatedAt = 1000000L,
            notes = null,
            isCorrected = false
        )
        dao.startNewSession(active)

        val completed = dao.completeSession(
            id = "session-1",
            endTimeEpochMs = 1030000L,
            updatedAtEpochMs = 1030005L,
            notes = "Woke up feeling rested"
        )

        assertEquals("COMPLETED", completed.status)
        assertEquals(1030000L, completed.endTime)
        assertEquals("Woke up feeling rested", completed.notes)

        // Verify active session is now null
        assertNull(dao.getActiveSession())

        // Verify completed sessions contains this session
        val completedList = dao.observeCompletedSessions().first()
        assertEquals(1, completedList.size)
        assertEquals("session-1", completedList[0].id)
    }

    @Test
    fun completeSession_withInvalidEndTime_throwsIllegalArgumentException() = runTest {
        val active = SleepSessionEntity(
            id = "session-1",
            startTime = 1000000L,
            endTime = null,
            timezoneId = "UTC",
            status = "ACTIVE",
            source = "MANUAL",
            createdAt = 1000000L,
            updatedAt = 1000000L,
            notes = null,
            isCorrected = false
        )
        dao.startNewSession(active)

        // Attempting to complete with end time earlier than start time
        try {
            dao.completeSession(
                id = "session-1",
                endTimeEpochMs = 999999L,
                updatedAtEpochMs = 1000000L
            )
            fail("Expected IllegalArgumentException when end time is before start time")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("must be strictly after") == true)
        }

        // Attempting to complete with zero duration (end == start)
        try {
            dao.completeSession(
                id = "session-1",
                endTimeEpochMs = 1000000L,
                updatedAtEpochMs = 1000000L
            )
            fail("Expected IllegalArgumentException when duration is zero")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("must be strictly after") == true)
        }
    }

    @Test
    fun update_modifiesTimestampsAndCorrectionFlag() = runTest {
        val entity = SleepSessionEntity(
            id = "session-1",
            startTime = 1000000L,
            endTime = 1028800L,
            timezoneId = "UTC",
            status = "COMPLETED",
            source = "MANUAL",
            createdAt = 1000000L,
            updatedAt = 1028800L,
            notes = null,
            isCorrected = false
        )
        dao.insert(entity)

        val corrected = entity.copy(
            startTime = 995000L,
            endTime = 1030000L,
            isCorrected = true,
            updatedAt = 1050000L,
            notes = "Adjusted bedtime"
        )
        dao.update(corrected)

        val retrieved = dao.getSessionById("session-1")
        assertNotNull(retrieved)
        assertEquals(995000L, retrieved?.startTime)
        assertEquals(1030000L, retrieved?.endTime)
        assertTrue(retrieved?.isCorrected == true)
        assertEquals("Adjusted bedtime", retrieved?.notes)
    }

    @Test
    fun deleteById_removesSpecificSession() = runTest {
        dao.insert(
            SleepSessionEntity(
                id = "s1",
                startTime = 100L,
                endTime = 200L,
                timezoneId = "UTC",
                status = "COMPLETED",
                source = "MANUAL",
                createdAt = 100L,
                updatedAt = 200L,
                notes = null,
                isCorrected = false
            )
        )
        dao.insert(
            SleepSessionEntity(
                id = "s2",
                startTime = 300L,
                endTime = 400L,
                timezoneId = "UTC",
                status = "COMPLETED",
                source = "MANUAL",
                createdAt = 300L,
                updatedAt = 400L,
                notes = null,
                isCorrected = false
            )
        )

        assertEquals(2, dao.getSessionCount())
        val deleted = dao.deleteById("s1")
        assertEquals(1, deleted)

        assertEquals(1, dao.getSessionCount())
        assertNull(dao.getSessionById("s1"))
        assertNotNull(dao.getSessionById("s2"))
    }

    @Test
    fun deleteAll_clearsEntireTable() = runTest {
        dao.insert(
            SleepSessionEntity("s1", 100L, 200L, "UTC", "COMPLETED", "MANUAL", 100L, 200L, null, false)
        )
        dao.insert(
            SleepSessionEntity("s2", 300L, 400L, "UTC", "COMPLETED", "MANUAL", 300L, 400L, null, false)
        )

        assertEquals(2, dao.getSessionCount())
        dao.deleteAll()
        assertEquals(0, dao.getSessionCount())
    }

    @Test
    fun observeCompletedSessions_chronologicalOrderNewestFirst() = runTest {
        dao.insert(
            SleepSessionEntity("old", 1000L, 2000L, "UTC", "COMPLETED", "MANUAL", 1000L, 2000L, null, false)
        )
        dao.insert(
            SleepSessionEntity("newest", 5000L, 6000L, "UTC", "COMPLETED", "MANUAL", 5000L, 6000L, null, false)
        )
        dao.insert(
            SleepSessionEntity("middle", 3000L, 4000L, "UTC", "COMPLETED", "MANUAL", 3000L, 4000L, null, false)
        )

        val list = dao.observeCompletedSessions().first()
        assertEquals(3, list.size)
        assertEquals("newest", list[0].id)
        assertEquals("middle", list[1].id)
        assertEquals("old", list[2].id)
    }
}
