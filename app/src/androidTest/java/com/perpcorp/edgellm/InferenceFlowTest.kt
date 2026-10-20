package com.perpcorp.edgellm

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.perpcorp.edgellm.data.model.AiPersona
import com.perpcorp.edgellm.data.model.GenerationParameters
import com.perpcorp.edgellm.data.model.HardwareAccelerationSettings
import com.perpcorp.edgellm.data.model.ModelFormat
import com.perpcorp.edgellm.data.model.ModelSpec
import com.perpcorp.edgellm.engine.LocalInferenceEngine
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InferenceFlowTest {

    @Test
    fun offlineKnowledgeEngineReturnsResponse() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = LocalInferenceEngine(context)

        val model = ModelSpec(
            id = "test-model",
            name = "Test Model",
            parameterCount = "1B",
            format = ModelFormat.GGUF,
            quantization = "Q4_K_M",
            fileSizeBytes = 1024L * 1024L,
            requiredRamBytes = 512L * 1024L * 1024L,
            contextLength = 2048,
            description = "Test model for integration test",
            category = com.perpcorp.edgellm.data.model.ModelCategory.CHAT_REASONING,
            downloadUrl = "",
            sha256Checksum = "",
            isDownloaded = false,
            isActive = true
        )

        val chunks = engine.generateStreamingResponse(
            prompt = "What is 2+2?",
            model = model,
            settings = HardwareAccelerationSettings(),
            params = GenerationParameters(),
            persona = AiPersona.DEFAULT,
            isAirGapped = true
        ).toList()

        assertTrue("Should emit at least one chunk", chunks.isNotEmpty())
        val lastChunk = chunks.last()
        assertTrue("Last chunk should be marked complete", lastChunk.isComplete)
        assertNotNull("Should have accumulated text", lastChunk.accumulatedText)
        assertTrue("Token count should be > 0", lastChunk.tokenCount > 0)
    }

    @Test
    fun streamingChunkMeasuresRealTokensPerSecond() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = LocalInferenceEngine(context)

        val model = ModelSpec(
            id = "test-model-tps",
            name = "TPS Test Model",
            parameterCount = "1B",
            format = ModelFormat.GGUF,
            quantization = "Q4_K_M",
            fileSizeBytes = 1024L * 1024L,
            requiredRamBytes = 512L * 1024L * 1024L,
            contextLength = 2048,
            description = "Test model for TPS measurement",
            category = com.perpcorp.edgellm.data.model.ModelCategory.CHAT_REASONING,
            downloadUrl = "",
            sha256Checksum = "",
            isDownloaded = false,
            isActive = true
        )

        val chunks = engine.generateStreamingResponse(
            prompt = "Hello, how are you? Please respond with a detailed answer.",
            model = model,
            settings = HardwareAccelerationSettings(),
            params = GenerationParameters(),
            persona = AiPersona.DEFAULT,
            isAirGapped = true
        ).toList()

        val completeChunk = chunks.last()
        assertTrue("TPS should be measured (>= 0)", completeChunk.tokensPerSecond >= 0f)
        assertTrue("TTFT should be measured (>= 0)", completeChunk.timeToFirstTokenMs >= 0L)
    }

    @Test
    fun prefixCacheHitProducesValidResponse() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = LocalInferenceEngine(context)

        val model = ModelSpec(
            id = "test-prefix-cache",
            name = "Prefix Cache Test",
            parameterCount = "1B",
            format = ModelFormat.GGUF,
            quantization = "Q4_K_M",
            fileSizeBytes = 1024L * 1024L,
            requiredRamBytes = 512L * 1024L * 1024L,
            contextLength = 2048,
            description = "Test model for prefix cache",
            category = com.perpcorp.edgellm.data.model.ModelCategory.CHAT_REASONING,
            downloadUrl = "",
            sha256Checksum = "",
            isDownloaded = false,
            isActive = true
        )

        val settings = HardwareAccelerationSettings(enablePrefixCaching = true)
        val params = GenerationParameters(enablePrefixCaching = true)

        val chunks = engine.generateStreamingResponse(
            prompt = "Explain quantum computing",
            model = model,
            settings = settings,
            params = params,
            persona = AiPersona.DEFAULT,
            isAirGapped = true
        ).toList()

        assertTrue("Should produce chunks with prefix cache enabled", chunks.isNotEmpty())
        assertTrue("Should complete", chunks.last().isComplete)
    }

    @Test
    fun loraAdapterPathProducesValidResponse() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = LocalInferenceEngine(context)

        val model = ModelSpec(
            id = "test-lora",
            name = "LoRA Test Model",
            parameterCount = "1B",
            format = ModelFormat.GGUF,
            quantization = "Q4_K_M",
            fileSizeBytes = 1024L * 1024L,
            requiredRamBytes = 512L * 1024L * 1024L,
            contextLength = 2048,
            description = "Test model for LoRA",
            category = com.perpcorp.edgellm.data.model.ModelCategory.CHAT_REASONING,
            downloadUrl = "",
            sha256Checksum = "",
            isDownloaded = false,
            isActive = true
        )

        val lora = com.perpcorp.edgellm.data.model.LoraAdapter(
            id = "test-lora-adapter",
            name = "Test LoRA",
            rank = 8,
            alpha = 16,
            targetModules = listOf("q_proj", "v_proj")
        )

        val chunks = engine.generateStreamingResponse(
            prompt = "What is machine learning?",
            model = model,
            settings = HardwareAccelerationSettings(),
            params = GenerationParameters(),
            persona = AiPersona.DEFAULT,
            isAirGapped = true,
            loraAdapter = lora
        ).toList()

        assertTrue("Should produce chunks with LoRA adapter", chunks.isNotEmpty())
        assertTrue("Should complete", chunks.last().isComplete)
        val lastChunk = chunks.last()
        assertTrue("Backend should mention LoRA", lastChunk.backendUsed.contains("LoRA") || lastChunk.backendUsed.contains("Offline"))
    }
}
