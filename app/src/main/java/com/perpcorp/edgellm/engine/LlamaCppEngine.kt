package com.perpcorp.edgellm.engine

import android.content.Context
import android.util.Log
import com.perpcorp.edgellm.BuildConfig
import com.perpcorp.edgellm.data.model.ComputeBackend
import com.perpcorp.edgellm.data.model.GenerationParameters
import com.perpcorp.edgellm.data.model.HardwareAccelerationSettings
import com.perpcorp.edgellm.data.model.ModelSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap

/**
 * Data class representing the strict factual output mandated by strict_response.gbnf.
 */
data class StrictFactualGbnfResponse(
    val isOnTopic: Boolean,
    val answer: String,
    val confidenceScore: Float
) {
    fun toJsonString(): String {
        val safeAnswer = answer.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        val formattedConfidence = if (confidenceScore >= 1.0f) "1.0" else String.format(java.util.Locale.US, "0.%02d", (confidenceScore * 100).toInt().coerceIn(0, 99))
        return "{\n  \"is_on_topic\": $isOnTopic,\n  \"answer\": \"$safeAnswer\",\n  \"confidence_score\": $formattedConfidence\n}"
    }
}

data class GbnfValidationResult(
    val isValid: Boolean,
    val parsedResponse: StrictFactualGbnfResponse?,
    val grammarErrors: List<String> = emptyList(),
    val message: String
)

/**
 * Formats a prompt using the GGUF's own chat template.
 *
 * Feeding an instruction-tuned model a bare prompt is the most common cause of
 * on-device models emitting garbage, so the template shipped inside the GGUF
 * metadata is honoured here.
 *
 * Detection is by marker-token search inside the template rather than by model
 * name, because a template string is far more reliable than a filename. Only
 * families we can render exactly are handled; [UNKNOWN] is returned otherwise
 * so callers can fall back to raw completion instead of guessing a format
 * that is subtly wrong.
 */
object ChatTemplateFormatter {

    enum class Family {
        CHATML, LLAMA3, GEMMA, PHI3, MISTRAL, UNKNOWN
    }

    private const val UNKNOWN = "unknown"

    fun detect(template: String): Family {
        if (template.isBlank()) return Family.UNKNOWN
        return when {
            // Llama 3.x header tokens are distinctive; check before ChatML
            // because some Llama 3 templates also mention im_start.
            template.contains("<|start_header_id|>") -> Family.LLAMA3
            template.contains("<|im_start|>") -> Family.CHATML
            template.contains("<start_of_turn>") -> Family.GEMMA
            template.contains("<|user|>") && template.contains("<|end|>") -> Family.PHI3
            template.contains("[INST]") -> Family.MISTRAL
            else -> Family.UNKNOWN
        }
    }

    /**
     * Render a single-turn system+user exchange.
     *
     * @return the templated prompt, or null when the template family is not
     *   recognised, in which case the caller should pass the raw user text.
     */
    fun render(
        template: String,
        systemPrompt: String?,
        userText: String
    ): Pair<String, Family>? {
        return when (detect(template)) {
            Family.CHATML -> {
                val sb = StringBuilder()
                if (!systemPrompt.isNullOrBlank()) {
                    sb.append("<|im_start|>system\n").append(systemPrompt).append("<|im_end|>\n")
                }
                sb.append("<|im_start|>user\n").append(userText).append("<|im_end|>\n")
                sb.append("<|im_start|>assistant\n")
                sb.toString() to Family.CHATML
            }

            Family.LLAMA3 -> {
                val sb = StringBuilder("<|begin_of_text|>")
                if (!systemPrompt.isNullOrBlank()) {
                    sb.append("<|start_header_id|>system<|end_header_id|>\n\n")
                        .append(systemPrompt).append("<|eot_id|>")
                }
                sb.append("<|start_header_id|>user<|end_header_id|>\n\n")
                    .append(userText).append("<|eot_id|>")
                    .append("<|start_header_id|>assistant<|end_header_id|>\n\n")
                sb.toString() to Family.LLAMA3
            }

            Family.GEMMA -> {
                val sb = StringBuilder("<bos>")
                if (!systemPrompt.isNullOrBlank()) {
                    sb.append("<start_of_turn>user\n").append(systemPrompt).append("<end_of_turn>\n")
                }
                sb.append("<start_of_turn>user\n").append(userText).append("<end_of_turn>\n")
                    .append("<start_of_turn>model\n")
                sb.toString() to Family.GEMMA
            }

            Family.PHI3 -> {
                val sb = StringBuilder()
                if (!systemPrompt.isNullOrBlank()) {
                    sb.append("<|system|>\n").append(systemPrompt).append("<|end|>\n")
                }
                sb.append("<|user|>\n").append(userText).append("<|end|>\n").append("<|assistant|>\n")
                sb.toString() to Family.PHI3
            }

            Family.MISTRAL -> {
                val sb = StringBuilder("[INST] ")
                if (!systemPrompt.isNullOrBlank()) sb.append(systemPrompt).append("\n\n")
                sb.append(userText).append(" [/INST]")
                sb.toString() to Family.MISTRAL
            }

            // No template, or a family we cannot render exactly. Returning null
            // makes the caller fall back to raw completion rather than emit a
            // prompt in a format we guessed at.
            Family.UNKNOWN -> null
        }
    }
}

