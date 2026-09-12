package com.example.data.model

data class McpServerSpec(
    val id: String,
    val name: String,
    val endpointUrl: String,
    val transportType: McpTransportType = McpTransportType.HTTP_SSE,
    val isEnabled: Boolean = true,
    val isConnected: Boolean = false,
    val authHeader: String? = null,
    val protocolVersion: String = "2024-11-05",
    val latencyMs: Long = 0L,
    val toolsCount: Int = 0,
    val resourcesCount: Int = 0,
    val promptsCount: Int = 0,
    val tools: List<McpToolDefinition> = emptyList(),
    val resources: List<McpResourceDefinition> = emptyList(),
    val lastSyncTimestamp: Long = 0L
)

enum class McpTransportType(val displayName: String) {
    HTTP_SSE("HTTP + SSE"),
    STDIO_LOCAL("Local Process / Stdio"),
    WEBSOCKET("WebSocket")
}

data class McpToolDefinition(
    val name: String,
    val description: String,
    val inputSchemaJson: String = "{}",
    val isEnabled: Boolean = true,
    val serverId: String,
    val category: String = "MCP Tool"
)

data class McpResourceDefinition(
    val uri: String,
    val name: String,
    val mimeType: String = "text/plain",
    val description: String = ""
)

data class McpToolCallResult(
    val serverId: String,
    val toolName: String,
    val argumentsJson: String,
    val outputContent: String,
    val isError: Boolean = false,
    val durationMs: Long = 0L
)

enum class PluginSourceType(val displayName: String) {
    BUILT_IN("Core Engine"),
    COMMUNITY_REPO("Plugin Directory"),
    CUSTOM_SCRIPT("Custom Script"),
    MCP_REMOTE("MCP Server Tool")
}
