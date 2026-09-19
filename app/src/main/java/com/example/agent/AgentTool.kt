package com.example.agent

/**
 * Category classification of agent tools matching rikkahub-agent capability domains.
 */
enum class AgentToolCategory(val title: String, val iconLabel: String) {
    DEVICE_CONTROL("Device Control", "📱"),
    SETTINGS("Settings & Radios", "⚙️"),
    MEDIA_AUDIO("Media & Audio", "🎵"),
    NOTIFICATIONS("Notifications", "🔔"),
    COMMUNICATION("Communication", "💬"),
    PRODUCTIVITY("Productivity & PIM", "📅"),
    TELEGRAM_BOT("Telegram Bot Service", "✈️"),
    LINUX_WORKSPACE("Linux Workspace & PRoot", "🐧"),
    SSH_REMOTE("SSH & Remote Server", "🔑"),
    SYSTEM_DIAGNOSTICS("Diagnostics & Sensors", "📊"),
    SECURITY_PRIVACY("Security & Vault", "🛡️")
}

/**
 * Security risk tier for an agent tool.
 * High and Critical risk tools require explicit user consent or confirmation before execution.
 */
enum class AgentSecurityLevel {
    SAFE,           // Read-only or benign action (e.g. read battery, get time, get ip)
    NORMAL,         // Standard intent or toggle (e.g. flashlight, volume, clipboard)
    ELEVATED,       // Sends intent to other apps (e.g. dial phone, compose email, open browser)
    RESTRICTED      // Critical or irreversible operations (e.g. wipe files, shell kill) - requires confirmation
}

/**
 * Parameter specification for an agent tool.
 */
data class AgentToolParameter(
    val name: String,
    val type: String,
    val description: String,
    val required: Boolean = true,
    val defaultValue: String? = null
)

/**
 * Execution outcome from invoking an agent tool.
 */
data class AgentToolResult(
    val isSuccess: Boolean,
    val output: String,
    val rawData: Map<String, Any?> = emptyMap(),
    val requiresUserConfirmation: Boolean = false,
    val executionTimeMs: Long = 0L
)

/**
 * Base interface for all RikkaHub Agent automation tools.
 */
interface AgentTool {
    val id: String
    val name: String
    val description: String
    val category: AgentToolCategory
    val securityLevel: AgentSecurityLevel
    val parameters: List<AgentToolParameter>

    suspend fun execute(
        context: android.content.Context,
        arguments: Map<String, String>
    ): AgentToolResult
}
