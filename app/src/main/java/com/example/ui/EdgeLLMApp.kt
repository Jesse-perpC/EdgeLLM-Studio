package com.example.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.example.ui.components.BillingAndAllocationsSheet
import com.example.ui.components.ThemeStudioBottomSheet
import com.example.ui.screens.ApiServerScreen
import com.example.ui.screens.BackgroundTasksScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DeviceAndModelsScreen
import com.example.ui.screens.EncryptedVaultScreen
import com.example.ui.screens.InferenceScreen
import com.example.ui.screens.PluginPipelineScreen
import com.example.ui.screens.SettingsScreen
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Public
import com.example.ui.components.AppNavigationMenuSheet
import com.example.ui.components.BillingAndAllocationsSheet
import com.example.ui.components.QuantizationCalculatorSheet
import com.example.ui.components.SiliconGovernorSheet
import com.example.ui.components.ThemeStudioBottomSheet
import com.example.ui.screens.ApiServerScreen
import com.example.ui.screens.BackgroundTasksScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DeviceAndModelsScreen
import com.example.ui.screens.EncryptedVaultScreen
import com.example.ui.screens.HfExplorerScreen
import com.example.ui.screens.ImageTaskScreen
import com.example.ui.screens.InferenceScreen
import com.example.ui.screens.ModelArenaScreen
import com.example.ui.screens.PluginPipelineScreen
import com.example.ui.screens.RagDebugScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import androidx.compose.material.icons.filled.Hub

enum class AppDestination(val label: String, val icon: ImageVector) {
    DASHBOARD("Home", Icons.Default.Dashboard),
    MODELS("Models", Icons.Default.Memory),
    HF_EXPLORER("HF Hub", Icons.Default.Public),
    CHAT("Chat", Icons.Default.Chat),
    IMAGE_STUDIO("Image AI", Icons.Default.AutoAwesome),
    RAG_DEBUG("RAG Debug", Icons.Default.Analytics),
    API("Server", Icons.Default.Hub),
    BENCHMARK("Arena", Icons.Default.CompareArrows),
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
    val userProfile by viewModel.userSubscriptionProfile.collectAsState()
    val hardware by viewModel.hardwareInfo.collectAsState()

    var currentDestination by remember { mutableStateOf(AppDestination.DASHBOARD) }
    var isInSettings by remember { mutableStateOf(false) }
    var showNavMenuSheet by remember { mutableStateOf(false) }
    var showBillingSheet by remember { mutableStateOf(false) }
    var showThemeStudioSheet by remember { mutableStateOf(false) }
    var showSiliconGovernorSheet by remember { mutableStateOf(false) }
    var showQuantCalcSheet by remember { mutableStateOf(false) }
    var prefilledExportText by remember { mutableStateOf("") }

