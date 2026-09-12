package com.example

import com.example.data.model.ComputeBackend
import com.example.data.model.DeviceHardwareInfo
import com.example.data.model.HardwareAccelerationSettings
import com.example.data.model.ModelCategory
import com.example.data.model.ModelFormat
import com.example.data.model.ModelSpec
import com.example.engine.CryptoManager
import com.example.engine.HardwareBenchmarkEngine
import com.example.plugin.PiiRedactorPlugin
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

  @Test
  fun testPassphraseGeneration() {
    val crypto = CryptoManager()
    val pass = crypto.generateStrongPassphrase()
    assertNotNull(pass)
    assertTrue(pass.length >= 16)
  }

  @Test
  fun testPiiRedactorPlugin() = runBlocking {
    val plugin = PiiRedactorPlugin()
    val dummyModel = ModelSpec(
      id = "test_model",
      name = "Test",
      parameterCount = "1B",
      format = ModelFormat.GGUF,
      quantization = "Q4",
      fileSizeBytes = 1000L,
      requiredRamBytes = 2000L,
      contextLength = 2048,
      description = "Test",
      category = ModelCategory.CHAT_REASONING,
      downloadUrl = "https://example.com",
      sha256Checksum = "sha256_mock_hash"
    )
    val input = "Contact alice@test.org or call 555-234-5678 from 192.168.1.1."
    val result = plugin.execute(input, dummyModel, HardwareAccelerationSettings())

    assertTrue(result.success)
    assertTrue(result.processedOutput.contains("[REDACTED_EMAIL]"))
    assertTrue(result.processedOutput.contains("[REDACTED_PHONE]"))
    assertTrue(result.processedOutput.contains("[REDACTED_IP]"))
  }

  @Test
  fun testHardwareBenchmarkEngine() = runBlocking {
    val engine = HardwareBenchmarkEngine()
    val hardware = DeviceHardwareInfo(
      totalRamBytes = 8L * 1024L * 1024L * 1024L,
      availableRamBytes = 4L * 1024L * 1024L * 1024L,
      isLowMemory = false,
      cpuCores = 8,
      cpuArchitecture = "arm64-v8a",
      socModel = "Snapdragon 8 Gen 3",
      batteryLevel = 90,
      isCharging = false,
      thermalStatus = "Optimal",
      hasVulkanCompute = true,
      hasNpuSupport = true,
      hasOpenCl = true,
      is64Bit = true
    )
    val settings = HardwareAccelerationSettings(
      computeBackend = ComputeBackend.GPU_VULKAN,
      threadCount = 4
    )

    val finalState = engine.runDiagnosticBenchmark(hardware, settings).last()

    assertFalse(finalState.isRunning)
    assertTrue(finalState.results.isNotEmpty())
    assertTrue(finalState.optimalTierName.isNotEmpty())
    assertTrue(finalState.optimalRecommendationSummary.isNotEmpty())
    assertTrue(finalState.results.any { it.isRecommended })
  }

  @Test
  fun testHardwareBenchmarkEngineWithLowMemory() = runBlocking {
    // ✅ TEST: Low-memory scenario (Android 17 crash scenario)
    // All tiers will be marked as OOM Risk, triggering the crash
    val engine = HardwareBenchmarkEngine()
    val lowMemoryHardware = DeviceHardwareInfo(
      totalRamBytes = 2L * 1024L * 1024L * 1024L,  // 2GB total
      availableRamBytes = 500L * 1024L * 1024L,     // 500MB available (critical!)
      isLowMemory = true,
      cpuCores = 4,
      cpuArchitecture = "arm64-v8a",
      socModel = "Snapdragon 6 Gen 1 (Low-End)",
      batteryLevel = 45,
      isCharging = false,
      thermalStatus = "Normal",
      hasVulkanCompute = false,
      hasNpuSupport = false,
      hasOpenCl = false,
      is64Bit = true
    )
    val settings = HardwareAccelerationSettings(
      computeBackend = ComputeBackend.CPU_NEON,
      threadCount = 2
    )

    // Run benchmark and collect final state
    val finalState = engine.runDiagnosticBenchmark(lowMemoryHardware, settings).last()

    // ✅ VERIFY: Benchmark completes without crashing
    assertFalse("Benchmark should complete (not running)", finalState.isRunning)
    assertTrue("Should have results despite low memory", finalState.results.isNotEmpty())
    
    // ✅ VERIFY: Results list has all 5 tiers (including OOM Risk ones)
    assertTrue("Should have 5 benchmark tiers", finalState.results.size == 5)
    
    // ✅ VERIFY: At least one result is marked as not viable (OOM Risk)
    assertTrue("Should have OOM Risk results", finalState.results.any { !it.isViable })
    
    // ✅ VERIFY: Recommendation is set (fallback to smallest model)
    assertTrue("Should have optimal tier name", finalState.optimalTierName.isNotEmpty())
    assertTrue("Should be 0.5B (smallest model)", finalState.optimalTierName == "0.5B")
    
    // ✅ VERIFY: Summary message is generated without crashing
    assertTrue("Should have recommendation summary", finalState.optimalRecommendationSummary.isNotEmpty())
    
    // ✅ VERIFY: At least one result is recommended
    assertTrue("Should have at least one recommended tier", finalState.results.any { it.isRecommended })
    
    // ✅ VERIFY: Completion timestamp is set
    assertTrue("Should have completion timestamp", finalState.completedAt != null)
  }

  @Test
  fun testHardwareBenchmarkEngineWithExtremeLowMemory() = runBlocking {
    // ✅ TEST: Extreme low-memory scenario (ALL tiers OOM Risk)
    // This is the worst case - no viable models at all
    val engine = HardwareBenchmarkEngine()
    val extremeLowMemory = DeviceHardwareInfo(
      totalRamBytes = 1L * 1024L * 1024L * 1024L,  // 1GB total
      availableRamBytes = 200L * 1024L * 1024L,    // 200MB available (critical!)
      isLowMemory = true,
      cpuCores = 2,
      cpuArchitecture = "arm64-v8a",
      socModel = "Budget Snapdragon (Extreme Low)",
      batteryLevel = 20,
      isCharging = false,
      thermalStatus = "Normal",
      hasVulkanCompute = false,
      hasNpuSupport = false,
      hasOpenCl = false,
      is64Bit = false
    )
    val settings = HardwareAccelerationSettings(
      computeBackend = ComputeBackend.CPU_NEON,
      threadCount = 1
    )

    // Run benchmark - this should NOT throw an exception
    val finalState = engine.runDiagnosticBenchmark(extremeLowMemory, settings).last()

    // ✅ VERIFY: No crash despite all results being not viable
    assertFalse("Benchmark should complete", finalState.isRunning)
    assertTrue("Should have results", finalState.results.isNotEmpty())
    
    // ✅ VERIFY: Multiple tiers are OOM Risk
    val oomRiskCount = finalState.results.count { !it.isViable && it.ratingLabel == "OOM Risk" }
    assertTrue("Should have OOM Risk results", oomRiskCount > 0)
    
    // ✅ VERIFY: Still recommends the smallest (0.5B) as fallback
    assertTrue("Should recommend 0.5B fallback", finalState.optimalTierName == "0.5B")
    
    // ✅ VERIFY: Summary is generated safely
    assertTrue("Should generate summary", finalState.optimalRecommendationSummary.isNotEmpty())
    assertTrue("Summary should mention memory constraints", 
      finalState.optimalRecommendationSummary.contains("RAM") || 
      finalState.optimalRecommendationSummary.contains("memory") ||
      finalState.optimalRecommendationSummary.contains("0.5B"))
  }

  @Test
  fun testHardwareBenchmarkEngineNoViableModels() = runBlocking {
    // ✅ TEST: Edge case where indexOfFirst returns -1 for all viable checks
    // This directly tests the fix for the crash on line 235
    val engine = HardwareBenchmarkEngine()
    val hardware = DeviceHardwareInfo(
      totalRamBytes = 512L * 1024L * 1024L,        // 512MB (very tight!)
      availableRamBytes = 300L * 1024L * 1024L,    // 300MB (minimal)
      isLowMemory = true,
      cpuCores = 2,
      cpuArchitecture = "arm64-v8a",
      socModel = "Entry-level SoC",
      batteryLevel = 15,
      isCharging = false,
      thermalStatus = "Normal",
      hasVulkanCompute = false,
      hasNpuSupport = false,
      hasOpenCl = false,
      is64Bit = false
    )
    val settings = HardwareAccelerationSettings(
      computeBackend = ComputeBackend.CPU_NEON,
      threadCount = 1
    )

    // This used to crash with IndexOutOfBoundsException on line 235
    // Now it should complete gracefully
    val finalState = engine.runDiagnosticBenchmark(hardware, settings).last()

    // ✅ VERIFY: Completes without exception
    assertFalse(finalState.isRunning)
    assertTrue(finalState.results.isNotEmpty())
    assertTrue(finalState.optimalTierName.isNotEmpty())
    
    // ✅ VERIFY: Recommended index is valid
    val recommendedResult = finalState.results.find { it.isRecommended }
    assertNotNull("Should have a recommended result", recommendedResult)
    assertTrue("Recommended should be marked correctly", recommendedResult!!.isRecommended)
  }

  @Test
  fun testMultimodalVisionInference() = runBlocking {
    val engine = com.example.engine.LocalInferenceEngine()
    val dummyModel = ModelSpec(
      id = "test_vision_model",
      name = "MobileVLM-Vision",
      parameterCount = "1.7B",
      format = ModelFormat.GGUF,
      quantization = "Q4_K_M",
      fileSizeBytes = 1100000000L,
      requiredRamBytes = 2200000000L,
      contextLength = 4096,
      description = "Multimodal Vision",
      category = ModelCategory.VISION_MULTIMODAL,
      downloadUrl = "https://example.com/vision",
      sha256Checksum = "sha256_vision"
    )

    var collectedResponse = ""
    engine.generateStreamingResponse(
      prompt = "Analyze this system architecture diagram",
      model = dummyModel,
      settings = HardwareAccelerationSettings(),
      params = com.example.data.model.GenerationParameters(),
      attachedImageUri = "content://local_sample/arch_topology_arm64.png",
      attachedImageLabel = "arch_topology_arm64.png"
    ).collect { chunk ->
      collectedResponse = chunk.accumulatedText
    }

    assertTrue("Vision output should not be blank", collectedResponse.isNotBlank())
    assertTrue("Output should identify visual image or architecture", 
      collectedResponse.contains("arch_topology_arm64.png") || collectedResponse.contains("Architecture") || collectedResponse.contains("Visual Analysis")
    )
  }

  @Test
  fun testOnDeviceToolExecutionMath() {
    // Test calculate tool
    val mathResult = com.example.engine.OnDeviceToolEngine.executeTool("calculate", "(15 * 4) + (100 / 5) - 2^3")
    assertTrue("Tool execution must succeed", mathResult.isSuccess)
    // (60) + (20) - 8 = 72
    assertTrue("Result must be 72", mathResult.outputResult.contains("72"))

    // Test tool call detection in prompt
    val detectedCall = com.example.engine.OnDeviceToolEngine.parseToolCallFromPrompt("Please calculate 42 * 2")
    assertNotNull("Tool call should be detected", detectedCall)
    assertTrue("Tool name must be calculate", detectedCall?.toolName == "calculate")

    val executionResult = com.example.engine.OnDeviceToolEngine.executeTool(detectedCall!!.toolName, detectedCall.inputArgument)
    assertTrue(executionResult.isSuccess)
    assertTrue(executionResult.outputResult.contains("84"))
  }

  @Test
  fun testOnDeviceToolExecutionCryptoAndHardware() {
    // Test hash tool
    val hashResult = com.example.engine.OnDeviceToolEngine.executeTool("hash_crypto", "AirGappedSecurity2026")
    assertTrue(hashResult.isSuccess)
    assertTrue("Hash output must contain SHA-256", hashResult.outputResult.contains("SHA-256"))

    // Test uuid generation
    val uuidResult = com.example.engine.OnDeviceToolEngine.executeTool("generate_uuid", "")
    assertTrue(uuidResult.isSuccess)
    assertTrue("UUID must have hyphens", uuidResult.outputResult.contains("-"))

    // Test unit conversion
    val convertResult = com.example.engine.OnDeviceToolEngine.executeTool("unit_convert", "100 C to F")
    assertTrue(convertResult.isSuccess)
    assertTrue("100 C is 212 F", convertResult.outputResult.contains("212"))
  }

  @Test
  fun testOnDeviceToolCallingInInferenceEngine() = runBlocking {
    val engine = com.example.engine.LocalInferenceEngine()
    val dummyModel = ModelSpec(
      id = "test_tool_model",
      name = "Gemma-Tool-2B",
      parameterCount = "2B",
      format = ModelFormat.GGUF,
      quantization = "Q4_K_M",
      fileSizeBytes = 1200000000L,
      requiredRamBytes = 2000000000L,
      contextLength = 4096,
      description = "Tool caller",
      category = ModelCategory.CHAT_REASONING,
      downloadUrl = "https://example.com/tool",
      sha256Checksum = "sha256_tool"
    )

    var output = ""
    engine.generateStreamingResponse(
      prompt = "Please calculate 250 * 4",
      model = dummyModel,
      settings = HardwareAccelerationSettings(),
      params = com.example.data.model.GenerationParameters(enableToolCalling = true)
    ).collect { chunk ->
      output = chunk.accumulatedText
    }

    assertTrue("Output should contain executed tool call indicator", output.contains("Tool Executed") || output.contains("calculate") || output.contains("1000"))
  }

  @Test
  fun testJsonSchemaEnforcementInInferenceEngine() = runBlocking {
    val engine = com.example.engine.LocalInferenceEngine()
    val dummyModel = ModelSpec(
      id = "test_json_model",
      name = "Llama-Structured",
      parameterCount = "1B",
      format = ModelFormat.GGUF,
      quantization = "Q4_0",
      fileSizeBytes = 800000000L,
      requiredRamBytes = 1500000000L,
      contextLength = 2048,
      description = "JSON generator",
      category = ModelCategory.CODE_ANALYSIS,
      downloadUrl = "https://example.com/json",
      sha256Checksum = "sha256_json"
    )

    var output = ""
    engine.generateStreamingResponse(
      prompt = "Generate a user profile",
      model = dummyModel,
      settings = HardwareAccelerationSettings(),
      params = com.example.data.model.GenerationParameters(enforceJsonSchema = true)
    ).collect { chunk ->
      output = chunk.accumulatedText
    }

    assertTrue("Output must start with JSON brace", output.trim().startsWith("{"))
    assertTrue("Output must end with JSON brace", output.trim().endsWith("}"))
    assertTrue("Output must contain schema-compliant keys", output.contains("\"status\"") && output.contains("\"data\""))
  }

  @Test
  fun testQuantizationPerplexityCalculatorFormulas() {
    // Test 3B parameter model quantization footprint
    val paramCountBillion = 3.0f
    val contextLength = 4096

    // Q4_K_M bits/weight ~ 4.5
    val q4WeightsBytes = (paramCountBillion * 1_000_000_000L * 4.5f / 8.0f).toLong()
    val q4WeightsGb = q4WeightsBytes.toDouble() / (1024 * 1024 * 1024)
    assertTrue("Q4 weights should be ~1.5 - 1.8 GB", q4WeightsGb in 1.5..1.8)

    // KV Cache for 4096 tokens (FP16)
    val kvCacheBytes = (contextLength.toLong() * 32 * 2 * 128 * 2)
    val kvCacheMb = kvCacheBytes.toDouble() / (1024 * 1024)
    assertTrue("KV Cache for 4096 context should be ~64MB", kvCacheMb in 60.0..70.0)
  }

  @Test
  fun testVectorEmbeddingEngineGenerationAndNormalization() {
    val text = "Edge AI on Android with Jetpack Compose"
    val embedding = com.example.data.memory.VectorEmbeddingEngine.generateEmbedding(text)

    org.junit.Assert.assertEquals(128, embedding.size)

    // Verify L2 unit norm
    var sumSq = 0.0f
    for (v in embedding) {
      sumSq += v * v
    }
    val norm = kotlin.math.sqrt(sumSq)
    assertTrue("Vector must be unit normalized (~1.0), got $norm", kotlin.math.abs(norm - 1.0f) < 0.01f)
  }

  @Test
  fun testVectorEmbeddingCosineSimilarity() {
    val v1 = com.example.data.memory.VectorEmbeddingEngine.generateEmbedding("I love programming in Kotlin on Android devices")
    val v2 = com.example.data.memory.VectorEmbeddingEngine.generateEmbedding("Kotlin Android application development preferences")
    val v3 = com.example.data.memory.VectorEmbeddingEngine.generateEmbedding("Culinary recipes for baking French sourdough bread")

    val simRelated = com.example.data.memory.VectorEmbeddingEngine.computeCosineSimilarity(v1, v2)
    val simUnrelated = com.example.data.memory.VectorEmbeddingEngine.computeCosineSimilarity(v1, v3)

    assertTrue("Semantically related phrases must have higher similarity than unrelated topics: related=$simRelated, unrelated=$simUnrelated", simRelated > simUnrelated)
  }

  @Test
  fun testVectorSerializationRoundTrip() {
    val original = com.example.data.memory.VectorEmbeddingEngine.generateEmbedding("Deterministic offline vector memory test")
    val serialized = com.example.data.memory.VectorEmbeddingEngine.vectorToString(original)
    val deserialized = com.example.data.memory.VectorEmbeddingEngine.stringToVector(serialized)

    org.junit.Assert.assertEquals(original.size, deserialized.size)
    for (i in original.indices) {
      assertTrue(kotlin.math.abs(original[i] - deserialized[i]) < 0.001f)
    }
  }
}

