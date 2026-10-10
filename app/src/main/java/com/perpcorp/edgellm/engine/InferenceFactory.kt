package com.perpcorp.edgellm.engine

import com.perpcorp.edgellm.data.model.ModelFormat

/**
 * Unified routing helper for all five runtimes (GGUF via llama.cpp,
 * MediaPipe/LiteRT, ONNX Runtime, Alibaba MNN, Stable Diffusion).
 *
 * Deliberately dependency-free: it only maps file extensions / [ModelFormat]
 * to the EXISTING engine wrappers owned by [LocalInferenceEngine]
 * (LlamaCppEngine, MediaPipeInferenceEngine, AlibabaMnnEngine,
 * AndroidAICoreEngine + generic ONNX path). It never renames
 * [LocalInferenceEngine] to an interface, so all MainViewModel /
 * OllamaInferenceServer call sites keep compiling with zero changes.
 */
enum class InferenceBackend(val displayName: String) {
    GGUF_LLAMACPP("llama.cpp (GGUF)"),
    MEDIAPIPE_LITERT("MediaPipe / LiteRT"),
    ONNX_RUNTIME("ONNX Runtime"),
    MNN_LLM("Alibaba MNN-LLM"),
    AICORE_GEMINI_NANO("Android AICore (Gemini Nano)"),
    STABLE_DIFFUSION("Stable Diffusion (image)")
}

object InferenceFactory {

    /**
     * Inspect the file extension during import and map to the app's
     * canonical [ModelFormat]. Throws for unsupported formats.
     */
    fun determineModelType(filePath: String): ModelFormat {
        val lower = filePath.lowercase()
        return when {
            lower.endsWith(".gguf") -> ModelFormat.GGUF
            lower.endsWith(".tflite") || lower.endsWith(".task") || lower.endsWith(".bin") -> ModelFormat.MEDIAPIPE_TASK
            lower.endsWith(".onnx") || lower.endsWith(".ort") -> ModelFormat.ONNX
            lower.endsWith(".mnn") -> ModelFormat.MNN_LLM
            lower.endsWith(".safetensors") || lower.endsWith(".ckpt") || lower.endsWith(".pt") ->
                throw IllegalArgumentException("SafeTensors / PyTorch checkpoints ($filePath) cannot run directly on mobile. Please convert to .gguf or LiteRT .task format first.")
            else -> throw IllegalArgumentException("Unsupported on-device runtime format: $filePath")
        }
    }

    /**
     * Map a [ModelFormat] to its native execution backend.
     * Stable Diffusion image models are handled by the image studio pipeline.
     */
    fun backendForFormat(format: ModelFormat, isImageModel: Boolean = false): InferenceBackend {
        if (isImageModel) return InferenceBackend.STABLE_DIFFUSION
        return when (format) {
            ModelFormat.GGUF -> InferenceBackend.GGUF_LLAMACPP
            ModelFormat.MEDIAPIPE_TASK, ModelFormat.TFLITE -> InferenceBackend.MEDIAPIPE_LITERT
            ModelFormat.ONNX -> InferenceBackend.ONNX_RUNTIME
            ModelFormat.MNN_LLM -> InferenceBackend.MNN_LLM
            ModelFormat.ANDROID_AICORE -> InferenceBackend.AICORE_GEMINI_NANO
        }
    }

    /**
     * One-line route description for logs / diagnostics.
     * Chat text itself stays answer-only; this never enters the chat bubble.
     */
    fun describeRoute(filePath: String, isImageModel: Boolean = false): String {
        return try {
            val format = determineModelType(filePath)
            "Route $filePath -> ${backendForFormat(format, isImageModel).displayName}"
        } catch (e: IllegalArgumentException) {
            "Route $filePath -> unsupported (${e.message})"
        }
    }
}
