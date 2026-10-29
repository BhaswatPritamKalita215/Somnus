package com.example.somnus.ui.assistant

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.somnus.data.repository.AssistantRepository
import com.example.somnus.domain.assistant.AssistantDiagnostics
import com.example.somnus.domain.assistant.AssistantMessage
import com.example.somnus.domain.assistant.IntentType
import com.example.somnus.domain.assistant.MessageRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.somnus.data.preferences.UserPreferencesRepository
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AssistantViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val tmpFolder: TemporaryFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var fakeRepository: FakeAssistantRepository
    private lateinit var viewModel: AssistantViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeAssistantRepository()
        viewModel = AssistantViewModel(fakeRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun onInputTextChanged_updatesInputTextWithinLimit() = runTest(testDispatcher) {
        viewModel.onInputTextChanged("Hello Somnus")
        advanceUntilIdle()

        assertEquals("Hello Somnus", viewModel.uiState.value.inputText)

        // Try exceeding 500 characters
        val tooLong = "A".repeat(501)
        viewModel.onInputTextChanged(tooLong)
        advanceUntilIdle()

        // Should not accept text longer than 500 characters
        assertEquals("Hello Somnus", viewModel.uiState.value.inputText)
    }

    @Test
    fun sendMessage_clearsInputAndEmitsMessage() = runTest(testDispatcher) {
        viewModel.onInputTextChanged("How did I sleep?")
        viewModel.sendMessage()
        advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.inputText)
        assertFalse(viewModel.uiState.value.isGenerating)
        assertEquals(2, viewModel.uiState.value.messages.size)
    }

    @Test
    fun cancelGeneration_resetsIsGenerating() = runTest(testDispatcher) {
        viewModel.sendMessage("Why am I tired?")
        viewModel.cancelGeneration()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isGenerating)
    }

    @Test
    fun clearConversation_invokesRepoClearAndHidesDialog() = runTest(testDispatcher) {
        viewModel.sendMessage("Test query")
        advanceUntilIdle()

        viewModel.setClearDialogVisible(true)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showClearDialog)

        viewModel.clearConversation()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showClearDialog)
        assertTrue(viewModel.uiState.value.messages.isEmpty())
    }

    @Test
    fun setDiagnosticsVisible_togglesSheetState() = runTest(testDispatcher) {
        assertFalse(viewModel.uiState.value.showDiagnosticsSheet)

        viewModel.setDiagnosticsVisible(true)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showDiagnosticsSheet)

        viewModel.setDiagnosticsVisible(false)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.showDiagnosticsSheet)
    }

    @Test
    fun uiState_cloudAiActive_falseByDefault() = runTest(testDispatcher) {
        assertFalse(viewModel.uiState.value.isCloudAiActive)
    }

    @Test
    fun uiState_cloudAiActive_trueOnlyWhenKeySetConsentGrantedAndCloudActive() = runTest(testDispatcher) {
        val testFile = tmpFolder.newFile("test_prefs.preferences_pb")
        val testDataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { testFile }
        )
        val prefsRepo = UserPreferencesRepository(testDataStore)

        fakeRepository.cloudActiveFlow.value = true
        val customVm = AssistantViewModel(fakeRepository, prefsRepo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            customVm.uiState.collect()
        }
        advanceUntilIdle()

        // Initially without key/consent -> false
        assertFalse(customVm.uiState.value.isCloudAiActive)

        // Set key only, without consent -> false
        prefsRepo.setGroqApiKey("gsk_valid_key")
        advanceUntilIdle()
        assertFalse(customVm.uiState.value.isCloudAiActive)

        // Grant consent -> true!
        prefsRepo.setGroqPrivacyConsentGranted(true)
        advanceUntilIdle()
        assertTrue(customVm.uiState.value.isCloudAiActive)

        // If cloud becomes inactive (e.g. offline/error) -> false
        fakeRepository.cloudActiveFlow.value = false
        advanceUntilIdle()
        assertFalse(customVm.uiState.value.isCloudAiActive)
    }
}

class FakeAssistantRepository : AssistantRepository {
    private val _messages = MutableStateFlow<List<AssistantMessage>>(emptyList())
    override val conversationMessages: Flow<List<AssistantMessage>> = _messages.asStateFlow()

    private val _diagnostics = MutableStateFlow(AssistantDiagnostics())
    override val diagnosticsFlow: Flow<AssistantDiagnostics> = _diagnostics.asStateFlow()

    val cloudActiveFlow = MutableStateFlow(false)
    override val isCloudInferenceActive: Flow<Boolean> = cloudActiveFlow.asStateFlow()

    override suspend fun processQuery(userText: String): Flow<AssistantMessage> = flow {
        val userMsg = AssistantMessage(
            id = "u-${System.currentTimeMillis()}",
            role = MessageRole.USER,
            content = userText,
            timestamp = Instant.now()
        )
        val assistantMsg = AssistantMessage(
            id = "a-${System.currentTimeMillis()}",
            role = MessageRole.ASSISTANT,
            content = "Response to: $userText",
            timestamp = Instant.now(),
            intentType = IntentType.LATEST_SLEEP,
            wasLocalHandled = true
        )

        val currentList = _messages.value.toMutableList()
        currentList.add(userMsg)
        currentList.add(assistantMsg)
        _messages.value = currentList

        emit(assistantMsg)
    }

    override suspend fun clearConversation(): Result<Unit> {
        _messages.value = emptyList()
        return Result.success(Unit)
    }

    override fun resetDiagnostics() {
        _diagnostics.value = AssistantDiagnostics()
    }

    override fun invalidateCache(domain: com.example.somnus.domain.assistant.DataReferenceDomain?) {
        // no-op for fake
    }
}
