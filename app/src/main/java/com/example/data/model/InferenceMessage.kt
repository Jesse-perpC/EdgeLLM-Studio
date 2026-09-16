package com.example.data.model

import com.example.engine.GrammarMode

data class InferenceMessage(
    val id: String,
    val sender: MessageSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val tokensGenerated: Int = 0,
    val tokensPerSecond: Float = 0f,
    val timeToFirstTokenMs: Long = 0L,
    val executionBackend: String = "",
    val modelId: String = "",
    val imageUri: String? = null,
    val imageLabel: String? = null,
    val sessionId: String = "default_session",
    val thoughtChain: String? = null,
    val conversationalContext: String? = null,
    val embeddingVector: List<Float> = emptyList(),
    val importanceScore: Float = 0.5f,
    val semanticTags: String? = null,
    val recalledMemories: List<String> = emptyList(),
    val isTurboBoost: Boolean = false,
    val isPrefixCacheHit: Boolean = false,
    val grammarModeUsed: GrammarMode = GrammarMode.NONE,
    val samplerUsed: String = "Min-P (0.05)"
)

enum class MessageSender {
    USER,
    ASSISTANT,
    SYSTEM
}

data class GenerationParameters(
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val minP: Float = 0.05f,
    val topK: Int = 40,
    val maxNewTokens: Int = 512,
    val repeatPenalty: Float = 1.1f,
    val systemPrompt: String = "You are an ultra-fast, privacy-preserving local on-device language model running directly on this mobile hardware. Answer accurately, concisely, and completely offline.",
    val enableToolCalling: Boolean = true,
    val enforceJsonSchema: Boolean = false,
    val jsonSchemaDefinition: String = "{\n  \"type\": \"object\",\n  \"properties\": {\n    \"status\": { \"type\": \"string\" },\n    \"data\": { \"type\": \"array\" }\n  }\n}",
    val isTurboBoost: Boolean = true,
    val enableThinkingMode: Boolean = true,
    val thinkingBudgetTokens: Int = 1024,
    val grammarMode: GrammarMode = GrammarMode.NONE,
    val customRegexPattern: String = "",
    val enablePrefixCaching: Boolean = true
)
