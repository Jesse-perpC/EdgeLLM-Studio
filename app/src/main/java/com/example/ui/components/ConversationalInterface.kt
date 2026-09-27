package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.InferenceMessage
import com.example.data.model.MessageSender
import com.example.data.model.ModelSpec
import com.example.engine.StreamTokenChunk
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Material 3 Conversational Interface for Jetpack Compose.
 * Displays user prompts and AI responses with rich markdown formatting,
 * Chain-of-Thought expandable reasoning, hardware inference telemetry,
 * voice playback controls, multimodal visual previews, and interactive quick-start suggestions.
 */
@Composable
fun ConversationalChatView(
    messages: List<InferenceMessage>,
    streamingChunk: StreamTokenChunk? = null,
    isGenerating: Boolean = false,
    activeModel: ModelSpec? = null,
    inputText: String,
    onInputTextChanged: (String) -> Unit,
    onSendMessage: () -> Unit,
    onStopGeneration: () -> Unit,
    attachedVisualLabel: String? = null,
    onAttachVisualClick: () -> Unit = {},
    onDetachVisualClick: () -> Unit = {},
    onVoiceInputClick: () -> Unit = {},
    isSpeaking: Boolean = false,
    currentlySpeakingId: String? = null,
    onToggleVoiceSpeak: (InferenceMessage) -> Unit = {},
    onCopyMessage: (String) -> Unit = {},
    onShareMessage: (String) -> Unit = {},
    onDeleteMessage: (String) -> Unit = {},
    onRegenerateMessage: ((InferenceMessage) -> Unit)? = null,
    onEditPrompt: ((String) -> Unit)? = null,
    onRateFeedback: ((String, Int) -> Unit)? = null,
    quickSuggestions: List<String> = listOf(
        "💡 Explain Quantum Computing Simply",
        "💻 Write a Kotlin Flow with StateFlow",
        "📄 Summarize an Invoice or Contract",
        "🖼️ Analyze Visuals with Vision ViT"
    ),
    onSelectSuggestion: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Automatically scroll down when new messages or tokens arrive
    LaunchedEffect(messages.size, streamingChunk?.tokenCount) {
        if (messages.isNotEmpty() || streamingChunk != null) {
            val targetIndex = (messages.size + (if (streamingChunk != null) 1 else 0) - 1).coerceAtLeast(0)
            listState.animateScrollToItem(targetIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Message list or Empty State Onboarding Hero
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (messages.isEmpty() && streamingChunk == null) {
                ConversationalEmptyState(
                    activeModel = activeModel,
                    quickSuggestions = quickSuggestions,
                    onSelectSuggestion = onSelectSuggestion,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("conversational_messages_list")
                ) {
                    items(messages, key = { it.id }) { message ->
                        val isMessageSpeaking = isSpeaking && currentlySpeakingId == message.id
                        val isLastAssistant = !isGenerating && messages.lastOrNull { it.sender == MessageSender.ASSISTANT }?.id == message.id

                        M3ConversationalBubble(
                            message = message,
                            isSpeaking = isMessageSpeaking,
                            onToggleVoiceSpeak = { onToggleVoiceSpeak(message) },
                            onCopy = { onCopyMessage(message.text) },
                            onShare = { onShareMessage(message.text) },
                            onDelete = { onDeleteMessage(message.id) },
                            onRegenerate = if (isLastAssistant && onRegenerateMessage != null) {
                                { onRegenerateMessage(message) }
                            } else null,
                            onEditPrompt = if (message.sender == MessageSender.USER && onEditPrompt != null) {
                                { onEditPrompt(message.text) }
                            } else null,
                            onRateFeedback = if (onRateFeedback != null) {
                                { rating -> onRateFeedback(message.id, rating) }
                            } else null
                        )
                    }

                    // Live Streaming AI Response Bubble
                    if (streamingChunk != null) {
                        item(key = "streaming_live_bubble") {
                            M3StreamingBubble(
                                chunk = streamingChunk,
                                activeModelName = activeModel?.name ?: "Edge Neural Kernel"
                            )
                        }
                    }
                }
            }
        }

        // Attached Visual Preview Banner
        AnimatedVisibility(
            visible = attachedVisualLabel != null,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it }
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = attachedVisualLabel ?: "Attached Visual",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "Multimodal Vision ViT ready for perception analysis",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
                            )
                        }
                    }
                    IconButton(
                        onClick = onDetachVisualClick,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove attached image",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        // Quick Suggestions Horizontal Row (when chat is active)
        if (messages.isNotEmpty() && !isGenerating) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(quickSuggestions) { suggestion ->
                    SuggestionChip(
                        onClick = { onSelectSuggestion(suggestion) },
                        label = { Text(suggestion, fontSize = 11.sp) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant)
                    )
                }
            }
        }

        // Material 3 Expressive Prompt Input Bar
        M3ConversationalInputBar(
            inputText = inputText,
            onInputTextChanged = onInputTextChanged,
            onSendMessage = onSendMessage,
            onStopGeneration = onStopGeneration,
            isGenerating = isGenerating,
            onAttachVisualClick = onAttachVisualClick,
            onVoiceInputClick = onVoiceInputClick,
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
        )
    }
}

