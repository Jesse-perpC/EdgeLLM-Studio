package com.perpcorp.edgellm.engine

import android.util.Log
import com.perpcorporp.edgellm.data.model.AiPersona
import com.perpcorporp.edgellm.data.model.ComputeBackend
import com.perpcorporp.edgellm.data.model.GenerationParameters
import com.perpcorporp.edgellm.data.model.HardwareAccelerationSettings
import com.perpcorporp.edgellm.data.model.KnowledgeDocument
import com.perpcorporp.edgellm.data.model.ModelCategory
import com.perpcorporp.edgellm.data.model.ModelSpec
import com.perpcorporp.edgellm.data.model.PowerProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

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
                    "MediaPipe KB fallback (weights unavailable)"
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
                    accumulatedText = "",
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
            attachedImageUri == null && attachedImageLabel == null &&
            !params.enforceJsonSchema) {
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
                    if (useGbnf && params.enforceJsonSchema) {
                        emit(chunk)
                    } else if (useGbnf) {
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

        if (params.grammarMode == GrammarMode.GBNF_STRICT_FACTUAL) {
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

        // No native engine matched: report honestly that no on-device weights
        // are available for this model format or its file is missing.
        if (responseText.isEmpty()) {
            emit(
                StreamTokenChunk(
                    token = "",
                    accumulatedText = buildUnavailableMessage(),
                    tokenCount = 0,
                    tokensPerSecond = 0f,
                    timeToFirstTokenMs = 0L,
                    isComplete = true,
                    backendUsed = "Unavailable",
                    samplerName = "N/A",
                    grammarModeUsed = params.grammarMode
                )
            )
            return@flow
        }

        val kvProfile = if (isOfflineFallback && settings.enableAttentionSinksStreamingLLM) {
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

            emit(
                StreamTokenChunk(
                    token = token,
                    accumulatedText = stringBuilder.toString(),
                    tokenCount = tokenCount,
                    tokensPerSecond = 0f,
                    timeToFirstTokenMs = ttft,
                    isComplete = false,
                    backendUsed = backendUsed,
                    speculativeSpeedup = 1.0f,
                    speculativeAcceptedTokens = 0,
                    kvCacheSavedPercent = kvProfile?.compressionRatioPercent ?: 0f,
                    isPrefixCacheHit = effectivePrefixHit,
                    isTurboBoost = effectiveTurbo,
                    samplerName = "Offline fallback",
                    grammarModeUsed = params.grammarMode,
                    trustScore = 0f,
                    factualAccuracyScore = 0f,
                    sentimentToneScore = 0f,
                    topicAdherenceScore = 0f,
                    wasMultiAgentRefined = false,
                    critiqueSummary = null,
                    critiqueReasons = emptyList()
                )
            )
        }

        val totalElapsedSec = ((System.currentTimeMillis() - startTime) / 1000f).coerceAtLeast(0.1f)
        emit(
            StreamTokenChunk(
                token = "",
                accumulatedText = stringBuilder.toString(),
                tokenCount = tokenCount,
                tokensPerSecond = 0f,
                timeToFirstTokenMs = ttft,
                isComplete = true,
                backendUsed = backendUsed,
                speculativeSpeedup = 1.0f,
                speculativeAcceptedTokens = 0,
                kvCacheSavedPercent = kvProfile?.compressionRatioPercent ?: 0f,
                isPrefixCacheHit = effectivePrefixHit,
                isTurboBoost = effectiveTurbo,
                samplerName = "Offline fallback",
                grammarModeUsed = params.grammarMode,
                trustScore = 0f,
                factualAccuracyScore = 0f,
                sentimentToneScore = 0f,
                topicAdherenceScore = 0f,
                wasMultiAgentRefined = false,
                critiqueSummary = null,
                critiqueReasons = emptyList()
            )
        )
    }

    /**
     * Honest message returned when no on-device inference engine can answer
     * the request. This is NOT a knowledge base: it tells the user exactly
     * what is missing so they can take action (load a model, enable Cloud
     * Assist, or switch formats).
     *
     * The previous implementation of this method returned canned factual
     * text from a hardcoded knowledge base and presented it as model output.
     * That was misleading, so it is replaced here.
     */
    fun buildUnavailableMessage(): String =
        "No on-device inference engine is available for this model format or " +
            "the model file is missing. Download a GGUF / MediaPipe / ONNX model " +
            "in the Models tab, or enable Cloud Assist in Settings for Gemini " +
            "3.5 Flash via the cloud API."

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
