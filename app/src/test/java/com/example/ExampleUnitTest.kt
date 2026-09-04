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
}
