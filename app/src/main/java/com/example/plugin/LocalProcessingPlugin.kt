package com.example.plugin

import com.example.data.model.HardwareAccelerationSettings
import com.example.data.model.ModelSpec
import com.example.data.model.PluginSpec
import com.example.data.model.PluginResult

interface LocalProcessingPlugin {
    val spec: PluginSpec
    suspend fun execute(
        input: String,
        model: ModelSpec,
        settings: HardwareAccelerationSettings
    ): PluginResult
}
