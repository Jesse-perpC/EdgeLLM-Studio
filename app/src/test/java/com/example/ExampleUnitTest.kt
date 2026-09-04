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
}
