package com.example.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.screens.BackgroundTasksScreen
import com.example.ui.screens.DeviceAndModelsScreen
import com.example.ui.screens.EncryptedVaultScreen
import com.example.ui.screens.InferenceScreen
import com.example.ui.screens.PluginPipelineScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme

enum class AppDestination(val label: String, val icon: ImageVector) {
    MODELS("Models", Icons.Default.Memory),
    CHAT("Inference", Icons.Default.Chat),
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

    var currentDestination by remember { mutableStateOf(AppDestination.MODELS) }
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
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFF10B981).copy(alpha = 0.15f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF10B981))
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Air-Gapped",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF10B981)
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
                                        contentDescription = destination.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = destination.label,
                                        fontSize = 11.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
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
                            AppDestination.MODELS -> DeviceAndModelsScreen(
                                viewModel = viewModel
                            )
                            AppDestination.CHAT -> InferenceScreen(
                                viewModel = viewModel,
                                onNavigateToExport = { text ->
                                    prefilledExportText = text
                                    currentDestination = AppDestination.VAULT
                                }
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