/**
 * Incremental extractor for the `"answer"` field of a strict_response JSON
 * stream.
 *
 * When GBNF mode is active the model is grammar-constrained to emit
 * `{"is_on_topic":..., "answer":..., "confidence_score":...}`. Chat wants the
 * answer text as it is produced, not the surrounding envelope, so this walks
 * the partial buffer and yields only the value bytes, tracking backslash
 * escapes so a `\"` inside the answer does not terminate the field early.
 */
private class JsonAnswerExtractor {

    private val buffer = StringBuilder()
    private var started = false
    private var finished = false
    private var escaped = false

    private companion object {
        const val KEY = "\"answer\": \""
        const val ALSO_KEY = "\"answer\":\""
    }

    /** Feed a raw JSON fragment; returns the answer text newly completed. */
    fun accept(fragment: String): String {
        if (finished) return ""
        buffer.append(fragment)

        if (!started) {
            var idx = buffer.indexOf(KEY)
            var keyLen = KEY.length
            if (idx < 0) {
                idx = buffer.indexOf(ALSO_KEY)
                keyLen = ALSO_KEY.length
            }
            if (idx < 0) {
                // Bound the buffer: a runaway model must not leak memory.
                if (buffer.length > 8192) finished = true
                return ""
            }
            buffer.delete(0, idx + keyLen)
            started = true
        }

        val out = StringBuilder()
        for (i in buffer.indices) {
            val c = buffer[i]
            if (escaped) {
                // Inside a JSON escape: \" -> quote, \n -> newline, else keep
                // the escaped char literally.
                out.append(
                    when (c) {
                        'n' -> '\n'
                        't' -> '\t'
                        'r' -> '\r'
                        else -> c
                    }
                )
                escaped = false
                continue
            }
            when (c) {
                '\\' -> escaped = true
                '"' -> {
                    // Unescaped quote ends the answer field.
                    buffer.delete(0, i + 1)
                    finished = true
                    return out.toString()
                }
                else -> out.append(c)
            }
        }
        buffer.clear()
        return out.toString()
    }

    /** Any trailing text still buffered when generation stopped mid-field. */
    fun flush(): String = if (!finished && buffer.isNotEmpty()) {
        val rest = buffer.toString()
        buffer.clear()
        finished = true
        rest.replace("\\n", "\n").replace("\\\"", "\"")
    } else {
        ""
    }
}

/**
 * Real GGUF inference over llama.cpp JNI.
 *
 * Holds one resident native context per model path, so a multi-gigabyte GGUF
 * is loaded once and then reused for every subsequent turn. Sampling changes
 * and GBNF grammar changes are pushed down in place rather than triggering a
 * reload.
 *
 * All telemetry reported here is measured: tokens/sec and TTFT come from wall
 * clock around tokens the native layer actually decoded, and the closing
 * figures come from llama.cpp's own counters via [LlamaContext.stats].
 */
class LlamaCppEngine(private val context: Context? = null) {

