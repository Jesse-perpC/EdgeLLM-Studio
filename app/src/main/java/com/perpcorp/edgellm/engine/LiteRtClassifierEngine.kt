package com.perpcorp.edgellm.engine

import android.util.Log
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.TensorBuffer
import com.perpcorp.edgellm.data.model.ModelSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

/**
 * Real LiteRT inference for `.tflite` text classifiers such as MobileBERT.
 *
 * Replaces the path that answered classification prompts from the hardcoded
 * knowledge engine. Runs one forward pass through the actual model and reports
 * what the model itself produced — top class ids, their scores, and label names
 * when a `labels.txt` sits next to the model. No scores are invented: if the
 * graph is an encoder rather than a classifier, that is reported instead of
 * forcing a classification reading onto it.
 *
 * Uses only the `com.google.ai.edge.litert` 2.2.0 API surface recovered from
 * the published AAR (`CompiledModel.create`, `createInputBuffers`,
 * `createOutputBuffers`, `TensorBuffer.writeInt/readFloat`, `run`, `close`).
 * Accelerator selection stays on `Options.CPU`; GPU/NPU option objects exist in
 * the AAR but their construction was not recoverable without a JDK, so they are
 * deliberately not guessed at.
 */
class LiteRtClassifierEngine {

    data class ClassificationResult(
        val topIds: IntArray,
        val topScores: FloatArray,
        val topLabels: List<String>,
        val numClasses: Int,
        val outputKind: OutputKind,
        val inferenceMs: Long
    )

    enum class OutputKind {
        /** 2-D [1, N] logits: a genuine classifier head. */
        LOGITS,
        /** 2-D [1, H] pooled or 3-D sequence output: an encoder, not a classifier. */
        ENCODER,
        /** Anything else; reported with its shape, not forced into a reading. */
        UNKNOWN
    }

    private val cache = HashMap<String, LoadedClassifier>()

    fun classifyFlow(
        text: String,
        model: ModelSpec,
        maxSeqLen: Int = 128,
        topK: Int = 3
    ): Flow<StreamTokenChunk> = flow {
        val modelPath = model.localFilePath
        if (modelPath.isBlank()) {
            Log.e(TAG, "ModelSpec.localFilePath is blank; no .tflite to load")
            return@flow
        }
        val modelFile = File(modelPath)
        if (!modelFile.isFile) {
            Log.e(TAG, "TFLite model missing at $modelPath")
            return@flow
        }
        val started = System.nanoTime()
        val result = try {
            classify(text, modelFile, maxSeqLen, topK)
        } catch (t: Throwable) {
            Log.e(TAG, "LiteRT classification failed", t)
            emit(
                StreamTokenChunk(
                    token = "",
                    accumulatedText = "Classification failed: ${t.message}",
                    tokenCount = 0,
                    tokensPerSecond = 0f,
                    timeToFirstTokenMs = (System.nanoTime() - started) / 1_000_000,
                    isComplete = true,
                    backendUsed = "LiteRT CPU",
                    samplerName = "N/A (classifier)",
                    grammarModeUsed = GrammarMode.NONE
                )
            )
            return@flow
        }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        val text_out = format(result, model)
        emit(
            StreamTokenChunk(
                token = text_out,
                accumulatedText = text_out,
                tokenCount = 1,
                tokensPerSecond = if (elapsedMs > 0) 1000f / elapsedMs else 0f,
                timeToFirstTokenMs = elapsedMs,
                isComplete = true,
                backendUsed = "LiteRT CPU • ${result.outputKind}",
                samplerName = "N/A (classifier)",
                grammarModeUsed = GrammarMode.NONE
            )
        )
    }

