package com.example.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.Settings
import android.service.voice.VoiceInteractionSession
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.example.data.local.AppDatabase
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

class EdgeVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val inferenceEngine = LocalInferenceEngine()
    private val cognitiveEngine = AssistantCognitiveEngine(context, inferenceEngine)
    private val voiceSpeechManager = VoiceSpeechManager(context.applicationContext as android.app.Application)

    private var rootView: View? = null
    private var statusTextView: TextView? = null
    private var thoughtTextView: TextView? = null
    private var responseTextView: TextView? = null

    override fun onCreate() {
        super.onCreate()
    }

    override fun onDestroy() {
        super.onDestroy()
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
            setPadding(48, 48, 48, 48)
            val bgDrawable = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xF018181B.toInt()) // Dark sleek glass container
                cornerRadius = 36f
                setStroke(2, 0xFF38BDF8.toInt())
            }
            background = bgDrawable
            val lp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM
                setMargins(24, 24, 24, 60)
            }
            layoutParams = lp
        }

        // Header Title
        val titleView = TextView(context).apply {
            text = "✨ EdgeLLM Assistant • Device Active"
            textSize = 16f
            setTextColor(0xFF38BDF8.toInt())
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        cardLayout.addView(titleView)

        // Telemetry status
        statusTextView = TextView(context).apply {
            text = "Ingesting screen context and listening..."
            textSize = 12f
            setTextColor(0xFF94A3B8.toInt())
            setPadding(0, 8, 0, 12)
        }
        cardLayout.addView(statusTextView)

        // Autonomous Thought Box
        thoughtTextView = TextView(context).apply {
            text = "Thinking..."
            textSize = 12f
            setTextColor(0xFFA78BFA.toInt())
            setPadding(24, 16, 24, 16)
            val thoughtBg = android.graphics.drawable.GradientDrawable().apply {
                setColor(0x30A78BFA.toInt())
                cornerRadius = 18f
            }
            background = thoughtBg
            visibility = View.GONE
        }
        cardLayout.addView(thoughtTextView)

        // Assistant Output Text
        responseTextView = TextView(context).apply {
            text = ""
            textSize = 15f
            setTextColor(0xFFF8FAFC.toInt())
            setPadding(0, 16, 0, 16)
        }
        cardLayout.addView(responseTextView)

        // Close button
        val dismissBtn = TextView(context).apply {
            text = "✕ Dismiss"
            textSize = 13f
            setTextColor(0xFF64748B.toInt())
            gravity = Gravity.END
            setPadding(16, 16, 16, 16)
            setOnClickListener { hide() }
        }
        cardLayout.addView(dismissBtn)

        container.addView(cardLayout)
        rootView = container
        return container
    }

    override fun onHandleAssist(data: Bundle?, structure: android.app.assist.AssistStructure?, content: android.app.assist.AssistContent?) {
        super.onHandleAssist(data, structure, content)

        val extractedScreenText = StringBuilder()
        structure?.let { struct ->
            for (i in 0 until struct.windowNodeCount) {
                val winNode = struct.getWindowNodeAt(i)
                traverseViewNode(winNode.rootViewNode, extractedScreenText)
            }
        }

        val screenContext = extractedScreenText.toString().take(1500).ifBlank { null }
        statusTextView?.text = if (screenContext != null) "Foreground context parsed • Ready" else "Ready to assist"

        // Default query if triggered via system home gesture
        val prompt = "What can I do for you right now on your device?"
        processAssistantQuery(prompt, screenContext)
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

    private fun processAssistantQuery(query: String, screenContext: String?) {
        sessionScope.launch {
            thoughtTextView?.visibility = View.VISIBLE
            thoughtTextView?.text = "🧠 Autonomous reasoning: Ingesting device state and contextual parameters..."

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

            responseTextView?.text = result.speechResponse
            statusTextView?.text = "⚡ Executed via ${result.executedToolSummary ?: "Zero-Cloud Edge"}"

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
