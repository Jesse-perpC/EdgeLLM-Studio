package com.perpcorp.edgellm.api

import android.content.Context
import com.perpcorp.edgellm.data.local.AppDatabase
import com.perpcorp.edgellm.data.model.ComputeBackend
import com.perpcorp.edgellm.data.model.HardwareAccelerationSettings
import com.perpcorp.edgellm.data.model.ModelSpec
import com.perpcorp.edgellm.data.model.PowerProfile
import com.perpcorp.edgellm.data.repository.EdgeLLMRepository
import com.perpcorp.edgellm.engine.LocalInferenceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking

/**
 * Singleton holder ensuring a single shared instance of OllamaInferenceServer
 * exists across MainViewModel, InferenceServerService, and UI components.
 */
object InferenceServerManager {

    @Volatile
    private var instance: OllamaInferenceServer? = null

    fun getInstance(
        context: Context,
        inferenceEngine: LocalInferenceEngine? = null,
        modelProvider: (() -> List<ModelSpec>)? = null,
        activeModelProvider: (() -> ModelSpec?)? = null,
        accelerationSettingsProvider: (() -> HardwareAccelerationSettings)? = null
    ): OllamaInferenceServer {
        return instance ?: synchronized(this) {
            instance ?: run {
                val appContext = context.applicationContext
                val engine = inferenceEngine ?: LocalInferenceEngine()
                val db = AppDatabase.getInstance(appContext)
                val repo = EdgeLLMRepository(db)

                val mProvider = modelProvider ?: {
                    runBlocking(Dispatchers.IO) {
                        repo.localModels.firstOrNull() ?: emptyList()
                    }
                }
                val aProvider = activeModelProvider ?: {
                    runBlocking(Dispatchers.IO) {
                        repo.localModels.firstOrNull()?.firstOrNull { it.isActive }
                            ?: repo.localModels.firstOrNull()?.firstOrNull { it.isDownloaded }
                    }
                }
                val accelProvider = accelerationSettingsProvider ?: {
                    HardwareAccelerationSettings(
                        computeBackend = ComputeBackend.NPU_NNAPI,
                        powerProfile = PowerProfile.BALANCED,
                        threadCount = 4
                    )
                }

                OllamaInferenceServer(
                    context = appContext,
                    inferenceEngine = engine,
                    modelProvider = mProvider,
                    activeModelProvider = aProvider,
                    accelerationSettingsProvider = accelProvider
                ).also { instance = it }
            }
        }
    }
}
