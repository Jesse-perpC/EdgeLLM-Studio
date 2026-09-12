package com.example.data.model

data class PluginSpec(
    val id: String,
    val name: String,
    val category: String,
    val description: String,
    val version: String,
    val isEnabled: Boolean,
    val isBuiltIn: Boolean = true,
    val iconKey: String,
    val executionCount: Int = 0,
    val configParameters: Map<String, String> = emptyMap(),
    val author: String = "EdgeLLM Team",
    val rating: Float = 4.9f,
    val installCount: String = "10k+",
    val hookPoint: String = "pre_inference", // "pre_inference", "post_inference", "tool_call", "custom_filter"
    val isInstalled: Boolean = true,
    val permissionsRequired: List<String> = emptyList()
)

data class PluginResult(
    val pluginId: String,
    val success: Boolean,
    val processedOutput: String,
    val metrics: Map<String, String> = emptyMap(),
    val durationMs: Long = 0L
)