    companion object {
        private const val TAG = "LlamaCppEngine"
        const val STRICT_RESPONSE_GBNF_ASSET = "strict_response.gbnf"

        /**
         * GBNF grammar constraining output to the strict factual envelope.
         * Applied natively by llama_sampler_init_grammar, which masks
         * disallowed tokens during sampling, so the shape below is structurally
         * guaranteed rather than validated after the fact.
         */
        const val DEFAULT_STRICT_RESPONSE_GBNF = """# Root JSON structure layout
root ::= "{\n" "  \"is_on_topic\": " boolean ",\n" "  \"answer\": " string ",\n" "  \"confidence_score\": " number "\n" "}"

# Force boolean to be exactly true or false (no quotes)
boolean ::= "true" | "false"

# Force confidence score to be a valid fractional float between 0.0 and 1.0
number ::= "0." [0-9] [0-9]? | "1.0"

# String wrapper that handles escaped characters inside the answer text
string ::= "\"" string-content "\""
string-content ::= [^"\\]* (escape-sequence [^"\\]*)*
escape-sequence ::= "\\" [btnfr"\\/] | "\\u" [0-9a-fA-F] [0-9a-fA-F] [0-9a-fA-F] [0-9a-fA-F]"""
    }

    /** One resident native context per GGUF path. */
    private val residentContexts = ConcurrentHashMap<String, LlamaContext>()

    /**
     * Load the strict GBNF grammar from assets, falling back to the embedded
     * copy. The native side rejects it at parse time if it is malformed, so a
     * bad asset degrades to unconstrained output with a log line rather than
     * a crash.
     */
    fun loadStrictResponseGbnf(): String {
        return try {
            val appContext = context?.applicationContext
            if (appContext != null) {
                appContext.assets.open(STRICT_RESPONSE_GBNF_ASSET).use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
                }
            } else {
                DEFAULT_STRICT_RESPONSE_GBNF
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read $STRICT_RESPONSE_GBNF_ASSET (${e.message}); using embedded grammar")
            DEFAULT_STRICT_RESPONSE_GBNF
        }
    }

    /**
     * Post-hoc validation of a strict_response payload.
     *
     * This is defence in depth only. The grammar already guarantees the shape
     * at decode time; this catches the case where the grammar could not be
     * installed and sampling ran unconstrained.
     */
    fun validateStrictGbnfResponse(rawText: String): GbnfValidationResult {
        val trimmed = rawText.trim()
        val errors = mutableListOf<String>()

        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            errors.add("Output does not start with '{' or end with '}' (Root JSON rule violated)")
        }

        val jsonCandidate = when {
            trimmed.startsWith("{") && trimmed.endsWith("}") -> trimmed
            trimmed.contains("{") && trimmed.contains("}") ->
                trimmed.substring(trimmed.indexOf('{'), trimmed.lastIndexOf('}') + 1)
            else -> trimmed
        }

        try {
            val json = JSONObject(jsonCandidate)
            if (!json.has("is_on_topic")) errors.add("Missing required boolean key 'is_on_topic'")
            if (!json.has("answer")) errors.add("Missing required string key 'answer'")
            if (!json.has("confidence_score")) errors.add("Missing required number key 'confidence_score'")

            val isOnTopic = json.optBoolean("is_on_topic", false)
            val answer = json.optString("answer", "")
            val confidence = json.optDouble("confidence_score", 0.0).toFloat()

            if (confidence < 0.0f || confidence > 1.0f) {
                errors.add("confidence_score must be between 0.0 and 1.0 (found $confidence)")
            }

            if (errors.isEmpty()) {
                return GbnfValidationResult(
                    isValid = true,
                    parsedResponse = StrictFactualGbnfResponse(isOnTopic, answer, confidence),
                    message = "Valid GBNF conforming output: is_on_topic=$isOnTopic, confidence=$confidence"
                )
            }
        } catch (e: Exception) {
            errors.add("JSON Syntax Parsing Exception: ${e.message}")
        }

