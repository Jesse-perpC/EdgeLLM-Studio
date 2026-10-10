package com.perpcorp.edgellm.engine

import ai.onnxruntime.NodeInfo
import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtException
import ai.onnxruntime.OrtLoggingLevel
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.util.Log
import com.perpcorp.edgellm.data.model.GenerationParameters
import com.perpcorp.edgellm.data.model.HardwareAccelerationSettings
import com.perpcorp.edgellm.data.model.ModelSpec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.LongBuffer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.exp
import kotlin.random.Random

/**
 * Real ONNX Runtime decoder inference.
 *
 * Replaces a branch that emitted hardcoded knowledge-base strings through a
 * `delay(20)` loop while reporting a hardcoded 28 ms TTFT and a tok/s figure
 * derived from the thread count. Every number reported here is measured around
 * work ONNX Runtime actually performed.
 *
 * Two decode strategies, chosen by inspecting the graph rather than the filename
 * (both `decoder_merged` and `decoder_with_past_merged` ship as `.onnx`):
 *  - `decoder_with_past_*` declares `past_key_values.*` inputs and `present.*`
 *    outputs, so each step feeds one token and ORT keeps the KV cache.
 *  - `decoder_model_merged` has no such inputs, so each step re-runs the whole
 *    prefix: correct, but quadratic. Reported honestly via [modeLabel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnnxLlmEngine {

    private val env: OrtEnvironment by lazy {
        OrtEnvironment.getEnvironment(OrtLoggingLevel.ORT_LOGGING_LEVEL_WARNING, "edgellm")
    }

    private val cache = HashMap<String, LoadedOnnxModel>()

    fun streamOnnxResponse(
        prompt: String,
        model: ModelSpec,
        settings: HardwareAccelerationSettings,
        params: GenerationParameters
    ): Flow<StreamTokenChunk> = callbackFlow {
        val modelPath = model.localFilePath
        if (modelPath.isBlank()) {
            Log.e(TAG, "ModelSpec.localFilePath is blank; no .onnx to load")
            close()
            return@callbackFlow
        }
        val modelFile = File(modelPath)
        if (!modelFile.isFile) {
            val msg = "ONNX model not found: $modelPath"
            Log.e(TAG, msg)
            close(IllegalStateException(msg))
            return@callbackFlow
        }

        val loaded = try {
            obtain(modelFile, settings)
        } catch (t: Throwable) {
            Log.e(TAG, "ONNX load failed for $modelPath", t)
            close(t)
            return@callbackFlow
        }

        val cancelled = AtomicBoolean(false)
        // The ProducerScope receiver is lost inside worker.execute, so capture
        // it for the blocking send from the decode thread.
        val scope = this
        // Dedicated thread: OrtSession.run is not re-entrant, and generation is a
        // blocking CPU/NPU call that must not pin the caller's dispatcher.
        val worker = Executors.newSingleThreadExecutor { r ->
            Thread(r, "onnx-decode").apply { priority = Thread.NORM_PRIORITY }
        }
        awaitClose {
            cancelled.set(true)
            worker.shutdownNow()
        }

        worker.execute {
            try {
                // Blocking send gives backpressure: the decode thread waits if
                // the collector is slow, so no token is ever dropped.
                generate(loaded, prompt, params, cancelled) { runBlocking { scope.send(it) } }
            } catch (t: Throwable) {
                if (!cancelled.get()) {
                    Log.e(TAG, "ONNX generation failed", t)
                    close(t)
                }
            } finally {
                close()
            }
        }
    }

    /**
     * The decode loop. `context` is every id the model has been told about;
     * `fedCount` is how many of those the session already holds in its KV cache,
     * so each iteration feeds only the new tail when a cache exists.
     */
    private fun generate(
        loaded: LoadedOnnxModel,
        prompt: String,
        params: GenerationParameters,
        cancelled: AtomicBoolean,
        emit: (StreamTokenChunk) -> Unit
    ) {
        val tokenizer = loaded.tokenizer
        val plan = loaded.plan
        val all = tokenizer.encode(prompt)
        if (all.isEmpty()) {
            Log.e(TAG, "Prompt tokenized to zero ids; nothing to run")
            return
        }

        // Encoder graphs (MiniLM-style) have no validated vocab head, so token
        // generation is unreachable for them by construction. They take a
        // single forward pass and report the embedding instead.
        if (!plan.isDecoder) {
            emitEmbedding(loaded, all, emit)
            return
        }

        val maxNew = params.maxNewTokens.coerceIn(1, 4096)
        val dropped = if (plan.maxPositions > 0 && all.size > plan.maxPositions) {
            all.size - plan.maxPositions
        } else {
            0
        }
        var context: IntArray =
            if (dropped > 0) all.copyOfRange(all.size - plan.maxPositions, all.size) else all
        if (dropped > 0) {
            Log.w(TAG, "Prompt is $all.size tokens; dropped $dropped to fit context ${plan.maxPositions}")
        }

        val rng = Random(System.nanoTime())
        val started = System.nanoTime()
        val accumulated = StringBuilder()
        val bytes = ByteSink()
        var ttftMs = -1L
        var fedCount = 0
        var generated = 0

        while (generated < maxNew && !cancelled.get()) {
            val step: IntArray
            val pastLen: Int
            if (plan.usesPast) {
                step = if (fedCount >= context.size) {
                    intArrayOf(context.last())
                } else {
                    context.copyOfRange(fedCount, context.size)
                }
                pastLen = fedCount
            } else {
                step = context
                pastLen = 0
            }

            val inputs = buildInputs(loaded, step, pastLen)
            val logits = try {
                loaded.session.run(inputs).use { result ->
                    val value = result.get(plan.logitsOutput)
                    if (!value.isPresent) {
                        throw IllegalStateException(
                            "Model has no output named '${plan.logitsOutput}'; " +
                                "present outputs: ${loaded.outputNames}"
                        )
                    }
                    value.get().value as? FloatArray
                        ?: throw IllegalStateException(
                            "Output '${plan.logitsOutput}' is not a float tensor. This engine " +
                                "drives decoder LMs only, not encoders or classifier heads."
                        )
                }
            } catch (e: OrtException) {
                throw IllegalStateException(
                    "ONNX run failed: ${e.message}. Input names and KV layout come from the " +
                        "graph, so this usually means the export uses a past_key_values shape " +
                        "the graph left symbolic.",
                    e
                )
            }

            if (logits.size < tokenizer.vocabSize) {
                throw IllegalStateException(
                    "Logits tensor holds ${logits.size} floats but the tokenizer reports a " +
                        "${tokenizer.vocabSize} vocab; input_ids and output vocab disagree."
                )
            }

            val next = sampleLastPosition(logits, tokenizer.vocabSize, params, rng)
            if (next in tokenizer.eosTokenIds) break

            bytes.append(tokenizer.decodeTokenBytes(next))
            val (text, leftover) = bytes.drainUtf8()
            // Any trailing bytes are a partial multi-byte sequence; they must
            // survive whether or not this step produced decodable text.
            bytes.restore(leftover)
            fedCount += step.size
            context = context.append(next)
            generated++
            if (ttftMs < 0) ttftMs = (System.nanoTime() - started) / 1_000_000

            if (text.isNotEmpty()) {
                accumulated.append(text)
                val elapsedMs = ((System.nanoTime() - started) / 1_000_000).coerceAtLeast(1L)
                emit(
                    StreamTokenChunk(
                        token = text,
                        accumulatedText = accumulated.toString(),
                        tokenCount = generated,
                        tokensPerSecond = generated * 1000f / elapsedMs,
                        timeToFirstTokenMs = if (ttftMs >= 0) ttftMs else elapsedMs,
                        isComplete = false,
                        backendUsed = backendLabel(loaded),
                        samplerName = params.samplerLabel(),
                        grammarModeUsed = GrammarMode.NONE,
                        // KV-cache steps genuinely avoid recomputing the prefix, but
                        // no honest single percentage exists, so report none rather
                        // than invent the 25f the old branch hardcoded.
                        kvCacheSavedPercent = 0f,
                        isTurboBoost = params.isTurboBoost
                    )
                )
            }
        }

        val elapsedMs = ((System.nanoTime() - started) / 1_000_000).coerceAtLeast(1L)
        val tps = if (generated > 0) generated * 1000f / elapsedMs else 0f
        emit(
            StreamTokenChunk(
                token = "",
                accumulatedText = accumulated.toString(),
                tokenCount = generated,
                tokensPerSecond = tps,
                timeToFirstTokenMs = if (ttftMs >= 0) ttftMs else elapsedMs,
                isComplete = true,
                backendUsed = backendLabel(loaded),
                samplerName = params.samplerLabel(),
                grammarModeUsed = GrammarMode.NONE,
                kvCacheSavedPercent = 0f,
                isTurboBoost = params.isTurboBoost
            )
        )
    }

    private fun backendLabel(loaded: LoadedOnnxModel): String =
        "ONNX Runtime ${loaded.backendLabel} • ${loaded.plan.modeLabel}"

    /**
     * Single forward pass for encoder graphs. Mean-pools a [1, seq, H] sequence
     * output over the (unpadded, fully-masked) sequence, or uses a pooled
     * vector as-is. Never samples: there is no vocabulary distribution here,
     * only hidden states, and treating them as logits is the bug this path
     * replaces.
     */
    private fun emitEmbedding(
        loaded: LoadedOnnxModel,
        ids: IntArray,
        emit: (StreamTokenChunk) -> Unit
    ) {
        val plan = loaded.plan
        val outputName = plan.embeddingOutput
            ?: throw IllegalStateException("Encoder plan has no embedding output")
        val started = System.nanoTime()
        val inputs = buildInputs(loaded, ids, pastLen = 0)
        val raw = try {
            loaded.session.run(inputs).use { result ->
                val value = result.get(outputName)
                if (!value.isPresent) {
                    throw IllegalStateException(
                        "Model has no output named '$outputName'; " +
                            "present outputs: ${loaded.outputNames}"
                    )
                }
                value.get().value as? FloatArray
                    ?: throw IllegalStateException(
                        "Output '$outputName' is not a float tensor; cannot embed."
                    )
            }
        } catch (e: OrtException) {
            throw IllegalStateException("ONNX encoder run failed: ${e.message}", e)
        }
        val elapsedMs = ((System.nanoTime() - started) / 1_000_000).coerceAtLeast(1L)

        // [1, seq, H] -> mean over seq. Inputs carry no padding (mask is all
        // ones), so a plain mean is the correct pool. Anything else is already
        // a single vector and is used as-is.
        val vector: FloatArray = if (ids.size > 1 && raw.size % ids.size == 0) {
            val h = raw.size / ids.size
            FloatArray(h) { j ->
                var sum = 0f
                for (i in ids.indices) sum += raw[i * h + j]
                sum / ids.size
            }
        } else {
            raw
        }
        var norm = 0.0
        for (v in vector) norm += (v * v).toDouble()
        norm = kotlin.math.sqrt(norm)
        val preview = vector.take(8).joinToString(", ") { "%.4f".format(it) }
        val text = buildString {
            append("Encoder embedding (${vector.size} dims, L2 norm ${"%.3f".format(norm)}, ")
            append("${elapsedMs} ms, ${ids.size} tokens):\n")
            append("[$preview${if (vector.size > 8) ", …" else ""}]\n")
            append("This model is a sentence encoder, not a generator: it maps text ")
            append("to vectors for similarity and retrieval. Use the vector above ")
            append("(or a retrieval index over it) rather than expecting chat output.")
        }
        emit(
            StreamTokenChunk(
                token = text,
                accumulatedText = text,
                tokenCount = 0,
                tokensPerSecond = 0f,
                timeToFirstTokenMs = elapsedMs,
                isComplete = true,
                backendUsed = backendLabel(loaded),
                samplerName = "N/A (encoder)",
                grammarModeUsed = GrammarMode.NONE,
                kvCacheSavedPercent = 0f,
                isTurboBoost = false
            )
        )
    }

    /**
     * Builds one step's input map. Names come from the graph so this works for
     * both Optimum exports and bare `torch.onnx.export` layouts.
     */
    private fun buildInputs(
        loaded: LoadedOnnxModel,
        step: IntArray,
        pastLen: Int
    ): Map<String, OnnxTensor> {
        val env = loaded.env
        val plan = loaded.plan
        val total = pastLen + step.size
        val map = LinkedHashMap<String, OnnxTensor>()

        map[plan.inputIds] = OnnxTensor.createTensor(
            env,
            LongBuffer.wrap(LongArray(step.size) { step[it].toLong() }),
            longArrayOf(1, step.size.toLong())
        )
        plan.attentionMask?.let {
            map[it] = OnnxTensor.createTensor(
                env,
                LongBuffer.wrap(LongArray(total) { 1L }),
                longArrayOf(1, total.toLong())
            )
        }
        plan.positionIds?.let {
            map[it] = OnnxTensor.createTensor(
                env,
                LongBuffer.wrap(LongArray(step.size) { (pastLen + it).toLong() }),
                longArrayOf(1, step.size.toLong())
            )
        }
        if (plan.usesPast) {
            for (name in plan.pastInputs) {
                val shape = plan.pastShapes[name] ?: continue
                map[name] = OnnxTensor.createTensor(env, LongBuffer.wrap(EMPTY_LONGS), shape)
            }
        }
        return map
    }

    /**
     * Samples one token from the last position of a [batch, seq, vocab] tensor.
     * Takes the trailing `vocabSize` floats, which is the last position for any
     * batch-1 autoregressive export, so no shape guessing is needed.
     */
    private fun sampleLastPosition(
        logits: FloatArray,
        vocabSize: Int,
        params: GenerationParameters,
        rng: Random
    ): Int {
        val offset = logits.size - vocabSize
        val temp = params.temperature

        if (temp <= 0f || params.topK == 1) {
            var best = 0
            var bestV = Float.NEGATIVE_INFINITY
            for (i in 0 until vocabSize) {
                val v = logits[offset + i]
                if (v > bestV) {
                    bestV = v
                    best = i
                }
            }
            return best
        }

        var maxV = Float.NEGATIVE_INFINITY
        for (i in 0 until vocabSize) {
            val v = logits[offset + i] / temp
            logits[offset + i] = v
            if (v > maxV) maxV = v
        }
        var sum = 0f
        for (i in 0 until vocabSize) {
            val e = exp((logits[offset + i] - maxV).toDouble()).toFloat()
            logits[offset + i] = e
            sum += e
        }
        if (sum <= 0f || !sum.isFinite()) return 0
        val inv = 1f / sum
        for (i in 0 until vocabSize) logits[offset + i] *= inv

        // Bounded top-k selection. A full sort of a 150k vocab per token would
        // dominate generation time; insertion into a k-sized buffer is O(n*k)
        // with k typically <= 64.
        val k = params.topK.coerceIn(1, vocabSize)
        val idx = IntArray(k) { -1 }
        val probs = FloatArray(k)
        for (i in 0 until vocabSize) {
            val p = logits[offset + i]
            if (idx[k - 1] < 0 || p > probs[k - 1]) {
                var j = k - 1
                while (j > 0 && p > probs[j - 1]) {
                    idx[j] = idx[j - 1]
                    probs[j] = probs[j - 1]
                    j--
                }
                idx[j] = i
                probs[j] = p
            }
        }

        var keep = k
        val topP = params.topP
        if (topP < 1f) {
            var cum = 0f
            for (i in 0 until k) {
                cum += probs[i]
                if (cum >= topP) {
                    keep = i + 1
                    break
                }
            }
        }
        val minP = params.minP
        if (minP > 0f) {
            val threshold = minP * probs[0]
            var m = 0
            while (m < keep && probs[m] >= threshold) m++
            keep = m
        }
        if (keep <= 0) keep = 1

        var wsum = 0f
        for (i in 0 until keep) wsum += probs[i]
        if (wsum <= 0f || !wsum.isFinite()) return idx[0]
        val target = rng.nextFloat() * wsum
        var acc = 0f
        for (i in 0 until keep) {
            acc += probs[i]
            if (acc >= target) return idx[i]
        }
        return idx[keep - 1]
    }

    private fun GenerationParameters.samplerLabel(): String =
        if (temperature <= 0f || topK == 1) {
            "Greedy (temp=0, top_k=1)"
        } else {
            "Top-K $topK / Top-P $topP / Min-P $minP (temp=$temperature)"
        }

    private fun obtain(modelFile: File, settings: HardwareAccelerationSettings): LoadedOnnxModel {
        val key = "${modelFile.absolutePath}|${settings.threadCount}|${settings.computeBackend}"
        synchronized(cache) { cache[key] }?.let { return it }

        val built = buildLoaded(modelFile, settings)
        synchronized(cache) {
            cache[key]?.let { existing ->
                built.close()
                return existing
            }
            cache[key] = built
            return built
        }
    }

    private fun buildLoaded(
        modelFile: File,
        settings: HardwareAccelerationSettings
    ): LoadedOnnxModel {
        val tokenizer = loadTokenizer(modelFile)

        val options = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(settings.threadCount.coerceIn(1, 16))
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            // NNAPI is the only on-accelerator path the full AAR exposes on stock
            // Android; a no-op on devices without it, which then falls back to CPU.
            try {
                addNnapi()
            } catch (e: OrtException) {
                Log.w(TAG, "NNAPI unavailable, staying on CPU: ${e.message}")
            }
        }
        val session = env.createSession(modelFile.absolutePath, options)
        val plan = OnnxGraphPlan.from(session, tokenizer.vocabSize)
        Log.i(TAG, "Loaded ${modelFile.name}: ${plan.describe()}")
        return LoadedOnnxModel(env, session, tokenizer, plan, options, session.outputInfo.keys.toList())
    }

    /**
     * Byte-level BPE first; MiniLM-style bundles declare a WordPiece
     * tokenizer.json and ship vocab.txt instead.
     */
    private fun loadTokenizer(modelFile: File): LlmTokenizer {
        val tokenizerFile = resolveTokenizer(modelFile)
        try {
            return HuggingFaceByteLevelTokenizer.load(tokenizerFile)
        } catch (e: UnsupportedOperationException) {
            val vocab = modelFile.parentFile?.let { File(it, "vocab.txt") }
            if (vocab != null && vocab.isFile) {
                Log.i(TAG, "tokenizer.json is not byte-level BPE; using vocab.txt WordPiece for ${modelFile.name}")
                return WordPieceTokenizer.load(vocab)
            }
            throw e
        }
    }

    private fun resolveTokenizer(modelFile: File): File {
        val parent = modelFile.parentFile
        val candidates = listOfNotNull(
            parent?.let { File(it, "tokenizer.json") },
            parent?.let { File(it, "tokenizer/tokenizer.json") },
            File(modelFile.parent, "${modelFile.nameWithoutExtension}.tokenizer.json")
        )
        return candidates.firstOrNull { it.isFile }
            ?: throw UnsupportedOperationException(
                "No tokenizer.json alongside ${modelFile.name}. An ONNX decoder has no built-in " +
                    "tokenizer, so text cannot be encoded without one; place the model's " +
                    "tokenizer.json in the same directory as the .onnx file."
            )
    }

    fun unloadAll() {
        synchronized(cache) {
            cache.values.forEach { it.close() }
            cache.clear()
        }
    }

    private companion object {
        const val TAG = "OnnxLlmEngine"
        val EMPTY_LONGS = LongArray(0)
    }
}

