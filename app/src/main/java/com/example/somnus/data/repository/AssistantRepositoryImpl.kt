package com.example.somnus.data.repository

import com.example.somnus.data.local.AssistantMessageDao
import com.example.somnus.data.local.AssistantMessageEntity
import com.example.somnus.domain.assistant.AssistantCache
import com.example.somnus.domain.assistant.AssistantDiagnostics
import com.example.somnus.domain.assistant.AssistantMessage
import com.example.somnus.domain.assistant.AssistantModelProvider
import com.example.somnus.domain.assistant.AssistantResponseParser
import com.example.somnus.domain.assistant.AssistantSafetyPolicy
import com.example.somnus.domain.assistant.ContextBuilder
import com.example.somnus.domain.assistant.DataReference
import com.example.somnus.domain.assistant.DataReferenceDomain
import com.example.somnus.domain.assistant.IntentRouter
import com.example.somnus.domain.assistant.IntentType
import com.example.somnus.domain.assistant.LocalDataQueryService
import com.example.somnus.domain.assistant.MessageRole
import com.example.somnus.domain.assistant.QuerySafetyResult
import com.example.somnus.domain.assistant.TokenBudgetConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Production implementation of [AssistantRepository].
 *
 * Coordinates intelligent intent routing, deterministic local data resolution,
 * token budgeting, caching, and Room persistence.
 */
