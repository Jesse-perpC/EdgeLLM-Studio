package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Hub
import com.example.ui.components.HardwareBenchmarkSheet
import com.example.ui.components.HardwareDashboardComponent
import com.example.ui.components.ModelDownloadProgressBanner
import com.example.ui.components.ModelImportStatusBar
import com.example.assistant.SystemAssistantCard
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onNavigateToChat: () -> Unit,
    onNavigateToModels: () -> Unit,
    onNavigateToApi: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val telemetryState by viewModel.telemetryState.collectAsState()
    val hardware by viewModel.hardwareInfo.collectAsState()
    val models by viewModel.models.collectAsState()
    val importProgress by viewModel.importProgress.collectAsState()
    val isDefaultAssistant by viewModel.isDefaultAssistant.collectAsState()
    var showBenchmarkSheet by remember { mutableStateOf(false) }

    val assistantRoleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshAssistantStatus()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("dashboard_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Hardware & LLM Monitor",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${hardware.socModel} • ${hardware.cpuCores} Cores Silicon",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "Real-Time Telemetry",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Android System-Level Voice Assistant Integration Card
        item {
            SystemAssistantCard(
                isDefaultAssistant = isDefaultAssistant,
                onRequestSetDefault = {
                    val intent = viewModel.getAssistantRoleRequestIntent()
                    if (intent != null) {
                        try {
                            assistantRoleLauncher.launch(intent)
                        } catch (e: Exception) {
                            viewModel.openSystemAssistantSettings(context)
                        }
                    } else {
                        viewModel.openSystemAssistantSettings(context)
                    }
                },
                onOpenSystemSettings = {
                    viewModel.openSystemAssistantSettings(context)
                },
                onTestAssistantOverlay = {
                    val intent = Intent(context, com.example.assistant.AssistantOverlayActivity::class.java).apply {
                        putExtra("query", "Summarize device status, check battery level, and tell me how my silicon engine is running.")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            )
        }

        // Live Model Import Status Bar
        item {
            ModelImportStatusBar(
                progress = importProgress,
                onCancel = { viewModel.cancelImport() },
                onDismiss = { viewModel.dismissImportProgress() },
                onLoadModel = { viewModel.setActiveModel(it) }
            )
        }

        // Active Downloads Banner if any
        val downloadingModels = models.filter { it.isDownloading || it.isPaused }
        if (downloadingModels.isNotEmpty()) {
            item {
                ModelDownloadProgressBanner(
                    downloadingModels = downloadingModels,
                    onPause = { viewModel.pauseDownload(it) },
                    onResume = { viewModel.downloadModel(it) },
                    onCancel = { viewModel.cancelDownload(it) }
                )
            }
        }

        // Primary Hardware Dashboard Component
        item {
            HardwareDashboardComponent(
                viewModel = viewModel,
                onOpenDiagnostic = { showBenchmarkSheet = true }
            )
        }

        // Trending: Dynamic KV Cache & Context Sizing Experimenter
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "KV Cache Attention Memory Sizer",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${telemetryState.activeContextTokens} / ${telemetryState.maxContextTokens} tok",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Simulate how long-context prompts scale memory usage on your device silicon.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Slider(
                        value = telemetryState.activeContextTokens.toFloat(),
                        onValueChange = { viewModel.setContextTokens(it.toInt()) },
                        valueRange = 128f..telemetryState.maxContextTokens.toFloat(),
                        steps = 6,
                        modifier = Modifier.testTag("kv_cache_context_slider")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("128 tok (Min KV)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${(telemetryState.memoryBreakdown.kvCacheBytes / (1024 * 1024))} MB KV Cache", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFFA855F7))
                        Text("${telemetryState.maxContextTokens} tok (Max)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Ollama API Inference Server Card
        item {
            val apiStats by viewModel.apiServerStats.collectAsState()
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (apiStats.isRunning) Color(0xFF0F172A) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (apiStats.isRunning) Color(0xFF10B981).copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToApi() }
                    .testTag("dashboard_api_server_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Surface(
                            shape = CircleShape,
                            color = if (apiStats.isRunning) Color(0xFF10B981).copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Hub,
                                    contentDescription = null,
                                    tint = if (apiStats.isRunning) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Ollama & OpenAI API Server",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (apiStats.isRunning) Color(0xFF10B981).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = if (apiStats.isRunning) "ONLINE" else "OFFLINE",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (apiStats.isRunning) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (apiStats.isRunning) apiStats.lanUrl else "Offline • Tap to configure external connections",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (apiStats.isRunning) Color(0xFF38BDF8) else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = if (apiStats.isRunning) androidx.compose.ui.text.font.FontFamily.Monospace else null
                            )
                        }
                    }

                    Switch(
                        checked = apiStats.isRunning,
                        onCheckedChange = { viewModel.toggleApiServer() }
                    )
                }
            }
        }

        // Quick Actions Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onNavigateToChat,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("dashboard_launch_inference_btn")
                ) {
                    Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Start Inference")
                }

                OutlinedButton(
                    onClick = onNavigateToModels,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("dashboard_view_models_btn")
                ) {
                    Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Model Catalog")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showBenchmarkSheet) {
        HardwareBenchmarkSheet(
            viewModel = viewModel,
            onDismiss = { showBenchmarkSheet = false },
            onSelectRecommendedTier = { _ ->
                onNavigateToModels()
            }
        )
    }
}
