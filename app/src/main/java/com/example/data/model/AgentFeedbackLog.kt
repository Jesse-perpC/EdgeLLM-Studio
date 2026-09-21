package com.example.data.model

data class AgentFeedbackLog(
    val id: String,
    val messageId: String,
    val sessionId: String = "default_session",
    val prompt: String,
    val candidateResponse: String,
    val factualAccuracyScore: Float,
    val sentimentToneScore: Float,
    val topicAdherenceScore: Float,
    val overallTrustScore: Float,
    val requiresRefinement: Boolean,
    val wasRefined: Boolean,
    val critiqueReasons: List<String> = emptyList(),
    val userRating: Int = 0, // -1 = thumbs down, 0 = unrated, 1 = thumbs up
    val userFeedbackText: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
