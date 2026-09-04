package com.example.data.model

data class InferenceMessage(
    val id: String,
    val sender: MessageSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val tokensGenerated: Int = 0,
    val tokensPerSecond: Float = 0f,
    val timeToFirstTokenMs: Long = 0L,
    val executionBackend: String = "",
    val modelId: String = ""
)

enum class MessageSender {
    USER,
    ASSISTANT,
    SYSTEM
}

data class GenerationParameters(
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val maxNewTokens: Int = 512,
    val repeatPenalty: Float = 1.1f,
    val systemPrompt: String = "You are an ultra-fast, privacy-preserving local on-device language model running directly on this mobile hardware. Answer accurately, concisely, and completely offline."
)
