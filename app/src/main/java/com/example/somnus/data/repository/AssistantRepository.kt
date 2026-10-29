package com.example.somnus.data.repository

import com.example.somnus.domain.assistant.AssistantDiagnostics
import com.example.somnus.domain.assistant.AssistantMessage
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface coordinating local AI Assistant interactions, conversation history,
 * token budgeting, and diagnostics.
 *
 * Guarantees:
 * 1. 100% Local-First: Zero remote data transmissions, zero telemetry SDKs.
 * 2. Deterministic Routing: Standard data lookups completely bypass model inference.
 * 3. Token-Bounded Context: Keeps prompts strictly under 1,000 tokens.
 * 4. Room Persistence: Stores conversation history in local SQLite database.
 */
interface AssistantRepository {

    /**
     * Observes the active conversation turns ordered chronologically.
     */
    val conversationMessages: Flow<List<AssistantMessage>>

    /**
     * Observes real-time token usage and efficiency diagnostics.
     */
    val diagnosticsFlow: Flow<AssistantDiagnostics>

    /**
     * Observes whether cloud inference is currently active and reachable.
     */
    val isCloudInferenceActive: Flow<Boolean> get() = kotlinx.coroutines.flow.flowOf(false)

    /**
     * Submits a user inquiry, runs deterministic routing, safety checks, optional compact context synthesis,
     * and streams the progressive response chunks.
     */
    suspend fun processQuery(userText: String): Flow<AssistantMessage>

    /**
     * Permanently deletes conversation history from local Room storage and clears in-memory caches.
     */
    suspend fun clearConversation(): Result<Unit>

    /**
     * Resets local diagnostics metrics.
     */
    fun resetDiagnostics()

    /**
     * Invalidates cache entries for a specific health domain (e.g. on new records) or all entries if domain is null.
     */
    fun invalidateCache(domain: com.example.somnus.domain.assistant.DataReferenceDomain? = null)
}