class AssistantRepositoryImpl(
    private val dao: AssistantMessageDao,
    private val localDataQueryService: LocalDataQueryService,
    private val contextBuilder: ContextBuilder,
    private val modelProvider: AssistantModelProvider,
    private val assistantCache: AssistantCache,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AssistantRepository {

    private val _diagnostics = MutableStateFlow(AssistantDiagnostics())
    override val diagnosticsFlow: Flow<AssistantDiagnostics> = _diagnostics.asStateFlow()

    override val conversationMessages: Flow<List<AssistantMessage>> =
        dao.observeAllMessages().map { entities ->
            entities.map { it.toDomain() }
        }.flowOn(ioDispatcher)

    override suspend fun processQuery(userText: String): Flow<AssistantMessage> = flow {
        val trimmedQuery = userText.trim()
        if (trimmedQuery.isBlank()) return@flow

        val currentDate = LocalDate.now()
        val queryStartTime = System.currentTimeMillis()

        // 1. Insert user message into Room
        val userEntity = AssistantMessageEntity(
            id = UUID.randomUUID().toString(),
            role = MessageRole.USER.name,
            content = trimmedQuery,
            timestampEpochMs = queryStartTime,
            wasLocalHandled = true,
            inputTokens = TokenBudgetConfig.estimateTokens(trimmedQuery)
        )
        dao.insertMessage(userEntity)

        updateDiagnostics { copy(totalRequests = totalRequests + 1) }

        // 2. Safety Interception: Refuse medical or pharmaceutical advice
        when (val safetyResult = AssistantSafetyPolicy.checkUserQuerySafety(trimmedQuery)) {
            is QuerySafetyResult.Prohibited -> {
                val assistantMsg = AssistantMessage(
                    role = MessageRole.ASSISTANT,
                    content = safetyResult.refusalReason,
                    wasLocalHandled = true,
                    intentType = IntentType.OUT_OF_SCOPE_MEDICAL
                )
                dao.insertMessage(assistantMsg.toEntity())
                updateDiagnostics { copy(localHandledRequests = localHandledRequests + 1) }
                emit(assistantMsg)
                return@flow
            }
            QuerySafetyResult.Safe -> { /* proceed */ }
        }

        // 3. Cache lookup
        val cacheKey = assistantCache.createKey(trimmedQuery, currentDate, "v1")
        val cached = assistantCache.get(cacheKey)
        if (cached != null) {
            updateDiagnostics { copy(cacheHits = cacheHits + 1, localHandledRequests = localHandledRequests + 1) }
            val assistantMsg = AssistantMessage(
                role = MessageRole.ASSISTANT,
                content = cached.response,
                wasLocalHandled = true,
                dataReferences = cached.dataReferences
            )
            dao.insertMessage(assistantMsg.toEntity())
            emit(assistantMsg)
            return@flow
        } else {
            updateDiagnostics { copy(cacheMisses = cacheMisses + 1) }
        }

        // 4. Intelligent Request Routing
        val routed = IntentRouter.route(trimmedQuery, currentDate)

        if (routed.isLocal) {
            // Handled without language model
            val queryResult = localDataQueryService.resolveLocalQuery(routed)
            val sanitized = AssistantSafetyPolicy.sanitizeResponse(queryResult.response)

            val latency = System.currentTimeMillis() - queryStartTime
            val assistantMsg = AssistantMessage(
                role = MessageRole.ASSISTANT,
                content = sanitized.safeContent,
                wasLocalHandled = true,
                latencyMs = latency,
                intentType = routed.intentType,
                dataReferences = queryResult.dataReferences
            )

            // Cache deterministic local result
            assistantCache.put(
                key = cacheKey,
                response = sanitized.safeContent,
                dataReferences = queryResult.dataReferences
            )

            dao.insertMessage(assistantMsg.toEntity())
            updateDiagnostics {
                copy(
                    localHandledRequests = localHandledRequests + 1,
                    totalLatencyMs = totalLatencyMs + latency
                )
            }

            emit(assistantMsg)
            return@flow
        }

        // 5. Complex Synthesis: Compact Context Construction & Model Provider
        updateDiagnostics { copy(modelInvocations = modelInvocations + 1) }

        val recentMessages = dao.getAllMessagesSync().takeLast(4).map { it.toDomain() }
        val promptContext = contextBuilder.buildPromptContext(trimmedQuery, recentMessages, currentDate)

        updateDiagnostics { copy(totalInputTokens = totalInputTokens + promptContext.estimatedInputTokens) }

        val messageId = UUID.randomUUID().toString()
        var finalContent = ""
        var finalOutputTokens = 0

        try {
            modelProvider.generateResponse(promptContext).collect { chunk ->
                finalContent = chunk.text
                finalOutputTokens = chunk.outputTokensUsed

                val inFlightMessage = AssistantMessage(
                    id = messageId,
                    role = MessageRole.ASSISTANT,
                    content = finalContent,
                    wasLocalHandled = false,
                    inputTokens = promptContext.estimatedInputTokens,
                    outputTokens = finalOutputTokens,
                    intentType = routed.intentType,
                    dataReferences = promptContext.dataReferences
                )
                emit(inFlightMessage)
            }

            val latency = System.currentTimeMillis() - queryStartTime
            val parsed = AssistantResponseParser.parseAndValidate(finalContent, promptContext.dataReferences)
            val sanitized = AssistantSafetyPolicy.sanitizeResponse(parsed.text)

            val finalMessage = AssistantMessage(
                id = messageId,
                role = MessageRole.ASSISTANT,
                content = sanitized.safeContent,
                wasLocalHandled = false,
                inputTokens = promptContext.estimatedInputTokens,
                outputTokens = finalOutputTokens,
                latencyMs = latency,
                intentType = routed.intentType,
                dataReferences = parsed.dataReferences,
                isError = parsed.isError
            )

            assistantCache.put(
                key = cacheKey,
                response = sanitized.safeContent,
                dataReferences = parsed.dataReferences
            )

            dao.insertMessage(finalMessage.toEntity())
            updateDiagnostics {
                copy(
                    totalOutputTokens = totalOutputTokens + finalOutputTokens,
                    totalLatencyMs = totalLatencyMs + latency
                )
            }

            emit(finalMessage)

        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - queryStartTime
            updateDiagnostics { copy(fallbackCount = fallbackCount + 1, totalLatencyMs = totalLatencyMs + latency) }

            // Safe fallback response without throwing or crashing
            val fallbackMsg = AssistantMessage(
                id = messageId,
                role = MessageRole.ASSISTANT,
                content = "I could not complete model synthesis on this device. However, your data remains safe locally:\n\n${promptContext.compactDataContext}",
                wasLocalHandled = true,
                latencyMs = latency,
                intentType = routed.intentType,
                dataReferences = promptContext.dataReferences,
                isError = false
            )
            dao.insertMessage(fallbackMsg.toEntity())
            emit(fallbackMsg)
        }
    }.flowOn(ioDispatcher)

    override suspend fun clearConversation(): Result<Unit> = withContext(ioDispatcher) {
        try {
            dao.deleteAll()
            assistantCache.clear()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun resetDiagnostics() {
        _diagnostics.value = AssistantDiagnostics()
    }

    private fun updateDiagnostics(transform: AssistantDiagnostics.() -> AssistantDiagnostics) {
        _diagnostics.value = _diagnostics.value.transform()
    }

    private fun AssistantMessageEntity.toDomain(): AssistantMessage {
        val domainIntent = intentType?.let { name ->
            try {
                IntentType.valueOf(name)
            } catch (e: Exception) {
                null
            }
        }

        return AssistantMessage(
            id = id,
            role = try {
                MessageRole.valueOf(role)
            } catch (e: Exception) {
                MessageRole.ASSISTANT
            },
            content = content,
            timestamp = Instant.ofEpochMilli(timestampEpochMs),
            wasLocalHandled = wasLocalHandled,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            latencyMs = latencyMs,
            intentType = domainIntent,
            dataReferences = deserializeReferences(referencesJson),
            isError = isError
        )
    }

    private fun AssistantMessage.toEntity(): AssistantMessageEntity {
        return AssistantMessageEntity(
            id = id,
            role = role.name,
            content = content,
            timestampEpochMs = timestamp.toEpochMilli(),
            wasLocalHandled = wasLocalHandled,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            latencyMs = latencyMs,
            intentType = intentType?.name,
            referencesJson = serializeReferences(dataReferences),
            isError = isError
        )
    }

    private fun serializeReferences(references: List<DataReference>): String {
        if (references.isEmpty()) return "[]"
        return references.joinToString(";") { ref ->
            "${ref.domain.name}|${ref.label}|${ref.value}|${ref.provenance ?: ""}"
        }
    }

    private fun deserializeReferences(raw: String): List<DataReference> {
        if (raw.isBlank() || raw == "[]") return emptyList()
        return raw.split(";").mapNotNull { item ->
            val parts = item.split("|")
            if (parts.size >= 3) {
                val domain = try {
                    DataReferenceDomain.valueOf(parts[0])
                } catch (e: Exception) {
                    DataReferenceDomain.SYSTEM
                }
                val label = parts[1]
                val value = parts[2]
                val prov = if (parts.size >= 4 && parts[3].isNotBlank()) parts[3] else null
                DataReference(domain, label, value, prov)
            } else null
        }
    }
}
