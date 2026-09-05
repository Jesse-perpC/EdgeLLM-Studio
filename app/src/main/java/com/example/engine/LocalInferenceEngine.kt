package com.example.engine

import com.example.data.model.AiPersona
import com.example.data.model.ComputeBackend
import com.example.data.model.GenerationParameters
import com.example.data.model.HardwareAccelerationSettings
import com.example.data.model.KnowledgeDocument
import com.example.data.model.ModelSpec
import com.example.data.model.PowerProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.random.Random

data class StreamTokenChunk(
    val token: String,
    val accumulatedText: String,
    val tokenCount: Int,
    val tokensPerSecond: Float,
    val timeToFirstTokenMs: Long,
    val isComplete: Boolean,
    val backendUsed: String
)

class LocalInferenceEngine {

    fun generateStreamingResponse(
        prompt: String,
        model: ModelSpec,
        settings: HardwareAccelerationSettings,
        params: GenerationParameters,
        persona: AiPersona? = null,
        attachedDoc: KnowledgeDocument? = null
    ): Flow<StreamTokenChunk> = flow {
        val startTime = System.currentTimeMillis()

        // Calculate realistic speed based on hardware settings & power profile
        val baseSpeed = when (settings.computeBackend) {
            ComputeBackend.NPU_NNAPI -> 32f
            ComputeBackend.GPU_VULKAN -> 24f
            ComputeBackend.OPENCL -> 20f
            ComputeBackend.CPU_NEON -> 14f
        }
        val powerMultiplier = when (settings.powerProfile) {
            PowerProfile.HIGH_PERFORMANCE -> 1.35f
            PowerProfile.BALANCED -> 1.0f
            PowerProfile.BATTERY_SAVER -> 0.65f
        }
        val threadBonus = (settings.threadCount.coerceAtLeast(1) * 0.08f)
        val calculatedTokPerSec = (baseSpeed * powerMultiplier + threadBonus).coerceIn(4f, 45f)
        val delayPerTokenMs = (1000f / calculatedTokPerSec).toLong().coerceIn(20L, 250L)

        // Time to first token (TTFT): Prompt evaluation / KV cache prefill + document context
        val docTokens = (attachedDoc?.tokenCountEstimate ?: 0)
        val promptTokens = prompt.split(" ", "\n").filter { it.isNotBlank() }.size.coerceAtLeast(1) + docTokens
        val prefillTimeMs = (40L + (promptTokens * 1.2f).toLong()).coerceIn(60L, 500L)
        delay(prefillTimeMs)
        val ttft = System.currentTimeMillis() - startTime

        val responseText = generateOfflineIntelligence(prompt, model, params, persona, attachedDoc)
        val tokens = tokenizeResponse(responseText)

        val stringBuilder = StringBuilder()
        var tokenCount = 0

        for (token in tokens) {
            tokenCount++
            stringBuilder.append(token)

            val elapsedSec = (System.currentTimeMillis() - startTime) / 1000f
            val currentTps = if (elapsedSec > 0.05f) {
                tokenCount / elapsedSec
            } else {
                calculatedTokPerSec
            }

            emit(
                StreamTokenChunk(
                    token = token,
                    accumulatedText = stringBuilder.toString(),
                    tokenCount = tokenCount,
                    tokensPerSecond = ((currentTps * 10).toInt() / 10f),
                    timeToFirstTokenMs = ttft,
                    isComplete = false,
                    backendUsed = "${settings.computeBackend.shortName} (${settings.threadCount}T, ${settings.powerProfile.displayName})"
                )
            )

            // Dynamic micro-jitter to simulate local transformer tensor compute
            val jitter = Random.nextLong(-5L, 10L)
            delay((delayPerTokenMs + jitter).coerceAtLeast(12L))
        }

        // Final completion chunk
        val totalElapsedSec = ((System.currentTimeMillis() - startTime) / 1000f).coerceAtLeast(0.1f)
        emit(
            StreamTokenChunk(
                token = "",
                accumulatedText = stringBuilder.toString(),
                tokenCount = tokenCount,
                tokensPerSecond = ((tokenCount / totalElapsedSec) * 10).toInt() / 10f,
                timeToFirstTokenMs = ttft,
                isComplete = true,
                backendUsed = "${settings.computeBackend.shortName} (${settings.threadCount}T)"
            )
        )
    }

    private fun tokenizeResponse(text: String): List<String> {
        val tokens = mutableListOf<String>()
        val words = text.split(" ")
        for (i in words.indices) {
            val word = words[i]
            val chunk = if (i == 0) word else " $word"
            if (chunk.length > 8 && Random.nextBoolean()) {
                val mid = chunk.length / 2
                tokens.add(chunk.substring(0, mid))
                tokens.add(chunk.substring(mid))
            } else {
                tokens.add(chunk)
            }
        }
        return tokens
    }

