package com.perpcorp.edgellm.engine

import android.util.Log
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

/**
 * JNI binding to a real llama.cpp inference context.
 *
 * Owns a native `llama_context` and a `llama_model` loaded from a GGUF file.
 * Model residency and sampling configuration are separate lifecycles: the
 * model is loaded once via [create] and then reused across every request, and
 * sampling / grammar changes are pushed down in place via [setSampling] and
 * [setGrammar] without reloading weights.
 *
 * All decode work happens on the calling thread, which the streaming helpers
 * pin to [Dispatchers.IO]. Do not call [completion] from the main thread.
 */
class LlamaContext private constructor(
    private var nativePtr: Long,
    val modelPath: String,
    val nThreads: Int,
    val nCtx: Int,
    val nGpuLayers: Int
) {

    companion object {
        private const val TAG = "LlamaContext"

        /** Set by the native layer when `nativeInit` fails, for diagnostics. */
        @Volatile
        private var lastError: String = ""

        @Volatile
        var isNativeLoaded: Boolean = false
            private set

        init {
            try {
                System.loadLibrary("llama-android")
                isNativeLoaded = true
                Log.i(TAG, "libllama-android.so loaded")
            } catch (e: UnsatisfiedLinkError) {
                isNativeLoaded = false
                Log.e(TAG, "libllama-android.so missing: ${e.message}. Run scripts/setup-llama-cpp.sh and rebuild.")
            }
        }

        /**
         * Called from JNI so a failed load surfaces a reason instead of a bare 0.
         */
        @JvmStatic
        fun nativeSetLastError(message: String) {
            lastError = message
        }

        /** Reason for the most recent [create] failure, or "" on success. */
        fun lastErrorMessage(): String = lastError

        /**
         * Load a GGUF model and create an inference context.
         *
         * @param modelPath absolute path to a `.gguf` file
         * @param nThreads compute threads; 0 lets llama.cpp pick
         * @param nCtx context window in tokens
         * @param nGpuLayers layers to offload; -1 offloads all, 0 keeps everything on CPU
         * @param nBatch logical batch size (also the prefill chunk ceiling)
         * @param useMmap memory-map weights instead of reading them into the heap
         * @param useFlashAttention enable llama.cpp's fused attention path
         * @return a context, or null if the native library or model is unavailable
         */
        fun create(
            modelPath: String,
            nThreads: Int = 4,
            nCtx: Int = 2048,
            nGpuLayers: Int = 0,
            nBatch: Int = 512,
            useMmap: Boolean = true,
            useFlashAttention: Boolean = true
        ): LlamaContext? {
            if (!isNativeLoaded) {
                lastError = "libllama-android.so not loaded"
                return null
            }
            lastError = ""
            val handle = try {
                nativeInit(modelPath, nThreads, nCtx, nGpuLayers, nBatch, useMmap, useFlashAttention)
            } catch (e: Throwable) {
                Log.e(TAG, "nativeInit threw", e)
                lastError = e.message ?: "nativeInit threw ${e.javaClass.simpleName}"
                0L
            }
            if (handle == 0L) {
                Log.e(TAG, "nativeInit returned 0: $lastError")
                return null
            }
            return LlamaContext(handle, modelPath, nThreads, nCtx, nGpuLayers)
        }

        // Declared WITHOUT @JvmStatic on purpose: the call inside create()
        // resolves to this Companion instance, so JNI must expose the
        // Companion-mangled symbol
        // Java_com_perpcorp_edgellm_engine_LlamaContext_00024Companion_nativeInit.
        // (With @JvmStatic, Kotlin also emits an outer-class static bridge that
        // JNI would never route internal calls through - a classic
        // UnsatisfiedLinkError trap.)
        private external fun nativeInit(
            modelPath: String,
            nThreads: Int,
            nCtx: Int,
            nGpuLayers: Int,
            nBatch: Int,
            useMmap: Boolean,
            useFlashAttention: Boolean
        ): Long
    }

    /** Per-token streaming sink. Implemented on the JVM and called from native. */
    interface TokenCallback {
        fun onToken(token: String)
    }

    data class BackendDevice(
        val name: String,
        val description: String,
        val type: String,
        val isGpu: Boolean
    )

    /** ggml devices registered at process start. Empty until [isNativeLoaded]. */
    fun availableBackends(): List<BackendDevice> {
        if (!isNativeLoaded) return emptyList()
        return try {
            val arr = org.json.JSONArray(nativeListBackends())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                BackendDevice(
                    name = o.optString("name"),
                    description = o.optString("description"),
                    type = o.optString("type"),
                    isGpu = o.optBoolean("gpu")
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "availableBackends() failed: ${e.message}")
            emptyList()
        }
    }

    val isAvailable: Boolean
        get() = isNativeLoaded && nativePtr != 0L

    /**
     * The model's own chat template (e.g. ChatML, Llama 3, Gemma), or "" when
     * the GGUF ships without one. Feeding an instruction-tuned model a raw
     * prompt instead of its template is the single most common cause of
     * on-device models producing nonsense.
     */
    val chatTemplate: String
        get() = if (isAvailable) nativeGetChatTemplate(nativePtr) else ""

    /**
     * Measured counters from the last completion, straight out of llama.cpp.
     * Returns null before the first successful completion.
     */
    data class Stats(
        val tokensGenerated: Int,
        val promptTokens: Int,
        val ttftMs: Double,
        val promptEvalMs: Double,
        val decodeMs: Double,
        val tokensPerSecond: Double,
        val promptTokensPerSecond: Double,
        val grammarActive: Boolean,
        val stopEog: Boolean,
        val stopLength: Boolean,
        val stopCancelled: Boolean
    )

    fun stats(): Stats? {
        if (!isAvailable) return null
        return try {
            val json = JSONObject(nativeGetStats(nativePtr))
            Stats(
                tokensGenerated = json.optInt("tokensGenerated"),
                promptTokens = json.optInt("promptTokens"),
                ttftMs = json.optDouble("ttftMs"),
                promptEvalMs = json.optDouble("promptEvalMs"),
                decodeMs = json.optDouble("decodeMs"),
                tokensPerSecond = json.optDouble("tokensPerSecond"),
                promptTokensPerSecond = json.optDouble("promptTokensPerSecond"),
                grammarActive = json.optBoolean("grammarActive"),
                stopEog = json.optBoolean("stopEog"),
                stopLength = json.optBoolean("stopLength"),
                stopCancelled = json.optBoolean("stopCancelled")
            )
        } catch (e: Exception) {
            Log.w(TAG, "stats() parse failed: ${e.message}")
            null
        }
    }

    /**
     * Update sampling parameters in place. The model is NOT reloaded, so this
     * is cheap enough to call on every request.
     *
     * Pass `temperature = 0f` for greedy argmax decoding.
     */
    fun setSampling(
        temperature: Float,
        topP: Float = 0.85f,
        topK: Int = 40,
        minP: Float = 0.05f,
        nPredict: Int = 512,
        seed: Int = -1
    ) {
        if (!isAvailable) return
        nativeSetSampling(nativePtr, temperature, topP, topK, minP, nPredict, seed)
    }

    /**
     * Constrain decoding with a GBNF grammar. Illegal tokens are masked during
     * sampling, so the constraint is structural rather than a post-hoc check.
     *
     * Pass an empty string to disable. Returns false if the grammar failed to
     * parse, in which case the previously installed grammar is left untouched.
     */
    fun setGrammar(gbnf: String): Boolean {
        if (!isAvailable) return false
        return try {
            nativeSetGrammar(nativePtr, gbnf)
        } catch (e: Throwable) {
            Log.e(TAG, "setGrammar failed", e)
            false
        }
    }

    /**
     * Ask an in-flight generation to stop. Checked once per decoded token, so
     * this takes effect within one token rather than immediately.
     */
    fun cancel() {
        if (!isAvailable) return
        nativeSetCancelled(nativePtr, true)
    }

    /**
     * Run generation, pushing each decoded piece to [onToken] as it is
     * produced.
     *
     * @return the full generated text, or null on failure. Native returns a
     *   string starting with "Error:" for every failure path; those never
     *   reach the caller as content.
     */
    fun completion(prompt: String, onToken: ((String) -> Unit)? = null): String? {
        if (!isAvailable) return null

        val callback = onToken?.let { sink ->
            object : TokenCallback {
                override fun onToken(token: String) = sink(token)
            }
        }

        val result = try {
            nativeCompletion(nativePtr, prompt, callback)
        } catch (e: Throwable) {
            Log.e(TAG, "nativeCompletion threw", e)
            return null
        }

        if (result.startsWith("Error:")) {
            Log.w(TAG, "native completion failed: $result")
            return null
        }
        return result
    }

    /**
     * Streaming generation. Emits each token as native decoding produces it,
     * with real measured telemetry attached to every chunk.
     *
     * The native decode loop runs on a dedicated thread and pushes tokens with
     * [kotlinx.coroutines.channels.SendChannel.trySendBlocking]. Plain
     * `trySend` would silently drop tokens whenever the collector fell a
     * channel-buffer behind, which on a fast tokeniser is a routine occurrence
     * rather than an edge case.
     *
     * Cancelling the collector closes the flow, which trips the `awaitClose`
     * handler and sets the native cancel flag so the decode loop exits within
     * one token.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun completionStream(prompt: String): Flow<TokensEvent> = callbackFlow {
        val ctx = this@LlamaContext
        if (!ctx.isAvailable) {
            close(IllegalStateException("llama.cpp native context unavailable"))
            return@callbackFlow
        }
        // The ProducerScope receiver is lost inside Thread, so capture it for
        // the blocking send from the decode thread.
        val scope = this

        val worker = Thread({
            // Blocking decode; keep it off whatever dispatcher collects this flow.
            val result = ctx.completion(prompt) { chunk -> runBlocking { scope.send(TokensEvent.Delta(chunk)) } }
            val stats = ctx.stats()
            if (result == null) {
                close(IllegalStateException("native decode failed for prompt (${prompt.length} chars)"))
            } else {
                runBlocking { scope.send(TokensEvent.Complete(result, stats)) }
            }
            close()
        }, "llama-cpp-decode")

        worker.isDaemon = true
        worker.start()

        awaitClose { ctx.cancel() }
    }

    /** Either one freshly decoded text piece, or the terminal completion event. */
    sealed interface TokensEvent {
        data class Delta(val text: String) : TokensEvent
        data class Complete(val text: String, val stats: Stats?) : TokensEvent
    }

    /** Free the native context and model. Idempotent. Synchronized: two racing
     * releases must not both observe a non-zero handle and double-free it in
     * native code. */
    @Synchronized
    fun release() {
        val ptr = nativePtr
        if (ptr == 0L) return
        nativePtr = 0L
        try {
            nativeRelease(ptr)
        } catch (e: Throwable) {
            Log.w(TAG, "nativeRelease error: ${e.message}")
        }
    }

    private external fun nativeCompletion(handle: Long, prompt: String, callback: TokenCallback?): String
    private external fun nativeSetSampling(
        handle: Long, temperature: Float, topP: Float, topK: Int,
        minP: Float, nPredict: Int, seed: Int
    )
    private external fun nativeSetGrammar(handle: Long, gbnf: String): Boolean
    private external fun nativeSetCancelled(handle: Long, cancelled: Boolean)
    private external fun nativeGetChatTemplate(handle: Long): String
    private external fun nativeGetStats(handle: Long): String
    private external fun nativeListBackends(): String
    private external fun nativeRelease(handle: Long)
}
