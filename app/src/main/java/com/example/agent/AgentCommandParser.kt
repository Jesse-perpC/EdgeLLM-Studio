package com.example.agent

/**
 * Intelligent parser that detects when user requests can be fulfilled or augmented by RikkaHub Agent tools.
 */
object AgentCommandParser {

    data class DetectedToolInvocation(
        val toolId: String,
        val arguments: Map<String, String>,
        val naturalExplanation: String
    )

    fun detectToolInvocation(userInput: String): DetectedToolInvocation? {
        val input = userInput.trim().lowercase()

        // Battery / Power
        if (input.contains("battery") || input.contains("power level") || input.contains("charge percentage")) {
            return DetectedToolInvocation("device_get_battery", emptyMap(), "Querying device battery telemetry")
        }

        // Flashlight / Torch
        if (input.contains("flashlight on") || input.contains("turn on torch") || input.contains("enable flashlight") || input.contains("torch on")) {
            return DetectedToolInvocation("device_toggle_flashlight", mapOf("state" to "true"), "Turning flashlight on")
        }
        if (input.contains("flashlight off") || input.contains("turn off torch") || input.contains("disable flashlight") || input.contains("torch off")) {
            return DetectedToolInvocation("device_toggle_flashlight", mapOf("state" to "false"), "Turning flashlight off")
        }

        // Haptic Vibration
        if (input.startsWith("/vibrate") || input.contains("vibrate phone") || input.contains("buzz phone")) {
            return DetectedToolInvocation("device_vibrate", mapOf("duration_ms" to "200"), "Triggering haptic feedback")
        }

        // Clipboard
        if (input.contains("read clipboard") || input.contains("paste clipboard") || input.contains("what is on my clipboard")) {
            return DetectedToolInvocation("device_clipboard_paste", emptyMap(), "Reading Android system clipboard")
        }

        // Diagnostics
        if (input.contains("system specs") || input.contains("hardware specs") || input.contains("device specs") || input.contains("device info")) {
            return DetectedToolInvocation("diag_system_specs", emptyMap(), "Running system hardware diagnostic")
        }
        if (input.contains("storage space") || input.contains("disk space") || input.contains("free space") || input.contains("storage health")) {
            return DetectedToolInvocation("diag_storage_status", emptyMap(), "Inspecting storage partition capacity")
        }
        if (input.contains("ram usage") || input.contains("memory usage") || input.contains("available ram")) {
            return DetectedToolInvocation("diag_ram_usage", emptyMap(), "Checking memory pressure and RAM headroom")
        }
        if (input.contains("my ip") || input.contains("network ip") || input.contains("ip address")) {
            return DetectedToolInvocation("diag_network_ip", emptyMap(), "Resolving network IP addresses")
        }
        if (input.contains("wifi status") || input.contains("wifi signal") || input.contains("wifi speed")) {
            return DetectedToolInvocation("diag_wifi_details", emptyMap(), "Checking Wi-Fi link parameters")
        }

        // Alarm & Timer
        if (input.startsWith("set timer for ") || input.startsWith("timer for ")) {
            val seconds = Regex("(\\d+)\\s*(second|sec|minute|min)").find(input)?.let { match ->
                val num = match.groupValues[1].toIntOrNull() ?: 60
                if (match.groupValues[2].startsWith("m")) num * 60 else num
            } ?: 180
            return DetectedToolInvocation("pim_set_timer", mapOf("seconds" to seconds.toString()), "Setting system clock timer")
        }

        // Linux Userland execution shortcut
        if (input.startsWith("$ ") || input.startsWith("sh ") || input.startsWith("bash ")) {
            val cmd = input.removePrefix("$ ").removePrefix("sh ").removePrefix("bash ")
            return DetectedToolInvocation("linux_shell_exec", mapOf("command" to cmd), "Executing POSIX shell command in Linux PRoot")
        }

        // Web Search
        if (input.startsWith("search web for ") || input.startsWith("google ")) {
            val q = input.removePrefix("search web for ").removePrefix("google ").trim()
            return DetectedToolInvocation("pim_search_web", mapOf("query" to q), "Triggering web search intent")
        }

        // Maps / Navigation
        if (input.startsWith("navigate to ") || input.startsWith("open map for ")) {
            val place = input.removePrefix("navigate to ").removePrefix("open map for ").trim()
            return DetectedToolInvocation("pim_open_maps", mapOf("location" to place), "Opening navigation maps")
        }

        return null
    }
}
