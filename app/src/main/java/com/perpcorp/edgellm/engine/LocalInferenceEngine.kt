package com.perpcorp.edgellm.engine

import android.util.Log
import com.perpcorp.edgellm.data.model.AiPersona
import com.perpcorp.edgellm.data.model.ComputeBackend
import com.perpcorp.edgellm.data.model.GenerationParameters
import com.perpcorp.edgellm.data.model.HardwareAccelerationSettings
import com.perpcorp.edgellm.data.model.KnowledgeDocument
import com.perpcorp.edgellm.data.model.ModelCategory
import com.perpcorp.edgellm.data.model.ModelSpec
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.Locale

data class StreamTokenChunk(
    val token: String,
    val accumulatedText: String,
    val tokenCount: Int,
    val tokensPerSecond: Float,
    val timeToFirstTokenMs: Long,
    val isComplete: Boolean,
    val backendUsed: String,
    val speculativeSpeedup: Float = 1.0f,
    val speculativeAcceptedTokens: Int = 0,
    val kvCacheSavedPercent: Float = 0f,
    val isPrefixCacheHit: Boolean = false,
    val isTurboBoost: Boolean = true,
    val samplerName: String = "Min-P (0.05)",
    val grammarModeUsed: GrammarMode = GrammarMode.NONE,
    val trustScore: Float = 0.95f,
    val factualAccuracyScore: Float = 0.95f,
    val sentimentToneScore: Float = 0.95f,
    val topicAdherenceScore: Float = 0.95f,
    val wasMultiAgentRefined: Boolean = false,
    val critiqueSummary: String? = null,
    val critiqueReasons: List<String> = emptyList()
)

class LocalInferenceEngine(private val context: android.content.Context? = null) {

    private companion object {
        const val TAG = "LocalInferenceEngine"
    }

    val geminiClient by lazy { GeminiInferenceClient() }
    val mediaPipeEngine by lazy { MediaPipeInferenceEngine(context) }
    val androidAICoreEngine by lazy { AndroidAICoreEngine(context) }
    val llamaCppEngine by lazy { LlamaCppEngine(context) }
    val onnxLlmEngine by lazy { OnnxLlmEngine() }
    val tfliteClassifierEngine by lazy { TfliteClassifierEngine() }

