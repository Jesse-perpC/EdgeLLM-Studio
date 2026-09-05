package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("EdgeLLM Studio", appName)
  }

  @Test
  fun `model filename parser extracts metadata accurately`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = com.example.engine.ModelDownloadManager(context)

    val ggufSpec = manager.parseModelSpecFromFileName(
      fileName = "deepseek-r1-distill-qwen-1.5b-q4_k_m.gguf",
      fileSizeBytes = 1_120_000_000L,
      folderName = "Downloads/Models"
    )
    org.junit.Assert.assertNotNull(ggufSpec)
    assertEquals(com.example.data.model.ModelFormat.GGUF, ggufSpec?.format)
    org.junit.Assert.assertTrue(ggufSpec?.parameterCount?.contains("1.5") == true)
    assertEquals("Q4_K_M", ggufSpec?.quantization)
    org.junit.Assert.assertTrue(ggufSpec?.isImported == true)
    org.junit.Assert.assertTrue(ggufSpec?.isDownloaded == true)

    val onnxSpec = manager.parseModelSpecFromFileName(
      fileName = "whisper-tiny-fp16.onnx",
      fileSizeBytes = 75_000_000L,
      folderName = "AudioModels"
    )
    org.junit.Assert.assertNotNull(onnxSpec)
    assertEquals(com.example.data.model.ModelFormat.ONNX, onnxSpec?.format)
    assertEquals("FP16", onnxSpec?.quantization)
  }

  @Test
  fun `demo folder import populates catalog and marks models ready`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = com.example.engine.ModelDownloadManager(context)

    manager.importDemoModelFolder()

    val models = manager.modelsState.value
    val importedModels = models.filter { it.isImported }

    org.junit.Assert.assertTrue("Imported models should not be empty", importedModels.isNotEmpty())
    org.junit.Assert.assertTrue(
      "All imported models should be marked as downloaded/ready",
      importedModels.all { it.isDownloaded }
    )
  }
}
