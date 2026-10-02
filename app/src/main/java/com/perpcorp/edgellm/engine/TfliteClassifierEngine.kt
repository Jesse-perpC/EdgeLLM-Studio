package com.perpcorp.edgellm.engine

import android.util.Log
import com.perpcorp.edgellm.data.model.ComputeBackend
import com.perpcorp.edgellm.data.model.HardwareAccelerationSettings
import com.perpcorp.edgellm.data.model.ModelSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.File

/**
 * Real TensorFlow Lite inference for `.tflite` text classifiers such as
 * MobileBERT.
 *
 * Replaces the path that answered classification prompts from the hardcoded
 * knowledge engine, and replaces the earlier LiteRT-based implementation:
 * LiteRT 2.2.0's runtime and api AARs declare the same manifest namespace,
 * which fails the build, and no fixed release exists.
 *
 * Runs one forward pass through the actual model and reports what the model
 * itself produced — top class ids, their scores, and label names when a
 * `labels.txt` sits next to the model. If the graph is an encoder rather than
 * a classifier, that is reported instead of forcing a classification reading
 * onto it. No scores are invented.
 */
class TfliteClassifierEngine {

    data class ClassificationResult(
        val topIds: IntArray,
        val topScores: FloatArray,
        val topLabels: List<String>,
        val numClasses: Int,
        val outputKind: OutputKind,
        val inferenceMs: Long,
        val backendUsed: String
    )

    enum class OutputKind {
        /** 2-D [1, N] float head: a genuine classifier. */
        LOGITS,
        /** Anything else: an encoder embedding, not a classifier. */
        ENCODER
    }

    private val cache = HashMap<String, LoadedClassifier>()