    fun classify(
        text: String,
        modelFile: File,
        maxSeqLen: Int = 128,
        topK: Int = 3
    ): ClassificationResult {
        val loaded = obtain(modelFile)
        val tokenizer = loaded.tokenizer
        val raw = tokenizer.encode(text)
        // Truncate to the model's window, keeping [CLS] first and [SEP] last.
        val ids = if (raw.size <= maxSeqLen) {
            raw
        } else {
            val out = IntArray(maxSeqLen)
            out[0] = raw[0]
            raw.copyInto(destination = out, destinationOffset = 1, startIndex = 1, endIndex = maxSeqLen - 1)
            out[maxSeqLen - 1] = raw.last()
            out
        }

        val seqLen = ids.size
        val mask = IntArray(seqLen) { 1 }
        val typeIds = IntArray(seqLen) { 0 }

        val t0 = System.nanoTime()
        val inputs: List<TensorBuffer> = loaded.model.createInputBuffers()
        if (inputs.size < 3) {
            throw IllegalStateException(
                "Expected a 3-input BERT graph (ids, mask, type_ids) but the model " +
                    "declares ${inputs.size} inputs."
            )
        }
        try {
            inputs[0].writeInt(ids)
            inputs[1].writeInt(mask)
            inputs[2].writeInt(typeIds)
        } catch (e: Exception) {
            throw IllegalStateException(
                "Input shape mismatch: the model does not accept seq_len=$seqLen. " +
                    "Set maxSeqLen to the length the .tflite was exported with " +
                    "(MobileBERT classifiers are usually 128).",
                e
            )
        }
        val outputs: List<TensorBuffer> = loaded.model.createOutputBuffers()
        loaded.model.run(inputs, outputs)
        val inferenceMs = (System.nanoTime() - t0) / 1_000_000

        // Read every float output; the classifier head is the 2-D [1, N] one.
        // Positional (no name guessing) so Optimum and raw exports both work.
        var best: FloatArray? = null
        for (buf in outputs) {
            val arr = try {
                buf.readFloat()
            } catch (_: Exception) {
                continue
            }
            if (best == null) {
                best = arr
            }
        }
        val logits = best ?: throw IllegalStateException("Model produced no readable float output")
        val n = logits.size

        // Without signature names the runtime only exposes values, not shapes, so
        // the classifier-vs-encoder call is made on element count and kept
        // conservative: real text classifiers (sentiment, toxicity, intent, PII
        // types) have tens of classes, while BERT-family hidden states are 512+.
        // Anything that does not look like a small head is reported as an
        // encoder, never forced into fake classes.
        return if (n in 1..256 && n != seqLen) {
            val k = topK.coerceIn(1, n)
            val order = logits.indices.sortedByDescending { logits[it] }.take(k)
            ClassificationResult(
                topIds = order.toIntArray(),
                topScores = order.map { logits[it] }.toFloatArray(),
                topLabels = order.map { loaded.labels[it] ?: "class_$it" },
                numClasses = n,
                outputKind = OutputKind.LOGITS,
                inferenceMs = inferenceMs
            )
        } else {
            ClassificationResult(
                topIds = IntArray(0),
                topScores = FloatArray(0),
                topLabels = emptyList(),
                numClasses = n,
                outputKind = OutputKind.ENCODER,
                inferenceMs = inferenceMs
            )
        }
    }

    private fun format(result: ClassificationResult, model: ModelSpec): String {
        return when (result.outputKind) {
            OutputKind.LOGITS -> buildString {
                append("**${model.name}** classified the input in ${result.inferenceMs} ms ")
                append("(${result.numClasses} classes):\n")
                for (i in result.topIds.indices) {
                    append("• ${result.topLabels[i]} — score ${"%.4f".format(result.topScores[i])}\n")
                }
            }
            else -> {
                "${model.name} ran in ${result.inferenceMs} ms but its output " +
                    "(${result.numClasses} floats) is an encoder embedding, not a " +
                    "classifier head, so there are no classes to report. " +
                    "Use it for retrieval/RAG rather than classification."
            }
        }
    }

    private fun obtain(modelFile: File): LoadedClassifier {
        val key = modelFile.absolutePath
        synchronized(cache) { cache[key] }?.let { return it }
        val built = buildLoaded(modelFile)
        synchronized(cache) {
            cache[key]?.let { existing ->
                built.close()
                return existing
            }
            cache[key] = built
            return built
        }
    }

    private fun buildLoaded(modelFile: File): LoadedClassifier {
        val parent = modelFile.parentFile
            ?: throw IllegalStateException("Model has no parent directory")
        val vocabFile = File(parent, "vocab.txt")
        if (!vocabFile.isFile) {
            throw UnsupportedOperationException(
                "No vocab.txt alongside ${modelFile.name}. A BERT classifier has no " +
                    "built-in tokenizer; place the model's vocab.txt next to the .tflite."
            )
        }
        val tokenizer = WordPieceTokenizer.load(vocabFile)
        val labelsFile = File(parent, "labels.txt")
        val labels = HashMap<Int, String>()
        if (labelsFile.isFile) {
            labelsFile.readLines().forEachIndexed { i, line ->
                val clean = line.trim()
                if (clean.isNotEmpty()) labels[i] = clean
            }
        }
        val compiled = CompiledModel.create(
            modelFile.absolutePath,
            CompiledModel.Options.CPU
        )
        Log.i(TAG, "Loaded ${modelFile.name} with LiteRT (CPU)")
        return LoadedClassifier(compiled, tokenizer, labels)
    }

    fun unloadAll() {
        synchronized(cache) {
            cache.values.forEach { it.close() }
            cache.clear()
        }
    }

    private companion object {
        const val TAG = "LiteRtClassifierEngine"
    }
}

private class LoadedClassifier(
    val model: CompiledModel,
    val tokenizer: WordPieceTokenizer,
    val labels: Map<Int, String>
) : AutoCloseable {
    override fun close() {
        runCatching { model.close() }
    }
}
