package com.example.data.model

import java.util.UUID

/**
 * Model Arena Battle Evaluation Record
 * Allows blind / named side-by-side battle comparisons between two on-device models
 * evaluating latency, tokens/second, time-to-first-token, response quality, and win rating.
 */
data class ModelArenaMatch(
    val id: String = UUID.randomUUID().toString(),
    val prompt: String,
    val category: String = "Reasoning & Knowledge",
    val modelAId: String,
    val modelAName: String,
    val modelAFormat: String,
    val modelBId: String,
    val modelBName: String,
    val modelBFormat: String,
    val isBlindMode: Boolean = true,
    var modelAResponse: String = "",
    var modelATtftMs: Long = 0L,
    var modelATps: Float = 0f,
    var modelATotalTimeMs: Long = 0L,
    var modelBResponse: String = "",
    var modelBTtftMs: Long = 0L,
    var modelBTps: Float = 0f,
    var modelBTotalTimeMs: Long = 0L,
    var isBattling: Boolean = false,
    var userVote: ArenaWinner? = null,
    val timestamp: Long = System.currentTimeMillis()
)

enum class ArenaWinner(val displayName: String) {
    MODEL_A("Model A Won"),
    MODEL_B("Model B Won"),
    TIE("Both are Equal"),
    BOTH_BAD("Both are Bad")
}

data class ArenaLeaderboardEntry(
    val modelId: String,
    val modelName: String,
    val format: String,
    val eloRating: Int,
    val wins: Int,
    val losses: Int,
    val ties: Int,
    val avgTokensPerSec: Float
)
