package com.example.data.model

data class RagChunkItem(
    val id: String,
    val documentTitle: String,
    val chunkIndex: Int,
    val totalChunks: Int,
    val sha256ContentHash: String,
    val content: String,
    val tokenCount: Int,
    val embeddingDimension: Int = 128,
    val cosineSimilarityScore: Float = 0f,
    val retrievalLatencyMs: Long = 8L,
    val sourceFormat: String = "PDF",
    val characterSpanStart: Int = 0,
    val characterSpanEnd: Int = 0
)

data class RagDebugQueryResult(
    val query: String,
    val executionTimeMs: Long,
    val thresholdUsed: Float,
    val totalCandidateChunks: Int,
    val matchedChunks: List<RagChunkItem>
)