private class LoadedOnnxModel(
    val env: OrtEnvironment,
    val session: OrtSession,
    val tokenizer: LlmTokenizer,
    val plan: OnnxGraphPlan,
    private val options: OrtSession.SessionOptions,
    val outputNames: List<String>
) : AutoCloseable {
    /** ORT reports the delegated nodes through logging, not a queryable API. */
    val backendLabel: String = "NNAPI→CPU"
    override fun close() {
        runCatching { session.close() }
        runCatching { options.close() }
    }
}

/**
 * What the graph actually declares, read from the session rather than inferred
 * from the filename.
 */
private class OnnxGraphPlan(
    val inputIds: String,
    val attentionMask: String?,
    val positionIds: String?,
    val pastInputs: List<String>,
    val pastShapes: Map<String, LongArray>,
    val logitsOutput: String,
    val maxPositions: Int,
    /**
     * True only when a float output was validated as a generative head: static
     * shape ending in the tokenizer vocab size. A MiniLM-style encoder exposes
     * `last_hidden_state` ([1, seq, 384]) instead; sampling token ids from
     * those embedding values is what produced garbage output, so generation is
     * gated on this flag and encoders take the single-forward embedding path.
     */
    val isDecoder: Boolean,
    /** Present exactly when [isDecoder] is false: the output to mean-pool. */
    val embeddingOutput: String?
) {
    val usesPast: Boolean get() = isDecoder && pastInputs.isNotEmpty() && pastShapes.size == pastInputs.size
    val modeLabel: String
        get() = when {
            !isDecoder -> "encoder"
            usesPast -> "KV-cache"
            else -> "full-prefix"
        }

    fun describe(): String = buildString {
        append(modeLabel)
        append(" in=").append(inputIds)
        append(" out=").append(if (isDecoder) logitsOutput else (embeddingOutput ?: "?"))
        append(" past=").append(pastInputs.size)
        if (maxPositions > 0) append(" ctx=").append(maxPositions)
    }

    companion object {
        fun from(session: OrtSession, vocabSize: Int): OnnxGraphPlan {
            val inputs: Map<String, NodeInfo> = session.inputInfo
            val outputs: Map<String, NodeInfo> = session.outputInfo

            fun pick(candidates: List<String>): String? =
                candidates.firstOrNull { inputs.containsKey(it) }

            fun outputShape(name: String): LongArray? =
                (outputs[name]?.info as? TensorInfo)?.shape

            val inputIds = pick(listOf("input_ids"))
                ?: inputs.keys.firstOrNull()
                ?: throw IllegalStateException("Model declares no inputs")

            val pastInputs = inputs.keys.filter { it.startsWith("past_key_values") }.sorted()

            // Exporters leave only the past sequence length symbolic, so the other
            // dims are concrete and a zero-length cache can be allocated. More than
            // one symbolic dim means the layout is unknowable without a config.json.
            val pastShapes = HashMap<String, LongArray>()
            for (name in pastInputs) {
                val shape = (inputs[name]?.info as? TensorInfo)?.shape ?: continue
                if (shape.count { it < 0 } > 1) continue
                pastShapes[name] = LongArray(shape.size) { if (shape[it] < 0) 0L else shape[it] }
            }

            fun isLogitsShape(shape: LongArray): Boolean {
                // [batch, seq, vocab] or [batch, vocab]. Batch/seq may be
                // symbolic (-1); vocab is always concrete in real exports and
                // must equal the tokenizer size, otherwise this output is not a
                // head over our vocabulary (e.g. an encoder hidden state).
                if (shape.size < 2) return false
                val last = shape.last()
                return last > 0 && last == vocabSize.toLong()
            }

            var logitsName: String? = null
            outputShape("logits")?.let { shape ->
                if (isFloat(outputs["logits"]!!) && isLogitsShape(shape)) logitsName = "logits"
            }
            if (logitsName == null) {
                for (key in outputs.keys) {
                    val info = outputs[key] ?: continue
                    if (!isFloat(info)) continue
                    val shape = (info.info as? TensorInfo)?.shape ?: continue
                    if (isLogitsShape(shape)) {
                        logitsName = key
                        break
                    }
                }
            }

            val isDecoder = logitsName != null
            val embeddingOutput = if (isDecoder) {
                null
            } else {
                // Prefer an explicitly pooled vector, else the sequence output
                // (mean-pooled at inference), else any float output.
                listOf("pooler_output", "sentence_embedding", "last_hidden_state")
                    .firstOrNull { outputs.containsKey(it) }
                    ?: outputs.keys.firstOrNull { key ->
                        outputs[key]?.let { isFloat(it) } == true
                    }
                    ?: throw IllegalStateException(
                        "Model declares no float outputs; neither a decoder head " +
                            "nor an encoder embedding is available."
                    )
            }

            val maxPositions = run {
                val shape = (inputs[inputIds]?.info as? TensorInfo)?.shape ?: return@run 0
                if (shape.size >= 2 && shape[1] > 0) shape[1].toInt() else 0
            }

            return OnnxGraphPlan(
                inputIds = inputIds,
                attentionMask = pick(listOf("attention_mask", "encoder_attention_mask")),
                positionIds = pick(listOf("position_ids", "cache_position")),
                pastInputs = pastInputs,
                pastShapes = pastShapes,
                logitsOutput = logitsName ?: "",
                maxPositions = maxPositions,
                isDecoder = isDecoder,
                embeddingOutput = embeddingOutput
            )
        }

        private fun isFloat(info: NodeInfo): Boolean =
            (info.info as? TensorInfo)?.type == OnnxJavaType.FLOAT
    }
}

