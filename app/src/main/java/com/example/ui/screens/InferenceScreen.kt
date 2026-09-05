package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BuiltInPromptTemplates
import com.example.data.model.InferenceMessage
import com.example.data.model.KnowledgeDocument
import com.example.data.model.MessageSender
import com.example.data.model.PromptTemplate
import com.example.ui.MainViewModel
import com.example.ui.components.ExportChatDialog
import com.example.ui.components.KnowledgeDocumentSheet
import com.example.ui.components.ModelImportSheet
import com.example.ui.components.ModelImportStatusBar
import com.example.ui.components.ModelSelectorSheet
import com.example.ui.components.PersonaSelectorSheet
import com.example.ui.components.PromptToolsSheet

@Composable
fun InferenceScreen(
    viewModel: MainViewModel,
    onNavigateToExport: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val messages by viewModel.chatMessages.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val streamingChunk by viewModel.streamingChunk.collectAsState()
    val models by viewModel.models.collectAsState()
    val params by viewModel.generationParameters.collectAsState()
    val accelerationSettings by viewModel.accelerationSettings.collectAsState()
    val importProgress by viewModel.importProgress.collectAsState()

    // Community-requested features: Personas, Local RAG, Voice TTS
    val activePersona by viewModel.activePersona.collectAsState()
    val activeKnowledgeDoc by viewModel.activeKnowledgeDoc.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()
    val currentlySpeakingId by viewModel.currentlySpeakingId.collectAsState()
    val autoVoiceReadout by viewModel.autoVoiceReadout.collectAsState()
    val speechRate by viewModel.speechRate.collectAsState()
    val telemetryState by viewModel.telemetryState.collectAsState()

    val activeModel = remember(models) {
        models.firstOrNull { it.isActive && it.isDownloaded } ?: models.firstOrNull { it.isDownloaded }
    }

    var inputText by remember { mutableStateOf("") }
    var showParamsDialog by remember { mutableStateOf(false) }
    var showModelPickerSheet by remember { mutableStateOf(false) }
    var showImportSheet by remember { mutableStateOf(false) }
    var showPersonaSheet by remember { mutableStateOf(false) }
    var showKnowledgeSheet by remember { mutableStateOf(false) }
    var showPromptToolsSheet by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showVoiceRateDialog by remember { mutableStateOf(false) }

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.importModelsFromFolder(uri)
        }
    }

    val filesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importModelFiles(uris)
        }
    }

    // Voice Speech-to-Text Recognizer
    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenList = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = spokenList?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                inputText = if (inputText.isBlank()) spokenText else "$inputText $spokenText"
            }
        }
    }

    val listState = rememberLazyListState()

    // Auto-scroll to bottom on new messages
    LaunchedEffect(messages.size, streamingChunk?.tokenCount) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("inference_screen")
    ) {
        // Top Active Model & Telemetry Bar
        Surface(
            tonalElevation = 2.dp,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .clickable { showModelPickerSheet = true }
                            .weight(1f)
                            .testTag("active_model_header_selector")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = activeModel?.name ?: "No Model Active",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Switch Model",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = "${accelerationSettings.computeBackend.shortName} • ${accelerationSettings.threadCount}T • ${activeModel?.quantization ?: "Q4_K_M"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.5.sp
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Quick Voice Auto-Readout toggle
                        IconButton(
                            onClick = { viewModel.toggleAutoVoiceReadout() },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = if (autoVoiceReadout) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                contentDescription = "Auto Voice Readout",
                                tint = if (autoVoiceReadout) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Export transcript dialog
                        IconButton(
                            onClick = { showExportDialog = true },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Export Chat",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Hyperparameters
                        IconButton(
                            onClick = { showParamsDialog = true },
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("open_params_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Parameters",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Clear chat history
                        IconButton(
                            onClick = { viewModel.clearChat() },
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("clear_chat_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear Chat History",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Persona & Document Grounding Quick Switcher Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Persona Chip
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .clickable { showPersonaSheet = true }
                            .testTag("persona_chip_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(activePersona.emoji, fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = activePersona.name,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (activePersona.supportsReasoningTrace) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.tertiary)
                                )
                            }
                        }
                    }

                    // Local Document Ingestion (RAG) Grounding Chip
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (activeKnowledgeDoc != null) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (activeKnowledgeDoc != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .clickable { showKnowledgeSheet = true }
                            .testTag("rag_grounding_chip_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = if (activeKnowledgeDoc != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = activeKnowledgeDoc?.let { "Grounded: ${it.title.take(14)}..." } ?: "+ Attach Doc (RAG)",
                                fontSize = 11.sp,
                                fontWeight = if (activeKnowledgeDoc != null) FontWeight.Bold else FontWeight.Normal,
                                color = if (activeKnowledgeDoc != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Prompt Library Quick Button
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.clickable { showPromptToolsSheet = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Tools",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        // Live Model Import Status Bar
        ModelImportStatusBar(
            progress = importProgress,
            onCancel = { viewModel.cancelImport() },
            onDismiss = { viewModel.dismissImportProgress() },
            onLoadModel = { viewModel.setActiveModel(it) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        // Live Inference Speedometer Bar (during generation)
        AnimatedVisibility(visible = isGenerating && streamingChunk != null) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${streamingChunk?.tokensPerSecond ?: 0f} tok/s",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "TTFT: ${streamingChunk?.timeToFirstTokenMs ?: 0}ms • Tokens: ${streamingChunk?.tokenCount ?: 0}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = { viewModel.stopGeneration() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop",
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Stop", fontSize = 10.sp)
                    }
                }
            }
        }

        // Messages List
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (messages.isEmpty() && streamingChunk == null) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Zero-Cloud Edge Intelligence",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "All computation executes locally on device silicon.\nNo telemetry, API keys, or cloud servers required.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Active persona indicator card
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .clickable { showPersonaSheet = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(activePersona.emoji, fontSize = 22.sp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Active Persona: ${activePersona.name}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = activePersona.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            items(messages, key = { it.id }) { msg ->
                val isMessageSpeaking = isSpeaking && currentlySpeakingId == msg.id
                val isLastAssistant = !isGenerating && messages.lastOrNull { it.sender == MessageSender.ASSISTANT }?.id == msg.id

                ChatMessageBubble(
                    message = msg,
                    isSpeaking = isMessageSpeaking,
                    onToggleVoiceSpeak = {
                        if (isMessageSpeaking) {
                            viewModel.stopSpeaking()
                        } else {
                            viewModel.speakMessage(msg.text, msg.id)
                        }
                    },
                    onCopy = { clipboardManager.setText(AnnotatedString(msg.text)) },
                    onExportEncrypted = { onNavigateToExport(msg.text) },
                    onDelete = { viewModel.deleteChatMessage(msg.id) },
                    onRegenerate = if (isLastAssistant) {
                        {
                            val lastUserMsg = messages.lastOrNull { it.sender == MessageSender.USER }?.text ?: ""
                            if (lastUserMsg.isNotBlank()) {
                                viewModel.regenerateResponse(msg, lastUserMsg)
                            }
                        }
                    } else null,
                    onEditPrompt = if (msg.sender == MessageSender.USER) {
                        { inputText = msg.text }
                    } else null
                )
            }

            // Streaming live response bubble
            streamingChunk?.let { chunk ->
                item {
                    StreamingResponseBubble(chunk = chunk)
                }
            }
        }

        // Quick Prompt Templates Row
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val quickTemplates = BuiltInPromptTemplates.ALL.take(5)
            items(quickTemplates, key = { it.id }) { template ->
                SuggestionChip(
                    onClick = {
                        inputText = template.prefix
                    },
                    label = {
                        Text("${template.iconEmoji} ${template.title}", fontSize = 11.5.sp)
                    }
                )
            }
            item {
                SuggestionChip(
                    onClick = { showPromptToolsSheet = true },
                    label = { Text("⚡ All Tools...", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.primary) }
                )
            }
        }

        // Bottom Prompt Input Bar
        Surface(
            tonalElevation = 4.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Attach context / RAG icon button
                IconButton(
                    onClick = { showKnowledgeSheet = true },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = "Attach Document",
                        tint = if (activeKnowledgeDoc != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Voice Speech-To-Text Dictation button
                IconButton(
                    onClick = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your prompt...")
                        }
                        try {
                            speechRecognizerLauncher.launch(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Voice input not supported on this device", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice Dictation",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            text = if (activeKnowledgeDoc != null) "Ask about grounded doc..." else "Ask local model offline...",
                            fontSize = 13.sp
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("prompt_input_field"),
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4,
                    enabled = !isGenerating
                )

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = {
                        if (inputText.isNotBlank() && !isGenerating) {
                            val promptToSend = inputText
                            inputText = ""
                            viewModel.sendPrompt(promptToSend)
                        }
                    },
                    enabled = inputText.isNotBlank() && !isGenerating,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (inputText.isNotBlank() && !isGenerating) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                        .testTag("send_prompt_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send Prompt",
                        tint = if (inputText.isNotBlank() && !isGenerating) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    // AI Persona Selection Sheet
    if (showPersonaSheet) {
        PersonaSelectorSheet(
            personas = viewModel.availablePersonas,
            activePersona = activePersona,
            onSelectPersona = { viewModel.selectPersona(it) },
            onSelectSamplePrompt = { samplePrompt ->
                inputText = samplePrompt
            },
            onDismiss = { showPersonaSheet = false }
        )
    }

    // Document Ingestion (RAG) Grounding Sheet
    if (showKnowledgeSheet) {
        KnowledgeDocumentSheet(
            activeDocument = activeKnowledgeDoc,
            sampleDocuments = viewModel.sampleKnowledgeDocs,
            onAttachDocument = { viewModel.attachKnowledgeDoc(it) },
            onDetachDocument = { viewModel.detachKnowledgeDoc() },
            onIngestCustomText = { title, content ->
                viewModel.ingestCustomKnowledge(title, content)
            },
            onDismiss = { showKnowledgeSheet = false }
        )
    }

    // Prompt Library & Tools Sheet
    if (showPromptToolsSheet) {
        PromptToolsSheet(
            templates = BuiltInPromptTemplates.ALL,
            onSelectTemplate = { template ->
                inputText = template.prefix
            },
            onDismiss = { showPromptToolsSheet = false }
        )
    }

    // Export Conversation Dialog
    if (showExportDialog) {
        ExportChatDialog(
            messages = messages,
            onExportToEncryptedVault = {
                val fullTranscript = messages.joinToString("\n\n") { "${it.sender}: ${it.text}" }
                onNavigateToExport(fullTranscript)
            },
            onDismiss = { showExportDialog = false }
        )
    }

    // Hyperparameters Dialog
    if (showParamsDialog) {
        var tempValue by remember { mutableFloatStateOf(params.temperature) }
        var topPValue by remember { mutableFloatStateOf(params.topP) }

        AlertDialog(
            onDismissRequest = { showParamsDialog = false },
            title = { Text("Inference Hyperparameters") },
            text = {
                Column {
                    Text("Temperature: ${"%.2f".format(tempValue)}", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = tempValue,
                        onValueChange = { tempValue = it },
                        valueRange = 0.1f..1.5f
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Top-P Nucleus: ${"%.2f".format(topPValue)}", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = topPValue,
                        onValueChange = { topPValue = it },
                        valueRange = 0.1f..1.0f
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Max Generation Tokens: ${params.maxNewTokens}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Text-to-Speech Speed: ${"%.2f".format(speechRate)}x", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = speechRate,
                        onValueChange = { viewModel.setSpeechRate(it) },
                        valueRange = 0.5f..2.0f
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateGenerationParameters(
                            params.copy(temperature = tempValue, topP = topPValue)
                        )
                        showParamsDialog = false
                    }
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showParamsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Model Selector Sheet
    if (showModelPickerSheet) {
        ModelSelectorSheet(
            models = models,
            activeModel = activeModel,
            onSelectModel = { modelId: String ->
                viewModel.setActiveModel(modelId)
            },
            onDownloadModel = { modelId: String ->
                viewModel.downloadModel(modelId)
            },
            onPauseDownload = { modelId: String ->
                viewModel.pauseDownload(modelId)
            },
            onCancelDownload = { modelId: String ->
                viewModel.cancelDownload(modelId)
            },
            onDeleteModel = { modelId: String ->
                viewModel.deleteModel(modelId)
            },
            onImportClick = {
                showModelPickerSheet = false
                showImportSheet = true
            },
            onDismiss = { showModelPickerSheet = false }
        )
    }

    // Model Import Sheet
    if (showImportSheet) {
        ModelImportSheet(
            onSelectFolder = {
                folderLauncher.launch(null)
            },
            onSelectFiles = {
                filesLauncher.launch(arrayOf("*/*"))
            },
            onImportDemoPack = {
                viewModel.importDemoModelFolder()
            },
            onDismiss = { showImportSheet = false }
        )
    }
}

@Composable
fun ChatMessageBubble(
    message: InferenceMessage,
    isSpeaking: Boolean = false,
    onToggleVoiceSpeak: () -> Unit = {},
    onCopy: () -> Unit,
    onExportEncrypted: () -> Unit,
    onDelete: () -> Unit = {},
    onRegenerate: (() -> Unit)? = null,
    onEditPrompt: (() -> Unit)? = null
) {
    val isUser = message.sender == MessageSender.USER

    // Parse <think>...</think> reasoning blocks for CoT models
    var isThinkingExpanded by remember { mutableStateOf(false) }
    val (thoughtProcess, finalAnswer) = remember(message.text) {
        val thinkRegex = Regex("<think>([\\s\\S]*?)</think>")
        val match = thinkRegex.find(message.text)
        if (match != null) {
            val thought = match.groupValues[1].trim()
            val answer = message.text.replace(thinkRegex, "").trim()
            Pair(thought, answer)
        } else {
            Pair(null, message.text)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Card(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ),
            modifier = Modifier.fillMaxWidth(if (isUser) 0.85f else 0.96f)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {

                // Interactive Chain-of-Thought collapsible box
                if (!isUser && !thoughtProcess.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isThinkingExpanded = !isThinkingExpanded }
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Psychology,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Thought Process (${thoughtProcess.split(" ").size} steps)",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                                Icon(
                                    imageVector = if (isThinkingExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            if (isThinkingExpanded) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = thoughtProcess,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.5.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Main Message Content
                Text(
                    text = finalAnswer,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )

                // Bottom actions row
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isUser && message.tokensGenerated > 0) {
                        Text(
                            text = "${message.tokensGenerated} tok • ${message.tokensPerSecond} t/s • ${message.timeToFirstTokenMs}ms",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = FontFamily.Monospace
                        )
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Voice Read Aloud Button (for assistant responses)
                        if (!isUser) {
                            IconButton(
                                onClick = onToggleVoiceSpeak,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = if (isSpeaking) Icons.Default.Stop else Icons.Default.VolumeUp,
                                    contentDescription = "Read Aloud",
                                    tint = if (isSpeaking) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }

                        // Regenerate response button (for assistant messages)
                        if (onRegenerate != null) {
                            IconButton(
                                onClick = onRegenerate,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Regenerate Response",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }

                        // Edit prompt button (for user messages)
                        if (onEditPrompt != null) {
                            IconButton(
                                onClick = onEditPrompt,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Prompt",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }

                        // Copy Button
                        IconButton(
                            onClick = onCopy,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                tint = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        // Encrypt to Vault Button
                        if (!isUser) {
                            IconButton(
                                onClick = onExportEncrypted,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Encrypt",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        // Delete message button
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = if (isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StreamingResponseBubble(chunk: com.example.engine.StreamTokenChunk) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Card(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = 4.dp,
                bottomEnd = 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth(0.96f)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = chunk.accumulatedText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Generating... • ${chunk.tokensPerSecond} tok/s • ${chunk.tokenCount} tokens",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )

                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
    }
}
