package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin

enum class LiveVoiceState {
    LISTENING,
    THINKING,
    SPEAKING
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EdgeLiveVoiceSheet(
    activeModelName: String,
    onDismiss: () -> Unit,
    onSendPrompt: (String) -> Unit,
    onStopSpeech: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var voiceState by remember { mutableStateOf(LiveVoiceState.LISTENING) }
    var transcriptPreview by remember { mutableStateOf("Listening for your voice...") }
    var assistantResponsePreview by remember { mutableStateOf("") }
    var isMuted by remember { mutableStateOf(false) }

    // Waveform Animation transitions
    val infiniteTransition = rememberInfiniteTransition(label = "waveform_anim")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase_anim"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_anim"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0F172A),
        modifier = modifier.testTag("edge_live_voice_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF38BDF8))
                    )
                    Text(
                        text = "Edge Live Duplex",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF38BDF8).copy(alpha = 0.25f)
                    ) {
                        Text(
                            text = activeModelName,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF38BDF8),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
                }
            }

            // Pulsing Waveform Visualizer (Gemini Live inspired)
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF0284C7).copy(alpha = 0.25f * pulseScale),
                                Color(0xFF0F172A).copy(alpha = 0.1f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    val centerY = height / 2f

                    val waveColors = listOf(
                        Color(0xFF38BDF8) to 1.0f,
                        Color(0xFFA855F7) to 0.7f,
                        Color(0xFF10B981) to 0.4f
                    )

                    waveColors.forEachIndexed { index, (color, alpha) ->
                        val path = Path()
                        val amplitude = when (voiceState) {
                            LiveVoiceState.LISTENING -> (30f + index * 10f) * pulseScale
                            LiveVoiceState.THINKING -> 15f
                            LiveVoiceState.SPEAKING -> (45f + index * 12f) * pulseScale
                        }
                        val frequency = 0.025f + (index * 0.005f)

                        path.moveTo(0f, centerY)
                        var x = 0f
                        while (x <= width) {
                            val y = centerY + amplitude * sin((x * frequency) + phase + (index * 1.2f))
                            path.lineTo(x, y)
                            x += 6f
                        }

                        drawPath(
                            path = path,
                            color = color.copy(alpha = alpha),
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }

                // Central Mic Icon
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(
                            when (voiceState) {
                                LiveVoiceState.LISTENING -> Color(0xFF0284C7)
                                LiveVoiceState.THINKING -> Color(0xFF9333EA)
                                LiveVoiceState.SPEAKING -> Color(0xFF059669)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (voiceState) {
                            LiveVoiceState.LISTENING -> Icons.Default.Mic
                            LiveVoiceState.THINKING -> Icons.Default.VolumeUp
                            LiveVoiceState.SPEAKING -> Icons.Default.VolumeUp
                        },
                        contentDescription = "Voice State",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            // Status label
            Text(
                text = when (voiceState) {
                    LiveVoiceState.LISTENING -> "Listening hands-free • Speak naturally"
                    LiveVoiceState.THINKING -> "Synthesizing neural tokens on-device..."
                    LiveVoiceState.SPEAKING -> "Speaking • Tap anywhere to interrupt"
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = when (voiceState) {
                    LiveVoiceState.LISTENING -> Color(0xFF38BDF8)
                    LiveVoiceState.THINKING -> Color(0xFFA855F7)
                    LiveVoiceState.SPEAKING -> Color(0xFF34D399)
                }
            )

            // Transcript bubble
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "CONVERSATIONAL DUPLEX STREAM",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = transcriptPreview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    if (assistantResponsePreview.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = assistantResponsePreview,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF38BDF8)
                        )
                    }
                }
            }

            // Quick Voice Action Triggers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "Turn on Flashlight" to "Turn on the flashlight",
                    "Battery Status" to "What is my battery level?",
                    "Check Schedule" to "Remind me to review sprint goals at 3 PM"
                ).forEach { (label, command) ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF334155),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                transcriptPreview = "You: $command"
                                voiceState = LiveVoiceState.THINKING
                                onSendPrompt(command)
                                voiceState = LiveVoiceState.SPEAKING
                                assistantResponsePreview = "Assistant executing on-device action..."
                            }
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp)
                        )
                    }
                }
            }

            // Control Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { isMuted = !isMuted },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF334155))
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Mute",
                        tint = if (isMuted) Color(0xFFEF4444) else Color.White
                    )
                }

                IconButton(
                    onClick = {
                        onStopSpeech()
                        voiceState = LiveVoiceState.LISTENING
                        assistantResponsePreview = ""
                    },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFDC2626))
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Interrupt / Stop",
                        tint = Color.White
                    )
                }
            }
        }
    }
}
