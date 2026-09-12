package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val sender: String,
    val text: String,
    val timestamp: Long,
    val tokensGenerated: Int,
    val tokensPerSecond: Float,
    val timeToFirstTokenMs: Long,
    val executionBackend: String,
    val modelId: String,
    val imageUri: String? = null,
    val imageLabel: String? = null,
    val sessionId: String = "default_session",
    val thoughtTrace: String? = null,
    val conversationalContext: String? = null,
    val embeddingVectorJson: String? = null,
    val importanceScore: Float = 0.5f,
    val semanticTags: String? = null
)

@Entity(tableName = "conversation_sessions")
data class ConversationSessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val contextSummary: String = "",
    val activePersonaId: String = "persona_general",
    val messageCount: Int = 0,
    val totalTokens: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val lastActiveAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "semantic_memories")
data class SemanticMemoryEntity(
    @PrimaryKey val id: String,
    val sessionId: String = "default_session",
    val sourceMessageId: String? = null,
    val memoryType: String = "FACT",
    val subject: String,
    val content: String,
    val embeddingVector: String,
    val embeddingDimension: Int = 128,
    val importanceScore: Float = 0.5f,
    val recallCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val lastRecalledAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "background_jobs")
data class BackgroundJobEntity(
    @PrimaryKey val id: String,
    val title: String,
    val jobType: String,
    val inputData: String,
    val status: String,
    val progressPercent: Int,
    val resultSummary: String,
    val errorMessage: String?,
    val createdAt: Long,
    val completedAt: Long?,
    val tokensProcessed: Int,
    val executionTimeMs: Long
)

@Entity(tableName = "encrypted_exports")
data class EncryptedExportEntity(
    @PrimaryKey val id: String,
    val title: String,
    val sourceType: String,
    val plainSizeBytes: Long,
    val cipherSizeBytes: Long,
    val algorithm: String,
    val sha256Hash: String,
    val ivBase64: String,
    val ciphertextPreview: String,
    val cloudTarget: String,
    val temporaryShareUrl: String?,
    val temporaryShareToken: String?,
    val temporaryShareExpiresAt: Long?,
    val isUploaded: Boolean,
    val createdAt: Long
)
