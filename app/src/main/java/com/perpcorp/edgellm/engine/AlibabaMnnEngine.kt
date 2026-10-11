package com.perpcorp.edgellm.engine

import android.content.Context
import android.util.Log
import com.perpcorp.edgellm.data.model.ComputeBackend
import com.perpcorp.edgellm.data.model.GenerationParameters
import com.perpcorp.edgellm.data.model.HardwareAccelerationSettings
import com.perpcorp.edgellm.data.model.ModelSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Alibaba MNN-LLM engine.
 *
 * This is a REAL integration, not a simulated stub. MNN-LLM executes actual
 * on-device weights through a native library via JNI. Alibaba does not publish
 * MNN-LLM as a Maven/Gradle artifact, so the native `libmnn_llm.so` (and its
 * `libmnn.so` dependency) must be built from source and bundled into the APK.
 * See `scripts/setup-mnn.md` for the exact build commands.
 *
 * Honesty contract:
 *  - If the native MNN runtime is present, this engine loads the model and
 *    streams real decoded tokens with measured throughput.
 *  - If the native runtime or model file is missing, it throws
 *    [MnnUnavailableException] with a specific reason. It NEVER falls back to a
 *    knowledge base or returns synthesized text pretending to be MNN output.
 *
 * The JNI signatures below mirror the C bridge in
 * `app/src/main/cpp/mnn/mnn_llm_jni.cpp`.
 */
class MnnEngine(private val context: Context? = null) {

    companion object {
        private const val TAG = "MnnEngine"

        init {
            try {
                // Load in dependency order: MNN core, LLM runtime, then JNI glue.
                System.loadLibrary("mnn")
                System.loadLibrary("mnn_llm")
                System.loadLibrary("edgellm_mnn_jni")
                nativeLibraryLoaded = true
                Log.i(TAG, "MNN-LLM native runtime loaded")
            } catch (t: UnsatisfiedLinkError) {
                // Expected on most builds — MNN is opt-in. Record the cause and
                // report Unavailable rather than pretending to run.
                nativeLibraryLoaded = false
                nativeLibraryError = t.message ?: "libmnn_llm.so / libedgellm_mnn_jni.so not found"
                Log.w(TAG, "MNN-LLM native runtime NOT available: $nativeLibraryError")
            }
        }

        @Volatile var nativeLibraryLoaded: Boolean = false
            private set
        @Volatile var nativeLibraryError: String? = null
            private set

        /** True when the bundled native MNN runtime can be used. */
        fun isRuntimeAvailable(): Boolean = nativeLibraryLoaded

        // ---- JNI bridge (implemented in app/src/main/cpp/mnn/mnn_llm_jni.cpp) ----
        private external fun nativeCreateSession(
            modelPath: String,
            threads: Int,
            useVulkan: Boolean
        ): Long
        private external fun nativeFeedPrompt(handle: Long, prompt: String)
        private external fun nativeFetchNextToken(handle: Long): String? // null == EOS
        private external fun nativeReset(handle: Long)
        private external fun nativeDestroy(handle: Long)
    }

    /** Thrown when MNN cannot run. Callers must surface the message, not invent output. */
    class MnnUnavailableException(message: String, cause: Throwable? = null) :
        RuntimeException(message, cause)

    data class MnnTokenChunk(
        val token: String,
        val accumulatedText: String,
        val tokenCount: Int,
        val tokensPerSecond: Float,
        val timeToFirstTokenMs: Long,
        val isComplete: Boolean
    )

    /**
     * Stream a real completion from an MNN model.
     *
     * @param model MUST have a valid `localFilePath` pointing at an MNN model
     *   directory (containing `config.json`, tokenizer, and `.mnn` weights).
     * @throws MnnUnavailableException if the runtime or model file is missing.
     */
    fun streamMnnResponse(
        prompt: String,
        model: ModelSpec,
        settings: HardwareAccelerationSettings,
        params: GenerationParameters
    ): Flow<MnnTokenChunk> = flow {
        if (!nativeLibraryLoaded) {
            throw MnnUnavailableException(
                "MNN-LLM runtime unavailable: " +
                    (nativeLibraryError ?: "native library not bundled") +
                    ". Build it from source (see scripts/setup-mnn.md) and rebuild the APK."
            )
        }
        val modelPath = model.localFilePath
        if (modelPath.isBlank()) {
            throw MnnUnavailableException("MNN model has no localFilePath configured.")
        }
        if (!java.io.File(modelPath).exists()) {
            throw MnnUnavailableException("MNN model path does not exist: $modelPath")
        }

        val threads = settings.threadCount.coerceAtLeast(1)
        val useVulkan = settings.computeBackend != ComputeBackend.CPU_NEON

        // Open the session on the IO dispatcher (native call can block).
        val handle = withContext(Dispatchers.IO) {
            nativeCreateSession(modelPath, threads, useVulkan)
        }
        if (handle == 0L) {
            throw MnnUnavailableException("MNN failed to create a session for $modelPath")
        }

        val startMs = System.currentTimeMillis()
        var ttftMs = 0L
        val sb = StringBuilder()
        var count = 0

        try {
            // Feed the prompt on IO.
            withContext(Dispatchers.IO) { nativeFeedPrompt(handle, prompt) }

            // Decode real tokens until EOS (null) or the token budget.
            val maxTokens = params.maxNewTokens.coerceAtLeast(1)
            while (count < maxTokens) {
                val tok = withContext(Dispatchers.IO) { nativeFetchNextToken(handle) }
                if (tok.isNullOrEmpty()) break // end of sequence
                count++
                if (count == 1) ttftMs = System.currentTimeMillis() - startMs
                sb.append(tok)

                val elapsedSec = (System.currentTimeMillis() - startMs) / 1000f
                val tps = if (elapsedSec > 0f) count / elapsedSec else 0f

                emit(
                    MnnTokenChunk(
                        token = tok,
                        accumulatedText = sb.toString(),
                        tokenCount = count,
                        tokensPerSecond = (tps * 10).toInt() / 10f,
                        timeToFirstTokenMs = ttftMs,
                        isComplete = false
                    )
                )
            }

            val totalSec = (System.currentTimeMillis() - startMs) / 1000f
            emit(
                MnnTokenChunk(
                    token = "",
                    accumulatedText = sb.toString(),
                    tokenCount = count,
                    tokensPerSecond = if (totalSec > 0f) (count / totalSec * 10).toInt() / 10f else 0f,
                    timeToFirstTokenMs = ttftMs,
                    isComplete = true
                )
            )
        } finally {
            // Always free the native session.
            withContext(Dispatchers.IO) { nativeDestroy(handle) }
        }
    }.flowOn(Dispatchers.Default)

    /** Reset the KV cache for a fresh conversation turn. */
    suspend fun reset(handle: Long) = withContext(Dispatchers.IO) { nativeReset(handle) }
}
