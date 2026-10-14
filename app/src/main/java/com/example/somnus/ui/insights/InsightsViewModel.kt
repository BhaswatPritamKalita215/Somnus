package com.example.somnus.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.somnus.domain.analysis.PsqiScoringEngine
import com.example.somnus.domain.analysis.PsqiScoringResult
import com.example.somnus.domain.analysis.SolCalculator
import com.example.somnus.domain.analysis.SolSummary
import com.example.somnus.domain.model.PsqiAssessment
import com.example.somnus.domain.model.PsqiResponses
import com.example.somnus.domain.model.PsqiStatus
import com.example.somnus.domain.model.SolMeasurementMethod
import com.example.somnus.domain.model.SolQualityFlag
import com.example.somnus.domain.model.SolRecord
import com.example.somnus.data.repository.PsqiRepository
import com.example.somnus.data.repository.SolRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

enum class InsightsTab {
    SOL,
    PSQI,
    COMPARISON
}

data class InsightsUiState(
    val selectedTab: InsightsTab = InsightsTab.SOL,
    val solRecords: List<SolRecord> = emptyList(),
    val weeklySolSummary: SolSummary? = null,
    val monthlySolSummary: SolSummary? = null,
    val latestSolRecord: SolRecord? = null,
    val psqiAssessments: List<PsqiAssessment> = emptyList(),
    val latestCompletedPsqi: PsqiAssessment? = null,
    val actionMessage: String? = null,
    val errorMessage: String? = null
)

