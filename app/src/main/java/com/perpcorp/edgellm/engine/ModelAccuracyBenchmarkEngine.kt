package com.perpcorp.edgellm.engine

import com.perpcorp.edgellm.data.model.ModelSpec

data class AccuracyBenchmarkResult(
    val modelName: String,
    val overallAccuracyScore: Float,
    val gsm8kScore: Float,
    val humanEvalScore: Float,
    val mmluScore: Float,
    val averageTurboSpeedTokPerSec: Float,
    val prefillLatencyMs: Long,
    val passesAllFidelityGates: Boolean = true,
    val timestamp: Long = System.currentTimeMillis(),
    val isEstimated: Boolean = true,
    val source: String = "Model catalog preset (not measured on this device)"
)

data class BenchmarkStage(
    val stageName: String,
    val progress: Float,
    val detail: String
)

/**
 * Model capability estimates derived from the catalog metadata attached to a
 * [ModelSpec].
 *
 * This intentionally does not run GSM8K, HumanEval, or MMLU. Those suites
 * require model weights, a tokenizer, and a harness. Returning the catalog
 * values with an explicit [AccuracyBenchmarkResult.isEstimated] flag keeps the
 * UI honest instead of fabricating an on-device evaluation.
 */
object ModelAccuracyBenchmarkEngine {

    suspend fun runFullCapabilityBenchmark(
        model: ModelSpec,
        isTurboBoost: Boolean = true,
        onStageUpdate: (BenchmarkStage) -> Unit
    ): AccuracyBenchmarkResult {
        onStageUpdate(
            BenchmarkStage(
                stageName = "Catalog estimates",
                progress = 1.0f,
                detail = "Reading published benchmark metadata; nothing was executed on-device."
            )
        )

        val gsm8k = model.benchmarkGsm8kScore.coerceIn(0f, 100f)
        val humanEval = model.benchmarkHumanEvalScore.coerceIn(0f, 100f)
        val mmlu = model.benchmarkMmluScore.coerceIn(0f, 100f)
        val overall = (gsm8k * 0.40f) + (humanEval * 0.35f) + (mmlu * 0.25f)
        val overallRounded = ((overall * 10).toInt()) / 10f
        val turboSpeed = if (isTurboBoost) model.maxTurboTokPerSec else model.maxTurboTokPerSec * 0.45f

        return AccuracyBenchmarkResult(
            modelName = model.name,
            overallAccuracyScore = overallRounded,
            gsm8kScore = ((gsm8k * 10).toInt()) / 10f,
            humanEvalScore = ((humanEval * 10).toInt()) / 10f,
            mmluScore = ((mmlu * 10).toInt()) / 10f,
            averageTurboSpeedTokPerSec = ((turboSpeed * 10).toInt()) / 10f,
            prefillLatencyMs = 0L,
            passesAllFidelityGates = overallRounded >= 75.0f,
            isEstimated = true,
            source = "Catalog metadata for ${model.name}"
        )
    }
}
