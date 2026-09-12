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
        registerPlugin(PromptJailbreakGuardPlugin())
        registerPlugin(MarkdownLaTeXBeautifierPlugin())
        refreshState()
    }

    val communityStorePlugins: List<PluginSpec> = listOf(
        PluginSpec(
            id = "store_git_analyzer",
            name = "Git Commit & PR Explainer",
            category = "DevOps & SCM",
            description = "Analyzes git diff patches and generates conventional commit messages and risk scores.",
            version = "1.4.0",
            isEnabled = false,
            isBuiltIn = false,
            iconKey = "Code",
            author = "OpenDev",
            rating = 4.9f,
            installCount = "31k+",
            hookPoint = "tool_call",
            isInstalled = false
        ),
        PluginSpec(
            id = "store_audio_transcriber",
            name = "Whisper Audio Cleaner",
            category = "Speech & Audio",
            description = "Normalizes voice transcription text, strips background hum tags, and restores punctuation.",
            version = "2.0.0",
            isEnabled = false,
            isBuiltIn = false,
            iconKey = "Analytics",
            author = "WhisperEdge",
            rating = 4.8f,
            installCount = "19k+",
            hookPoint = "pre_inference",
            isInstalled = false
        ),
        PluginSpec(
            id = "store_fhir_medical",
            name = "FHIR Medical Record Anonymizer",
            category = "Healthcare & HIPAA",
            description = "Strips HIPAA Safe Harbor 18 identifiers from clinical notes while maintaining medical syntax.",
            version = "3.1.2",
            isEnabled = false,
            isBuiltIn = false,
            iconKey = "Shield",
            author = "MedPrivacy Org",
            rating = 5.0f,
            installCount = "8k+",
            hookPoint = "pre_inference",
            isInstalled = false
        ),
        PluginSpec(
            id = "store_rag_reranker",
            name = "Cross-Encoder Reranker",
            category = "RAG & Search",
            description = "Re-scores top-k retrieved RAG text chunks using reciprocal rank fusion before context injection.",
            version = "1.1.0",
            isEnabled = false,
            isBuiltIn = false,
            iconKey = "Extension",
            author = "SearchLabs",
            rating = 4.7f,
            installCount = "42k+",
            hookPoint = "pre_inference",
            isInstalled = false
        )
    )

    fun installFromCommunity(spec: PluginSpec) {
        val installedSpec = spec.copy(isInstalled = true, isEnabled = true)
        val dummyPlugin = object : LocalProcessingPlugin {
            override val spec: PluginSpec = installedSpec
            override suspend fun execute(
                input: String,
                model: ModelSpec,
                settings: HardwareAccelerationSettings
            ): PluginResult {
                return PluginResult(
                    pluginId = spec.id,
                    success = true,
                    processedOutput = "=== Plugin Execution: ${spec.name} ===\nHook: ${spec.hookPoint}\nOutput: Processed ${input.length} characters seamlessly on device.",
                    metrics = mapOf("Engine" to "Community Plugin", "Status" to "Active"),
                    durationMs = 35L
                )
            }
        }
        registerPlugin(dummyPlugin)
    }

    fun uninstallPlugin(id: String) {
        pluginsMap.remove(id)
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
