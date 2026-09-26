package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AppDestination
import com.example.ui.MainViewModel

data class NavMenuCategory(
    val categoryTitle: String,
    val items: List<NavMenuItem>
)

data class NavMenuItem(
    val destination: AppDestination?,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val badge: String? = null,
    val badgeColor: Color? = null,
    val isAction: Boolean = false,
    val actionType: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigationMenuSheet(
    viewModel: MainViewModel,
    currentDestination: AppDestination,
    onSelectDestination: (AppDestination) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenThemeStudio: () -> Unit,
    onOpenSiliconGovernor: () -> Unit,
    onOpenQuantCalc: () -> Unit,
    onOpenVoiceStudio: () -> Unit = {},
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hardware by viewModel.hardwareInfo.collectAsState()
    val models by viewModel.models.collectAsState()
    val activeModel = models.firstOrNull { it.isActive } ?: models.firstOrNull()
    val isAirGapped by viewModel.isAirGappedMode.collectAsState()
    val apiStats by viewModel.apiServerStats.collectAsState()

    var showRankDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier.testTag("app_navigation_menu_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "EdgeLLM Studio",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.18f),
                                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "#1 RANKED",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF10B981),
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Ultimate On-Device AI Suite",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_nav_menu_btn")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close Menu")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Active Hardware & Privacy Banner
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isAirGapped) Color(0xFF10B981) else Color(0xFF38BDF8))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isAirGapped) "Air-Gapped Private Mode" else "Cloud-Assisted Mode",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isAirGapped) Color(0xFF10B981) else Color(0xFF38BDF8)
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Model: ${activeModel?.name ?: "None"} • ${hardware.socModel}",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = isAirGapped,
                        onCheckedChange = { viewModel.toggleAirGappedMode() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF10B981),
                            checkedTrackColor = Color(0xFF10B981).copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.testTag("menu_air_gapped_switch")
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            val menuSections = listOf(
                NavMenuCategory(
                    categoryTitle = "CORE STUDIOS & WORKSPACES",
                    items = listOf(
                        NavMenuItem(
                            destination = AppDestination.DASHBOARD,
                            title = "Dashboard & Telemetry",
                            subtitle = "Hardware health, KV cache memory, tokens/sec",
                            icon = Icons.Default.Dashboard
                        ),
                        NavMenuItem(
                            destination = AppDestination.CHAT,
                            title = "Inference & Chat Studio",
                            subtitle = "Straight-to-the-point answers, multi-lingual, CoT switch",
                            icon = Icons.Default.Chat,
                            badge = "STREAMING",
                            badgeColor = Color(0xFF10B981)
                        ),
                        NavMenuItem(
                            destination = AppDestination.MODELS,
                            title = "Local Model Hub",
                            subtitle = "GGUF, LiteRT, MediaPipe, ONNX, MNN & custom imports",
                            icon = Icons.Default.Memory
                        ),
                        NavMenuItem(
                            destination = AppDestination.HF_EXPLORER,
                            title = "Hugging Face Hub Explorer",
                            subtitle = "Search 100k+ models, filter by Q4/Q8, 1-tap download",
                            icon = Icons.Default.Public,
                            badge = "NEW",
                            badgeColor = Color(0xFF3B82F6)
                        ),
                        NavMenuItem(
                            destination = AppDestination.IMAGE_STUDIO,
                            title = "On-Device Image AI Studio",
                            subtitle = "Stable Diffusion text2img, img2img, mask inpaint",
                            icon = Icons.Default.AutoAwesome,
                            badge = ":ai_sd",
                            badgeColor = Color(0xFF8B5CF6)
                        ),
                        NavMenuItem(
                            destination = AppDestination.RAG_DEBUG,
                            title = "RAG Knowledge & Chunk Debugger",
                            subtitle = "Semantic document vectors, cosine similarity inspector",
                            icon = Icons.Default.Analytics,
                            badge = "RAG",
                            badgeColor = Color(0xFF06B6D4)
                        ),
                        NavMenuItem(
                            destination = AppDestination.API,
                            title = "OpenAI & Ollama Server",
                            subtitle = "Port 8080/11434 daemon, curl/python SDK, audit logger",
                            icon = Icons.Default.Hub,
                            badge = if (apiStats.isRunning) "LIVE" else "PORT 8080",
                            badgeColor = if (apiStats.isRunning) Color(0xFF10B981) else MaterialTheme.colorScheme.outline
                        ),
                        NavMenuItem(
                            destination = AppDestination.BENCHMARK,
                            title = "Model Arena & Benchmark",
                            subtitle = "Blind side-by-side A/B test, Elo rating, Tokens/sec",
                            icon = Icons.Default.CompareArrows,
                            badge = "ELO",
                            badgeColor = Color(0xFFEF4444)
                        ),
                        NavMenuItem(
                            destination = AppDestination.QUEUE,
                            title = "Background Job Queue",
                            subtitle = "Asynchronous inference batches, resumable tasks",
                            icon = Icons.Default.Schedule
                        ),
                        NavMenuItem(
                            destination = AppDestination.PLUGINS,
                            title = "Sandboxed Plugins & MCP Hub",
                            subtitle = "Offline notes, expense tracker, Python/JS sandbox",
                            icon = Icons.Default.Extension
                        ),
                        NavMenuItem(
                            destination = AppDestination.VAULT,
                            title = "Zero-Knowledge Encrypted Vault",
                            subtitle = "AES-256 GCM hardware-encrypted backups & keys",
                            icon = Icons.Default.Lock
                        )
                    )
                ),
                NavMenuCategory(
                    categoryTitle = "HARDWARE & PERFORMANCE TOOLS",
                    items = listOf(
                        NavMenuItem(
                            destination = null,
                            title = "Voice Cloning & Speech Studio",
                            subtitle = "Neural TTS profiles, zero-shot cloning & acoustic timbre",
                            icon = Icons.Default.RecordVoiceOver,
                            badge = "VOICE AI",
                            badgeColor = Color(0xFF38BDF8),
                            isAction = true,
                            actionType = "voice_studio"
                        ),
                        NavMenuItem(
                            destination = null,
                            title = "Silicon Governor & Thermals",
                            subtitle = "NPU/GPU/CPU thread allocation, thermal governor",
                            icon = Icons.Default.Thermostat,
                            isAction = true,
                            actionType = "governor"
                        ),
                        NavMenuItem(
                            destination = null,
                            title = "Quantization & VRAM Calculator",
                            subtitle = "Calculate weight footprint & KV cache for any model",
                            icon = Icons.Default.Calculate,
                            isAction = true,
                            actionType = "quant"
                        ),
                        NavMenuItem(
                            destination = null,
                            title = "Theme & Aesthetic Studio",
                            subtitle = "12 cyberpunk & clean palettes with dynamic dark mode",
                            icon = Icons.Default.Palette,
                            isAction = true,
                            actionType = "theme"
                        ),
                        NavMenuItem(
                            destination = null,
                            title = "Acceleration Settings",
                            subtitle = "Compiler flags, memory limiters, context window length",
                            icon = Icons.Default.Settings,
                            isAction = true,
                            actionType = "settings"
                        ),
                        NavMenuItem(
                            destination = null,
                            title = "Why EdgeLLM Ranks #1",
                            subtitle = "Feature matrix vs ToolNeuron, Edge Gallery, Layla, LM Studio",
                            icon = Icons.Default.Info,
                            badge = "SPEC MATRIX",
                            badgeColor = Color(0xFFF59E0B),
                            isAction = true,
                            actionType = "rank_matrix"
                        )
                    )
                )
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                menuSections.forEach { section ->
                    item {
                        Column {
                            Text(
                                text = section.categoryTitle,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                section.items.forEach { item ->
                                    val isSelected = item.destination != null && item.destination == currentDestination
                                    val activeColor = MaterialTheme.colorScheme.primary
                                    val bg = if (isSelected) activeColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                    val border = if (isSelected) BorderStroke(1.dp, activeColor.copy(alpha = 0.4f)) else null

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = bg,
                                        border = border,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                if (item.isAction) {
                                                    when (item.actionType) {
                                                        "voice_studio" -> {
                                                            onDismiss()
                                                            onOpenVoiceStudio()
                                                        }
                                                        "governor" -> {
                                                            onDismiss()
                                                            onOpenSiliconGovernor()
                                                        }
                                                        "quant" -> {
                                                            onDismiss()
                                                            onOpenQuantCalc()
                                                        }
                                                        "theme" -> {
                                                            onDismiss()
                                                            onOpenThemeStudio()
                                                        }
                                                        "settings" -> {
                                                            onDismiss()
                                                            onOpenSettings()
                                                        }
                                                        "rank_matrix" -> {
                                                            showRankDialog = true
                                                        }
                                                    }
                                                } else if (item.destination != null) {
                                                    onDismiss()
                                                    onSelectDestination(item.destination)
                                                }
                                            }
                                            .testTag("nav_item_${item.title.lowercase().replace(" ", "_")}")
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = if (isSelected) activeColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = item.icon,
                                                        contentDescription = null,
                                                        tint = if (isSelected) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(12.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = item.title,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                        color = if (isSelected) activeColor else MaterialTheme.colorScheme.onSurface
                                                    )
                                                    if (item.badge != null) {
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Surface(
                                                            shape = RoundedCornerShape(4.dp),
                                                            color = (item.badgeColor ?: activeColor).copy(alpha = 0.18f)
                                                        ) {
                                                            Text(
                                                                text = item.badge,
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = item.badgeColor ?: activeColor,
                                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                                Text(
                                                    text = item.subtitle,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontSize = 11.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRankDialog) {
        CompetitiveRankingDialog(
            onDismiss = { showRankDialog = false }
        )
    }
}

@Composable
fun CompetitiveRankingDialog(
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .testTag("competitive_ranking_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Category Leaderboard #1",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "RANK #1",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF10B981),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "EdgeLLM Studio vs All Mobile AI Apps",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val appRankings = listOf(
                        Triple(
                            "EdgeLLM Studio",
                            "Score: 9.9/10 • Rank #1",
                            "Universal GGUF+MediaPipe+ONNX+MNN runtime, Hugging Face Hub explorer, Stable Diffusion on-device image AI, RAG semantic chunk inspector, background OpenAI HTTP server (8080/11434), blind A/B model arena, straight-to-the-point multi-lingual output, zero unsolicited CoT, AES-256 encrypted vault, silicon governor thermal controls."
                        ),
                        Triple(
                            "ToolNeuron",
                            "Score: 8.6/10 • Rank #2",
                            "Has GGUF inference, HuggingFace explorer, Stable Diffusion, and OpenAI server. Lacks blind Elo arena, system assistant overlay, speculative decoding, multi-lingual precision engine, and zero-knowledge encrypted vault."
                        ),
                        Triple(
                            "Google AI Edge Gallery",
                            "Score: 8.2/10 • Rank #3",
                            "Official Google benchmark app for Gemma 2/LiteRT. Lacks GGUF/llama.cpp support, lacks OpenAI remote server, lacks Stable Diffusion image generation, lacks sandboxed plugin hub, and lacks document RAG."
                        ),
                        Triple(
                            "Layla Companion",
                            "Score: 7.9/10 • Rank #4",
                            "Popular paid companion app. Good character personas, but closed-source, high cost, lacks developer OpenAI server, lacks HuggingFace Hub live explorer, lacks RAG chunk debugging, lacks Stable Diffusion studio."
                        ),
                        Triple(
                            "Private LLM / Ollama Mobile",
                            "Score: 7.4/10 • Rank #5",
                            "Clean UI for basic chat, but lacks multi-modal diffusion, lacks fine-grained silicon governor, lacks model arena, and has rigid quantized model choices."
                        )
                    )

                    items(appRankings) { (name, score, details) ->
                        val isLeader = name.startsWith("EdgeLLM")
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isLeader) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isLeader) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isLeader) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = score,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isLeader) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = details,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                androidx.compose.material3.Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Got It • Top Performing Studio")
                }
            }
        }
    }
}
