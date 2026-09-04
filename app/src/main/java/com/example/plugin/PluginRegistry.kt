package com.example.plugin

import com.example.data.model.HardwareAccelerationSettings
import com.example.data.model.ModelSpec
import com.example.data.model.PluginSpec
import com.example.data.model.PluginResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class PluginRegistry {

    private val pluginsMap = mutableMapOf<String, LocalProcessingPlugin>()

    private val _pluginsState = MutableStateFlow<List<PluginSpec>>(emptyList())
    val pluginsState: StateFlow<List<PluginSpec>> = _pluginsState.asStateFlow()

    init {
        registerPlugin(PiiRedactorPlugin())
        registerPlugin(LogAnomalyDetectorPlugin())
        registerPlugin(MeetingSummarizerPlugin())
        registerPlugin(CodeSecurityReviewPlugin())
        refreshState()
    }

    fun registerPlugin(plugin: LocalProcessingPlugin) {
        pluginsMap[plugin.spec.id] = plugin
        refreshState()
    }

    fun togglePlugin(id: String, enabled: Boolean) {
        val current = pluginsMap[id] ?: return
        val updatedSpec = current.spec.copy(isEnabled = enabled)
        pluginsMap[id] = object : LocalProcessingPlugin {
            override val spec: PluginSpec = updatedSpec
            override suspend fun execute(
                input: String,
                model: ModelSpec,
                settings: HardwareAccelerationSettings
            ): PluginResult {
                return current.execute(input, model, settings)
            }
        }
        refreshState()
    }

    fun addCustomPlugin(name: String, category: String, description: String, keywordFilter: String) {
        val newId = "plugin_custom_${UUID.randomUUID().toString().take(8)}"
        val customSpec = PluginSpec(
            id = newId,
            name = name,
            category = category,
            description = description,
            version = "1.0.0",
            isEnabled = true,
            isBuiltIn = false,
            iconKey = "Extension",
            configParameters = mapOf("filter" to keywordFilter)
        )

        val customPlugin = object : LocalProcessingPlugin {
            override val spec: PluginSpec = customSpec
            override suspend fun execute(
                input: String,
                model: ModelSpec,
                settings: HardwareAccelerationSettings
            ): PluginResult {
                val lines = input.lines()
                val matched = if (keywordFilter.isNotBlank()) {
                    lines.filter { it.contains(keywordFilter, ignoreCase = true) }
                } else {
                    lines
                }
                val output = buildString {
                    appendLine("=== Custom Plugin: $name ===")
                    appendLine("Filter: \"$keywordFilter\" | Matches: ${matched.size}/${lines.size}")
                    appendLine()
                    matched.take(15).forEach { appendLine("> $it") }
                }
                return PluginResult(
                    pluginId = spec.id,
                    success = true,
                    processedOutput = output,
                    metrics = mapOf("Filter" to keywordFilter, "Matches" to "${matched.size}"),
                    durationMs = 45L
                )
            }
        }
        registerPlugin(customPlugin)
    }

    suspend fun executePlugin(
        pluginId: String,
        input: String,
        model: ModelSpec,
        settings: HardwareAccelerationSettings
    ): PluginResult? {
        val plugin = pluginsMap[pluginId] ?: return null
        return plugin.execute(input, model, settings)
    }

    suspend fun executePipeline(
        input: String,
        model: ModelSpec,
        settings: HardwareAccelerationSettings
    ): String {
        var currentData = input
        val enabledPlugins = pluginsMap.values.filter { it.spec.isEnabled }

        for (plugin in enabledPlugins) {
            val result = plugin.execute(currentData, model, settings)
            if (result.success && result.processedOutput.isNotBlank()) {
                currentData = result.processedOutput
            }
        }
        return currentData
    }

    private fun refreshState() {
        _pluginsState.value = pluginsMap.values.map { it.spec }
    }
}