/**
 * Material 3 Message Bubble:
 * - Distinct User Input container (primary/onPrimary, asymmetric speech tail)
 * - Distinct AI Response container (surfaceVariant, avatar icon, reasoning disclosure, telemetry)
 */
@Composable
fun M3ConversationalBubble(
    message: InferenceMessage,
    isSpeaking: Boolean,
    onToggleVoiceSpeak: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onRegenerate: (() -> Unit)?,
    onEditPrompt: (() -> Unit)?,
    onRateFeedback: ((Int) -> Unit)?
) {
    val isUser = message.sender == MessageSender.USER
    val sdf = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val timeFormatted = remember(message.timestamp) { sdf.format(Date(message.timestamp)) }

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
        modifier = Modifier
            .fillMaxWidth()
            .testTag(if (isUser) "user_message_bubble" else "assistant_message_bubble"),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        // Sender Badge & Time
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            if (!isUser) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(11.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "EdgeLLM Assistant",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
            }

            Text(
                text = timeFormatted,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                fontSize = 10.sp
            )

            if (isUser) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "You",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 11.sp
                )
            }
        }

        // Main Bubble Card
        Card(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            ),
            border = if (!isUser) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
            modifier = Modifier.widthIn(max = 340.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Attached Multimodal Visual Badge
                if (message.imageLabel != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        border = BorderStroke(
                            1.dp,
                            if (isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = message.imageLabel ?: "Visual Attached",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.5.sp
                            )
                        }
                    }
                }

                // Recalled Semantic Memories Context Badge
                if (message.recalledMemories.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = null,
                                tint = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Recalled: ${message.recalledMemories.size} memories",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onTertiaryContainer,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Interactive Chain-of-Thought collapsible box
                if (!isUser && !thoughtProcess.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Reasoning Process (${thoughtProcess.split(" ").size} steps)",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.tertiary,
                                        fontSize = 11.sp
                                    )
                                }
                                Icon(
                                    imageVector = if (isThinkingExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            if (isThinkingExpanded) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = thoughtProcess,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Message Text
                Text(
                    text = finalAnswer,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Action Bar & Telemetry Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isUser && message.tokensGenerated > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${message.tokensGenerated} tok • ${message.tokensPerSecond} t/s",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontFamily = FontFamily.Monospace
                            )
                            if (message.isTurboBoost) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF10B981).copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "⚡ Turbo",
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF10B981),
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    // Action buttons
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!isUser) {
                            // Voice TTS read-aloud button
                            IconButton(
                                onClick = onToggleVoiceSpeak,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = if (isSpeaking) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                    contentDescription = "Read Aloud",
                                    tint = if (isSpeaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.size(15.dp)
                                )
                            }

                            // Regenerate button
                            if (onRegenerate != null) {
                                IconButton(
                                    onClick = onRegenerate,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Regenerate",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }

                            // Thumbs up / down feedback
                            if (onRateFeedback != null) {
                                IconButton(
                                    onClick = { onRateFeedback(if (message.userRating == 1) 0 else 1) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ThumbUp,
                                        contentDescription = "Helpful",
                                        tint = if (message.userRating == 1) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onRateFeedback(if (message.userRating == -1) 0 else -1) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ThumbDown,
                                        contentDescription = "Unhelpful",
                                        tint = if (message.userRating == -1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }

                        // Edit Prompt for user messages
                        if (isUser && onEditPrompt != null) {
                            IconButton(
                                onClick = onEditPrompt,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Prompt",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(14.dp)
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
                                contentDescription = "Copy Text",
                                tint = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        // Share Button
                        IconButton(
                            onClick = onShare,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share Message",
                                tint = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        // Delete Button
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Message",
                                tint = if (isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Material 3 Live Streaming Response Bubble:
 * Shows animated token synthesis with live speed meter.
 */
@Composable
fun M3StreamingBubble(
    chunk: StreamTokenChunk,
    activeModelName: String
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("streaming_bubble"),
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF10B981).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981))
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "$activeModelName • Generating",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF10B981),
                fontSize = 11.sp
            )
        }

        Card(
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f)),
            modifier = Modifier.widthIn(max = 340.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = chunk.accumulatedText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${chunk.tokensPerSecond} tok/s • ${chunk.tokenCount} tokens",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF10B981),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981).copy(alpha = dotAlpha))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Synthesizing",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF10B981)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Material 3 Expressive Conversational Input Bar:
 * - Pill-shaped container
 * - Photo/Gallery visual upload button
 * - Voice dictation button
 * - Morphing Send / Stop Floating Action Button
 */
@Composable
fun M3ConversationalInputBar(
    inputText: String,
    onInputTextChanged: (String) -> Unit,
    onSendMessage: () -> Unit,
    onStopGeneration: () -> Unit,
    isGenerating: Boolean,
    onAttachVisualClick: () -> Unit,
    onVoiceInputClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        tonalElevation = 6.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Attach Visual / Image Button
            IconButton(
                onClick = onAttachVisualClick,
                modifier = Modifier
                    .size(42.dp)
                    .testTag("attach_visual_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.AddPhotoAlternate,
                    contentDescription = "Attach Visual",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Voice Dictation Button
            IconButton(
                onClick = onVoiceInputClick,
                modifier = Modifier
                    .size(42.dp)
                    .testTag("voice_dictation_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Voice Dictation",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Main Text Field
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputTextChanged,
                placeholder = {
                    Text(
                        text = "Message your on-device AI...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("conversational_text_input"),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                ),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (inputText.isNotBlank() && !isGenerating) {
                        onSendMessage()
                    }
                })
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Send / Stop Action Button
            val sendButtonBg by animateColorAsState(
                targetValue = if (isGenerating) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                label = "btnBg"
            )

            FilledIconButton(
                onClick = {
                    if (isGenerating) {
                        onStopGeneration()
                    } else if (inputText.isNotBlank()) {
                        onSendMessage()
                    }
                },
                modifier = Modifier
                    .size(46.dp)
                    .testTag(if (isGenerating) "stop_generation_btn" else "send_message_btn"),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = sendButtonBg)
            ) {
                if (isGenerating) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop Generation",
                        tint = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send Message",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}

/**
 * Material 3 Empty State Hero Card:
 * Welcomes users and offers quick conversation starter chips.
 */
@Composable
fun ConversationalEmptyState(
    activeModel: ModelSpec?,
    quickSuggestions: List<String>,
    onSelectSuggestion: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(34.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Edge Neural Studio",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(4.dp))

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF10B981).copy(alpha = 0.15f)
        ) {
            Text(
                text = "100% PRIVATE • AIR-GAPPED ON-DEVICE",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF10B981),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Active: ${activeModel?.name ?: "TinyLlama 1.1B"} (${activeModel?.quantization ?: "Q4_K_M"})\nAsk questions, synthesize code, or attach visuals for perception analysis.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "SUGGESTED PROMPTS",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            quickSuggestions.forEach { suggestion ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectSuggestion(suggestion) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = suggestion,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}
