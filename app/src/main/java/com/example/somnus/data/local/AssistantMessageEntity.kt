package com.example.somnus.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Room entity representing an exchange in the local AI Assistant conversation.
 *
 * Guarantees:
 * 1. Strictly local persistence: Never synced to external servers or remote telemetry.
 * 2. Privacy-preserving: Contains no extraneous device identifiers or credentials.
 * 3. Token-aware: Tracks input and output token counts for transparency and local diagnostics.
 */
@Entity(
    tableName = "assistant_messages",
    indices = [
        Index("timestampEpochMs")
    ]
)
data class AssistantMessageEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val role: String, // "USER", "ASSISTANT", "SYSTEM"
    val content: String,
    val timestampEpochMs: Long = System.currentTimeMillis(),
    val wasLocalHandled: Boolean = false,
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val latencyMs: Long = 0L,
    val intentType: String? = null,
    val referencesJson: String = "[]",
    val isError: Boolean = false
)
