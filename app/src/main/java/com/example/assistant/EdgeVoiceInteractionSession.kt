package com.example.assistant

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.service.voice.VoiceInteractionSession
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.data.model.HardwareAccelerationSettings
import com.example.data.model.ModelCategory
import com.example.data.model.ModelFormat
import com.example.data.model.ModelSpec
import com.example.engine.LocalInferenceEngine
import com.example.engine.VoiceSpeechManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * System-Wide Voice Interaction Session with Android Speech-to-Text Integration
 * and Gemini / On-Device Cognitive Engine conversational processing.
 */
class EdgeVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

    companion object {
        private const val TAG = "EdgeVoiceSession"
    }

    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val inferenceEngine = LocalInferenceEngine()
    private val cognitiveEngine = AssistantCognitiveEngine(context, inferenceEngine)
    private val voiceSpeechManager = VoiceSpeechManager(context.applicationContext as android.app.Application)

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var lastScreenContext: String? = null

    // UI Elements
    private var rootView: View? = null
    private var statusTextView: TextView? = null
    private var liveTranscribeTextView: TextView? = null
    private var thoughtTextView: TextView? = null
    private var responseTextView: TextView? = null
    private var micButton: ImageButton? = null
    private var inputQueryEditText: EditText? = null
    private var micWaveIndicator: View? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "EdgeVoiceInteractionSession created")
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSpeechRecognition()
        speechRecognizer?.destroy()
        speechRecognizer = null
        sessionScope.cancel()
        voiceSpeechManager.shutdown()
    }

    override fun onCreateContentView(): View {
        val container = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val cardLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 36, 40, 36)
            val bgDrawable = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xF0131722.toInt()) // Deep slate modern glass container
                cornerRadius = 32f
                setStroke(2, 0xFF38BDF8.toInt())
            }
            background = bgDrawable
            val lp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM
                setMargins(20, 20, 20, 48)
            }
            layoutParams = lp
        }

        // Top Header Row
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val titleView = TextView(context).apply {
            text = "✨ EdgeLLM Voice Assistant"
            textSize = 15f
            setTextColor(0xFF38BDF8.toInt())
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        headerRow.addView(titleView)

        val dismissBtn = TextView(context).apply {
            text = "✕"
            textSize = 18f
            setTextColor(0xFF94A3B8.toInt())
            setPadding(16, 8, 8, 8)
            setOnClickListener {
                stopSpeechRecognition()
                voiceSpeechManager.stop()
                hide()
            }
        }
        headerRow.addView(dismissBtn)
        cardLayout.addView(headerRow)

        // Telemetry status
        statusTextView = TextView(context).apply {
            text = "Ready to listen • Connected to Gemini & Edge Silicon"
            textSize = 11.5f
            setTextColor(0xFF94A3B8.toInt())
            setPadding(0, 4, 0, 8)
        }
        cardLayout.addView(statusTextView)

        // Live Voice Transcription Box
        liveTranscribeTextView = TextView(context).apply {
            text = "Tap microphone to speak or use text input below..."
            textSize = 13.5f
            setTextColor(0xFFE2E8F0.toInt())
            setPadding(20, 14, 20, 14)
            val liveBg = android.graphics.drawable.GradientDrawable().apply {
                setColor(0x2538BDF8.toInt())
                cornerRadius = 16f
                setStroke(1, 0x4038BDF8.toInt())
            }
            background = liveBg
        }
        cardLayout.addView(liveTranscribeTextView)

        // Mic volume wave indicator bar
        micWaveIndicator = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                6
            ).apply {
                setMargins(16, 8, 16, 8)
            }
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF38BDF8.toInt())
                cornerRadius = 3f
            }
            visibility = View.GONE
        }
        cardLayout.addView(micWaveIndicator)

        // Autonomous Thought Box
        thoughtTextView = TextView(context).apply {
            text = "Thinking..."
            textSize = 11.5f
            setTextColor(0xFFA78BFA.toInt())
            setPadding(20, 12, 20, 12)
            val thoughtBg = android.graphics.drawable.GradientDrawable().apply {
                setColor(0x25A78BFA.toInt())
                cornerRadius = 14f
            }
            background = thoughtBg
            visibility = View.GONE
        }
        cardLayout.addView(thoughtTextView)

        // Assistant Output Text
        responseTextView = TextView(context).apply {
            text = ""
            textSize = 14f
            setTextColor(0xFFF8FAFC.toInt())
            setPadding(0, 12, 0, 12)
            visibility = View.GONE
        }
        cardLayout.addView(responseTextView)

        // Input Controls Bar (Mic Button + Text Input + Send Button)
        val inputControlsLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 8, 0, 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Microphone Button
        micButton = ImageButton(context).apply {
            setImageResource(android.R.drawable.ic_btn_speak_now)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val micBg = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(0xFF0284C7.toInt())
            }
            background = micBg
            val p = LinearLayout.LayoutParams(88, 88).apply {
                setMargins(0, 0, 12, 0)
            }
            layoutParams = p
            setOnClickListener {
                if (isListening) {
                    stopSpeechRecognition()
                } else {
                    startSpeechRecognition()
                }
            }
        }
        inputControlsLayout.addView(micButton)

        // Text input field
        inputQueryEditText = EditText(context).apply {
            hint = "Or type query..."
            textSize = 13f
            setTextColor(0xFFF1F5F9.toInt())
            setHintTextColor(0xFF64748B.toInt())
            val editBg = android.graphics.drawable.GradientDrawable().apply {
                setColor(0x20FFFFFF.toInt())
                cornerRadius = 24f
                setStroke(1, 0x40FFFFFF.toInt())
            }
            background = editBg
            setPadding(24, 14, 24, 14)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            maxLines = 2
        }
        inputControlsLayout.addView(inputQueryEditText)

        // Send Button
        val sendBtn = TextView(context).apply {
            text = "Send"
            textSize = 13f
            setTextColor(0xFFFFFFFF.toInt())
            setTypeface(null, android.graphics.Typeface.BOLD)
            val sendBg = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF38BDF8.toInt())
                cornerRadius = 24f
            }
            background = sendBg
            setPadding(28, 14, 28, 14)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(12, 0, 0, 0)
            }
            layoutParams = lp
            setOnClickListener {
                val entered = inputQueryEditText?.text?.toString()?.trim() ?: ""
                if (entered.isNotBlank()) {
                    inputQueryEditText?.setText("")
                    stopSpeechRecognition()
                    processAssistantQuery(entered, lastScreenContext)
                }
            }
        }
        inputControlsLayout.addView(sendBtn)

        cardLayout.addView(inputControlsLayout)
        container.addView(cardLayout)
        rootView = container
        return container
    }

    override fun onHandleAssist(
        data: Bundle?,
        structure: android.app.assist.AssistStructure?,
        content: android.app.assist.AssistContent?
    ) {
        super.onHandleAssist(data, structure, content)

        val extractedScreenText = StringBuilder()
        structure?.let { struct ->
            for (i in 0 until struct.windowNodeCount) {
                val winNode = struct.getWindowNodeAt(i)
                traverseViewNode(winNode.rootViewNode, extractedScreenText)
            }
        }

        val screenContext = extractedScreenText.toString().take(1500).ifBlank { null }
        lastScreenContext = screenContext
        statusTextView?.text = if (screenContext != null) {
            "Foreground screen parsed • Listening for query..."
        } else {
            "Listening for voice query..."
        }

        // Start listening via Speech-to-Text automatically on trigger
        startSpeechRecognition()
    }

    private fun traverseViewNode(node: android.app.assist.AssistStructure.ViewNode?, out: StringBuilder) {
        if (node == null) return
        node.text?.let {
            if (it.isNotBlank()) {
                out.append(it).append("\n")
            }
        }
        for (i in 0 until node.childCount) {
            traverseViewNode(node.getChildAt(i), out)
        }
    }

    private fun startSpeechRecognition() {
        // Verify audio recording permission
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            statusTextView?.text = "⚠️ Microphone permission required for voice recognition."
            liveTranscribeTextView?.text = "Grant RECORD_AUDIO permission to speak."
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            statusTextView?.text = "SpeechRecognizer unavailable on device. You can type below."
            return
        }

        stopSpeechRecognition()

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext).apply {
                setRecognitionListener(createSpeechListener())
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }

            speechRecognizer?.startListening(intent)
            isListening = true
            updateMicUiState(true)
            statusTextView?.text = "🎙️ Listening... Speak your prompt"
            liveTranscribeTextView?.text = "Listening..."
        } catch (e: Exception) {
            Log.e(TAG, "Error starting voice recognition", e)
            isListening = false
            updateMicUiState(false)
            statusTextView?.text = "Voice recognizer error: ${e.message}"
        }
    }

    private fun stopSpeechRecognition() {
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping SpeechRecognizer", e)
        } finally {
            isListening = false
            updateMicUiState(false)
        }
    }

    private fun updateMicUiState(active: Boolean) {
        micButton?.let { btn ->
            val bg = btn.background as? android.graphics.drawable.GradientDrawable
            bg?.setColor(if (active) 0xFFEF4444.toInt() else 0xFF0284C7.toInt())
        }
        micWaveIndicator?.visibility = if (active) View.VISIBLE else View.GONE
    }

    private fun createSpeechListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            isListening = true
            updateMicUiState(true)
            statusTextView?.text = "🎙️ Ready • Listening..."
        }

        override fun onBeginningOfSpeech() {
            statusTextView?.text = "🎙️ Detecting speech..."
        }

        override fun onRmsChanged(rmsdB: Float) {
            // Visualize audio level amplitude
            val normalized = (rmsdB.coerceIn(-2f, 10f) + 2f) / 12f
            val newHeight = (6 + (normalized * 18)).toInt()
            micWaveIndicator?.layoutParams?.height = newHeight
            micWaveIndicator?.requestLayout()
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            isListening = false
            updateMicUiState(false)
            statusTextView?.text = "Processing speech transcription..."
        }

        override fun onError(error: Int) {
            isListening = false
            updateMicUiState(false)
            val errorMsg = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech matched. Tap mic to retry."
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout. Tap mic to retry."
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
                else -> "Voice input error ($error)."
            }
            statusTextView?.text = errorMsg
        }

        override fun onResults(results: Bundle?) {
            isListening = false
            updateMicUiState(false)
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val recognizedText = matches?.firstOrNull()?.trim() ?: ""

            if (recognizedText.isNotBlank()) {
                liveTranscribeTextView?.text = "\"$recognizedText\""
                processAssistantQuery(recognizedText, lastScreenContext)
            } else {
                statusTextView?.text = "No words recognized. Tap mic to speak again."
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partial = matches?.firstOrNull()?.trim() ?: ""
            if (partial.isNotBlank()) {
                liveTranscribeTextView?.text = "\"$partial...\""
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun processAssistantQuery(query: String, screenContext: String?) {
        sessionScope.launch {
            thoughtTextView?.visibility = View.VISIBLE
            thoughtTextView?.text = "🧠 Autonomous reasoning: Ingesting query \"$query\" into cognitive pipeline..."
            responseTextView?.visibility = View.GONE
            statusTextView?.text = "⚡ Evaluating through Gemini & Edge Silicon..."

            val fallbackModel = ModelSpec(
                id = "tinyllama-1.1b-gguf",
                name = "TinyLlama 1.1B Chat",
                parameterCount = "1.1 Billion",
                format = ModelFormat.GGUF,
                quantization = "Q4_K_M",
                fileSizeBytes = 669L * 1024L * 1024L,
                requiredRamBytes = 950L * 1024L * 1024L,
                contextLength = 2048,
                description = "High-speed mobile assistant model.",
                category = ModelCategory.CHAT_REASONING,
                downloadUrl = "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF",
                sha256Checksum = "9b64ea22fa706dfa2ce47e923e3c0f65349e5d4e112d7b5ea5f0612c77d94f21",
                isDownloaded = true,
                isActive = true
            )

            val result = withContext(Dispatchers.IO) {
                cognitiveEngine.thinkAndRespond(
                    query = query,
                    screenContext = screenContext,
                    activeModel = fallbackModel,
                    settings = HardwareAccelerationSettings()
                )
            }

            if (!result.thoughtChain.isNullOrBlank()) {
                thoughtTextView?.visibility = View.VISIBLE
                thoughtTextView?.text = "🧠 ${result.thoughtChain}"
            } else {
                thoughtTextView?.visibility = View.GONE
            }

            responseTextView?.visibility = View.VISIBLE
            responseTextView?.text = result.speechResponse
            statusTextView?.text = "⚡ Executed via ${result.executedToolSummary ?: "Gemini & Edge Neural Kernel"}"

            voiceSpeechManager.speak(result.speechResponse, "assistant_${System.currentTimeMillis()}")

            // Handle device control action if parsed
            result.actionTriggered?.let { action ->
                handleDeviceAction(action)
            }
        }
    }

    private fun handleDeviceAction(action: AssistantMobileAction) {
        val resultSummary = DeviceControlBridge.executeAction(context, action)
        statusTextView?.text = "⚡ $resultSummary"
    }
}
