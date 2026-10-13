package com.example.somnus.ui.settings

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.somnus.data.local.SomnusDatabase
import com.example.somnus.data.preferences.ThemeMode
import com.example.somnus.data.preferences.UserPreferencesRepository
import com.example.somnus.data.repository.SleepSessionRepositoryImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SettingsViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val tmpFolder: TemporaryFolder = TemporaryFolder()

    private lateinit var db: SomnusDatabase
    private lateinit var repository: SleepSessionRepositoryImpl
    private lateinit var preferencesRepository: UserPreferencesRepository
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = SomnusDatabase.buildInMemoryDatabase(context)
        repository = SleepSessionRepositoryImpl(db.sleepSessionDao(), testDispatcher)

        val testDataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { File(tmpFolder.newFolder(), "test_preferences.preferences_pb") }
        )
        preferencesRepository = UserPreferencesRepository(testDataStore)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun deleteAllData_clearsSessionsAndSetsSuccess() = runTest(testDispatcher) {
        val start = Instant.now().minus(Duration.ofHours(8)).truncatedTo(ChronoUnit.MILLIS)
        val end = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        repository.startSession("s1", start, ZoneId.of("UTC"))
        repository.endSession("s1", end)
        advanceUntilIdle()

        val viewModel = SettingsViewModel(repository, preferencesRepository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.sessionCount)

        viewModel.deleteAllData()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.deletionSuccess)
        assertEquals(0, repository.getSessionCount())
        assertEquals(0, viewModel.uiState.value.sessionCount)
    }

    @Test
    fun setThemeMode_updatesPreference() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(repository, preferencesRepository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        viewModel.setThemeMode(ThemeMode.LIGHT)
        advanceUntilIdle()

        val savedPrefs = preferencesRepository.userPreferencesFlow.first()
        assertEquals(ThemeMode.LIGHT, savedPrefs.themeMode)
    }
}
