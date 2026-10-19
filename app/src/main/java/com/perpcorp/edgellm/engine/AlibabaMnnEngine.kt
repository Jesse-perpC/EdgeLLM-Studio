package com.perpcorp.edgellm.engine

import android.util.Log
import android.content.Context
import com.perpcorp.edgellm.data.model.AiPersona
import com.perpcorp.edgellm.data.model.ModelSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

/**
 * Alibaba MNN (Mobile Neural Network) & MNN-LLM Inference Engine.
 *
 * **Status: UNAVAILABLE.**
 *
 * Alibaba publishes no Maven artifact for MNN (verified absent from Maven
 * Central, JitPack, Aliyun, and Google Maven). The reference Android app
 * resolves its native libraries through a Gradle *download* plugin, and the
 * LLM runtime is a separate C++-only build target (`MNN_LLM=ON`) that the
 * stock Android release does not include.
 *
 * This engine deliberately does NOT simulate inference. Every call reports
 * an explicit unavailable result with the concrete reason, so callers can
 * surface an honest error instead of presenting synthesized text as model
 * output. See scripts/setup-mnn.md for what is needed to wire this up.
 *
 * When MNN is eventually linked, the API to target (read from `master`) is:
 *   `MNN::llm::Llm::createLLM / load / response / generate_init / generate / stoped / reset`
 *   `LlmContext` exposes `prefill_us`, `decode_us`, `ttfa_us`, `history_tokens`,
 *   `output_tokens`, `generate_str`, and a `LlmStatus` enum — everything needed
 *   to replace the fabricated timing that used to be emitted here.
 */
class AlibabaMnnEngine(private val context: Context? = null) {

    enum class MnnBackend(val displayName: String, val shortName: String) {
        CPU_NEON("CPU (ARM NEON + FP16/i8mm)", "MNN-CPU"),
        OPENCL("OpenCL (Adreno/Mali Direct)", "MNN-OpenCL"),
        VULKAN("Vulkan Compute Shaders", "MNN-Vulkan"),
        NPU_NNAPI("NPU (Snapdragon HTP / MediaTek APU)", "MNN-NPU")
    }

    enum class MnnQuantization(val displayName: String, val bitsPerWeight: Float, val memoryRatio: Float) {
        W4A16("W4A16 (4-Bit Weights, 16-Bit Act)", 4.0f, 0.28f),
        W8A16("W8A16 (8-Bit Weights, 16-Bit Act)", 8.0f, 0.55f),
        FP16("FP16 (Half-Precision Float)", 16.0f, 1.0f),
        KV_INT8("Dynamic KV Cache INT8 Compressed", 8.0f, 0.45f)
    }

    data class MnnLlmConfig(
        val backend: MnnBackend = MnnBackend.OPENCL,
        val quantization: MnnQuantization = MnnQuantization.W4A16,
        val threadCount: Int = 4,
        val enablePromptCache: Boolean = true,
        val enableChunkedPrefill: Boolean = true,
        val chunkedPrefillBatch: Int = 256,
        val kvCacheLimitTokens: Int = 4096,
        val enableVisionMultimodal: Boolean = false
    )

    data class MnnTokenChunk(
        val token: String,
        val accumulatedText: String,
        val tokenCount: Int,
        val currentDecodeSpeedTps: Float,
        val prefillSpeedTps: Float,
        val timeToFirstTokenMs: Long,
        val peakMemoryMb: Long,
        val isComplete: Boolean,
        val backendUsed: MnnBackend,
        val promptCacheHit: Boolean,
        val isError: Boolean = false,
        val errorMessage: String? = null
    )

    /**
     * Streaming generation. Always reports unavailable — no simulation, no
     * fallback text, no fabricated tok/s. Callers should treat an
     * [MnnTokenChunk] with [isError] == true as a hard failure.
     */
    fun generateStreamingResponse(
        prompt: String,
        config: MnnLlmConfig,
        model: ModelSpec? = null,
        persona: AiPersona? = null
    ): Flow<MnnTokenChunk> = flow {
        emit(
            MnnTokenChunk(
                token = "",
                accumulatedText = "",
                tokenCount = 0,
                currentDecodeSpeedTps = 0f,
                prefillSpeedTps = 0f,
                timeToFirstTokenMs = 0L,
                peakMemoryMb = 0L,
                isComplete = true,
                backendUsed = config.backend,
                promptCacheHit = false,
                isError = true,
                errorMessage = unavailableReason()
            )
        )
    }

    private fun unavailableReason(): String =
        "MNN-LLM is not linked into this build. Alibaba publishes no Maven " +
            "artifact for MNN; the LLM runtime is a separate C++-only build " +
            "target (MNN_LLM=ON) that the stock Android release does not " +
            "include. See scripts/setup-mnn.md for the steps to wire it up, " +
            "or use a GGUF / MediaPipe / ONNX model instead."

    companion object {
        private const val TAG = "MnnEngine"

        @Volatile var nativeLibraryLoaded: Boolean = false
            private set
        @Volatile var nativeLibraryError: String? = null
            private set

        init {
            try {
                System.loadLibrary("mnn")
                System.loadLibrary("mnn_llm")
                System.loadLibrary("edgellm_mnn_jni")
                nativeLibraryLoaded = true
                Log.i(TAG, "MNN-LLM native runtime loaded")
            } catch (t: UnsatisfiedLinkError) {
                nativeLibraryLoaded = false
                nativeLibraryError = t.message ?: "libmnn_llm.so / libedgellm_mnn_jni.so not found"
                Log.w(TAG, "MNN-LLM native runtime NOT available: $nativeLibraryError")
            }
        }

        fun isRuntimeAvailable(): Boolean = nativeLibraryLoaded

        private external fun nativeCreateSession(
            modelPath: String,
            threads: Int,
            useVulkan: Boolean
        ): Long
        private external fun nativeFeedPrompt(handle: Long, prompt: String)
        private external fun nativeFetchNextToken(handle: Long): String?
        private external fun nativeReset(handle: Long)
        private external fun nativeDestroy(handle: Long)
    }
}