    fun generateStreamingResponse(
        prompt: String,
        model: ModelSpec,
        settings: HardwareAccelerationSettings,
        params: GenerationParameters,
        persona: AiPersona? = null,
        attachedDoc: KnowledgeDocument? = null,
        attachedImageUri: String? = null,
        attachedImageLabel: String? = null,
        recalledMemories: List<String> = emptyList(),
        loraAdapter: com.perpcorp.edgellm.data.model.LoraAdapter? = null,
        isAirGapped: Boolean = false
    ): Flow<StreamTokenChunk> = flow {
        val startTime = System.currentTimeMillis()

        val isTurbo = params.isTurboBoost || settings.turboBoostMode

        val docTokens = (attachedDoc?.tokenCountEstimate ?: 0)
        val imageTokens = if (attachedImageUri != null || attachedImageLabel != null) 256 else 0
        val memoryTokens = recalledMemories.sumOf { it.length / 4 }
        val promptTokens = prompt.split(" ", "\n").filter { it.isNotBlank() }.size.coerceAtLeast(1) + docTokens + imageTokens + memoryTokens

        val (prefixEntry, isPrefixHit) = if (settings.enablePrefixCaching && params.enablePrefixCaching) {
            PrefixKVCacheManager.getOrComputePrefix(model.id, params.systemPrompt, persona?.id)
        } else Pair(null, false)

        val ttft = System.currentTimeMillis() - startTime

        val effectivePrompt = if (recalledMemories.isNotEmpty()) {
            "### Contextual Persistent Memories (Recalled via 128-D Vector Cosine Similarity):\n" +
                recalledMemories.joinToString("\n") { "• $it" } +
                "\n\nUser Question:\n$prompt"
        } else {
            prompt
        }

        val nonGbnfStrictPrompt = OfflineKnowledgeEngine.wrapStrictPromptTemplate(effectivePrompt)

        val strictExecutionPrompt = if (isAirGapped || params.temperature == 0.0f) {
            OfflineKnowledgeEngine.wrapStrictPrompt(effectivePrompt)
        } else {
            effectivePrompt
        }

        // 1. Android AICore / Gemini Nano (real on-device via Google Generative AI client).
        // Requires Android 14+, AI Core system service, and model downloaded via Play Services.
        // Falls back to KB if unavailable — availability checked inside the engine.
        if (model.format == com.perpcorp.edgellm.data.model.ModelFormat.ANDROID_AICORE) {
            androidAICoreEngine.generateStreamingResponse(
                prompt = nonGbnfStrictPrompt,
                systemInstruction = params.systemPrompt,
                model = model,
                persona = persona
            ).collect { chunk ->
                val displayText = sanitizeNonGbnfChunk(chunk.accumulatedText, prompt, effectivePrompt, nonGbnfStrictPrompt)
                emit(
                    StreamTokenChunk(
                        token = chunk.token,
                        accumulatedText = displayText,
                        tokenCount = chunk.tokenCount,
                        tokensPerSecond = chunk.tokensPerSecond,
                        timeToFirstTokenMs = chunk.timeToFirstTokenMs,
                        isComplete = chunk.isComplete,
                        backendUsed = chunk.backendUsed,
                        speculativeSpeedup = 1.0f,
                        speculativeAcceptedTokens = 0,
                        kvCacheSavedPercent = 0f,
                        isPrefixCacheHit = false,
                        isTurboBoost = params.isTurboBoost,
                        samplerName = "Gemini Nano (AI Core)",
                        grammarModeUsed = GrammarMode.NONE
                    )
                )
            }
            return@flow
        }

        if (model.format == com.perpcorp.edgellm.data.model.ModelFormat.MEDIAPIPE_TASK ||
            (model.format == com.perpcorp.edgellm.data.model.ModelFormat.TFLITE &&
                model.category == ModelCategory.CHAT_REASONING)) {
            val mpDelegate = when (settings.computeBackend) {
                ComputeBackend.CPU_NEON -> MediaPipeInferenceEngine.MediaPipeDelegate.CPU
                else -> MediaPipeInferenceEngine.MediaPipeDelegate.GPU
            }
            val mpOptions = MediaPipeInferenceEngine.MediaPipeLlmOptions(
                modelPath = model.localFilePath,
                maxTokens = params.maxNewTokens,
                topK = params.topK,
                temperature = params.temperature,
                delegate = mpDelegate,
                loraPath = null,
                supportedLoraRank = loraAdapter?.rank ?: 8
            )
            mediaPipeEngine.generateStreamingResponse(nonGbnfStrictPrompt, mpOptions, model, persona).collect { chunk ->
                val loraBadge = if (chunk.loraRank != null) " (LoRA r=${chunk.loraRank})" else ""
                val displayText = sanitizeNonGbnfChunk(chunk.accumulatedText, prompt, effectivePrompt, nonGbnfStrictPrompt)
                val backend = if (chunk.usedFallback) {
                    "MediaPipe (weights unavailable)"
                } else {
                    "MediaPipe GenAI • ${chunk.delegateUsed.displayName}$loraBadge"
                }
                emit(
                    StreamTokenChunk(
                        token = chunk.token,
                        accumulatedText = displayText,
                        tokenCount = chunk.tokenCount,
                        tokensPerSecond = chunk.tokensPerSecond,
                        timeToFirstTokenMs = chunk.timeToFirstTokenMs,
                        isComplete = chunk.isComplete,
                        backendUsed = backend,
                        speculativeSpeedup = 1.0f,
                        speculativeAcceptedTokens = 0,
                        kvCacheSavedPercent = 0f,
                        isPrefixCacheHit = false,
                        isTurboBoost = params.isTurboBoost,
                        samplerName = "Top-K (${params.topK}) temp=${params.temperature}",
                        grammarModeUsed = GrammarMode.NONE
                    )
                )
            }
            return@flow
        }

        if (model.format == com.perpcorp.edgellm.data.model.ModelFormat.MNN_LLM) {
            Log.e(TAG, "MNN_LLM requested, but no MNN-LLM native runtime is linked")
            emit(
                StreamTokenChunk(
                    token = "",
                    accumulatedText = unavailableMessage(
                        model,
                        "The MNN-LLM native runtime is not linked into this build."
                    ),
                    tokenCount = 0,
                    tokensPerSecond = 0f,
                    timeToFirstTokenMs = 0L,
                    isComplete = true,
                    backendUsed = "Unavailable",
                    samplerName = "Unavailable",
                    grammarModeUsed = GrammarMode.NONE
                )
            )
            return@flow
        }

        if (model.format == com.perpcorp.edgellm.data.model.ModelFormat.GGUF &&
            (attachedImageUri != null || attachedImageLabel != null)) {
            emit(
                StreamTokenChunk(
                    token = "",
                    accumulatedText = unavailableMessage(
                        model,
                        "The loaded GGUF is a text model and cannot see images. " +
                            "Load a multimodal GGUF together with its vision projector to analyse images."
                    ),
                    tokenCount = 0,
                    tokensPerSecond = 0f,
                    timeToFirstTokenMs = 0L,
                    isComplete = true,
                    backendUsed = "GGUF (text-only)",
                    samplerName = "N/A",
                    grammarModeUsed = GrammarMode.NONE
                )
            )
            return@flow
        }

        if (model.format == com.perpcorp.edgellm.data.model.ModelFormat.GGUF) {
            val useGbnf = params.grammarMode == GrammarMode.GBNF_STRICT_FACTUAL
            var emittedAny = false
            var failure: Throwable? = null
            try {
                llamaCppEngine.streamLlamaCppResponse(
                    prompt = effectivePrompt,
                    model = model,
                    settings = settings,
                    params = params,
                    useGbnfGrammar = useGbnf
                ).collect { chunk ->
                    emittedAny = true
                    if (useGbnf) {
                        emit(chunk)
                    } else {
                        val displayText = sanitizeNonGbnfChunk(chunk.accumulatedText, prompt, effectivePrompt, nonGbnfStrictPrompt)
                        emit(chunk.copy(accumulatedText = displayText))
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "GGUF inference failed for ${model.localFilePath}", t)
                failure = t
            }
            if (!emittedAny) {
                val reason = failure?.message?.takeIf { it.isNotBlank() }
                    ?: when {
                        model.localFilePath.isBlank() ->
                            "no model file configured (localFilePath is blank)"
                        !java.io.File(model.localFilePath).isFile ->
                            "model file not found: ${model.localFilePath}"
                        else ->
                            "native decode produced no output " +
                                "(UnsatisfiedLinkError means libllama-android.so is missing; " +
                                "otherwise see logcat for $TAG)"
                    }
                emit(
                    StreamTokenChunk(
                        token = "",
                        accumulatedText = "GGUF inference failed: $reason",
                        tokenCount = 0,
                        tokensPerSecond = 0f,
                        timeToFirstTokenMs = 0L,
                        isComplete = true,
                        backendUsed = "GGUF (failed)",
                        samplerName = "N/A",
                        grammarModeUsed = GrammarMode.NONE
                    )
                )
            }
            return@flow
        }

        if (model.format == com.perpcorp.edgellm.data.model.ModelFormat.ONNX) {
            onnxLlmEngine.streamOnnxResponse(
                prompt = effectivePrompt,
                model = model,
                settings = settings,
                params = params
            ).collect { chunk -> emit(chunk) }
            return@flow
        }

        if (model.format == com.perpcorp.edgellm.data.model.ModelFormat.TFLITE &&
            model.category == ModelCategory.EMBEDDINGS_CLASSIFICATION) {
            tfliteClassifierEngine.classifyFlow(
                text = effectivePrompt,
                model = model,
                settings = settings
            ).collect { chunk -> emit(chunk) }
            return@flow
        }

        if (params.grammarMode == GrammarMode.GBNF_STRICT_FACTUAL &&
            model.format == com.perpcorp.edgellm.data.model.ModelFormat.GGUF) {
            llamaCppEngine.streamLlamaCppResponse(
                prompt = effectivePrompt,
                model = model,
                settings = settings,
                params = params,
                useGbnfGrammar = true
            ).collect { chunk ->
                val displayText = if (!params.enforceJsonSchema && chunk.accumulatedText.contains("\"answer\":")) {
                    try {
                        val json = org.json.JSONObject(chunk.accumulatedText)
                        json.optString("answer", chunk.accumulatedText)
                    } catch (_: Exception) {
                        chunk.accumulatedText.substringAfter("\"answer\":").substringAfter("\"").substringBeforeLast("\"")
                    }
                } else chunk.accumulatedText
                emit(chunk.copy(accumulatedText = displayText))
            }
            return@flow
        }

        // Every format with a real runtime returned above. If execution reaches
        // here there is no engine that can answer, so say that plainly instead
        // of fabricating a plausible-sounding response from a lookup table.
        val reason = when {
            model.localFilePath.isBlank() -> "the model file path is empty"
            !java.io.File(model.localFilePath).isFile -> "the model file is missing at ${model.localFilePath}"
            else -> "no inference backend handles ${model.format.displayName}"
        }
        val honestFailure = unavailableMessage(
            model,
            "No on-device engine produced a response because $reason. " +
                "Download or import a supported model to get real answers."
        )
        val (responseText, backendUsed, isOfflineFallback) =
            Triple(honestFailure, "Unavailable", true)

        val specResult = if (!isOfflineFallback &&
            (settings.enableSpeculativeDecoding || isTurbo)) {
            SpeculativeDecodingEngine.evaluateSpeculativeBatch(
                draftModel = null,
                targetModel = model,
                lookaheadK = if (isTurbo) (settings.speculativeLookaheadTokens + 2).coerceAtMost(8) else settings.speculativeLookaheadTokens,
                temperature = params.temperature,
                isTurboBoost = isTurbo
            )
        } else null

        val speedupMultiplier = specResult?.metrics?.effectiveSpeedupMultiplier ?: 1.0f
        val kvProfile = if (!isOfflineFallback && settings.enableAttentionSinksStreamingLLM) {
            AttentionSinkManager.calculateCacheProfile(
                totalTurnTokens = promptTokens + 280,
                windowSize = settings.streamingLlmWindowTokens
            )
        } else null

        val effectiveTurbo = isTurbo && !isOfflineFallback
        val effectivePrefixHit = isPrefixHit && !isOfflineFallback

        val cleanCheck = OutputVerificationEngine.verifyAndCleanOutput(responseText, isKnowledgeBase = isOfflineFallback)
        val textToProcess = if (!cleanCheck.isValid) cleanCheck.cleanedText else responseText

        val verification = OutputVerificationEngine.verifyAndRefine(
            rawOutput = textToProcess,
            query = prompt,
            enableThinkingMode = params.enableThinkingMode
        )
        val verifiedResponseText = verification.verifiedText
        val tokens = tokenizeResponse(verifiedResponseText)

        val stringBuilder = StringBuilder()
        var tokenCount = 0

        for (token in tokens) {
            tokenCount++
            stringBuilder.append(token)

            val elapsedSec = (System.currentTimeMillis() - startTime) / 1000f
            val currentTps = if (elapsedSec > 0.05f) {
                tokenCount / elapsedSec
            } else {
                0f
            }

            emit(
                StreamTokenChunk(
                    token = token,
                    accumulatedText = stringBuilder.toString(),
                    tokenCount = tokenCount,
                    tokensPerSecond = ((currentTps * 10).toInt() / 10f),
                    timeToFirstTokenMs = ttft,
                    isComplete = false,
                    backendUsed = backendUsed,
                    speculativeSpeedup = speedupMultiplier,
                    speculativeAcceptedTokens = specResult?.acceptedTokensThisPass ?: 0,
                    kvCacheSavedPercent = kvProfile?.compressionRatioPercent ?: 0f,
                    isPrefixCacheHit = effectivePrefixHit,
                    isTurboBoost = effectiveTurbo,
                    samplerName = "Min-P (${params.minP})",
                    grammarModeUsed = params.grammarMode
                )
            )

            delay(16L)
        }

        val totalElapsedSec = ((System.currentTimeMillis() - startTime) / 1000f).coerceAtLeast(0.1f)
        emit(
            StreamTokenChunk(
                token = "",
                accumulatedText = stringBuilder.toString(),
                tokenCount = tokenCount,
                tokensPerSecond = ((tokenCount / totalElapsedSec) * 10).toInt() / 10f,
                timeToFirstTokenMs = ttft,
                isComplete = true,
                backendUsed = backendUsed,
                speculativeSpeedup = speedupMultiplier,
                speculativeAcceptedTokens = specResult?.acceptedTokensThisPass ?: 0,
                kvCacheSavedPercent = kvProfile?.compressionRatioPercent ?: 0f,
                isPrefixCacheHit = effectivePrefixHit,
                isTurboBoost = effectiveTurbo,
                samplerName = "Min-P (${params.minP})",
                grammarModeUsed = params.grammarMode,
                trustScore = verification.trustScore,
                factualAccuracyScore = verification.factualAccuracyScore,
                sentimentToneScore = verification.sentimentToneScore,
                topicAdherenceScore = verification.topicAdherenceScore,
                wasMultiAgentRefined = verification.wasMultiAgentRefined,
                critiqueSummary = verification.multiAgentCritiqueSummary ?: if (verification.correctionsApplied.isNotEmpty()) verification.correctionsApplied.joinToString("; ") else null,
                critiqueReasons = verification.correctionsApplied + verification.verificationFlags
            )
        )
    }

    /**
     * Non-streaming helper for code paths that cannot collect a Flow.
     *
     * This never invents an answer. It either executes a deterministic tool
     * call, returns real recalled data, quotes an attached document, or runs
     * the actual GGUF engine. Otherwise it reports that nothing ran.
     */
    fun generateOfflineIntelligence(
        prompt: String,
        model: ModelSpec,
        params: GenerationParameters,
        persona: AiPersona? = null,
        attachedDoc: KnowledgeDocument? = null,
        attachedImageUri: String? = null,
        attachedImageLabel: String? = null,
        recalledMemories: List<String> = emptyList(),
        loraAdapter: com.perpcorp.edgellm.data.model.LoraAdapter? = null
    ): String {
        val lower = prompt.trim().lowercase(Locale.US)

        if (params.enableToolCalling) {
            val toolCall = OnDeviceToolEngine.parseToolCallFromPrompt(prompt)
            if (toolCall != null) {
                return toolCall.outputResult
            }
        }

        if (attachedImageUri != null || attachedImageLabel != null) {
            return unavailableMessage(
                model,
                "image understanding needs a multimodal vision model, and the loaded checkpoint is ${model.format.displayName}"
            )
        }

        if (prompt.contains("[SCREEN_CONTEXT]")) {
            val screenText = prompt.substringAfter("[SCREEN_CONTEXT]").substringBefore("[/SCREEN_CONTEXT]").trim()
            val userQuestion = prompt.substringAfter("[/SCREEN_CONTEXT]").trim().ifBlank { "Summarize this on-screen content." }
            val lines = screenText.lines().filter { it.isNotBlank() }.take(5)
            val entityBullets = lines.joinToString("\n") { "• ${it.take(80)}" }
            return "For \"$userQuestion\":\n\n$entityBullets"
        }

        val isMemoryQuery = lower.contains("what do you remember") || lower.contains("do you remember") ||
            lower.contains("my preference") || lower.contains("my memory") || lower.contains("remember me")
        if (isMemoryQuery) {
            return if (recalledMemories.isNotEmpty()) {
                recalledMemories.joinToString("\n") { "• $it" }
            } else {
                "No saved memories matched this query."
            }
        }

        val hasWeights = model.localFilePath.isNotBlank() && java.io.File(model.localFilePath).isFile
        if (model.format == com.perpcorp.edgellm.data.model.ModelFormat.GGUF && hasWeights) {
            val factual = llamaCppEngine.generateStrictFactualResponse(prompt, model)
            return when (params.grammarMode) {
                GrammarMode.GBNF_STRICT_FACTUAL, GrammarMode.JSON_STRICT -> factual.toJsonString()
                else -> factual.answer
            }
        }

        if (attachedDoc != null) {
            val docPreview = attachedDoc.content.take(400).replace("\n", " ")
            return "From `${attachedDoc.title}`:\n\n" +
                "- **Summary:** ${attachedDoc.summary}\n" +
                "> \"$docPreview...\""
        }

        val reason = when {
            !hasWeights -> "its weight file is not present at ${model.localFilePath.ifBlank { "(empty path)" }}"
            else -> "no on-device runtime is available for ${model.format.displayName}"
        }
        return unavailableMessage(
            model,
            "$reason. No answer was fabricated; download or import a supported model and retry."
        )
    }

    private fun unavailableMessage(model: ModelSpec, detail: String): String =
        "**No response generated**\n\n${model.name} (${model.format.displayName}) did not run: $detail"

    fun clearPrefixCache() {
        PrefixKVCacheManager.clearCache()
    }

    private fun sanitizeNonGbnfChunk(
        accumulatedText: String,
        originalPrompt: String,
        effectivePrompt: String,
        strictPrompt: String
    ): String {
        var display = accumulatedText
        if (display.contains(strictPrompt)) {
            display = display.replace(strictPrompt, originalPrompt)
        }
        if (display.contains(effectivePrompt) && effectivePrompt != originalPrompt) {
            display = display.replace(effectivePrompt, originalPrompt)
        }
        display = display
            .replace("<|im_start|>system", "")
            .replace("<|im_start|>user", "")
            .replace("<|im_start|>assistant", "")
            .replace("<|im_end|>", "")
            .replace("[SYSTEM_INSTRUCTION]", "")
            .replace("[/SYSTEM_INSTRUCTION]", "")
            .replace("[USER_QUERY]", "")
            .replace("[/USER_QUERY]", "")
            .trim()
        return OutputVerificationEngine.verifyAndSanitizeText(display, isKnowledgeBase = false)
    }

    fun buildLlamaServerCommandArgs(
        modelPath: String,
        grammarPath: String? = null,
        port: Int = 8080
    ): List<String> {
        return mutableListOf<String>().apply {
            add("./llama-server")
            add("-m")
            add(modelPath)
            add("--port")
            add(port.toString())
            add("--temp")
            add("0.0")

            grammarPath?.let {
                add("--grammar-file")
                add(it)
            }
        }
    }

    fun startLocalEngineInstance(
        modelPath: String,
        grammarPath: String? = null,
        port: Int = 8080
    ): ProcessBuilder {
        val commandArgs = buildLlamaServerCommandArgs(modelPath, grammarPath, port)
        return ProcessBuilder(commandArgs)
    }

    fun tokenizeResponse(text: String): List<String> =
        text.split(Regex("\\s+")).filter { it.isNotBlank() }
}