        return GbnfValidationResult(
            isValid = false,
            parsedResponse = null,
            grammarErrors = errors,
            message = "GBNF validation failed: ${errors.joinToString("; ")}"
        )
    }

    /**
     * Map the user's ComputeBackend preference onto an actual layer-offload
     * count, gated on what the native layer really registered. Asking for 0
     * layers when no GPU backend was compiled in is the honest answer: the
     * weights stay on the CPU instead of the UI claiming Vulkan offload that
     * never happens.
     *
     * Which APK you installed matters: only the `vulkan` product flavor
     * compiles the ggml Vulkan backend (BuildConfig.GGML_VULKAN_COMPILED).
     * The default `cpu` flavor can never offload, even on a Vulkan-capable
     * device — and on a `vulkan` build running on a driverless device we still
     * fall back to CPU instead of failing.
     */
    private fun resolveGpuLayers(ctx: LlamaContext, settings: HardwareAccelerationSettings): Int {
        if (settings.computeBackend == ComputeBackend.CPU_NEON) return 0
        if (!BuildConfig.GGML_VULKAN_COMPILED) {
            Log.w(
                TAG,
                "${settings.computeBackend.shortName} requested but this APK was built " +
                    "CPU-only (flavor=${BuildConfig.FLAVOR}); running on CPU. Install the " +
                    "`vulkan` flavor build for GPU offload."
            )
            return 0
        }
        val gpuPresent = runCatching { ctx.availableBackends().any { it.isGpu } }.getOrDefault(false)
        if (!gpuPresent) {
            Log.w(
                TAG,
                "${settings.computeBackend.shortName} requested and this APK has the Vulkan " +
                    "backend, but no GPU device registered on this phone; running on CPU."
            )
            return 0
        }
        Log.i(TAG, "GPU offload active (flavor=${BuildConfig.FLAVOR}): offloading all layers")
        return -1 // offload all layers
    }

    /**
     * Get (or lazily create) the resident context for a model.
     * Returns null when the native library is missing or the model will not
     * load, with the reason logged.
     */
    private fun obtainContext(
        modelPath: String,
        model: ModelSpec,
        settings: HardwareAccelerationSettings
    ): LlamaContext? {
        residentContexts[modelPath]?.let { if (it.isAvailable) return it }

        if (!File(modelPath).exists()) {
            Log.e(TAG, "GGUF not found on disk: $modelPath")
            return null
        }
        if (!LlamaContext.isNativeLoaded) {
            Log.e(TAG, "libllama-android.so not loaded; GGUF inference unavailable")
            return null
        }

        // n_ctx: honour the model's declared window but cap it, since KV cache
        // is the dominant memory cost alongside the weights themselves.
        val nCtx = model.contextLength.coerceIn(512, 8192)
        val nBatch = settings.chunkedPrefillBatchSize.coerceIn(32, 512)

        // First pass at CPU so we can query backends, then reload with the
        // right offload. Simpler than threading a static device probe through.
        var ctx = LlamaContext.create(
            modelPath = modelPath,
            nThreads = settings.threadCount.coerceAtLeast(1),
            nCtx = nCtx,
            nGpuLayers = 0,
            nBatch = nBatch,
            useMmap = settings.useMmap,
            useFlashAttention = settings.useFlashAttention
        ) ?: run {
            Log.e(TAG, "Failed to create llama context: ${LlamaContext.lastErrorMessage()}")
            return null
        }

        val gpuLayers = resolveGpuLayers(ctx, settings)
        if (gpuLayers != 0) {
            // GPU offload changes buffer placement, so the context has to be
            // rebuilt. Do it once and keep the GPU-capable context resident.
            ctx.release()
            ctx = LlamaContext.create(
                modelPath = modelPath,
                nThreads = settings.threadCount.coerceAtLeast(1),
                nCtx = nCtx,
                nGpuLayers = gpuLayers,
                nBatch = nBatch,
                useMmap = settings.useMmap,
                useFlashAttention = settings.useFlashAttention
            ) ?: run {
                Log.e(TAG, "GPU-offloaded context failed: ${LlamaContext.lastErrorMessage()}")
                return null
            }
        }

        residentContexts[modelPath] = ctx
        return ctx
    }

    /** Release a specific model. Call when the user unloads or deletes it. */
    fun unload(modelPath: String) {
        residentContexts.remove(modelPath)?.release()
    }

    /** Release every resident context. Call when tearing the engine down. */
    fun releaseAll() {
        residentContexts.keys.forEach { residentContexts.remove(it)?.release() }
    }

    /**
     * Stream real llama.cpp generation.
     *
     * Telemetry is measured, not modelled: TTFT is the wall clock until the
     * first token the native layer produced, and tokens/sec is computed from
     * the count of tokens actually decoded since then. The final chunk carries
     * llama.cpp's own counters.
     */
    fun streamLlamaCppResponse(
        prompt: String,
        model: ModelSpec,
        settings: HardwareAccelerationSettings,
        params: GenerationParameters,
        useGbnfGrammar: Boolean = true
    ): Flow<StreamTokenChunk> = flow {
        val modelPath = model.localFilePath
        if (modelPath.isBlank()) {
            Log.e(TAG, "ModelSpec.localFilePath is blank; no GGUF to load")
            return@flow
        }

        val ctx = obtainContext(modelPath, model, settings)
        if (ctx == null) {
            Log.e(TAG, "No usable llama.cpp context for $modelPath")
            return@flow
        }

        // Greedy when a grammar is constraining the output: a grammar plus
        // sampling can wander into a dead end and stall.
        val temperature = if (useGbnfGrammar) 0.0f else params.temperature
        val topK = if (useGbnfGrammar) 1 else params.topK
        ctx.setSampling(
            temperature = temperature,
            topP = params.topP,
            topK = topK,
            minP = if (useGbnfGrammar) 0.0f else params.minP,
            nPredict = params.maxNewTokens,
            seed = -1
        )

        if (useGbnfGrammar) {
            val installed = ctx.setGrammar(loadStrictResponseGbnf())
            if (!installed) Log.w(TAG, "GBNF grammar rejected by native parser; output will be unconstrained")
        } else {
            ctx.setGrammar("")
        }

        // Format the prompt with the GGUF's own chat template. Unknown
        // families fall back to the raw text rather than a guessed format.
        val templated = ChatTemplateFormatter.render(ctx.chatTemplate, params.systemPrompt, prompt)
        if (templated == null) {
            Log.i(TAG, "No usable chat template for this GGUF; sending raw prompt")
        }
        val finalPrompt = templated?.first ?: prompt
        val templateFamily = templated?.second

        val extractor = if (useGbnfGrammar && !params.enforceJsonSchema) JsonAnswerExtractor() else null

        val sb = StringBuilder()
        var tokenCount = 0
        var firstTokenAtNs = 0L
        val startNs = System.nanoTime()
        var ttftMs = 0L

        val backendLabel = buildString {
            append("llama.cpp GGUF")
            append(" • ")
            append(
                when (settings.computeBackend) {
                    ComputeBackend.CPU_NEON -> "CPU-NEON"
                    ComputeBackend.GPU_VULKAN -> "Vulkan"
                    ComputeBackend.OPENCL -> "OpenCL"
                    ComputeBackend.NPU_NNAPI -> "NPU-NNAPI"
                }
            )
            if (templateFamily != null) append(" • ${templateFamily.name}")
            if (useGbnfGrammar) append(" • GBNF")
        }

        ctx.completionStream(finalPrompt).collect { event ->
            when (event) {
                is LlamaContext.TokensEvent.Delta -> {
                    // Mark the first decoded token even when it is part of the
                    // JSON envelope rather than answer text, so live tok/s is
                    // measured from the real start of decoding.
                    if (firstTokenAtNs == 0L) {
                        firstTokenAtNs = System.nanoTime()
                        ttftMs = ((firstTokenAtNs - startNs) / 1_000_000L).coerceAtLeast(0L)
                    }

                    // In GBNF chat mode we surface the answer text as it is
                    // decoded rather than the JSON envelope around it.
                    val visible = when {
                        extractor != null -> extractor.accept(event.text)
                        else -> event.text
                    }
                    if (visible.isEmpty()) return@collect

                    tokenCount++
                    sb.append(visible)

                    val decodeSec = (System.nanoTime() - firstTokenAtNs) / 1_000_000_000.0
                    val liveTps = if (decodeSec > 0.001) tokenCount / decodeSec.toFloat() else 0f

                    emit(
                        StreamTokenChunk(
                            token = visible,
                            accumulatedText = sb.toString(),
                            tokenCount = tokenCount,
                            tokensPerSecond = liveTps,
                            timeToFirstTokenMs = ttftMs,
                            isComplete = false,
                            backendUsed = backendLabel,
                            // No speculative decoding, KV compaction or prefix
                            // cache is implemented in this engine, so these
                            // report zero rather than flattering constants.
                            speculativeSpeedup = 1.0f,
                            speculativeAcceptedTokens = 0,
                            kvCacheSavedPercent = 0f,
                            isPrefixCacheHit = false,
                            isTurboBoost = false,
                            samplerName = "Greedy (temp=$temperature, top_k=$topK)",
                            grammarModeUsed = if (useGbnfGrammar) GrammarMode.GBNF_STRICT_FACTUAL else GrammarMode.NONE
                        )
                    )
                }

                is LlamaContext.TokensEvent.Complete -> {
                    // A grammar-constrained run that ended mid-field still has
                    // buffered answer bytes worth showing.
                    val tail = extractor?.flush().orEmpty()
                    if (tail.isNotEmpty()) {
                        sb.append(tail)
                        emit(
                            StreamTokenChunk(
                                token = tail,
                                accumulatedText = sb.toString(),
                                tokenCount = tokenCount + 1,
                                tokensPerSecond = 0f,
                                timeToFirstTokenMs = ttftMs,
                                isComplete = true,
                                backendUsed = backendLabel,
                                samplerName = "Greedy (temp=$temperature, top_k=$topK)",
                                grammarModeUsed = if (useGbnfGrammar) GrammarMode.GBNF_STRICT_FACTUAL else GrammarMode.NONE
                            )
                        )
                    }

                    val stats = event.stats
                    val finalTps = stats?.tokensPerSecond?.toFloat() ?: 0f
                    val finalTtft = stats?.ttftMs?.toLong()?.takeIf { it > 0 } ?: ttftMs

                    Log.i(
                        TAG,
                        "decode done: ${stats?.tokensGenerated ?: tokenCount} tok, " +
                            "prompt ${stats?.promptTokens ?: 0} tok, ttft ${finalTtft}ms, " +
                            "${"%.2f".format(stats?.tokensPerSecond ?: 0.0)} tok/s, " +
                            "stop=eog:${stats?.stopEog == true}/len:${stats?.stopLength == true}/cancel:${stats?.stopCancelled == true}"
                    )

                    emit(
                        StreamTokenChunk(
                            token = "",
                            accumulatedText = sb.toString(),
                            tokenCount = stats?.tokensGenerated ?: tokenCount,
                            tokensPerSecond = finalTps,
                            timeToFirstTokenMs = finalTtft,
                            isComplete = true,
                            backendUsed = backendLabel,
                            speculativeSpeedup = 1.0f,
                            speculativeAcceptedTokens = 0,
                            kvCacheSavedPercent = 0f,
                            isPrefixCacheHit = false,
                            isTurboBoost = false,
                            samplerName = "Greedy (temp=$temperature, top_k=$topK)",
                            grammarModeUsed = if (useGbnfGrammar) GrammarMode.GBNF_STRICT_FACTUAL else GrammarMode.NONE
                        )
                    )
                }
            }
        }
    }

    /**
     * Blocking single-shot generation against the real model.
     *
     * Runs the native decode loop on the calling thread. Call from a
     * background dispatcher; this is not main-thread safe.
     */
    fun generateStrictFactualResponse(
        prompt: String,
        model: ModelSpec,
        overrideConfidence: Float? = null
    ): StrictFactualGbnfResponse {
        val modelPath = model.localFilePath
        val defaultSettings = HardwareAccelerationSettings()
        val ctx = if (modelPath.isBlank()) null else obtainContext(modelPath, model, defaultSettings)

        if (ctx == null) {
            Log.e(TAG, "generateStrictFactualResponse: no native context available")
            return StrictFactualGbnfResponse(
                isOnTopic = false,
                answer = "Inference engine unavailable: no GGUF model is loaded.",
                confidenceScore = 0.0f
            )
        }

        ctx.setSampling(temperature = 0.0f, topP = 0.85f, topK = 1, minP = 0.0f, nPredict = 512, seed = -1)
        if (!ctx.setGrammar(loadStrictResponseGbnf())) {
            Log.w(TAG, "GBNF grammar rejected; generating without constraint")
        }

        val raw = ctx.completion(prompt)
            ?: return StrictFactualGbnfResponse(
                isOnTopic = false,
                answer = "Inference engine error while generating a response.",
                confidenceScore = 0.0f
            )

        val validation = validateStrictGbnfResponse(raw)
        if (validation.isValid && validation.parsedResponse != null) {
            val parsed = validation.parsedResponse
            return parsed.copy(
                confidenceScore = overrideConfidence ?: parsed.confidenceScore
            )
        }

        // Grammar was unavailable or the run stopped mid-object. Surface the
        // raw generation rather than inventing a confident answer.
        Log.w(TAG, "strict response failed validation: ${validation.message}")
        return StrictFactualGbnfResponse(
            isOnTopic = false,
            answer = raw.trim().ifEmpty { "No response generated." },
            confidenceScore = overrideConfidence ?: 0.0f
        )
    }
}
