package com.example.somnus.ui.insights

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.somnus.data.local.SomnusDatabase
import com.example.somnus.data.repository.PsqiRepositoryImpl
import com.example.somnus.data.repository.SolRepositoryImpl
import com.example.somnus.domain.model.PsqiStatus
import com.example.somnus.domain.model.SolMeasurementMethod
import com.example.somnus.domain.model.SyntheticPsqiFixtures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class InsightsViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var db: SomnusDatabase
    private lateinit var solRepository: SolRepositoryImpl
    private lateinit var psqiRepository: PsqiRepositoryImpl
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = SomnusDatabase.buildInMemoryDatabase(context)
        solRepository = SolRepositoryImpl(db.solRecordDao())
        psqiRepository = PsqiRepositoryImpl(db.psqiDao())
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun selectTab_updatesSelectedTab() = runTest(testDispatcher) {
        val viewModel = InsightsViewModel(solRepository, psqiRepository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        assertEquals(InsightsTab.SOL, viewModel.uiState.value.selectedTab)

        viewModel.selectTab(InsightsTab.PSQI)
        advanceUntilIdle()
        assertEquals(InsightsTab.PSQI, viewModel.uiState.value.selectedTab)

        viewModel.selectTab(InsightsTab.COMPARISON)
        advanceUntilIdle()
        assertEquals(InsightsTab.COMPARISON, viewModel.uiState.value.selectedTab)
    }

    @Test
    fun saveSolRecord_persistsRecordAndUpdatesSummary() = runTest(testDispatcher) {
        val viewModel = InsightsViewModel(solRepository, psqiRepository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        viewModel.saveSolRecord(
            reportingDate = LocalDate.now(),
            latencyMinutes = 25,
            method = SolMeasurementMethod.SELF_REPORTED,
            notes = "Test entry"
        )
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.solRecords.size)
        assertEquals(25, viewModel.uiState.value.latestSolRecord?.latencyMinutes)
        assertEquals(25.0, viewModel.uiState.value.weeklySolSummary?.averageLatencyMinutes!!, 0.01)
        assertNotNull(viewModel.uiState.value.actionMessage)
    }

    @Test
    fun calculateAndSaveSolFromTimestamps_calculatesMinutes() = runTest(testDispatcher) {
        val viewModel = InsightsViewModel(solRepository, psqiRepository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        val attempt = Instant.parse("2026-10-14T23:00:00Z")
        val onset = Instant.parse("2026-10-14T23:35:00Z")

        viewModel.calculateAndSaveSolFromTimestamps(
            attemptToSleep = attempt,
            sleepOnset = onset,
            reportingDate = LocalDate.now()
        )
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.solRecords.size)
        assertEquals(35, viewModel.uiState.value.latestSolRecord?.latencyMinutes)
    }

    @Test
    fun scoreAndSavePsqiAssessment_scoresCompletedFixture() = runTest(testDispatcher) {
        val viewModel = InsightsViewModel(solRepository, psqiRepository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        viewModel.scoreAndSavePsqiAssessment(SyntheticPsqiFixtures.MODERATE_DISTURBANCE)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.psqiAssessments.size)
        val latest = viewModel.uiState.value.latestCompletedPsqi
        assertNotNull(latest)
        assertEquals(8, latest!!.componentScores?.globalScore)
        assertEquals(PsqiStatus.COMPLETED, latest.status)
    }

    @Test
    fun scoreAndSavePsqiAssessment_incompleteFixture_preservesIncompleteStatusWithoutScores() = runTest(testDispatcher) {
        val viewModel = InsightsViewModel(solRepository, psqiRepository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        viewModel.scoreAndSavePsqiAssessment(SyntheticPsqiFixtures.INCOMPLETE_FIXTURE)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.psqiAssessments.size)
        val saved = viewModel.uiState.value.psqiAssessments.first()
        assertEquals(PsqiStatus.INCOMPLETE, saved.status)
        assertNull(saved.componentScores)
        assertNull(viewModel.uiState.value.latestCompletedPsqi) // Since latest completed only returns completed
    }

    @Test
    fun deleteSolRecord_removesFromRepository() = runTest(testDispatcher) {
        val viewModel = InsightsViewModel(solRepository, psqiRepository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        viewModel.saveSolRecord(LocalDate.now(), 20, SolMeasurementMethod.SELF_REPORTED)
        advanceUntilIdle()
        val id = viewModel.uiState.value.solRecords.first().id

        viewModel.deleteSolRecord(id)
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.solRecords.size)
    }
}
