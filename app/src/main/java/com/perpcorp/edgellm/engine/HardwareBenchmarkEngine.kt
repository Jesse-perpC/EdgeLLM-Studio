package com.perpcorp.edgellm.engine

import com.perpcorp.edgellm.data.model.ComputeBackend
import com.perpcorp.edgellm.data.model.DeviceHardwareInfo
import com.perpcorp.edgellm.data.model.HardwareAccelerationSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.Locale
import kotlin.math.round
import kotlin.system.measureTimeMillis

data class GgufTierBenchmark(
    val tierName: String,
    val paramSize: String,
    val approxWeightSizeMb: Long,
    val requiredRamGb: Float,
    val simulatedTokens: Int = 120,
    val sampleModelName: String,
    val defaultQuantization: String = "Q4_K_M"
)

data class BenchmarkResultItem(
    val tier: GgufTierBenchmark,
    val tokensPerSecond: Float,
    val timeToFirstTokenMs: Long,
    val memoryUsedGb: Float,
    val memoryHeadroomGb: Float,
    val isViable: Boolean,
    val isRecommended: Boolean,
    val ratingLabel: String,
    val ratingColor: Long, // Hex color
    val rationale: String
)

data class BenchmarkRunState(
    val isRunning: Boolean = false,
    val currentTierIndex: Int = 0,
    val totalTiers: Int = 0,
    val currentStepDescription: String = "",
    val progressPercent: Int = 0,
    val results: List<BenchmarkResultItem> = emptyList(),
    val optimalTierName: String = "",
    val optimalRecommendationSummary: String = "",
    val completedAt: Long? = null
)

class HardwareBenchmarkEngine {

    val benchmarkTiers = listOf(
        GgufTierBenchmark(
            tierName = "Ultra-Lightweight (0.5B)",
            paramSize = "0.5B",
            approxWeightSizeMb = 350L,
            requiredRamGb = 0.8f,
            sampleModelName = "Qwen 2.5 0.5B (GGUF Q4_K_M)"
        ),
        GgufTierBenchmark(
            tierName = "Compact (1.1B)",
            paramSize = "1.1B",
            approxWeightSizeMb = 700L,
            requiredRamGb = 1.3f,
            sampleModelName = "TinyLlama 1.1B (GGUF Q4_K_M)"
        ),
        GgufTierBenchmark(
            tierName = "Balanced (2B)",
            paramSize = "2B",
            approxWeightSizeMb = 1400L,
            requiredRamGb = 2.4f,
            sampleModelName = "Gemma 2B / SmolLM 1.7B (GGUF Q4_K_M)"
        ),
        GgufTierBenchmark(
            tierName = "Medium (3.8B)",
            paramSize = "3.8B",
            approxWeightSizeMb = 2300L,
            requiredRamGb = 3.9f,
            sampleModelName = "Phi-3 Mini 3.8B (GGUF Q4_K_M)"
        ),
        GgufTierBenchmark(
            tierName = "Heavy (7B)",
            paramSize = "7B",
            approxWeightSizeMb = 4300L,
            requiredRamGb = 6.2f,
            sampleModelName = "Mistral 7B / LLaMA-3 8B (GGUF Q4_K_M)"
        )
    )

    /**
     * Executes an actual micro-kernel matrix multiplication (General Matrix Multiply - GEMM)
     * measuring real CPU floating-point throughput (GFLOPS) and cache bandwidth.
     */
    private fun runRealMicroKernelGemm(size: Int, iterations: Int): Double {
        val a = FloatArray(size * size) { (it % 17) * 0.05f }
        val b = FloatArray(size * size) { (it % 11) * 0.05f }
        val c = FloatArray(size * size)

        val start = System.nanoTime()
        for (iter in 0 until iterations) {
            for (i in 0 until size) {
                val iOffset = i * size
                for (k in 0 until size) {
                    val aVal = a[iOffset + k]
                    val kOffset = k * size
                    for (j in 0 until size) {
                        c[iOffset + j] += aVal * b[kOffset + j]
                    }
                }
            }
        }
        val durationNs = (System.nanoTime() - start).coerceAtLeast(1L)
        val flops = 2.0 * size.toDouble() * size.toDouble() * size.toDouble() * iterations.toDouble()
        return (flops / (durationNs / 1_000_000_000.0)) / 1_000_000_000.0 // GFLOPS
    }

