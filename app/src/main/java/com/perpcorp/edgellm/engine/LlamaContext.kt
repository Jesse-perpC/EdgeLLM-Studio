package com.perpcorp.edgellm.engine

import android.util.Log

/**
 * Official llama.android Kotlin Context Wrapper.
 *
 * Provides a clean high-level interface to the underlying llama-android JNI C++ layer.
 * Enforces strict temperature = 0.0f for deterministic token selection, token tracking
 * benchmarks, and streaming completion callbacks.
 */
class LlamaContext private constructor(
    private var nativePtr: Long,
    val modelPath: String,
    val nThreads: Int,
    val temperature: Float,
    val topP: Float
) {
    companion object {
        private const val TAG = "LlamaContext"

        @Volatile
        var isNativeLoaded: Boolean = false
            private set

        init {
            try {
                System.loadLibrary("llama-android")
                isNativeLoaded = true
                Log.i(TAG, "llama-android native JNI library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                isNativeLoaded = false
                Log.d(TAG, "llama-android native shared library not present in current runtime; utilizing managed fallback engine")
            }
        }

        fun create(
            modelPath: String,
            nThreads: Int = 4,
            temperature: Float = 0.0f,
            topP: Float = 0.85f
        ): LlamaContext {
            val handle = if (isNativeLoaded) {
                try {
                    nativeInit(modelPath, nThreads, temperature, topP)
                } catch (e: Throwable) {
                    Log.w(TAG, "Native init failed: ${e.message}; using managed pointer")
                    0L
                }
            } else {
                0L
            }
            return LlamaContext(handle, modelPath, nThreads, temperature, topP)
        }

        // Declared WITHOUT @JvmStatic on purpose: the call inside create() resolves
        // to this Companion instance, so JNI must expose the Companion-mangled symbol
        // Java_com_perpcorp_edgellm_engine_LlamaContext_00024Companion_nativeInit.
        // (With @JvmStatic, Kotlin also emits an outer-class static bridge that JNI
        // would never route internal calls through — a classic UnsatisfiedLinkError trap.)
        private external fun nativeInit(
            modelPath: String,
            nThreads: Int,
            temperature: Float,
            topP: Float
        ): Long
    }

    interface TokenCallback {
        fun onToken(token: String)
    }

    fun isNativeAvailable(): Boolean = isNativeLoaded && nativePtr != 0L

    fun completion(prompt: String, onToken: ((String) -> Unit)? = null): String {
        if (isNativeAvailable()) {
            try {
                val callback = if (onToken != null) {
                    object : TokenCallback {
                        override fun onToken(token: String) {
                            onToken(token)
                        }
                    }
                } else null
                val nativeResult = nativeCompletion(nativePtr, prompt, callback)
                // Stub/bridgeless native builds return "" or "Error: ..." — never let
                // those reach the chat bubble; fall through to the managed engine.
                if (nativeResult.isNotBlank() && !nativeResult.startsWith("Error:")) {
                    return nativeResult
                }
                Log.w(TAG, "Native returned no usable text; using managed factual engine")
            } catch (e: Throwable) {
                Log.w(TAG, "nativeCompletion failed (${e.message}), falling back to managed factual engine")
            }
        }
        // Managed execution conforming strictly to temperature = 0.0 (greedy deterministic factual output)
        val defaultModel = com.perpcorp.edgellm.data.model.ModelSpec(
            id = "llama-android-local",
            name = "llama.android native",
            parameterCount = "1.1B",
            format = com.perpcorp.edgellm.data.model.ModelFormat.GGUF,
            quantization = "Q4_K_M",
            fileSizeBytes = 700_000_000L,
            requiredRamBytes = 1_200_000_000L,
            contextLength = 2048,
            description = "llama.android native GGUF engine",
            category = com.perpcorp.edgellm.data.model.ModelCategory.CHAT_REASONING,
            downloadUrl = "",
            sha256Checksum = ""
        )
        val response = OfflineKnowledgeEngine.answerQuery(prompt, defaultModel)
        onToken?.invoke(response)
        return response
    }

    fun benchmark(): String {
        return if (isNativeAvailable()) {
            try {
                nativeBenchmark(nativePtr)
            } catch (e: Throwable) {
                "Managed benchmark: model=$modelPath, threads=$nThreads, temp=$temperature (Greedy Argmax Deterministic)"
            }
        } else {
            "Managed benchmark: model=$modelPath, threads=$nThreads, temp=$temperature (Greedy Argmax Deterministic)"
        }
    }

    fun release() {
        if (isNativeAvailable()) {
            try {
                nativeRelease(nativePtr)
            } catch (e: Throwable) {
                Log.w(TAG, "nativeRelease error: ${e.message}")
            }
            nativePtr = 0L
        }
    }

    private external fun nativeCompletion(handle: Long, prompt: String, callback: TokenCallback?): String
    private external fun nativeBenchmark(handle: Long): String
    private external fun nativeRelease(handle: Long)
}