    MyApplicationTheme(
        themeMode = themeMode,
        accentPalette = accentPalette
    ) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = if (isInSettings) "Acceleration & Theme" else "EdgeLLM Studio",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!isInSettings) {
                                Spacer(modifier = Modifier.height(2.dp))
                                val badgeColor = if (isAirGapped) Color(0xFF10B981) else Color(0xFF38BDF8)
                                val badgeText = if (isAirGapped) "Air-Gapped Private" else "Cloud Assisted"
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { viewModel.toggleAirGappedMode() }
                                        .testTag("toggle_air_gapped_mode_btn")
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
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
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

                            // Theme & Visual Aesthetic Studio Launcher (Anytime Access)
                            IconButton(
                                onClick = { showThemeStudioSheet = true },
                                modifier = Modifier.testTag("open_theme_studio_top_btn")
                            ) {
                                Box(contentAlignment = Alignment.TopEnd) {
                                    Icon(
                                        imageVector = Icons.Default.Palette,
                                        contentDescription = "Futuristic Theme Studio (${accentPalette.title})",
                                        tint = accentPalette.primaryPreview
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(accentPalette.accentGlow)
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    val nextMode = when (themeMode) {
                                        com.example.ui.theme.AppThemeMode.DARK -> com.example.ui.theme.AppThemeMode.LIGHT
                                        com.example.ui.theme.AppThemeMode.LIGHT -> com.example.ui.theme.AppThemeMode.SYSTEM
                                        com.example.ui.theme.AppThemeMode.SYSTEM -> com.example.ui.theme.AppThemeMode.DARK
                                    }
                                    viewModel.setThemeMode(nextMode)
                                },
                                modifier = Modifier.testTag("quick_theme_toggle_btn")
                            ) {
                                Icon(
                                    imageVector = when (themeMode) {
                                        com.example.ui.theme.AppThemeMode.DARK -> Icons.Default.DarkMode
                                        com.example.ui.theme.AppThemeMode.LIGHT -> Icons.Default.LightMode
                                        com.example.ui.theme.AppThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                                    },
                                    contentDescription = "Quick Theme Switcher (${themeMode.title})",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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

                            // 3-Lines Hamburger Menu Button in the top right corner
                            IconButton(
                                onClick = { showNavMenuSheet = true },
                                modifier = Modifier.testTag("top_hamburger_menu_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Main App Menu",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                if (!isInSettings) {
                    val essentialDestinations = listOf(
                        AppDestination.DASHBOARD,
                        AppDestination.CHAT,
                        AppDestination.MODELS,
                        AppDestination.BENCHMARK,
                        AppDestination.IMAGE_STUDIO
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            essentialDestinations.forEach { destination ->
                                val selected = currentDestination == destination
                                val activeColor = MaterialTheme.colorScheme.primary

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { currentDestination = destination }
                                        .padding(vertical = 4.dp)
                                        .testTag("nav_${destination.name.lowercase()}")
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (selected) activeColor.copy(alpha = 0.16f) else Color.Transparent,
                                        modifier = Modifier
                                            .height(32.dp)
                                            .width(52.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = destination.icon,
                                                contentDescription = destination.label,
                                                tint = if (selected) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = destination.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 11.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
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
                                onNavigateToApi = { currentDestination = AppDestination.API },
                                onNavigateToImageStudio = { currentDestination = AppDestination.IMAGE_STUDIO },
                                onNavigateToRagDebug = { currentDestination = AppDestination.RAG_DEBUG },
                                onNavigateToArena = { currentDestination = AppDestination.BENCHMARK }
                            )
                            AppDestination.MODELS -> DeviceAndModelsScreen(
                                viewModel = viewModel,
                                onNavigateToChat = { currentDestination = AppDestination.CHAT }
                            )
                            AppDestination.HF_EXPLORER -> HfExplorerScreen(
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
                            AppDestination.IMAGE_STUDIO -> ImageTaskScreen(
                                viewModel = viewModel
                            )
                            AppDestination.RAG_DEBUG -> RagDebugScreen(
                                viewModel = viewModel
                            )
                            AppDestination.API -> ApiServerScreen(
                                viewModel = viewModel
                            )
                            AppDestination.BENCHMARK -> ModelArenaScreen(
                                viewModel = viewModel,
                                onNavigateToChat = { currentDestination = AppDestination.CHAT }
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
            if (showNavMenuSheet) {
                AppNavigationMenuSheet(
                    viewModel = viewModel,
                    currentDestination = currentDestination,
                    onSelectDestination = { dest ->
                        currentDestination = dest
                        isInSettings = false
                    },
                    onOpenSettings = { isInSettings = true },
                    onOpenThemeStudio = { showThemeStudioSheet = true },
                    onOpenSiliconGovernor = { showSiliconGovernorSheet = true },
                    onOpenQuantCalc = { showQuantCalcSheet = true },
                    onDismiss = { showNavMenuSheet = false }
                )
            }
            if (showSiliconGovernorSheet) {
                SiliconGovernorSheet(
                    viewModel = viewModel,
                    onDismiss = { showSiliconGovernorSheet = false }
                )
            }
            if (showQuantCalcSheet) {
                QuantizationCalculatorSheet(
                    deviceHardware = hardware,
                    onDismiss = { showQuantCalcSheet = false }
                )
            }
            if (showBillingSheet) {
                BillingAndAllocationsSheet(
                    viewModel = viewModel,
                    onDismiss = { showBillingSheet = false }
                )
            }
            if (showThemeStudioSheet) {
                ThemeStudioBottomSheet(
                    viewModel = viewModel,
                    onDismiss = { showThemeStudioSheet = false }
                )
            }
        }
    }
}