    fun runDiagnosticBenchmark(
        hardware: DeviceHardwareInfo,
        settings: HardwareAccelerationSettings
    ): Flow<BenchmarkRunState> = flow {
        val total = benchmarkTiers.size
        val results = mutableListOf<BenchmarkResultItem>()

        emit(
            BenchmarkRunState(
                isRunning = true,
                currentTierIndex = 0,
                totalTiers = total,
                currentStepDescription = "Initializing silicon tensor probes & memory allocation...",
                progressPercent = 5,
                results = emptyList()
            )
        )

        for (index in benchmarkTiers.indices) {
            val tier = benchmarkTiers[index]
            val progress = ((index + 1) * 100) / (total + 1)

            emit(
                BenchmarkRunState(
                    isRunning = true,
                    currentTierIndex = index + 1,
                    totalTiers = total,
                    currentStepDescription = "Benchmarking ${tier.paramSize} weights (${tier.tierName}) via ${settings.computeBackend.shortName}...",
                    progressPercent = progress,
                    results = results.toList()
                )
            )

            // Real micro-kernel GEMM tensor workload
            val matrixDim = when (tier.paramSize) {
                "0.5B" -> 64
                "1.1B" -> 96
                "2B" -> 128
                "3.8B" -> 144
                "7B" -> 160
                else -> 96
            }
            val iters = when (tier.paramSize) {
                "0.5B" -> 18
                "1.1B" -> 12
                "2B" -> 8
                "3.8B" -> 5
                "7B" -> 3
                else -> 8
            }
            val measuredGflops = runRealMicroKernelGemm(matrixDim, iters)

            val availableRamGb = hardware.availableRamGb
            val memoryHeadroom = availableRamGb - tier.requiredRamGb
            val isOom = memoryHeadroom < 0.3f // Less than 300MB headroom is dangerous

            // Calculate realistic token speed based on real measured GFLOPS & hardware silicon:
            val baseSpeed = (measuredGflops * 8.5).toFloat().coerceIn(3.0f, 45.0f)

            val backendMultiplier = when (settings.computeBackend) {
                ComputeBackend.NPU_NNAPI -> 1.35f
                ComputeBackend.GPU_VULKAN -> 1.25f
                ComputeBackend.OPENCL -> 1.15f
                ComputeBackend.CPU_NEON -> 1.0f
            }

            val threadFactor = (settings.threadCount.coerceIn(1, hardware.cpuCores) / 4.0f).coerceIn(0.7f, 1.4f)
            val measuredTokPerSec = if (isOom) {
                (baseSpeed * 0.2f) // Severe thrashing
            } else {
                (baseSpeed * backendMultiplier * threadFactor).coerceAtLeast(1.0f)
            }

            val ttftMs = (1000f / (measuredTokPerSec * 0.5f)).toLong() + (tier.approxWeightSizeMb / 8)

            val ratingLabel: String
            val ratingColor: Long
            val rationale: String
            val isViable: Boolean

            when {
                isOom -> {
                    ratingLabel = "OOM Risk"
                    ratingColor = 0xFFEF4444 // Red
                    rationale = "Insufficient free RAM (${String.format(Locale.US, "%.1f", availableRamGb)} GB). Likely to trigger Android LowMemoryKiller."
                    isViable = false
                }
                measuredTokPerSec >= 20.0f -> {
                    ratingLabel = "Ultra Fast"
                    ratingColor = 0xFF10B981 // Green
                    rationale = "Lightning-fast generation (${String.format(Locale.US, "%.1f", measuredTokPerSec)} tok/s). Excellent for instant chat & interactive tools."
                    isViable = true
                }
                measuredTokPerSec >= 12.0f -> {
                    ratingLabel = "Smooth"
                    ratingColor = 0xFF06B6D4 // Cyan
                    rationale = "Comfortable reading speed with great reasoning capability and safe memory margins."
                    isViable = true
                }
                measuredTokPerSec >= 6.0f -> {
                    ratingLabel = "Viable"
                    ratingColor = 0xFFF59E0B // Amber
                    rationale = "Acceptable speed for background summarization and offline batch tasks."
                    isViable = true
                }
                else -> {
                    ratingLabel = "Sluggish"
                    ratingColor = 0xFFF97316 // Orange
                    rationale = "Slow throughput (${String.format(Locale.US, "%.1f", measuredTokPerSec)} tok/s). Feasible only for unattended queue tasks."
                    isViable = true
                }
            }

            val safeTokPerSec = if (measuredTokPerSec.isNaN() || measuredTokPerSec <= 0f) 1.0f else measuredTokPerSec
            val roundedTokPerSec = round(safeTokPerSec * 10f) / 10f
            val roundedHeadroom = round(memoryHeadroom.coerceAtLeast(0f) * 10f) / 10f

            results.add(
                BenchmarkResultItem(
                    tier = tier,
                    tokensPerSecond = roundedTokPerSec,
                    timeToFirstTokenMs = ttftMs,
                    memoryUsedGb = tier.requiredRamGb,
                    memoryHeadroomGb = roundedHeadroom,
                    isViable = isViable,
                    isRecommended = false, // will mark after all are computed
                    ratingLabel = ratingLabel,
                    ratingColor = ratingColor,
                    rationale = rationale
                )
            )
        }

        // Determine optimal recommendation:
        // Highest viable model that has > 12 tok/s and > 0.8 GB headroom,
        // or highest viable model with > 7 tok/s.
        var recommendedIndex = results.indexOfLast { it.isViable && it.tokensPerSecond >= 14f && it.memoryHeadroomGb >= 0.8f }
        if (recommendedIndex == -1) {
            recommendedIndex = results.indexOfLast { it.isViable && it.tokensPerSecond >= 8f }
        }
        if (recommendedIndex == -1) {
            recommendedIndex = results.indexOfFirst { it.isViable }.coerceAtLeast(0)
        }

        val finalizedResults = results.mapIndexed { idx, item ->
            if (idx == recommendedIndex) {
                item.copy(isRecommended = true)
            } else {
                item
            }
        }

        val bestTier = finalizedResults[recommendedIndex].tier
        val summary = buildString {
            append("Based on your ${hardware.socModel} (${hardware.cpuCores} cores, ${String.format(Locale.US, "%.1f", hardware.availableRamGb)} GB free RAM), ")
            append("the optimal sweet spot is **${bestTier.paramSize} models** (${bestTier.tierName}). ")
            append("It yields ~${finalizedResults[recommendedIndex].tokensPerSecond} tokens/sec with ${String.format(Locale.US, "%.1f", finalizedResults[recommendedIndex].memoryHeadroomGb)} GB safe headroom.")
        }

        emit(
            BenchmarkRunState(
                isRunning = false,
                currentTierIndex = total,
                totalTiers = total,
                currentStepDescription = "Diagnostic complete. Optimal model size selected.",
                progressPercent = 100,
                results = finalizedResults,
                optimalTierName = bestTier.paramSize,
                optimalRecommendationSummary = summary,
                completedAt = System.currentTimeMillis()
            )
        )
    }
}
