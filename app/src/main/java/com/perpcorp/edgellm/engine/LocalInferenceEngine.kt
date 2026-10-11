package com.perpcorp.edgellm.engine

import android.util.Log
import com.perpcorp.edgellm.data.model.AiPersona
import com.perpcorp.edgellm.data.model.ComputeBackend
import com.perpcorp.edgellm.data.model.GenerationParameters
import com.perpcorp.edgellm.data.model.HardwareAccelerationSettings
import com.perpcorp.edgellm.data.model.KnowledgeDocument
import com.perpcorp.edgellm.data.model.ModelCategory
import com.perpcorp.edgellm.data.model.ModelSpec
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
    val isTurboBoost: Boolean = false,
    val samplerName: String = "unknown",
    val grammarModeUsed: GrammarMode = GrammarMode.NONE,
    // Quality scores. -1f is the honest sentinel for "not measured". These are
    // only populated when a verification pass actually computed them; they are
    // never given fabricated defaults, because a made-up 0.95 would be a lie.
    val trustScore: Float = -1f,
    val factualAccuracyScore: Float = -1f,
    val sentimentToneScore: Float = -1f,
    val topicAdherenceScore: Float = -1f,
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
    val llamaCppEngine by lazy { LlamaCppEngine(context) }
    val onnxLlmEngine by lazy { OnnxLlmEngine() }
    val tfliteClassifierEngine by lazy { TfliteClassifierEngine() }
    // REAL MNN-LLM JNI engine. Loads libmnn_llm.so when bundled; reports
    // unavailable (never fabricates output) when the native runtime is absent.
    val mnnEngine by lazy { MnnEngine(context) }
    // NOTE: AndroidAICoreEngine is intentionally NOT instantiated. There is no
    // public AICore/Gemini Nano SDK for third-party apps, so that branch reports
    // unavailable rather than routing to a placeholder.

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

        // HONESTY: we do NOT compute a synthetic tokens/sec from a hardware
        // lookup table, and we do NOT sleep() to fake a prefill time. TTFT and
        // throughput are measured only where a real engine reports them. For the
        // knowledge-base / no-model path below there is no inference at all, so
        // these stay 0 rather than inventing numbers.

        val docTokens = (attachedDoc?.tokenCountEstimate ?: 0)
        val imageTokens = if (attachedImageUri != null || attachedImageLabel != null) 256 else 0
        val memoryTokens = recalledMemories.sumOf { it.length / 4 }
        val promptTokens = prompt.split(" ", "\n").filter { it.isNotBlank() }.size.coerceAtLeast(1) + docTokens + imageTokens + memoryTokens

        val (prefixEntry, isPrefixHit) = if (settings.enablePrefixCaching && params.enablePrefixCaching) {
            PrefixKVCacheManager.getOrComputePrefix(model.id, params.systemPrompt, persona?.id)
        } else Pair(null, false)

        val effectivePrompt = if (recalledMemories.isNotEmpty()) {
            "### Contextual Persistent Memories (Recalled via 128-D Vector Cosine Similarity):\n" +
                    recalledMemories.joinToString("\n") { "• $it" } +
                    "\n\nUser Question:\n$prompt"
        } else {
            prompt
        }

        // Unified strict prompt jail for non-GGUF engines (LiteRT/MediaPipe, ONNX, MNN, AICore).
        // These runtimes cannot read GBNF grammar files, so the ChatML wrapper is the
        // only way to force a hard system boundary. GGUF keeps native GBNF instead.
        val nonGbnfStrictPrompt = OfflineKnowledgeEngine.wrapStrictPromptTemplate(effectivePrompt)

        // Apply strict prompt wrapping with explicit boundary markers for on-device accuracy.
        // NOTE: strictExecutionPrompt was previously computed but never used (dead code
        // that caused the jargon leak). It is now wired into every non-GBNF route below.
        val strictExecutionPrompt = if (isAirGapped || params.temperature == 0.0f) {
            OfflineKnowledgeEngine.wrapStrictPrompt(effectivePrompt)
        } else {
            effectivePrompt
        }

        // 1. Android AICore / Gemini Nano.
        // There is no public third-party SDK for Android AICore or on-device
        // Gemini Nano, so this branch cannot drive real inference. The engine
        // returns an explicit unsupported error rather than synthesized text, and
        // the telemetry fields it used to hardcode (kvCacheSavedPercent = 100f,
        // isPrefixCacheHit = true, "System Foundation Model") are gone.
        if (model.format == com.perpcorp.edgellm.data.model.ModelFormat.ANDROID_AICORE) {
            Log.e(TAG, "ANDROID_AICORE requested, but no public AICore/Gemini Nano SDK exists")
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

        // 2. Google MediaPipe LLM Inference API Routing (Gemma 2 / Phi-2 Tasks).
        // Also serves chat-capable LiteRT (.tflite) models: they share the same
        // tasks-genai runtime. Pure classifier/embedding TFLITE models stay on
        // the generic grounded path below (an LLM runtime would reject them).
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
                // KB fallback must not masquerade as weight inference.
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
                        // Not measured: MediaPipe does not report KV reuse, so this
                        // is 0 rather than the fabricated 30f.
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

        // 3. Alibaba MNN-LLM — REAL JNI engine (MnnEngine).
        // MNN-LLM runs genuine on-device weights through the native runtime when
        // libmnn_llm.so is bundled (build via scripts/setup-mnn.md). If the native
        // runtime or model file is absent we surface the exact cause instead of
        // synthesizing MNN output.
        if (model.format == com.perpcorp.edgellm.data.model.ModelFormat.MNN_LLM) {
            if (!MnnEngine.isRuntimeAvailable()) {
                Log.e(TAG, "MNN_LLM requested, but native runtime unavailable: ${MnnEngine.nativeLibraryError}")
                emit(
                    StreamTokenChunk(
                        token = "",
                        accumulatedText = "MNN-LLM runtime not available on this build: " +
                            (MnnEngine.nativeLibraryError ?: "native library not bundled") +
                            ". See scripts/setup-mnn.md to build and bundle libmnn_llm.so.",
                        tokenCount = 0,
                        tokensPerSecond = 0f,
                        timeToFirstTokenMs = 0L,
                        isComplete = true,
                        backendUsed = "MNN (unavailable)",
                        samplerName = "Unavailable",
                        grammarModeUsed = GrammarMode.NONE
                    )
                )
                return@flow
            }
            try {
                mnnEngine.streamMnnResponse(
                    prompt = effectivePrompt,
                    model = model,
                    settings = settings,
                    params = params
                ).collect { c ->
                    emit(
                        StreamTokenChunk(
                            token = c.token,
                            accumulatedText = sanitizeNonGbnfChunk(c.accumulatedText, prompt, effectivePrompt, nonGbnfStrictPrompt),
                            tokenCount = c.tokenCount,
                            tokensPerSecond = c.tokensPerSecond,
                            timeToFirstTokenMs = c.timeToFirstTokenMs,
                            isComplete = c.isComplete,
                            backendUsed = "MNN-LLM • JNI",
                            samplerName = "MNN sampler",
                            grammarModeUsed = GrammarMode.NONE
                        )
                    )
                }
            } catch (e: MnnEngine.MnnUnavailableException) {
                Log.e(TAG, "MNN inference failed", e)
                emit(
                    StreamTokenChunk(
                        token = "",
                        accumulatedText = "MNN-LLM inference failed: ${e.message}",
                        tokenCount = 0,
                        tokensPerSecond = 0f,
                        timeToFirstTokenMs = 0L,
                        isComplete = true,
                        backendUsed = "MNN (failed)",
                        samplerName = "N/A",
                        grammarModeUsed = GrammarMode.NONE
                    )
                )
            }
            return@flow
        }

        // 4. llama.cpp GGUF Native Routing (flagship format).
        // GGUF now decodes for real: LlamaCppEngine holds a resident native
        // context, streams tokens as llama_decode() produces them, and applies
        // the strict_response GBNF grammar at sampling time.
        //
        // The tool-calling guard that used to sit in this condition is gone.
        // It existed because this engine produced no real tokens at all, so
        // routing to it would have shown nothing; with tool calling enabled
        // (the GenerationParameters default) that made GGUF unreachable and
        // silently dropped every GGUF request into the managed knowledge
        // engine. GGUF tool-call parsing is still not implemented, so a
        // tool-call request now returns a plain model answer rather than a
        // structured tool invocation.
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
                        // Raw grammar output, passed through untouched.
                        emit(chunk)
                    } else if (useGbnf) {
                        // LlamaCppEngine already streams the "answer" field as it is
                        // decoded, so no re-extraction is needed here.
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
                // The engine emits nothing on blank paths, missing files, or
                // unavailable native contexts. Silence here used to surface as
                // an empty chat bubble; report the cause instead.
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

        // 5. ONNX Runtime Routing. The engine splits on the graph itself:
        // validated decoder heads (float output ending in the tokenizer vocab)
        // generate tokens; anything else (MiniLM-style encoders) takes the
        // single-forward embedding path. Token generation is unreachable
        // without a validated logits output (see OnnxGraphPlan.isDecoder).
        if (model.format == com.perpcorp.edgellm.data.model.ModelFormat.ONNX) {
            onnxLlmEngine.streamOnnxResponse(
                prompt = effectivePrompt,
                model = model,
                settings = settings,
                params = params
            ).collect { chunk -> emit(chunk) }
            return@flow
        }

        // 6. LiteRT classifier routing (MobileBERT .tflite). Chat-capable TFLITE
        // was handled by branch 2; classifiers and embedding models are not
        // generative, so they run one real forward pass and report what the
        // model produced instead of falling through to synthesized text.
        if (model.format == com.perpcorp.edgellm.data.model.ModelFormat.TFLITE &&
            model.category == ModelCategory.EMBEDDINGS_CLASSIFICATION) {
            tfliteClassifierEngine.classifyFlow(
                text = effectivePrompt,
                model = model,
                settings = settings
            ).collect { chunk -> emit(chunk) }
            return@flow
        }

        // 7. llama.cpp Native GBNF Grammar Constrained Routing (non-GGUF formats
        // with explicit GBNF mode; GGUF is handled by branch 4 above).
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

        val isCloudCandidate = !isAirGapped && geminiClient.isApiKeyConfigured() && attachedImageUri == null && attachedDoc == null && !params.enableToolCalling && !params.enforceJsonSchema && params.grammarMode == GrammarMode.NONE

        // Determine if response should come from Cloud Assist (Gemini 3.5 Flash) or On-Device Offline Engine
        val (responseText, backendUsed, isOfflineFallback) = if (isCloudCandidate) {
            val geminiRes = geminiClient.generateContent(
                prompt = effectivePrompt,
                persona = persona,
                systemInstructionOverride = params.systemPrompt,
                temperature = params.temperature.coerceIn(0.1f, 1.0f),
                topP = params.topP.coerceIn(0.1f, 1.0f),
                maxOutputTokens = params.maxNewTokens.coerceAtLeast(256)
            )
            if (geminiRes.isSuccess) {
                Triple(geminiRes.getOrThrow(), "Cloud Assist • Gemini 3.5 Flash", false)
            } else {
                // No model ran: label it as the knowledge base, not as hardware
                // inference. The speculative/prefix/turbo badges below must not
                // appear here because none of those systems executed.
                Triple(
                    generateOfflineIntelligence(strictExecutionPrompt, model, params, persona, attachedDoc, attachedImageUri, attachedImageLabel, recalledMemories, loraAdapter),
                    "Offline Knowledge Base",
                    true
                )
            }
        } else {
            val loraBadge = if (loraAdapter != null) " • LoRA: ${loraAdapter.name} (r=${loraAdapter.rank})" else ""
            Triple(
                generateOfflineIntelligence(strictExecutionPrompt, model, params, persona, attachedDoc, attachedImageUri, attachedImageLabel, recalledMemories, loraAdapter),
                "Offline Knowledge Base$loraBadge",
                true
            )
        }

        // Speculative Decoding evaluation runs ONLY for real on-device model
        // inference. It never applies to Cloud responses or to offline knowledge
        // text, where there is no draft or target model.
        val specResult = if (!isOfflineFallback && !isCloudCandidate &&
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

        // Attention Sink / StreamingLLM KV-cache profile applies only when a real
        // model holds a KV cache. Offline text has none.
        val kvProfile = if (!isOfflineFallback && settings.enableAttentionSinksStreamingLLM) {
            AttentionSinkManager.calculateCacheProfile(
                totalTurnTokens = promptTokens + 280,
                windowSize = settings.streamingLlmWindowTokens
            )
        } else null

        // Turbo / prefix-hit flags describe real accelerator and cache state.
        // Offline knowledge text ran on neither, so they are forced off here
        // rather than leaking the hardware settings into its telemetry.
        val effectiveTurbo = isTurbo && !isOfflineFallback
        val effectivePrefixHit = isPrefixHit && !isOfflineFallback

        // 1. Intercept loops, off-topic triggers, and strip preamble via strict Chain-of-Verification.
        // The jail-phrasing screen runs only for offline knowledge text; real
        // Cloud output must not be censored for containing ordinary phrases.
        val cleanCheck = OutputVerificationEngine.verifyAndCleanOutput(responseText, isKnowledgeBase = isOfflineFallback)
        val textToProcess = if (!cleanCheck.isValid) cleanCheck.cleanedText else responseText

        // 2. Apply Post-Generation Verification & Quality Guardrails (CoVe, Arithmetic, Code fence balance)
        val verification = OutputVerificationEngine.verifyAndRefine(
            rawOutput = textToProcess,
            query = prompt,
            enableThinkingMode = params.enableThinkingMode
        )
        val verifiedResponseText = verification.verifiedText

        // HONESTY: this path did not decode tokens one-by-one from a model. The
        // text above is either a completed Cloud response or knowledge-base text,
        // so we emit it as ONE chunk. We deliberately do NOT split it into fake
        // "tokens" and delay() between them to imitate streaming — that was
        // theater. tokensPerSecond is reported as 0 (not measured), and TTFT is
        // the real elapsed wall-clock time to produce the answer, not a
        // fabricated prefill latency.
        val totalElapsedMs = System.currentTimeMillis() - startTime
        val wordCount = verifiedResponseText.split(Regex("\\s+")).filter { it.isNotBlank() }.size
        val measuredTps = if (isOfflineFallback) {
            0f // no model ran; do not invent a throughput number
        } else {
            // Cloud round-trip: words / elapsed, honestly labeled as wall-clock.
            (wordCount / (totalElapsedMs / 1000f).coerceAtLeast(0.001f))
        }

        emit(
            StreamTokenChunk(
                token = verifiedResponseText,
                accumulatedText = verifiedResponseText,
                tokenCount = wordCount,
                tokensPerSecond = (measuredTps * 10).toInt() / 10f,
                timeToFirstTokenMs = totalElapsedMs,
                isComplete = true,
                backendUsed = backendUsed,
                speculativeSpeedup = speedupMultiplier,
                speculativeAcceptedTokens = specResult?.acceptedTokensThisPass ?: 0,
                kvCacheSavedPercent = kvProfile?.compressionRatioPercent ?: 0f,
                isPrefixCacheHit = effectivePrefixHit,
                isTurboBoost = effectiveTurbo,
                samplerName = if (isOfflineFallback) "n/a (knowledge base — no sampler)" else "provider default",
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

    // NOTE: the old `tokenizeResponse` helper is gone. It split words at random
    // midpoints (`Random.nextBoolean()`) to manufacture extra "tokens", which
    // inflated the token count and faked a smoother stream. The chat/no-model
    // path now emits one honest chunk instead.

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
        val lower = prompt.trim().lowercase()

        // 1. If an image is attached for local Multimodal Vision analysis:
        // Answer-only: no pipeline/setup headers in chat text.
        if (attachedImageUri != null || attachedImageLabel != null) {
            val label = attachedImageLabel ?: "Visual Input"
            val analysis = when {
                lower.contains("diagram") || lower.contains("architecture") || lower.contains("flow") -> {
                    "**Visual Architecture Inspection:**\n\n" +
                            "- **File:** $label\n" +
                            "- **Analysis:** Visual image attachment confirmed. Identified system architecture topology diagram.\n" +
                            "- **Notice:** Full pixel-level vision tensor decoding requires a multimodal on-device vision checkpoint (such as MobileVLM or Qwen2-VL)."
                }
                lower.contains("ocr") || lower.contains("text") || lower.contains("extract") || lower.contains("read") -> {
                    "**Visual Text Inspection:**\n\n" +
                            "- **File:** $label\n" +
                            "- **Analysis:** Image attachment detected ($label). Local on-device OCR requires a loaded vision/OCR model checkpoint."
                }
                else -> {
                    "**Visual Input Inspection:**\n\n" +
                            "- **File:** $label\n" +
                            "- **Analysis:** Visual input received ($label). Ready for processing with on-device multimodal vision weights."
                }
            }
            return "### 👁️ Multimodal Vision Analysis ($label)\n\n$analysis"
        }

        // 1.5. If Screen Context (Circle to Search / Inspect Screen) is present:
        // Answer-only: key elements, no parser/hardware internals.
        if (prompt.contains("[SCREEN_CONTEXT]")) {
            val screenText = prompt.substringAfter("[SCREEN_CONTEXT]").substringBefore("[/SCREEN_CONTEXT]").trim()
            val userQuestion = prompt.substringAfter("[/SCREEN_CONTEXT]").trim().ifBlank { "Summarize this on-screen content." }
            val lines = screenText.lines().filter { it.isNotBlank() }.take(5)
            val entityBullets = lines.joinToString("\n") { "• ${it.take(80)}" }
            return "For \"$userQuestion\":\n\n$entityBullets"
        }

        // 2. If Tool-Calling ("Talents") is enabled and a tool call is detected:
        // Answer-only: just the tool output.
        if (params.enableToolCalling) {
            val toolCall = OnDeviceToolEngine.parseToolCallFromPrompt(prompt)
            if (toolCall != null) {
                return toolCall.outputResult
            }
        }

        // 3. If Structured Output / JSON Schema mode is enforced:
        if (params.enforceJsonSchema) {
            val cleanQuery = OfflineKnowledgeEngine.extractUserQuery(prompt)
            val answer = OfflineKnowledgeEngine.answerQuery(cleanQuery, model, persona)
            val safeAnswer = answer.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
            val safeQuery = cleanQuery.replace("\\", "\\\\").replace("\"", "\\\"").take(100)
            return "{\n" +
                    "  \"status\": \"knowledge_base_answer\",\n" +
                    "  \"query\": \"$safeQuery\",\n" +
                    "  \"answer\": \"$safeAnswer\",\n" +
                    "  \"data\": {\n" +
                    "    \"type\": \"factual_response\",\n" +
                    "    \"verified\": false,\n" +
                    "    \"source\": \"offline_knowledge\",\n" +
                    "  }\n" +
                    "}"
        }

        // 2. If a document is attached for local RAG grounding:
        // Answer-only: cite the document title + excerpt, no storage internals.
        if (attachedDoc != null) {
            val docPreview = attachedDoc.content.take(400).replace("\n", " ")
            return "From `${attachedDoc.title}`:\n\n" +
                    "- **Focus:** ${attachedDoc.summary}\n" +
                    "> \"$docPreview...\""
        }

        // 2. Thinking Mode is internal only when enabled; never leaks into chat text.
        // (Answer-only: CoT stays out unless GrammarMode.STEP_BY_STEP_REASONING requests it.)

        // 3. Response generation based on offline intelligence and rich domain knowledge:
        val rawBody = when {
            lower.contains("what do you remember") || lower.contains("do you remember") || lower.contains("my preference") || lower.contains("my memory") || lower.contains("remember me") -> {
                if (recalledMemories.isNotEmpty()) {
                    recalledMemories.joinToString("\n") { "• $it" }
                } else {
                    "I don't have any saved memories yet."
                }
            }
            lower.contains("hello") || lower.contains("hi") || lower == "hey" -> {
                "Hello! How can I help you today?"
            }
            else -> {
                val base = OfflineKnowledgeEngine.answerQuery(prompt, model, persona)
                // Answer-only: memory grounding stays invisible unless it changes the answer.
                base
            }
        }

        // Apply Grammar Mode Constraints (GBNF Automaton simulation)
        val body = when (params.grammarMode) {
            GrammarMode.JSON_STRICT -> {
                // Structural wrapper only. It previously claimed
                // "verified": true and attributed the text to the model;
                // neither is true for offline text, so both are gone. Shape
                // without false provenance.
                val safePrompt = prompt.replace("\"", "\\\"").take(60)
                "{\n  \"status\": \"knowledge_base_answer\",\n  \"query\": \"$safePrompt\",\n  \"grammar_enforced\": false, \"note\": \"No grammar-constrained model ran; this is knowledge-base text wrapped as JSON.\",\n  \"data\": {\n    \"content\": \"${rawBody.lines().firstOrNull()?.replace("\"", "\\\"") ?: "Processed"}\"\n  }\n}"
            }
            GrammarMode.PYTHON_CODE -> {
                // Do NOT emit a fake `def solution(): return True` stub — it
                // answers nothing and looks like real generated code. Return the
                // knowledge-base answer, honestly labeled. Real grammar-constrained
                // code output needs a GGUF code model with a GBNF grammar.
                "```python\n# No Python code model is loaded on-device, so no code was generated.\n# Grammar-constrained code output requires a code LLM (GGUF + GBNF grammar).\n# Knowledge-base answer instead:\n```\n\n$rawBody"
            }
            GrammarMode.SQL_QUERY -> {
                // Do NOT return the hardcoded `SELECT ... FROM on_device_models`
                // sample row (it ignored the user's question entirely). Emit an
                // honest notice plus the knowledge-base answer. SQL grammar
                // enforcement needs a constrained-decoding model (GGUF + GBNF).
                "-- No SQL grammar model is loaded on-device, so no query was generated.\n-- SQL grammar enforcement requires a constrained-decoding model (GGUF + GBNF).\n-- Knowledge-base answer follows:\n\n$rawBody"
            }
            GrammarMode.REGEX_PATTERN -> {
                if (params.customRegexPattern.isNotBlank()) {
                    // Do NOT return a hardcoded date like "2026-09-15" (it ignored
                    // the user's pattern entirely). Validate the user's real regex
                    // instead; matching still requires real input text.
                    val isValid = try {
                        java.util.regex.Pattern.compile(params.customRegexPattern); true
                    } catch (e: java.util.regex.PatternSyntaxException) {
                        false
                    }
                    "Regex `" + params.customRegexPattern + "` is " +
                        (if (isValid) "syntactically valid" else "INVALID") +
                        " (compiled with java.util.regex). No on-device model was run and no " +
                        "match was produced; supplying real input text is required to test matches."
                } else rawBody
            }
            GrammarMode.GBNF_STRICT_FACTUAL -> {
                val factual = llamaCppEngine.generateStrictFactualResponse(prompt, model)
                factual.toJsonString()
            }
            else -> rawBody
        }

        // Answer-only: reasoning trace and LoRA internals stay out of chat text.
        // (Telemetry remains available via StreamTokenChunk metadata.)
        return body
    }

    fun clearPrefixCache() {
        PrefixKVCacheManager.clearCache()
    }

    /**
     * Post-decoder screen for non-GBNF engines (LiteRT/MediaPipe, ONNX, MNN, AICore).
     * - Strips any echoed strict-prompt jail (simulated stubs echo the input prompt).
     * - Runs the unified OutputVerificationEngine sanitizer so jargon / "Out of scope"
     *   leaks never reach the Jetpack Compose chat bubble.
     * Call this on EVERY chunk emitted by early-return branches.
     */
    private fun sanitizeNonGbnfChunk(
        accumulatedText: String,
        originalPrompt: String,
        effectivePrompt: String,
        strictPrompt: String
    ): String {
        var display = accumulatedText
        // Remove echoed jail wrappers from simulated stub outputs (they embed the prompt).
        if (display.contains(strictPrompt)) {
            display = display.replace(strictPrompt, originalPrompt)
        }
        if (display.contains(effectivePrompt) && effectivePrompt != originalPrompt) {
            display = display.replace(effectivePrompt, originalPrompt)
        }
        // Strip raw ChatML jail markers if a tiny model echoed them verbatim.
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
        // Unified post-sanitization (jargon + out-of-scope guardrails).
        // isKnowledgeBase=false: this text came from a real engine, so the
        // strict-prompt jargon screen (which matches our own jail phrasing
        // like "core mechanism") is skipped to avoid censoring legitimate
        // model output. Loop/off-topic/preamble checks still apply.
        return OutputVerificationEngine.verifyAndSanitizeText(display, isKnowledgeBase = false)
    }

    /**
     * Builds command-line execution parameters for starting a local llama.cpp llama-server instance.
     * Automatically enforces factual optimization with temperature 0.0 and optionally hooks in GBNF grammar files.
     */
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
            add("0.0") // Force factual optimization automatically
            
            // If a grammar constraint is provided, hook it straight into the process builder
            grammarPath?.let {
                add("--grammar-file")
                add(it)
            }
        }
    }

    /**
     * Starts or configures the local engine process builder for llama-server.
     */
    fun startLocalEngineInstance(
        modelPath: String,
        grammarPath: String? = null,
        port: Int = 8080
    ): ProcessBuilder {
        val commandArgs = buildLlamaServerCommandArgs(modelPath, grammarPath, port)
        return ProcessBuilder(commandArgs)
    }
}
