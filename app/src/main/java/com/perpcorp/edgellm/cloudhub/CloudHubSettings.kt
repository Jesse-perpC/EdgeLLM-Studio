package com.perpcorp.edgellm.cloudhub

/**
 * Setting keys + store contract ported from PrivateLM `constants.dart`
 * (settings keys) and `hive_service.dart` (`getSetting`/`setSetting`).
 *
 * Keys are namespaced `cloudhub_*` so they can never collide with EdgeLLM's
 * existing prefs. Secrets MUST live behind [CloudHubStore] implemented with
 * EncryptedSharedPreferences ([EncryptedCloudHubStore]) — never plain prefs.
 */
object CloudHubKeys {
    const val INFERENCE_MODE = "cloudhub_inference_mode" // "local" | "cloud"
    const val PROVIDER = "cloudhub_provider"
    const val TEMPERATURE = "cloudhub_temperature"
    const val MAX_TOKENS = "cloudhub_max_tokens"
    const val CONTEXT_SIZE = "cloudhub_context_size"
    const val SYSTEM_PROMPT = "cloudhub_system_prompt"
    const val AUTOCONFIG_DONE = "cloudhub_autoconfig_done"

    const val CUSTOM_NAME = "cloudhub_custom_name"
    const val CUSTOM_BASE_URL = "cloudhub_custom_base_url"

    fun apiKeyFor(providerId: String) = "cloudhub_key_$providerId"
    fun modelFor(providerId: String) = "cloudhub_model_$providerId"
}

enum class InferenceMode(val id: String) {
    LOCAL("local"),
    CLOUD("cloud"),
    ;

    companion object {
        fun fromId(id: String?): InferenceMode =
            entries.firstOrNull { it.id == id } ?: LOCAL
    }
}

object CloudHubDefaults {
    const val PROVIDER = CloudProviders.OPENROUTER
    const val TEMPERATURE = 0.7f
    const val MAX_TOKENS = 1024
    const val CONTEXT_SIZE = 2048
    const val SYSTEM_PROMPT =
        "You are AI Chat, a helpful and friendly assistant. " +
            "Be concise, accurate, and conversational. " +
            "Answer questions directly without unnecessary preamble."
}

/**
 * Minimal key/value settings backend (mirrors HiveService.get/setSetting).
 * Production implementation: [EncryptedCloudHubStore].
 */
interface CloudHubStore {
    fun getString(key: String, default: String? = null): String?
    fun putString(key: String, value: String)
    fun getFloat(key: String, default: Float): Float
    fun putFloat(key: String, value: Float)
    fun getInt(key: String, default: Int): Int
    fun putInt(key: String, value: Int)
    fun getBoolean(key: String, default: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun remove(key: String)
}

fun CloudHubStore.inferenceMode(): InferenceMode =
    InferenceMode.fromId(getString(CloudHubKeys.INFERENCE_MODE, InferenceMode.LOCAL.id))

fun CloudHubStore.provider(): CloudProvider =
    CloudProviders.byId(getString(CloudHubKeys.PROVIDER, CloudHubDefaults.PROVIDER))

fun CloudHubStore.apiKeyFor(providerId: String): String =
    getString(CloudHubKeys.apiKeyFor(providerId), "").orEmpty()

fun CloudHubStore.modelFor(provider: CloudProvider): String {
    val stored = getString(CloudHubKeys.modelFor(provider.id), "").orEmpty()
    return stored.ifBlank { provider.defaultModel }
}

fun CloudHubStore.temperature(): Float =
    getFloat(CloudHubKeys.TEMPERATURE, CloudHubDefaults.TEMPERATURE)

fun CloudHubStore.maxTokens(): Int =
    getInt(CloudHubKeys.MAX_TOKENS, CloudHubDefaults.MAX_TOKENS)

fun CloudHubStore.contextSize(): Int =
    getInt(CloudHubKeys.CONTEXT_SIZE, CloudHubDefaults.CONTEXT_SIZE)

fun CloudHubStore.systemPrompt(): String =
    getString(CloudHubKeys.SYSTEM_PROMPT, CloudHubDefaults.SYSTEM_PROMPT)
        .orEmpty().ifBlank { CloudHubDefaults.SYSTEM_PROMPT }
