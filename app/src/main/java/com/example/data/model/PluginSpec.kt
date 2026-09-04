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
    val configParameters: Map<String, String> = emptyMap()
)

data class PluginResult(
    val pluginId: String,
    val success: Boolean,
    val processedOutput: String,
    val metrics: Map<String, String> = emptyMap(),
    val durationMs: Long = 0L
)
