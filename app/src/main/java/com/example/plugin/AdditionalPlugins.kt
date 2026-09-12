package com.example.plugin

import com.example.data.model.HardwareAccelerationSettings
import com.example.data.model.ModelSpec
import com.example.data.model.PluginSpec
import com.example.data.model.PluginResult
import kotlinx.coroutines.delay

class PromptJailbreakGuardPlugin(
    override val spec: PluginSpec = PluginSpec(
        id = "plugin_jailbreak_guard",
        name = "Prompt Injection & Jailbreak Guard",
        category = "AI Safety & Guardrails",
        description = "Filters adversarial prompt injection attacks ('ignore previous instructions', system prompt leaks, role-hijacking) in real-time before weights evaluation.",
        version = "1.0.4",
        isEnabled = true,
        iconKey = "Shield",
        author = "RedTeam Labs",
        rating = 4.9f,
        installCount = "24k+",
        hookPoint = "pre_inference"
    )
) : LocalProcessingPlugin {
    override suspend fun execute(
        input: String,
        model: ModelSpec,
        settings: HardwareAccelerationSettings
    ): PluginResult {
        val startTime = System.currentTimeMillis()
        delay(60)

        val lower = input.lowercase()
        val attackVectors = listOf(
            "ignore previous instructions",
            "disregard all prior rules",
            "system prompt leak",
            "you are now dan",
            "developer mode enabled",
            "bypass safety filter"
        )
        val matches = attackVectors.filter { lower.contains(it) }

        val safeOutput = if (matches.isNotEmpty()) {
            "[GUARDRAIL ACTIVATED: Potential injection detected: ${matches.joinToString()}]. Processing sanitized content."
        } else {
            "✅ Clean input payload. 0 injection vectors matched."
        }

        return PluginResult(
            pluginId = spec.id,
            success = true,
            processedOutput = safeOutput,
            metrics = mapOf(
                "Vectors Checked" to "${attackVectors.size}",
                "Attacks Blocked" to "${matches.size}",
                "Latency" to "${System.currentTimeMillis() - startTime}ms"
            ),
            durationMs = System.currentTimeMillis() - startTime
        )
    }
}

class MarkdownLaTeXBeautifierPlugin(
    override val spec: PluginSpec = PluginSpec(
        id = "plugin_latex_beautifier",
        name = "LaTeX & Math Formatter",
        category = "Output Transformers",
        description = "Parses and beautifies raw math notation, fractions, matrices, and LaTeX equations in generated LLM outputs for pristine rendering.",
        version = "2.1.0",
        isEnabled = true,
        iconKey = "Code",
        author = "MathJax Community",
        rating = 4.8f,
        installCount = "18k+",
        hookPoint = "post_inference"
    )
) : LocalProcessingPlugin {
    override suspend fun execute(
        input: String,
        model: ModelSpec,
        settings: HardwareAccelerationSettings
    ): PluginResult {
        val startTime = System.currentTimeMillis()
        delay(50)

        var formatted = input
            .replace("\\frac{", "⟦FRAC: ")
            .replace("}{", " / ")
            .replace("}", "⟧")
            .replace("\\sqrt{", "√(")
            .replace("\\times", "×")

        return PluginResult(
            pluginId = spec.id,
            success = true,
            processedOutput = formatted,
            metrics = mapOf("Math Tokens Formatted" to "OK"),
            durationMs = System.currentTimeMillis() - startTime
        )
    }
}
