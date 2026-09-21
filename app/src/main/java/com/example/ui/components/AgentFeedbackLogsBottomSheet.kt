package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AgentFeedbackLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentFeedbackLogsBottomSheet(
    feedbackLogs: List<AgentFeedbackLog>,
    onDeleteLog: (String) -> Unit,
    onClearAllLogs: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault()) }

    val avgTrustScore = if (feedbackLogs.isNotEmpty()) {
        val total = feedbackLogs.fold(0.0) { acc, log -> acc + log.overallTrustScore }
        (total / feedbackLogs.size).toFloat()
    } else 0f

    val refinedCount = feedbackLogs.count { it.wasRefined }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("agent_feedback_logs_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FactCheck,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Multi-Agent Audit Logs",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${feedbackLogs.size} logs • Avg Trust: ${(avgTrustScore * 100).toInt()}% • $refinedCount Refined",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (feedbackLogs.isNotEmpty()) {
                        IconButton(
                            onClick = onClearAllLogs,
                            modifier = Modifier.testTag("clear_all_audit_logs_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear All Logs",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Summary Metrics Bar
            if (feedbackLogs.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${(avgTrustScore * 100).toInt()}%",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (avgTrustScore >= 0.8f) Color(0xFF10B981) else Color(0xFFF59E0B)
                            )
                            Text(
                                text = "Avg Trust",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$refinedCount",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                            Text(
                                text = "Refinements",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val upvotes = feedbackLogs.count { it.userRating > 0 }
                            val downvotes = feedbackLogs.count { it.userRating < 0 }
                            Text(
                                text = "+$upvotes / -$downvotes",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "User Votes",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // Logs List
            if (feedbackLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No Agent Audit Logs Yet",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Responses generated in chat are automatically critiqued and verified by the dual-agent feedback loop.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(feedbackLogs, key = { it.id }) { log ->
                        FeedbackLogItemCard(
                            log = log,
                            formattedTime = dateFormat.format(Date(log.timestamp)),
                            onDelete = { onDeleteLog(log.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FeedbackLogItemCard(
    log: AgentFeedbackLog,
    formattedTime: String,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        border = BorderStroke(
            0.8.dp,
            if (log.wasRefined) Color(0xFF10B981).copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Top row: timestamp, refinement pill, rating pill, delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )

                    if (log.wasRefined) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "✨ Refined",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }

                    if (log.userRating != 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (log.userRating > 0) Color(0xFF10B981).copy(alpha = 0.2f) else MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (log.userRating > 0) Icons.Default.ThumbUp else Icons.Default.ThumbDown,
                                    contentDescription = null,
                                    tint = if (log.userRating > 0) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = if (log.userRating > 0) "Helpful" else "Flagged",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (log.userRating > 0) Color(0xFF10B981) else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Log",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Trust Score Row & Progress Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Trust Score: ${(log.overallTrustScore * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (log.overallTrustScore >= 0.8f) Color(0xFF10B981) else Color(0xFFF59E0B)
                )
                Text(
                    text = "Factual: ${(log.factualAccuracyScore * 100).toInt()}% • Tone: ${(log.sentimentToneScore * 100).toInt()}% • Adherence: ${(log.topicAdherenceScore * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.5.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            LinearProgressIndicator(
                progress = { log.overallTrustScore.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = if (log.overallTrustScore >= 0.8f) Color(0xFF10B981) else Color(0xFFF59E0B),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            // Original Prompt Excerpt
            if (log.prompt.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Prompt: ${log.prompt.take(80)}${if (log.prompt.length > 80) "..." else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
            }

            // Critique notes if available
            if (log.critiqueReasons.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Text(
                        text = log.critiqueReasons.joinToString("\n") { "• $it" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(6.dp)
                    )
                }
            }
        }
    }
}
