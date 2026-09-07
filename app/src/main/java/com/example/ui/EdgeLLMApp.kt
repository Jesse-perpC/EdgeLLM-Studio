package com.example.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
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
import com.example.ui.screens.ApiServerScreen
import com.example.ui.screens.BackgroundTasksScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DeviceAndModelsScreen
import com.example.ui.screens.EncryptedVaultScreen
import com.example.ui.screens.InferenceScreen
import com.example.ui.screens.PluginPipelineScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import androidx.compose.material.icons.filled.Hub

enum class AppDestination(val label: String, val icon: ImageVector) {
    DASHBOARD("Dashboard", Icons.Default.Dashboard),
    MODELS("Models", Icons.Default.Memory),
    CHAT("Inference", Icons.Default.Chat),
    API("API", Icons.Default.Hub),
    QUEUE("Queue", Icons.Default.Schedule),
    PLUGINS("Plugins", Icons.Default.Extension),
    VAULT("Vault", Icons.Default.Lock)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EdgeLLMApp(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val accentPalette by viewModel.accentPalette.collectAsState()
    val apiStats by viewModel.apiServerStats.collectAsState()
    val isAirGapped by viewModel.isAirGappedMode.collectAsState()

    var currentDestination by remember { mutableStateOf(AppDestination.DASHBOARD) }
    var isInSettings by remember { mutableStateOf(false) }
    var prefilledExportText by remember { mutableStateOf("") }

    MyApplicationTheme(
        themeMode = themeMode,
        accentPalette = accentPalette
    ) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isInSettings) "Acceleration & Theme" else "EdgeLLM Studio",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (!isInSettings) {
                                Spacer(modifier = Modifier.width(8.dp))
                                val badgeColor = if (isAirGapped) Color(0xFF10B981) else Color(0xFF38BDF8)
                                val badgeText = if (isAirGapped) "Air-Gapped" else "Cloud Assist"
                                Surface(
                                    shape = CircleShape,
                                    color = badgeColor.copy(alpha = 0.15f),
                                    modifier = Modifier
                                        .clickable { viewModel.toggleAirGappedMode() }
                                        .testTag("toggle_air_gapped_mode_btn")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(badgeColor)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = badgeText,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = badgeColor
                                        )
                                    }
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        if (isInSettings) {
                            IconButton(
                                onClick = { isInSettings = false },
                                modifier = Modifier.testTag("back_from_settings_btn")
                            ) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    actions = {
                        if (!isInSettings) {
                            IconButton(
                                onClick = { currentDestination = AppDestination.API },
                                modifier = Modifier.testTag("open_api_server_top_btn")
                            ) {
                                Box(contentAlignment = Alignment.TopEnd) {
                                    Icon(
                                        imageVector = Icons.Default.Hub,
                                        contentDescription = "API Inference Server",
                                        tint = if (apiStats.isRunning) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (apiStats.isRunning) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF10B981))
                                        )
                                    }
                                }
                            }

                            IconButton(
                                onClick = { isInSettings = true },
                                modifier = Modifier.testTag("open_settings_top_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Hardware & Themes",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                if (!isInSettings) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        AppDestination.entries.forEach { destination ->
                            val selected = currentDestination == destination
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentDestination = destination },
                                icon = {
                                    Icon(
                                        imageVector = destination.icon,
                                        contentDescription = destination.label,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = destination.label,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.5.sp,
                                            letterSpacing = (-0.3).sp
                                        ),
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                modifier = Modifier.testTag("nav_${destination.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isInSettings) {
                    SettingsScreen(viewModel = viewModel)
                } else {
                    AnimatedContent(
                        targetState = currentDestination,
                        label = "tab_transition"
                    ) { dest ->
                        when (dest) {
                            AppDestination.DASHBOARD -> DashboardScreen(
                                viewModel = viewModel,
                                onNavigateToChat = { currentDestination = AppDestination.CHAT },
                                onNavigateToModels = { currentDestination = AppDestination.MODELS },
                                onNavigateToApi = { currentDestination = AppDestination.API }
                            )
                            AppDestination.MODELS -> DeviceAndModelsScreen(
                                viewModel = viewModel,
                                onNavigateToChat = { currentDestination = AppDestination.CHAT }
                            )
                            AppDestination.CHAT -> InferenceScreen(
                                viewModel = viewModel,
                                onNavigateToExport = { text ->
                                    prefilledExportText = text
                                    currentDestination = AppDestination.VAULT
                                }
                            )
                            AppDestination.API -> ApiServerScreen(
                                viewModel = viewModel
                            )
                            AppDestination.QUEUE -> BackgroundTasksScreen(
                                viewModel = viewModel,
                                onNavigateToExport = { text ->
                                    prefilledExportText = text
                                    currentDestination = AppDestination.VAULT
                                }
                            )
                            AppDestination.PLUGINS -> PluginPipelineScreen(
                                viewModel = viewModel,
                                onNavigateToExport = { text ->
                                    prefilledExportText = text
                                    currentDestination = AppDestination.VAULT
                                }
                            )
                            AppDestination.VAULT -> EncryptedVaultScreen(
                                viewModel = viewModel,
                                prefilledExportContent = prefilledExportText
                            )
                        }
                    }
                }
            }
        }
    }
}