/** Growable byte buffer that only ever emits complete UTF-8 sequences. */
private class ByteSink {
    private var buf = ByteArray(128)
    private var len = 0

    fun append(b: Byte) {
        ensure(1)
        buf[len++] = b
    }

    fun append(other: ByteArray) {
        if (other.isEmpty()) return
        ensure(other.size)
        System.arraycopy(other, 0, buf, len, other.size)
        len += other.size
    }

    /** Returns the decodable prefix and hands back any incomplete tail. */
    fun drainUtf8(): Pair<String, ByteArray> {
        var i = 0
        while (i < len) {
            val lead = buf[i].toInt() and 0xFF
            val need = when {
                lead <= 0x7F -> 1
                lead and 0xE0 == 0xC0 -> 2
                lead and 0xF0 == 0xE0 -> 3
                lead and 0xF8 == 0xF0 -> 4
                else -> 0
            }
            if (need == 0) {
                i++
                continue
            }
            if (i + need > len) break
            var ok = true
            for (j in 1 until need) {
                if (buf[i + j].toInt() and 0xC0 != 0x80) {
                    ok = false
                    break
                }
            }
            if (!ok) {
                i++
                continue
            }
            i += need
        }
        val text = String(buf, 0, i, Charsets.UTF_8)
        val leftover = buf.copyOfRange(i, len)
        len = 0
        return text to leftover
    }

    fun restore(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        ensure(bytes.size)
        System.arraycopy(bytes, 0, buf, 0, bytes.size)
        len = bytes.size
    }

    private fun ensure(extra: Int) {
        if (len + extra <= buf.size) return
        var cap = buf.size
        while (cap < len + extra) cap *= 2
        buf = buf.copyOf(cap)
    }
}

private fun IntArray.append(value: Int): IntArray {
    val out = copyOf(size + 1)
    out[size] = value
    return out
}
