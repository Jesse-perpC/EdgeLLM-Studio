package com.example.engine

import com.example.data.model.ModelCategory
import com.example.data.model.ModelFormat
import com.example.data.model.ModelSpec

/**
 * Extension -> ModelFormat routing. Dependency-free: only maps to the engine
 * wrappers owned by LocalInferenceEngine. Never renames LocalInferenceEngine.
 */
enum class InferenceBackend(val displayName: String) {
    MEDIAPIPE_LITERT("MediaPipe / LiteRT (real weights)"),
    GGUF_LLAMACPP("llama.cpp GGUF (native required)"),
    ONNX_RUNTIME("ONNX Runtime (native required)"),
    TFLITE_CLASSIFIER("TFLite classifier (non-chat)"),
    UNSUPPORTED("Unsupported")
}

object InferenceFactory {

    fun determineModelType(filePath: String): ModelFormat {
        val lower = filePath.lowercase()
        return when {
            lower.endsWith(".gguf") -> ModelFormat.GGUF
            lower.endsWith(".task") -> ModelFormat.MEDIAPIPE_TASK
            // .bin is ambiguous: MediaPipe LLM bundles use .bin, but so do raw
            // checkpoints. Default to MEDIAPIPE_TASK; LocalInferenceEngine verifies
            // the file actually loads before claiming real inference.
            lower.endsWith(".bin") -> ModelFormat.MEDIAPIPE_TASK
            lower.endsWith(".tflite") -> ModelFormat.TFLITE
            lower.endsWith(".onnx") || lower.endsWith(".ort") -> ModelFormat.ONNX
            lower.endsWith(".mnn") -> ModelFormat.MNN_LLM
            lower.endsWith(".safetensors") || lower.endsWith(".ckpt") || lower.endsWith(".pt") ->
                throw IllegalArgumentException("SafeTensors / PyTorch checkpoints ($filePath) cannot run directly on mobile. Convert to .gguf or MediaPipe .task first.")
            else -> throw IllegalArgumentException("Unsupported on-device runtime format: $filePath")
        }
    }

    fun backendForFormat(format: ModelFormat, category: ModelCategory? = null): InferenceBackend {
        return when (format) {
            ModelFormat.MEDIAPIPE_TASK -> InferenceBackend.MEDIAPIPE_LITERT
            // Chat-capable LiteRT .tflite LLMs share the tasks-genai runtime.
            // Pure classifier/embedding TFLITEs stay on the non-chat path.
            ModelFormat.TFLITE ->
                if (category == ModelCategory.CHAT_REASONING) InferenceBackend.MEDIAPIPE_LITERT
                else InferenceBackend.TFLITE_CLASSIFIER
            ModelFormat.GGUF -> InferenceBackend.GGUF_LLAMACPP
            ModelFormat.ONNX -> InferenceBackend.ONNX_RUNTIME
            ModelFormat.MNN_LLM, ModelFormat.ANDROID_AICORE -> InferenceBackend.UNSUPPORTED
        }
    }

    fun describeRoute(filePath: String, category: ModelCategory? = null): String {
        return try {
            val format = determineModelType(filePath)
            "Route $filePath -> ${backendForFormat(format, category).displayName}"
        } catch (e: IllegalArgumentException) {
            "Route $filePath -> unsupported (${e.message})"
        }
    }

    /** True only when this model can attempt real weight inference today. */
    fun canRunRealWeights(spec: ModelSpec): Boolean {
        val backend = backendForFormat(spec.format, spec.category)
        if (backend != InferenceBackend.MEDIAPIPE_LITERT) return false
        val path = spec.localFilePath.ifBlank { return false }
        return path.lowercase().endsWith(".task") || path.lowercase().endsWith(".bin")
    }
}
