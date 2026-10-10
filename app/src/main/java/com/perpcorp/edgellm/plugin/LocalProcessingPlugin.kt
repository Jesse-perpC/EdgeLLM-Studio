package com.perpcorp.edgellm.plugin

import com.perpcorp.edgellm.data.model.HardwareAccelerationSettings
import com.perpcorp.edgellm.data.model.ModelSpec
import com.perpcorp.edgellm.data.model.PluginSpec
import com.perpcorp.edgellm.data.model.PluginResult

interface LocalProcessingPlugin {
    val spec: PluginSpec
    suspend fun execute(
        input: String,
        model: ModelSpec,
        settings: HardwareAccelerationSettings
    ): PluginResult
}