    fun classifyFlow(
        text: String,
        model: ModelSpec,
        settings: HardwareAccelerationSettings,
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
            classify(text, modelFile, settings, maxSeqLen, topK)
        } catch (t: Throwable) {
            Log.e(TAG, "TFLite classification failed", t)
            emit(
                StreamTokenChunk(
                    token = "",
                    accumulatedText = "Classification failed: ${t.message}",
                    tokenCount = 0,
                    tokensPerSecond = 0f,
                    timeToFirstTokenMs = (System.nanoTime() - started) / 1_000_000,
                    isComplete = true,
                    backendUsed = "TFLite (failed)",
                    samplerName = "N/A (classifier)",
                    grammarModeUsed = GrammarMode.NONE
                )
            )
            return@flow
        }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        val textOut = format(result, model)
        emit(
            StreamTokenChunk(
                token = textOut,
                accumulatedText = textOut,
                tokenCount = 1,
                tokensPerSecond = if (elapsedMs > 0) 1000f / elapsedMs else 0f,
                timeToFirstTokenMs = elapsedMs,
                isComplete = true,
                backendUsed = result.backendUsed,
                samplerName = "N/A (classifier)",
                grammarModeUsed = GrammarMode.NONE
            )
        )
    }

    fun classify(
        text: String,
        modelFile: File,
        settings: HardwareAccelerationSettings,
        maxSeqLen: Int = 128,
        topK: Int = 3
    ): ClassificationResult {
        val loaded = obtain(modelFile, settings)
        val tokenizer = loaded.tokenizer
        val raw = tokenizer.encode(text)
        // Truncate to the model's window, keeping [CLS] first and [SEP] last.
        val seqLen = loaded.seqLen
        val ids = if (raw.size <= seqLen) {
            raw
        } else {
            val out = IntArray(seqLen)
            out[0] = raw[0]
            raw.copyInto(destination = out, destinationOffset = 1, startIndex = 1, endIndex = seqLen - 1)
            out[seqLen - 1] = raw.last()
            out
        }

        val ids2d = Array(1) { ids }
        val mask2d = Array(1) { IntArray(seqLen) { 1 } }
        val type2d = Array(1) { IntArray(seqLen) { 0 } }

        val t0 = System.nanoTime()
        val n = loaded.numClasses
        val logits2d = Array(1) { FloatArray(n) }
        val outputs = HashMap<Int, Any>()
        outputs[0] = logits2d
        try {
            loaded.interpreter.runForMultipleInputsOutputs(
                arrayOf(ids2d, mask2d, type2d),
                outputs
            )
        } catch (e: Exception) {
            throw IllegalStateException(
                "TFLite run failed: ${e.message}. The model may expect a different " +
                    "input layout than BERT ids/mask/type_ids.",
                e
            )
        }
        val inferenceMs = (System.nanoTime() - t0) / 1_000_000
        val logits = logits2d[0]

        // Conservative head check, same rule as before: real text classifiers
        // have tens of classes while BERT hidden states are 512+. Anything
        // that does not look like a small head is reported as an encoder.
        return if (n in 1..256) {
            val k = topK.coerceIn(1, n)
            val order = logits.indices.sortedByDescending { logits[it] }.take(k)
            ClassificationResult(
                topIds = order.toIntArray(),
                topScores = order.map { logits[it] }.toFloatArray(),
                topLabels = order.map { loaded.labels[it] ?: "class_$it" },
                numClasses = n,
                outputKind = OutputKind.LOGITS,
                inferenceMs = inferenceMs,
                backendUsed = loaded.backendLabel
            )
        } else {
            ClassificationResult(
                topIds = IntArray(0),
                topScores = FloatArray(0),
                topLabels = emptyList(),
                numClasses = n,
                outputKind = OutputKind.ENCODER,
                inferenceMs = inferenceMs,
                backendUsed = loaded.backendLabel
            )
        }
    }

    private fun format(result: ClassificationResult, model: ModelSpec): String {
        return when (result.outputKind) {
            OutputKind.LOGITS -> buildString {
                append("**${model.name}** classified the input in ${result.inferenceMs} ms ")
                append("(${result.numClasses} classes, ${result.backendUsed}):\n")
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

    private fun obtain(modelFile: File, settings: HardwareAccelerationSettings): LoadedClassifier {
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
    ): LoadedClassifier {
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

        val wantNnapi = settings.computeBackend == ComputeBackend.NPU_NNAPI
        // Best-effort NNAPI: if the device has no NNAPI driver, allocation
        // throws and we fall back to CPU+XNNPACK rather than failing.
        var backendLabel = "TFLite CPU+XNNPACK"
        val interpreter: Interpreter = if (wantNnapi) {
            try {
                Interpreter(
                    modelFile,
                    Interpreter.Options()
                        .setNumThreads(settings.threadCount.coerceIn(1, 8))
                        .setUseXNNPACK(true)
                        .setUseNNAPI(true)
                ).also { it.allocateTensors() }
                    .also { backendLabel = "TFLite NNAPI" }
            } catch (e: Exception) {
                Log.w(TAG, "NNAPI unavailable, falling back to CPU: ${e.message}")
                Interpreter(
                    modelFile,
                    Interpreter.Options()
                        .setNumThreads(settings.threadCount.coerceIn(1, 8))
                        .setUseXNNPACK(true)
                )
            }
        } else {
            Interpreter(
                modelFile,
                Interpreter.Options()
                    .setNumThreads(settings.threadCount.coerceIn(1, 8))
                    .setUseXNNPACK(true)
            )
        }

        // Validate the graph before accepting it: 3 INT32 inputs, one FLOAT32
        // output. Anything else is a clear error naming the mismatch, not a
        // silent misread.
        if (interpreter.inputTensorCount < 3) {
            interpreter.close()
            throw IllegalStateException(
                "Expected a 3-input BERT graph (ids, mask, type_ids) but the model " +
                    "declares ${interpreter.inputTensorCount} inputs."
            )
        }
        for (i in 0..2) {
            if (interpreter.getInputTensor(i).dataType() != DataType.INT32) {
                interpreter.close()
                throw IllegalStateException(
                    "Input $i is ${interpreter.getInputTensor(i).dataType()}, not INT32; " +
                        "this engine drives BERT-style int32 classifiers only."
                )
            }
        }
        if (interpreter.outputTensorCount < 1) {
            interpreter.close()
            throw IllegalStateException("Model declares no outputs.")
        }
        val outShape = interpreter.getOutputTensor(0).shape()
        if (interpreter.getOutputTensor(0).dataType() != DataType.FLOAT32 ||
            outShape.size != 2 || outShape[0] != 1 || outShape[1] <= 0
        ) {
            interpreter.close()
            throw IllegalStateException(
                "Output 0 has shape ${outShape.toList()} and type " +
                    "${interpreter.getOutputTensor(0).dataType()}; expected float " +
                    "[1, N]. Encoder-only graphs are reported, not forced."
            )
        }
        val numClasses = outShape[1]

        // Declared sequence length comes from the graph; fall back to the
        // caller's window when dynamic.
        val declaredSeq = interpreter.getInputTensor(0).shape().getOrNull(1) ?: 0
        val seqLen = if (declaredSeq > 0) declaredSeq else 128
        if (declaredSeq <= 0) {
            try {
                for (i in 0..2) interpreter.resizeInput(i, intArrayOf(1, seqLen))
                interpreter.allocateTensors()
            } catch (e: Exception) {
                interpreter.close()
                throw IllegalStateException(
                    "Model has a dynamic sequence axis but resize to $seqLen failed: " +
                        "${e.message}",
                    e
                )
            }
        }

        Log.i(TAG, "Loaded ${modelFile.name} with $backendLabel (seq=$seqLen, classes=$numClasses)")
        return LoadedClassifier(interpreter, tokenizer, labels, seqLen, numClasses, backendLabel)
    }

    fun unloadAll() {
        synchronized(cache) {
            cache.values.forEach { it.close() }
            cache.clear()
        }
    }

    private companion object {
        const val TAG = "TfliteClassifierEngine"
    }
}

private class LoadedClassifier(
    val interpreter: Interpreter,
    val tokenizer: WordPieceTokenizer,
    val labels: Map<Int, String>,
    val seqLen: Int,
    val numClasses: Int,
    val backendLabel: String
) : AutoCloseable {
    override fun close() {
        runCatching { interpreter.close() }
    }
}
