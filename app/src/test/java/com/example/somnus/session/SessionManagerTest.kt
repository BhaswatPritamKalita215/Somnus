package com.example.somnus.session

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.somnus.data.local.SomnusDatabase
import com.example.somnus.data.repository.SleepSessionRepositoryImpl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SessionManagerTest {

    private lateinit var db: SomnusDatabase
    private lateinit var repository: SleepSessionRepositoryImpl
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = SomnusDatabase.buildInMemoryDatabase(context)
        repository = SleepSessionRepositoryImpl(db.sleepSessionDao(), testDispatcher)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun startSession_createsActiveSession() = runTest(testDispatcher) {
        val manager = SessionManager(repository, testScope)

        val result = manager.startSession("Going to sleep early")
        assertTrue(result.isSuccess)

        val created = result.getOrNull()
        assertNotNull(created)
        assertTrue(created!!.isActive)
        assertEquals("Going to sleep early", created.notes)

        val activeInRepo = repository.getActiveSessionOneShot()
        assertNotNull(activeInRepo)
        assertEquals(created.id, activeInRepo?.id)
    }

    @Test
    fun startSession_whenAlreadyActive_failsAndPreventsDuplicates() = runTest(testDispatcher) {
        val manager = SessionManager(repository, testScope)

        val first = manager.startSession()
        assertTrue(first.isSuccess)

        val second = manager.startSession()
        assertTrue(second.isFailure)
        assertTrue(second.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun endSession_completesActiveSession() = runTest(testDispatcher) {
        val manager = SessionManager(repository, testScope)

        manager.startSession()
        val endResult = manager.endSession("Rested")
        assertTrue(endResult.isSuccess)

        val completed = endResult.getOrNull()
        assertNotNull(completed)
        assertTrue(completed!!.isCompleted)
        assertNotNull(completed.endTime)

        assertNull(repository.getActiveSessionOneShot())
        val completedList = repository.completedSessions.first()
        assertEquals(1, completedList.size)
    }

    @Test
    fun discardActiveSession_removesOngoingSession() = runTest(testDispatcher) {
        val manager = SessionManager(repository, testScope)

        manager.startSession()
        assertNotNull(repository.getActiveSessionOneShot())

        val discardResult = manager.discardActiveSession()
        assertTrue(discardResult.isSuccess)

        assertNull(repository.getActiveSessionOneShot())
        assertEquals(0, repository.getSessionCount())
    }

    @Test
    fun sessionLifecycle_survivesProcessRestart() = runTest(testDispatcher) {
        // Step 1: Initial process starts a session
        val process1Manager = SessionManager(repository, testScope)
        val startResult = process1Manager.startSession()
        assertTrue(startResult.isSuccess)
        val originalSessionId = startResult.getOrNull()!!.id

        // Step 2: Simulate process termination and recreation of SessionManager
        val process2Manager = SessionManager(repository, testScope)
        val activeInRelaunch = repository.getActiveSessionOneShot()

        assertNotNull(activeInRelaunch)
        assertEquals(originalSessionId, activeInRelaunch?.id)
        assertTrue(activeInRelaunch?.isActive == true)
    }
}
