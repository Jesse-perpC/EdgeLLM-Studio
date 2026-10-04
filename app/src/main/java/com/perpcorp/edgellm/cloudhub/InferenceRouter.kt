package com.perpcorp.edgellm.cloudhub

import android.util.Log

/**
 * Local/cloud routing ported from PrivateLM's `inference_mode` switch
 * (`chat_controller` + `task_controller`).
 *
 * - LOCAL (default): delegates to [LocalChatBackend] — the caller wires this
 *   to `LocalInferenceEngine` streaming. Air-gapped behavior unchanged.
 * - CLOUD: delegates to [CloudHubService] — only reachable when the user both
 *   stored a key ([CloudHubService.isConfigured]) and selected cloud mode.
 *   Unconfigured cloud mode returns the settings error, never silent local.
 */
class InferenceRouter(
    private val store: CloudHubStore,
    private val cloud: CloudHubService,
    private val local: LocalChatBackend,
) {

    /** Local generation backend (EdgeLLM's own engines). */
    fun interface LocalChatBackend {
        /**
         * @param systemPrompt optional system instruction (null = engine default).
         * @param onToken streaming sink; non-streaming backends call once.
         * @return full reply text, or `ERROR: ...`.
         */
        suspend fun generate(
            prompt: String,
            systemPrompt: String?,
            imageBase64: String?,
            temperature: Float,
            maxTokens: Int,
            onToken: (String) -> Unit,
        ): String
    }

    fun activeMode(): InferenceMode = store.inferenceMode()

    /**
     * Route one chat turn. [history] is oldest→newest including the new user
     * message; [imageBase64] is JPEG-base64 or null (see [ChatAttachmentUtils]).
     */
    suspend fun routeChat(
        history: List<CloudMessage>,
        imageBase64: String? = null,
        temperature: Float? = null,
        maxTokens: Int? = null,
        onToken: (String) -> Unit = {},
    ): String {
        val mode = activeMode()
        val temp = temperature ?: store.temperature()
        val tokens = maxTokens ?: store.maxTokens()

        if (mode == InferenceMode.CLOUD) {
            val provider = store.provider()
            if (!cloud.isConfigured(provider)) {
                Log.w(TAG, "Cloud mode selected but ${provider.id} is not configured")
                return cloud.configurationError(provider)
            }
            val withSystem = withSystemPrompt(history)
            return if (provider.supportsStreaming) {
                cloud.streamMessage(withSystem, imageBase64, temp.toDouble(), tokens, onToken)
            } else {
                val full = cloud.sendMessage(withSystem, imageBase64, temp.toDouble(), tokens)
                if (full.isNotEmpty() && !full.startsWith("ERROR")) onToken(full)
                full
            }
        }

        val lastUser = history.lastOrNull { it.role == "user" }?.content.orEmpty()
        return local.generate(
            lastUser,
            store.systemPrompt().ifBlank { null },
            imageBase64,
            temp,
            tokens,
            onToken,
        )
    }

    private fun withSystemPrompt(history: List<CloudMessage>): List<CloudMessage> {
        if (history.any { it.role == "system" }) return history
        val system = store.systemPrompt()
        if (system.isBlank()) return history
        return listOf(CloudMessage("system", system)) + history
    }

    companion object {
        private const val TAG = "InferenceRouter"
    }
}
