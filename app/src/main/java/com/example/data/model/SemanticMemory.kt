package com.example.data.model

enum class MemoryType(val displayName: String, val emoji: String) {
    USER_PREFERENCE("User Preference", "💜"),
    FACT("Knowledge Fact", "🔷"),
    EPISODIC("Episodic Event", "🟢"),
    CONVERSATION_SUMMARY("Session Summary", "🟡")
}

data class SemanticMemoryRecord(
    val id: String,
    val sessionId: String = "default_session",
    val sourceMessageId: String? = null,
    val memoryType: MemoryType = MemoryType.FACT,
    val subject: String,
    val content: String,
    val embeddingVector: List<Float> = emptyList(),
    val embeddingDimension: Int = 128,
    val importanceScore: Float = 0.5f,
    val recallCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val lastRecalledAt: Long = System.currentTimeMillis()
)

data class SemanticSearchResult(
    val memory: SemanticMemoryRecord,
    val similarityScore: Float,
    val matchedExcerpt: String
)

data class ConversationSession(
    val id: String,
    val title: String,
    val contextSummary: String = "",
    val activePersonaId: String = "persona_general",
    val messageCount: Int = 0,
    val totalTokens: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val lastActiveAt: Long = System.currentTimeMillis()
)