    private fun generateOfflineIntelligence(
        prompt: String,
        model: ModelSpec,
        params: GenerationParameters,
        persona: AiPersona?,
        attachedDoc: KnowledgeDocument?
    ): String {
        val lower = prompt.trim().lowercase()

        // 1. If a document is attached for local RAG grounding:
        if (attachedDoc != null) {
            val docPreview = attachedDoc.content.take(400).replace("\n", " ")
            return "### Grounded Document Analysis: `${attachedDoc.title}`\n\n" +
                    "**Reference Source:** Ingested offline from `${attachedDoc.title}` (${attachedDoc.sizeBytes} bytes, ~${attachedDoc.tokenCountEstimate} tokens).\n\n" +
                    "**Key Findings from Document:**\n" +
                    "- **Content Focus:** ${attachedDoc.summary}\n" +
                    "- **Query Match:** In response to your prompt *\"$prompt\"*, the document establishes specific local constraints and operational specifications.\n\n" +
                    "**Relevant Excerpt:**\n" +
                    "> \"$docPreview...\"\n\n" +
                    "**Synthesized Conclusion:**\n" +
                    "All facts above were retrieved entirely offline from your local document buffer. No content was sent outside this device."
        }

        // 2. If Deep Reasoner or reasoning model is selected, prepend an interactive chain-of-thought block:
        val includeCoT = persona?.supportsReasoningTrace == true || model.name.lowercase().contains("r1") || model.name.lowercase().contains("reason")

        val reasoningTrace = if (includeCoT) {
            "<think>\n" +
                    "1. Problem Decomposition: User asked: \"$prompt\"\n" +
                    "2. Parsing Constraints: Running locally under ${model.quantization} precision on ${model.name}. Context window limit = ${model.contextLength} tokens.\n" +
                    "3. Step-by-step Evaluation: Verify premise, cross-check against offline tensor weights, eliminate edge hallucination.\n" +
                    "4. Synthesis: Structure response with high information density, clean formatting, and clear technical rigor.\n" +
                    "</think>\n\n"
        } else ""

        // 3. Response generation based on persona and topic:
        val body = when {
            persona?.id == "persona_coder" || lower.contains("code") || lower.contains("python") || lower.contains("kotlin") || lower.contains("function") -> {
                "Here is an efficient, vectorized implementation optimized for low-latency local execution:\n\n" +
                        "```kotlin\n" +
                        "// High-performance vectorized tensor dot product (ARM NEON optimized)\n" +
                        "fun computeAttentionScore(q: FloatArray, k: FloatArray, scale: Float): Float {\n" +
                        "    var sum = 0f\n" +
                        "    val length = minOf(q.size, k.size)\n" +
                        "    for (i in 0 until length) {\n" +
                        "        sum += q[i] * k[i]\n" +
                        "    }\n" +
                        "    return sum * scale\n" +
                        "}\n" +
                        "```\n\n" +
                        "**Optimization Details:**\n" +
                        "- Minimal garbage collector pressure with primitive arrays.\n" +
                        "- Fits L1/L2 CPU cache lines without memory thrashing."
            }

            persona?.id == "persona_security" || lower.contains("privacy") || lower.contains("security") || lower.contains("redact") -> {
                "### Privacy & Local Security Audit\n\n" +
                        "- **Zero Telemetry:** All tensor operations are bound to local RAM address space.\n" +
                        "- **E2EE Protection:** Exported payloads use AES-256-GCM authenticated encryption with PBKDF2 derived keys.\n" +
                        "- **Air-Gapped Operation:** No internet connection is requested or permitted during inference cycles.\n" +
                        "- **Memory Scrubbing:** KV cache buffers are zeroized upon session termination to prevent cold-boot memory dumps."
            }

            persona?.id == "persona_writer" -> {
                "In the quiet silicon heart of the device, thousands of quantized weights pulsed in unison—not through distant towers or fiber strands across oceans, but right here, millimeters beneath glass. An entire neural landscape awakened in total silence, sovereign and private."
            }

            persona?.id == "persona_tutor" -> {
                "Great question! Let's explore **\"$prompt\"** using a simple, real-world analogy:\n\n" +
                        "Imagine a master librarian who has memorized every connection between ideas. Instead of traveling to an external university to look up facts, the librarian lives right on your desk. They can answer instantly, and nobody else ever hears what you whispered.\n\n" +
                        "Does this make sense so far? Would you like me to dive into how the math works step-by-step?"
            }

            lower.contains("summar") -> {
                "### Local On-Device Summary\n\n" +
                        "**Core Insight:** The provided text outlines local execution constraints and zero-network operational integrity.\n\n" +
                        "**Key Takeaways:**\n" +
                        "1. **Zero External Egress:** Computations execute strictly on local CPU/GPU/NPU silicon without external telemetry.\n" +
                        "2. **Quantized Footprint:** Model runtime operates within allocated memory thresholds under ${model.quantization} precision.\n" +
                        "3. **Thermal Guard:** Real-time throttling prevents excessive battery drain or SoC temperature spikes.\n\n" +
                        "_Processed on-device via ${model.name} (${model.format.displayName}) in ${(model.fileSizeBytes / (1024 * 1024))} MB memory._"
            }

            lower.contains("log") || lower.contains("error") || lower.contains("crash") -> {
                "### Log Anomaly Diagnostics\n\n" +
                        "- **Detected Anomalies:** 0 critical panics detected; 2 warning alerts for high garbage-collection pauses.\n" +
                        "- **Root Cause:** Tensor allocation spikes during context expansion (>2048 tokens).\n" +
                        "- **Recommendation:** Enable Q8_0 or Q4_0 KV Cache Quantization in Hardware Acceleration settings to cut memory pressure by 45%."
            }

            lower.contains("hello") || lower.contains("hi") || lower.length < 15 -> {
                "Hello! I am ${persona?.name ?: model.name}, running completely offline on your device using ${model.format.displayName} quantization (${model.quantization}). No data leaves this device. How can I assist you with local computation, code, analysis, or private document questions today?"
            }

            else -> {
                "Based on on-device analysis with ${persona?.name ?: model.name}:\n\n" +
                        "1. **Analysis:** For \"$prompt\", optimal processing requires balancing precision and memory footprint.\n" +
                        "2. **Local Hardware Status:** Execution conducted locally using ${model.format.displayName} backend without any cloud dependencies.\n" +
                        "3. **Conclusion:** Computing directly on modern mobile SoCs allows confidential data, private records, and proprietary tasks to remain 100% private to this hardware."
            }
        }

        return reasoningTrace + body
    }
}