class InsightsViewModel(
    private val solRepository: SolRepository,
    private val psqiRepository: PsqiRepository
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(InsightsTab.SOL)
    private val _actionMessage = MutableStateFlow<String?>(null)
    private val _errorMessage = MutableStateFlow<String?>(null)

    private val _uiControls = combine(_selectedTab, _actionMessage, _errorMessage) { tab, msg, error ->
        Triple(tab, msg, error)
    }

    val uiState: StateFlow<InsightsUiState> = combine(
        solRepository.allRecords,
        psqiRepository.allAssessments,
        psqiRepository.latestCompletedAssessment,
        _uiControls
    ) { allSol, allPsqi, latestPsqi, (tab, msg, error) ->
        val sortedSol = allSol.sortedByDescending { it.reportingDate }
        val latestSol = sortedSol.firstOrNull()

        // Calculate 7-day and 30-day summaries
        val today = LocalDate.now()
        val sevenDaysAgo = today.minusDays(7)
        val thirtyDaysAgo = today.minusDays(30)

        val last7DaysRecords = sortedSol.filter { !it.reportingDate.isBefore(sevenDaysAgo) }
        val last30DaysRecords = sortedSol.filter { !it.reportingDate.isBefore(thirtyDaysAgo) }

        val weeklySummary = SolCalculator.calculateSummary(last7DaysRecords, 7, "Past 7 Days")
        val monthlySummary = SolCalculator.calculateSummary(last30DaysRecords, 30, "Past 30 Days")

        InsightsUiState(
            selectedTab = tab,
            solRecords = sortedSol,
            weeklySolSummary = weeklySummary,
            monthlySolSummary = monthlySummary,
            latestSolRecord = latestSol,
            psqiAssessments = allPsqi,
            latestCompletedPsqi = latestPsqi,
            actionMessage = msg,
            errorMessage = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = InsightsUiState()
    )

    fun selectTab(tab: InsightsTab) {
        _selectedTab.value = tab
    }

    fun clearMessages() {
        _actionMessage.value = null
        _errorMessage.value = null
    }

    // --- SOL Actions ---

    fun saveSolRecord(
        reportingDate: LocalDate,
        latencyMinutes: Int,
        method: SolMeasurementMethod,
        sourceIdentifier: String = "manual",
        sourceDeviceMetadata: String? = null,
        attemptToSleepTime: Instant? = null,
        sleepOnsetTime: Instant? = null,
        notes: String? = null
    ) {
        viewModelScope.launch {
            if (latencyMinutes < 0) {
                _errorMessage.value = "Latency cannot be negative"
                return@launch
            }

            val qualityFlags = SolCalculator.evaluateQualityFlags(
                latencyMinutes = latencyMinutes,
                hasExplicitTimestamps = attemptToSleepTime != null && sleepOnsetTime != null,
                isManuallyEdited = false
            )

            val record = SolRecord(
                id = UUID.randomUUID().toString(),
                reportingDate = reportingDate,
                zoneId = ZoneId.systemDefault(),
                latencyMinutes = latencyMinutes,
                method = method,
                sourceIdentifier = sourceIdentifier,
                sourceDeviceMetadata = sourceDeviceMetadata,
                qualityFlags = qualityFlags,
                attemptToSleepTime = attemptToSleepTime,
                sleepOnsetTime = sleepOnsetTime,
                notes = notes
            )

            val result = solRepository.saveRecord(record)
            result.onSuccess {
                _actionMessage.value = "Logged SOL of $latencyMinutes min for $reportingDate"
            }.onFailure { err ->
                _errorMessage.value = err.message ?: "Failed to save SOL record"
            }
        }
    }

    fun calculateAndSaveSolFromTimestamps(
        attemptToSleep: Instant,
        sleepOnset: Instant,
        reportingDate: LocalDate,
        method: SolMeasurementMethod = SolMeasurementMethod.SELF_REPORTED,
        notes: String? = null
    ) {
        viewModelScope.launch {
            val calcResult = SolCalculator.calculateSolMinutes(attemptToSleep, sleepOnset)
            calcResult.onSuccess { minutes ->
                val qualityFlags = SolCalculator.evaluateQualityFlags(
                    latencyMinutes = minutes,
                    hasExplicitTimestamps = true,
                    isManuallyEdited = false
                )

                val record = SolRecord(
                    reportingDate = reportingDate,
                    zoneId = ZoneId.systemDefault(),
                    latencyMinutes = minutes,
                    method = method,
                    sourceIdentifier = if (method == SolMeasurementMethod.SELF_REPORTED) "manual_timestamps" else "algorithm",
                    qualityFlags = qualityFlags,
                    attemptToSleepTime = attemptToSleep,
                    sleepOnsetTime = sleepOnset,
                    notes = notes
                )
                solRepository.saveRecord(record)
                _actionMessage.value = "Calculated SOL of $minutes min saved"
            }.onFailure { err ->
                _errorMessage.value = err.message ?: "Invalid timestamps"
            }
        }
    }

    fun deleteSolRecord(id: String) {
        viewModelScope.launch {
            solRepository.deleteRecord(id)
            _actionMessage.value = "SOL record deleted"
        }
    }

    // --- PSQI Actions ---

    fun scoreAndSavePsqiAssessment(
        responses: PsqiResponses,
        version: String = "Authorized-PSQI-v1",
        reportingPeriod: String = "Past 1 Month",
        notes: String? = null
    ) {
        viewModelScope.launch {
            val scoringResult = PsqiScoringEngine.score(responses)
            when (scoringResult) {
                is PsqiScoringResult.Complete -> {
                    val assessment = PsqiAssessment(
                        startedAt = Instant.now().minusSeconds(300),
                        completedAt = Instant.now(),
                        version = version,
                        reportingPeriod = reportingPeriod,
                        status = PsqiStatus.COMPLETED,
                        scoringVersion = PsqiScoringEngine.SCORING_VERSION,
                        responses = responses,
                        componentScores = scoringResult.componentScores,
                        notes = notes
                    )
                    psqiRepository.saveAssessment(assessment)
                    _actionMessage.value = "Completed PSQI assessment saved (Global Score: ${scoringResult.globalScore})"
                }
                is PsqiScoringResult.Incomplete -> {
                    // Save as INCOMPLETE without fabricating scores
                    val assessment = PsqiAssessment(
                        startedAt = Instant.now(),
                        completedAt = null,
                        version = version,
                        reportingPeriod = reportingPeriod,
                        status = PsqiStatus.INCOMPLETE,
                        scoringVersion = PsqiScoringEngine.SCORING_VERSION,
                        responses = responses,
                        componentScores = null,
                        notes = notes
                    )
                    psqiRepository.saveAssessment(assessment)
                    _actionMessage.value = "Saved incomplete assessment (${scoringResult.missingItems.size} items missing)"
                }
            }
        }
    }

    fun deletePsqiAssessment(id: String) {
        viewModelScope.launch {
            psqiRepository.deleteAssessment(id)
            _actionMessage.value = "PSQI assessment deleted"
        }
    }

    companion object {
        fun provideFactory(
            solRepository: SolRepository,
            psqiRepository: PsqiRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return InsightsViewModel(solRepository, psqiRepository) as T
            }
        }
    }
}
