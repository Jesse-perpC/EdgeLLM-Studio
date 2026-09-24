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
    val category: String = "Reasoning & Logic",
    val modelAId: String,
    val modelAName: String,
    val modelAFormat: String,
    val modelAParameters: String = "2.0B",
    val modelAQuant: String = "Q4_K_M",
    val modelAComputeBackend: String = "Vulkan 1.3 GPU",
    val modelBId: String,
    val modelBName: String,
    val modelBFormat: String,
    val modelBParameters: String = "1.5B",
    val modelBQuant: String = "INT4 MNN",
    val modelBComputeBackend: String = "Hexagon NPU",
    val isBlindMode: Boolean = true,
    var modelAResponse: String = "",
    var modelATtftMs: Long = 0L,
    var modelATps: Float = 0f,
    var modelATotalTimeMs: Long = 0L,
    var modelBResponse: String = "",
    var modelBTtftMs: Long = 0L,
    var modelBTps: Float = 0f,
    var modelBTotalTimeMs: Long = 0L,
    var streamProgressA: Float = 0f,
    var streamProgressB: Float = 0f,
    var isBattling: Boolean = false,
    var userVote: ArenaWinner? = null,
    var eloDeltaA: Int = 0,
    var eloDeltaB: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

enum class ArenaWinner(val displayName: String) {
    MODEL_A("Model Alpha Won"),
    MODEL_B("Model Beta Won"),
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
    val avgTokensPerSec: Float,
    val tierBadge: String = "Master",
    val primaryAccelerator: String = "NPU / GPU"
)

data class ArenaChallengePrompt(
    val id: String,
    val title: String,
    val category: String,
    val prompt: String,
    val difficulty: String, // "Novice", "Hardcore", "Grandmaster"
    val iconEmoji: String
)
