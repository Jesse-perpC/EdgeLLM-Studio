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

  @Test
  fun `room database stores and retrieves local model metadata accurately`() {
    kotlinx.coroutines.runBlocking {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val db = androidx.room.Room.inMemoryDatabaseBuilder(
        context,
        com.example.data.local.AppDatabase::class.java
      ).allowMainThreadQueries().build()

      val dao = db.localModelDao()

      val entity = com.example.data.local.entity.LocalModelEntity(
        id = "test-llama-1b",
        name = "Llama 3.2 1B Instruct",
        parameterCount = "1.2B",
        format = "GGUF",
        quantization = "Q4_K_M",
        fileSize = 850_000_000L,
        requiredRamBytes = 1_200_000_000L,
        contextLength = 4096,
        description = "Fast local reasoning",
        category = "CHAT_REASONING",
        downloadUrl = "https://example.com/llama.gguf",
        sha256Checksum = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        isDownloaded = true,
        downloadProgress = 100,
        isImported = false,
        path = "/data/user/0/com.example/files/models/test-llama-1b.gguf",
        isActive = true
      )

      dao.insertModel(entity)

      val retrieved = dao.getModelById("test-llama-1b")
      org.junit.Assert.assertNotNull(retrieved)
      assertEquals("Llama 3.2 1B Instruct", retrieved?.name)
      assertEquals(850_000_000L, retrieved?.fileSize)
      assertEquals("/data/user/0/com.example/files/models/test-llama-1b.gguf", retrieved?.path)
      assertEquals(true, retrieved?.isDownloaded)

      dao.deleteModel("test-llama-1b")
      val afterDelete = dao.getModelById("test-llama-1b")
      org.junit.Assert.assertNull(afterDelete)

      db.close()
    }
  }

  @Test
  fun `memory safety manager correctly evaluates storage and ram limits without OOM`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val safetyManager = com.example.engine.MemorySafetyManager(context)

    // Reasonable size model (e.g. 500MB)
    val lightModel = com.example.data.model.ModelSpec(
      id = "light-model",
      name = "Lightweight TinyModel",
      parameterCount = "0.5B",
      format = com.example.data.model.ModelFormat.GGUF,
      quantization = "Q4_0",
      fileSizeBytes = 300_000_000L,
      requiredRamBytes = 500_000_000L,
      contextLength = 2048,
      description = "Ultra-small",
      category = com.example.data.model.ModelCategory.CHAT_REASONING,
      downloadUrl = "https://example.com/tiny.gguf",
      sha256Checksum = "dummy-sha"
    )

    val lightReport = safetyManager.evaluateModelSafety(lightModel)
    org.junit.Assert.assertNotNull(lightReport)
    org.junit.Assert.assertTrue(lightReport.totalRamBytes > 0)
    org.junit.Assert.assertTrue(lightReport.availableRamBytes > 0)
    org.junit.Assert.assertNotNull(lightReport.recommendation)

    // Impossibly large model (e.g. 1000 Terabytes) to test constraint tripping without crashing
    val hugeModel = com.example.data.model.ModelSpec(
      id = "giant-model",
      name = "Giant 70B Unquantized",
      parameterCount = "70B",
      format = com.example.data.model.ModelFormat.GGUF,
      quantization = "FP32",
      fileSizeBytes = 100_000_000_000_000L,
      requiredRamBytes = 150_000_000_000_000L,
      contextLength = 32768,
      description = "Oversized model",
      category = com.example.data.model.ModelCategory.CHAT_REASONING,
      downloadUrl = "https://example.com/huge.gguf",
      sha256Checksum = "dummy-sha"
    )

    val hugeReport = safetyManager.evaluateModelSafety(hugeModel)
    org.junit.Assert.assertFalse("Storage must not be sufficient for 100TB", hugeReport.isStorageSufficient)
    org.junit.Assert.assertFalse("Should be marked unsafe to run", hugeReport.isSafeToRun)
    org.junit.Assert.assertTrue(hugeReport.warningMessage?.contains("Insufficient local storage") == true)
  }

  @Test
  fun `checksum verification streams safely using chunked buffer without loading entire file in RAM`() {
    kotlinx.coroutines.runBlocking {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val manager = com.example.engine.ModelDownloadManager(context)
      manager.importDemoModelFolder()

      val importedModel = manager.modelsState.value.first { it.isImported }

      val deferred = kotlinx.coroutines.CompletableDeferred<Pair<Boolean, String>>()
      manager.verifyModelChecksum(importedModel.id) { isValid, hash ->
        deferred.complete(Pair(isValid, hash))
      }

      val (isValid, hash) = deferred.await()
      org.junit.Assert.assertTrue(isValid)
      org.junit.Assert.assertTrue("Hash should be non-empty SHA-256", hash.length == 64)
    }
  }

  @Test
  fun `ollama json helper produces valid ollama and openai schemas`() {
    val dummyModel = com.example.data.model.ModelSpec(
      id = "llama-3-2-1b",
      name = "Llama 3.2 1B Instruct",
      parameterCount = "1.2B",
      format = com.example.data.model.ModelFormat.GGUF,
      quantization = "Q4_K_M",
      fileSizeBytes = 850_000_000L,
      requiredRamBytes = 1_200_000_000L,
      contextLength = 4096,
      description = "Test model",
      category = com.example.data.model.ModelCategory.CHAT_REASONING,
      downloadUrl = "https://example.com/model.gguf",
      sha256Checksum = "a1b2c3d4e5f67890",
      isDownloaded = true
    )

    // Verify Ollama version JSON
    val versionJson = com.example.api.OllamaJsonHelper.createVersionResponse()
    val vObj = org.json.JSONObject(versionJson)
    org.junit.Assert.assertTrue(vObj.has("version"))
    assertEquals("0.4.0-edge-android", vObj.getString("version"))

    // Verify Ollama tags JSON schema
    val tagsJson = com.example.api.OllamaJsonHelper.createTagsResponse(listOf(dummyModel))
    val tagsObj = org.json.JSONObject(tagsJson)
    org.junit.Assert.assertTrue(tagsObj.has("models"))
    val modelsArr = tagsObj.getJSONArray("models")
    assertEquals(1, modelsArr.length())
    val firstItem = modelsArr.getJSONObject(0)
    assertEquals("llama-3-2-1b", firstItem.getString("model"))
    org.junit.Assert.assertTrue(firstItem.has("details"))
    assertEquals("gguf", firstItem.getJSONObject("details").getString("format"))

    // Verify OpenAI models JSON schema
    val openAiJson = com.example.api.OllamaJsonHelper.createOpenAiModelsResponse(listOf(dummyModel))
    val openAiObj = org.json.JSONObject(openAiJson)
    assertEquals("list", openAiObj.getString("object"))
    val dataArr = openAiObj.getJSONArray("data")
    assertEquals(1, dataArr.length())
    assertEquals("llama-3-2-1b", dataArr.getJSONObject(0).getString("id"))
  }

  @Test
  fun `ollama inference server starts listens and serves requests`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val engine = com.example.engine.LocalInferenceEngine()
    val testModel = com.example.data.model.ModelSpec(
      id = "test-llama",
      name = "Test Llama",
      parameterCount = "1.0B",
      format = com.example.data.model.ModelFormat.GGUF,
      quantization = "Q4_K_M",
      fileSizeBytes = 500_000_000L,
      requiredRamBytes = 800_000_000L,
      contextLength = 2048,
      description = "Test model",
      category = com.example.data.model.ModelCategory.CHAT_REASONING,
      downloadUrl = "https://example.com/test.gguf",
      sha256Checksum = "12345",
      isDownloaded = true
    )

    val server = com.example.api.OllamaInferenceServer(
      context = context,
      inferenceEngine = engine,
      modelProvider = { listOf(testModel) },
      activeModelProvider = { testModel },
      accelerationSettingsProvider = {
        com.example.data.model.HardwareAccelerationSettings()
      }
    )

    // Start server on an ephemeral random high port for unit testing
    val testPort = 18434
    val started = server.start(port = testPort, bindToLan = false)
    org.junit.Assert.assertTrue("Server should start successfully", started)
    org.junit.Assert.assertTrue("Stats should show running", server.serverStats.value.isRunning)
    assertEquals(testPort, server.serverStats.value.port)

    try {
      // Connect to the running server socket directly
      val url = java.net.URL("http://127.0.0.1:$testPort/api/version")
      val conn = url.openConnection() as java.net.HttpURLConnection
      conn.connectTimeout = 3000
      conn.readTimeout = 3000
      conn.requestMethod = "GET"
      assertEquals(200, conn.responseCode)
      val body = conn.inputStream.bufferedReader().readText()
      org.junit.Assert.assertTrue(body.contains("0.4.0-edge-android"))
      conn.disconnect()

      // Connect to /api/tags
      val tagsUrl = java.net.URL("http://127.0.0.1:$testPort/api/tags")
      val tagsConn = tagsUrl.openConnection() as java.net.HttpURLConnection
      tagsConn.connectTimeout = 3000
      tagsConn.readTimeout = 3000
      tagsConn.requestMethod = "GET"
      assertEquals(200, tagsConn.responseCode)
      val tagsBody = tagsConn.inputStream.bufferedReader().readText()
      org.junit.Assert.assertTrue(tagsBody.contains("test-llama"))
      tagsConn.disconnect()
    } finally {
      server.stop()
      org.junit.Assert.assertFalse("Server should be stopped", server.serverStats.value.isRunning)
    }
  }

  @Test
  fun `offline knowledge engine generates accurate response for java objects and technical queries`() {
    val dummyModel = com.example.data.model.ModelSpec(
      id = "test-model",
      name = "Llama 3.2 1B",
      parameterCount = "1.2B",
      format = com.example.data.model.ModelFormat.GGUF,
      quantization = "Q4_K_M",
      fileSizeBytes = 500_000_000L,
      requiredRamBytes = 800_000_000L,
      contextLength = 4096,
      description = "Test model",
      category = com.example.data.model.ModelCategory.CHAT_REASONING,
      downloadUrl = "https://example.com/test.gguf",
      sha256Checksum = "dummy-sha",
      isDownloaded = true
    )

    val engine = com.example.engine.LocalInferenceEngine()
    val response = engine.generateOfflineIntelligence(
      prompt = "what are objects in java?",
      model = dummyModel,
      params = com.example.data.model.GenerationParameters()
    )

    org.junit.Assert.assertTrue("Response should explain instance of a class", response.contains("instance of a class", ignoreCase = true))
    org.junit.Assert.assertTrue("Response should explain state and behavior", response.contains("state", ignoreCase = true) && response.contains("behavior", ignoreCase = true))
    org.junit.Assert.assertTrue("Response should mention heap memory", response.contains("Heap", ignoreCase = true))
    org.junit.Assert.assertTrue("Response should include Java code snippet", response.contains("public class") || response.contains("new "))
  }

  @Test
  fun `speech to text manager initializes and reports state properly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val sttManager = com.example.engine.SpeechToTextManager(context)

    assertEquals(com.example.engine.SpeechToTextManager.SpeechState.Idle, sttManager.speechState.value)
    org.junit.Assert.assertFalse(sttManager.isListening.value)
    assertEquals("", sttManager.partialText.value)

    sttManager.cancel()
    sttManager.destroy()
  }

  @Test
  fun `edge recognition service instance creation succeeds`() {
    val service = com.example.assistant.EdgeRecognitionService()
    org.junit.Assert.assertNotNull(service)
  }

  @Test
  fun `precise response engine delivers direct on-point answers for factual and math queries`() {
    val dummyModel = com.example.data.model.ModelSpec(
      id = "test-model",
      name = "Test Model",
      parameterCount = "1B",
      format = com.example.data.model.ModelFormat.GGUF,
      quantization = "Q4_K_M",
      fileSizeBytes = 500L * 1024 * 1024,
      requiredRamBytes = 800L * 1024 * 1024,
      contextLength = 2048,
      description = "Test",
      category = com.example.data.model.ModelCategory.GENERAL,
      downloadUrl = "https://example.com/test",
      sha256Checksum = "abc",
      isDownloaded = true,
      isActive = true
    )
    val engine = com.example.engine.LocalInferenceEngine()

    // 1. Factual query
    val capitalResponse = engine.generateOfflineIntelligence(
      prompt = "what is the capital of France?",
      model = dummyModel,
      params = com.example.data.model.GenerationParameters()
    )
    org.junit.Assert.assertTrue("Capital of France should be Paris", capitalResponse.contains("Paris", ignoreCase = true))

    // 2. Math calculation
    val mathResponse = engine.generateOfflineIntelligence(
      prompt = "calculate 25 * 14",
      model = dummyModel,
      params = com.example.data.model.GenerationParameters()
    )
    org.junit.Assert.assertTrue("Calculation should equal 350", mathResponse.contains("350"))

    // 3. Physical constant
    val physicsResponse = engine.generateOfflineIntelligence(
      prompt = "what is the speed of light?",
      model = dummyModel,
      params = com.example.data.model.GenerationParameters()
    )
    org.junit.Assert.assertTrue("Speed of light should mention 299,792,458", physicsResponse.contains("299,792,458"))

    // 4. Code snippet
    val codeResponse = engine.generateOfflineIntelligence(
      prompt = "write code in python to process data",
      model = dummyModel,
      params = com.example.data.model.GenerationParameters()
    )
    org.junit.Assert.assertTrue("Code response should contain python block", codeResponse.contains("```python"))

    // 5. Default parameters verify precision configuration
    val defaultParams = com.example.data.model.GenerationParameters()
    org.junit.Assert.assertEquals(0.3f, defaultParams.temperature, 0.01f)
    org.junit.Assert.assertTrue("System prompt should enforce staying on topic", defaultParams.systemPrompt.contains("strictly on topic", ignoreCase = true))
  }

  @Test
  fun `output verification engine strips conversational preamble and balances unclosed code blocks`() {
    val rawPreambleOutput = "Sure! Here is the answer to your question: The speed of light is 300,000 km/s. Hope this helps!"
    val result = com.example.engine.OutputVerificationEngine.verifyAndRefine(
      rawOutput = rawPreambleOutput,
      query = "what is the speed of light?"
    )

    org.junit.Assert.assertFalse("Preamble should be stripped", result.verifiedText.startsWith("Sure!"))
    org.junit.Assert.assertFalse("Trailing fluff should be stripped", result.verifiedText.endsWith("Hope this helps!"))
    org.junit.Assert.assertTrue("Core content must be preserved", result.verifiedText.contains("speed of light"))
    org.junit.Assert.assertTrue("Corrections should be logged", result.correctionsApplied.isNotEmpty())

    // Unclosed markdown code fence test
    val unclosedCode = "Here is the code:\n```kotlin\nval x = 42"
    val codeResult = com.example.engine.OutputVerificationEngine.verifyAndRefine(
      rawOutput = unclosedCode,
      query = "write code"
    )
    val fenceCount = codeResult.verifiedText.split("```").size - 1
    org.junit.Assert.assertEquals("Code fences must be balanced", 2, fenceCount)
  }

  @Test
  fun `chain of thought validation layer processes thought blocks and preserves reasoning step integrity`() {
    val modelOutputWithCot = "<think>\n1. User asks for photosynthesis definition.\n2. Chemical formula: 6CO2 + 6H2O -> C6H12O6 + 6O2.\n3. Formulate direct explanation.\n</think>\n\nPhotosynthesis is the biological process by which plants convert light energy into chemical energy."
    val result = com.example.engine.OutputVerificationEngine.verifyAndRefine(
      rawOutput = modelOutputWithCot,
      query = "explain photosynthesis"
    )

    org.junit.Assert.assertTrue("Output must contain think block", result.verifiedText.contains("<think>"))
    org.junit.Assert.assertTrue("Output must contain end think tag", result.verifiedText.contains("</think>"))
    org.junit.Assert.assertTrue("Output must contain final answer", result.verifiedText.contains("Photosynthesis is the biological process"))
    org.junit.Assert.assertTrue("Flags must indicate verified CoT", result.verificationFlags.contains("COT_VERIFIED"))
  }

  @Test
  fun `multi agent feedback loop critiques and refines inaccurate factual calculations`() {
    val query = "calculate 45 * 8"
    val faultyInitialOutput = "The answer to 45 * 8 is 320."

    val critique = com.example.engine.MultiAgentRefinementEngine.evaluateCandidate(
      query = query,
      candidateOutput = faultyInitialOutput
    )

    org.junit.Assert.assertTrue("Faulty math must trigger refinement", critique.requiresRefinement)
    org.junit.Assert.assertTrue("Must diagnose arithmetic inaccuracy", critique.diagnosedWeaknesses.contains("ARITHMETIC_INACCURACY"))
    org.junit.Assert.assertTrue("Factual score must be below threshold", critique.factualAccuracyScore < 0.75f)

    val refinementResult = com.example.engine.MultiAgentRefinementEngine.refineIfNeeded(
      query = query,
      initialOutput = faultyInitialOutput
    )

    org.junit.Assert.assertTrue("Output must be refined", refinementResult.wasRefined)
    org.junit.Assert.assertTrue("Refined text must contain correct calculation 360", refinementResult.refinedText.contains("360"))
    org.junit.Assert.assertTrue("Post-refinement trust must exceed pre-refinement", refinementResult.evaluation.overallTrustScore >= critique.overallTrustScore)
  }

  @Test
  fun `multi agent feedback loop audits tone and apologetic fluff below threshold`() {
    val query = "what is kotlin"
    val apologeticOutput = "I apologize, as an AI I am sorry for any confusion, but Kotlin is a statically typed programming language."

    val critique = com.example.engine.MultiAgentRefinementEngine.evaluateCandidate(
      query = query,
      candidateOutput = apologeticOutput
    )

    org.junit.Assert.assertTrue("Must detect apologetic tone", critique.diagnosedWeaknesses.contains("APOLOGETIC_BOILERPLATE"))

    val refinementResult = com.example.engine.MultiAgentRefinementEngine.refineIfNeeded(
      query = query,
      initialOutput = apologeticOutput
    )

    org.junit.Assert.assertTrue("Must refine apologetic phrasing", refinementResult.wasRefined)
    org.junit.Assert.assertFalse("Refined text must not contain 'as an ai'", refinementResult.refinedText.contains("as an ai", ignoreCase = true))
  }

  @Test
  fun `agent feedback dao persists and retrieves feedback logs correctly`() {
    kotlinx.coroutines.runBlocking {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val db = androidx.room.Room.inMemoryDatabaseBuilder(
        context,
        com.example.data.local.AppDatabase::class.java
      ).allowMainThreadQueries().build()

      val dao = db.agentFeedbackDao()

      val feedbackLog = com.example.data.local.entity.AgentFeedbackLogEntity(
        id = "fb_test_101",
        messageId = "msg_test_101",
        sessionId = "session_default",
        originalPrompt = "What is 15 * 12?",
        modelResponse = "15 * 12 is 180.",
        factualAccuracyScore = 0.98f,
        sentimentToneScore = 0.95f,
        topicAdherenceScore = 0.96f,
        trustScore = 0.96f,
        wasRefined = true,
        critiqueSummary = "Refined arithmetic error",
        userRating = 1,
        userNotes = "Accurate calculation",
        createdAt = System.currentTimeMillis()
      )

      dao.insertFeedback(feedbackLog)

      val retrieved = dao.getFeedbackForMessage("msg_test_101")
      org.junit.Assert.assertNotNull(retrieved)
      assertEquals("fb_test_101", retrieved?.id)
      assertEquals(1, retrieved?.userRating)
      org.junit.Assert.assertTrue(retrieved?.wasRefined == true)
      assertEquals(0.98f, retrieved?.factualAccuracyScore ?: 0f, 0.001f)

      // Test rating update
      dao.updateUserRating("fb_test_101", -1, "Found error")
      val updated = dao.getFeedbackForMessage("msg_test_101")
      assertEquals(-1, updated?.userRating)
      assertEquals("Found error", updated?.userNotes)

      // Test deletion
      dao.deleteFeedback("fb_test_101")
      org.junit.Assert.assertNull(dao.getFeedbackForMessage("msg_test_101"))

      db.close()
    }
  }

  @Test
  fun `chat message dao correctly stores and loads audit trust scores`() {
    kotlinx.coroutines.runBlocking {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val db = androidx.room.Room.inMemoryDatabaseBuilder(
        context,
        com.example.data.local.AppDatabase::class.java
      ).allowMainThreadQueries().build()

      val chatDao = db.chatDao()

      val messageEntity = com.example.data.local.entity.ChatMessageEntity(
        id = "msg_audit_test",
        sender = "ASSISTANT",
        text = "Verified response",
        timestamp = System.currentTimeMillis(),
        tokensGenerated = 42,
        tokensPerSecond = 24.5f,
        timeToFirstTokenMs = 120,
        executionBackend = "NNAPI",
        modelId = "test-model",
        trustScore = 0.94f,
        factualAccuracyScore = 0.97f,
        sentimentToneScore = 0.91f,
        wasRefined = true,
        critiqueSummary = "Self-refinement applied",
        userRating = 1,
        userFeedbackNotes = "Helpful"
      )

      chatDao.insertMessage(messageEntity)

      val retrieved = chatDao.getMessageById("msg_audit_test")
      org.junit.Assert.assertNotNull(retrieved)
      assertEquals(0.94f, retrieved?.trustScore ?: 0f, 0.001f)
      assertEquals(0.97f, retrieved?.factualAccuracyScore ?: 0f, 0.001f)
      org.junit.Assert.assertTrue(retrieved?.wasRefined == true)
      assertEquals(1, retrieved?.userRating)

      db.close()
    }
  }
}
