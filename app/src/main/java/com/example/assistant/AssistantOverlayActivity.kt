package com.example.assistant

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.HardwareAccelerationSettings
import com.example.data.model.ModelCategory
import com.example.data.model.ModelFormat
import com.example.data.model.ModelSpec
import com.example.engine.LocalInferenceEngine
import com.example.engine.VoiceSpeechManager
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AssistantOverlayActivity : ComponentActivity() {

    private lateinit var voiceSpeechManager: VoiceSpeechManager
    private val inferenceEngine = LocalInferenceEngine()
    private lateinit var cognitiveEngine: AssistantCognitiveEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        voiceSpeechManager = VoiceSpeechManager(application)
        cognitiveEngine = AssistantCognitiveEngine(this, inferenceEngine)

        // Read query if sent via assist intent
        val assistQuery = intent?.getStringExtra("query") ?: ""

        setContent {
            MyApplicationTheme {
                AssistantOverlayContent(
                    initialQuery = assistQuery,
                    cognitiveEngine = cognitiveEngine,
                    voiceSpeechManager = voiceSpeechManager,
                    onDismiss = { finish() }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceSpeechManager.shutdown()
    }
}

@Composable
fun AssistantOverlayContent(
    initialQuery: String,
    cognitiveEngine: AssistantCognitiveEngine,
    voiceSpeechManager: VoiceSpeechManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var userQuery by remember { mutableStateOf(initialQuery) }
    var assistantThought by remember { mutableStateOf<String?>(null) }
    var assistantResponse by remember { mutableStateOf<String?>(null) }
    var statusText by remember { mutableStateOf("Autonomous Assistant listening...") }
    var isThinking by remember { mutableStateOf(false) }

    fun runQuery(prompt: String) {
        if (prompt.isBlank() || isThinking) return
        isThinking = true
        statusText = "Synthesizing on-device reasoning..."
        assistantThought = "Parsing intent: \"$prompt\" • Evaluating device sensors & local context..."
        assistantResponse = null

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

        scope.launch {
            val res = withContext(Dispatchers.IO) {
                cognitiveEngine.thinkAndRespond(
                    query = prompt,
                    screenContext = null,
                    activeModel = fallbackModel,
                    settings = HardwareAccelerationSettings()
                )
            }
            assistantThought = res.thoughtChain
            assistantResponse = res.speechResponse
            statusText = "Completed via ${res.executedToolSummary ?: "Edge Neural Kernel"}"
            isThinking = false

            voiceSpeechManager.speak(res.speechResponse, "overlay_${System.currentTimeMillis()}")

            // Execute autonomous mobile device action
            res.actionTriggered?.let { action ->
                val actionResult = DeviceControlBridge.executeAction(context, action)
                statusText = "$statusText • $actionResult"
            }
        }
    }

    LaunchedEffect(Unit) {
        if (initialQuery.isNotBlank()) {
            runQuery(initialQuery)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Card(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = false) {} // Prevent click-through dismissal
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Drag handle / Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF38BDF8))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "EdgeLLM Mobile Assistant",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Thought Chain Box
                assistantThought?.let { thought ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFA855F7).copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFA855F7).copy(alpha = 0.3f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color(0xFFA855F7),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Internal Autonomous Thought",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFA855F7)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = thought,
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Assistant conversational answer
                assistantResponse?.let { answer ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = answer,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Input Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = userQuery,
                        onValueChange = { userQuery = it },
                        placeholder = { Text("Ask your device assistant anything...", fontSize = 13.sp) },
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.weight(1f),
                        maxLines = 3
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            val q = userQuery
                            userQuery = ""
                            runQuery(q)
                        },
                        enabled = userQuery.isNotBlank() && !isThinking,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(
                                if (userQuery.isNotBlank() && !isThinking) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send",
                            tint = if (userQuery.isNotBlank()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
            }
        }
    }
}
