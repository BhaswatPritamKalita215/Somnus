package com.example.somnus.ui.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.somnus.data.preferences.UserPreferences
import com.example.somnus.data.preferences.UserPreferencesRepository
import com.example.somnus.data.repository.AssistantRepository
import com.example.somnus.domain.assistant.AssistantDiagnostics
import com.example.somnus.domain.assistant.AssistantMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AssistantUiState(
    val messages: List<AssistantMessage> = emptyList(),
    val inputText: String = "",
    val isGenerating: Boolean = false,
    val diagnostics: AssistantDiagnostics = AssistantDiagnostics(),
    val showClearDialog: Boolean = false,
    val showDiagnosticsSheet: Boolean = false,
    val errorMessage: String? = null,
    val isCloudAiActive: Boolean = false,
    val suggestedPrompts: List<String> = listOf(
        "How did I sleep last night?",
        "How many steps today?",
        "Show my calorie intake",
        "Why do I wake up frequently?",
        "Compare this week's sleep to last week",
        "What is my average bedtime?"
    )
)

/**
 * ViewModel managing the conversational state, token-budgeted prompt execution,
 * cancellation, and developer diagnostics for the Somnus AI Assistant.
 */
class AssistantViewModel(
    private val assistantRepository: AssistantRepository,
    private val preferencesRepository: UserPreferencesRepository? = null
) : ViewModel() {

    private val _inputText = MutableStateFlow("")
    private val _isGenerating = MutableStateFlow(false)
    private val _showClearDialog = MutableStateFlow(false)
    private val _showDiagnosticsSheet = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    private var activeGenerationJob: Job? = null

    private data class LocalUiState(
        val input: String = "",
        val isGenerating: Boolean = false,
        val showClear: Boolean = false,
        val showDiag: Boolean = false,
        val error: String? = null
    )

    private val _localUiState = combine(
        _inputText,
        _isGenerating,
        _showClearDialog,
        _showDiagnosticsSheet,
        _errorMessage
    ) { input, isGenerating, showClear, showDiag, error ->
        LocalUiState(input, isGenerating, showClear, showDiag, error)
    }

    val uiState: StateFlow<AssistantUiState> = combine(
        assistantRepository.conversationMessages,
        assistantRepository.diagnosticsFlow,
        assistantRepository.isCloudInferenceActive,
        preferencesRepository?.userPreferencesFlow ?: flowOf(UserPreferences()),
        _localUiState
    ) { messages, diagnostics, isCloudActive, prefs, local ->
        val cloudAiActive = isCloudActive &&
            prefs.groqApiKey.isNotBlank() &&
            prefs.groqPrivacyConsentGranted

        AssistantUiState(
            messages = messages,
            inputText = local.input,
            isGenerating = local.isGenerating,
            diagnostics = diagnostics,
            showClearDialog = local.showClear,
            showDiagnosticsSheet = local.showDiag,
            errorMessage = local.error,
            isCloudAiActive = cloudAiActive
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AssistantUiState()
    )

    fun onInputTextChanged(text: String) {
        // Enforce reasonable prompt length bounds (max 500 characters)
        if (text.length <= 500) {
            _inputText.value = text
        }
    }

    fun sendMessage(customPrompt: String? = null) {
        val promptToSend = (customPrompt ?: _inputText.value).trim()
        if (promptToSend.isBlank()) return
        if (_isGenerating.value) return

        if (customPrompt == null) {
            _inputText.value = ""
        }

        _errorMessage.value = null
        _isGenerating.value = true

        activeGenerationJob?.cancel()
        activeGenerationJob = viewModelScope.launch {
            try {
                assistantRepository.processQuery(promptToSend).collect {
                    // Chunks and updates are persisted to Room and emitted via conversationMessages flow
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    _errorMessage.value = e.message ?: "Failed to generate response. Please try again."
                }
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun cancelGeneration() {
        activeGenerationJob?.cancel()
        activeGenerationJob = null
        _isGenerating.value = false
    }

    fun clearConversation() {
        viewModelScope.launch {
            assistantRepository.clearConversation()
            _showClearDialog.value = false
        }
    }

    fun setClearDialogVisible(visible: Boolean) {
        _showClearDialog.value = visible
    }

    fun setDiagnosticsVisible(visible: Boolean) {
        _showDiagnosticsSheet.value = visible
    }

    fun clearError() {
        _errorMessage.value = null
    }

    companion object {
        fun provideFactory(
            assistantRepository: AssistantRepository,
            preferencesRepository: UserPreferencesRepository? = null
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AssistantViewModel(assistantRepository, preferencesRepository) as T
                }
            }
    }
}
