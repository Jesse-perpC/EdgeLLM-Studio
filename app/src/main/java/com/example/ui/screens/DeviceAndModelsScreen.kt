package com.example.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import com.example.data.model.ModelCategory
import com.example.data.model.ModelFormat
import com.example.ui.MainViewModel
import com.example.ui.components.DownloadCustomModelDialog
import com.example.ui.components.HardwareBenchmarkSheet
import com.example.ui.components.HardwareDashboardComponent
import com.example.ui.components.HardwareHeaderCard
import com.example.ui.components.ModelDownloadProgressBanner
import com.example.ui.components.ModelImportSheet
import com.example.ui.components.ModelImportStatusBar
import com.example.ui.components.ModelItemCard
import com.example.ui.components.QuantizationCalculatorSheet

@Composable
fun DeviceAndModelsScreen(
    viewModel: MainViewModel,
    onNavigateToChat: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val hardware by viewModel.hardwareInfo.collectAsState()
    val models by viewModel.models.collectAsState()
    val benchmarkState by viewModel.benchmarkState.collectAsState()
    val importProgress by viewModel.importProgress.collectAsState()

    var selectedFormatFilter by remember { mutableStateOf<ModelFormat?>(null) }
    var activeParamFilter by remember { mutableStateOf<String?>(null) }
    var showOnlyImported by remember { mutableStateOf(false) }
    var showOnlyDownloaded by remember { mutableStateOf(false) }
    var selectedCategoryFilter by remember { mutableStateOf<ModelCategory?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showBenchmarkSheet by remember { mutableStateOf(false) }
    var showQuantizationSheet by remember { mutableStateOf(false) }
    var showImportSheet by remember { mutableStateOf(false) }
    var showDownloadCustomModelDialog by remember { mutableStateOf(false) }
    var showLiveDashboard by remember { mutableStateOf(false) }

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

    val filteredModels = remember(
        models,
        selectedFormatFilter,
        activeParamFilter,
        showOnlyImported,
        showOnlyDownloaded,
        selectedCategoryFilter,
        searchQuery
    ) {
        models.filter { model ->
            val matchesFormat = selectedFormatFilter == null || model.format == selectedFormatFilter
            val matchesParam = activeParamFilter == null || model.parameterCount.contains(activeParamFilter!!, ignoreCase = true)
            val matchesImported = !showOnlyImported || model.isImported
            val matchesDownloaded = !showOnlyDownloaded || model.isDownloaded
            val matchesCategory = selectedCategoryFilter == null || model.category == selectedCategoryFilter
            val matchesSearch = searchQuery.isBlank() ||
                model.name.contains(searchQuery, ignoreCase = true) ||
                model.description.contains(searchQuery, ignoreCase = true) ||
                model.quantization.contains(searchQuery, ignoreCase = true) ||
                model.parameterCount.contains(searchQuery, ignoreCase = true) ||
                model.format.name.contains(searchQuery, ignoreCase = true) ||
                model.format.displayName.contains(searchQuery, ignoreCase = true)
            matchesFormat && matchesParam && matchesImported && matchesDownloaded && matchesCategory && matchesSearch
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("device_models_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Hardware Silicon Card
        item {
            HardwareHeaderCard(
                hardware = hardware,
                onRefresh = { viewModel.refreshHardware() }
            )
        }

        // Live Model Import Status Bar (Visible during folder scanning/importing or upon completion/error)
        item {
            ModelImportStatusBar(
                progress = importProgress,
                onCancel = { viewModel.cancelImport() },
                onDismiss = { viewModel.dismissImportProgress() },
                onLoadModel = { viewModel.setActiveModel(it) }
            )
        }

        // Active Downloads Banner (Visible whenever any models are downloading or paused)
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

        // Live Telemetry & Acceleration Toggle Card
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("telemetry_dashboard_expand_card")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
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
                                    .background(Color(0xFF06B6D4).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = null,
                                    tint = Color(0xFF06B6D4),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Live Silicon & Memory Telemetry",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Real-time GPU/NPU utilization and RAM breakdown",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        androidx.compose.material3.TextButton(
                            onClick = { showLiveDashboard = !showLiveDashboard },
                            modifier = Modifier.testTag("toggle_dashboard_card_btn")
                        ) {
                            Text(
                                text = if (showLiveDashboard) "Collapse" else "View Charts",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (showLiveDashboard) {
                        Spacer(modifier = Modifier.height(14.dp))
                        HardwareDashboardComponent(
                            viewModel = viewModel,
                            onOpenDiagnostic = { showBenchmarkSheet = true }
                        )
                    }
                }
            }
        }

        // Diagnostic Inference Speed Benchmark Banner
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("benchmark_launcher_card")
            ) {
                Row(
                    modifier = Modifier
                        .padding(14.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = "Speed Diagnostic",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Inference Speed Diagnostic",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            if (benchmarkState.optimalTierName.isNotBlank()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Optimal: ${benchmarkState.optimalTierName} GGUF",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF10B981)
                                    )
                                }
                            } else {
                                Text(
                                    text = "Measure tokens/sec to choose ideal GGUF scale",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = { showBenchmarkSheet = true },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("open_benchmark_dialog_btn")
                    ) {
                        Text(
                            text = if (benchmarkState.results.isEmpty()) "Test Speed" else "View Report",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Quantization & Perplexity Calculator Card
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("quantization_calculator_launcher_card")
            ) {
                Row(
                    modifier = Modifier
                        .padding(14.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Calculate,
                                contentDescription = "Quantization Calculator",
                                tint = MaterialTheme.colorScheme.onSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Quantization & Perplexity",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Simulate RAM footprint, bits/weight & perplexity loss across GGUF formats",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = { showQuantizationSheet = true },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("open_quant_calculator_btn")
                    ) {
                        Text(
                            text = "Calculate",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Active Parameter Filter notice if applied from benchmark
        if (activeParamFilter != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Filtered by Diagnostic: $activeParamFilter tier",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        IconButton(
                            onClick = { activeParamFilter = null },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear filter",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // Storage & Headroom Status Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Storage & Memory Headroom",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        val activeModel = models.firstOrNull { it.isActive && it.isDownloaded }
                        if (activeModel != null) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "Active: ${activeModel.name.take(14)}...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF10B981),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "RAM Available",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val availGb = String.format(java.util.Locale.US, "%.1f", hardware.availableRamBytes / (1024.0 * 1024.0 * 1024.0))
                            val totalGb = String.format(java.util.Locale.US, "%.1f", hardware.totalRamBytes / (1024.0 * 1024.0 * 1024.0))
                            Text(
                                text = "$availGb GB / $totalGb GB",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Column {
                            Text(
                                text = "Offline Weights",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val downloadedCount = models.count { it.isDownloaded }
                            val downloadedMb = models.filter { it.isDownloaded }.sumOf { it.fileSizeBytes } / (1024 * 1024)
                            Text(
                                text = "$downloadedCount Ready ($downloadedMb MB)",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Column {
                            Text(
                                text = "Max Safe Size",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val maxSafeMb = (hardware.availableRamBytes * 0.70 / (1024 * 1024 * 1024.0))
                            Text(
                                text = "~${String.format(java.util.Locale.US, "%.1f", maxSafeMb)} GB",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                        }
                    }
                }
            }
        }

        // Catalog Header
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "On-Device Model Catalog",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981))
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "${models.count { it.isDownloaded }} of ${models.size} Ready • 100% Offline in RAM",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Action buttons row responsive across all phone widths:
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showDownloadCustomModelDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("catalog_download_url_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add URL", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { showImportSheet = true },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("catalog_import_folder_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Import", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { showBenchmarkSheet = true },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("catalog_benchmark_shortcut_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Speed Test", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Search and Category Filter
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search models, architectures (Llama, Qwen, Gemma, Phi), quants...", fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(20.dp))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("model_catalog_search_input")
                )

                // Category chips LazyRow
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedCategoryFilter == null,
                            onClick = { selectedCategoryFilter = null },
                            label = { Text("All Tasks", fontSize = 12.sp) },
                            modifier = Modifier.testTag("cat_filter_all")
                        )
                    }
                    ModelCategory.entries.forEach { cat ->
                        item {
                            FilterChip(
                                selected = selectedCategoryFilter == cat,
                                onClick = {
                                    selectedCategoryFilter = if (selectedCategoryFilter == cat) null else cat
                                },
                                label = { Text(cat.title, fontSize = 12.sp) },
                                modifier = Modifier.testTag("cat_filter_${cat.name}")
                            )
                        }
                    }
                }
            }
        }

        // Framework & Source filter chips (Scrollable LazyRow with badges & icons)
        item {
            val allCount = models.size
            val downloadedCount = models.count { it.isDownloaded }
            val ggufCount = models.count { it.format == ModelFormat.GGUF }
            val tfliteCount = models.count { it.format == ModelFormat.TFLITE }
            val onnxCount = models.count { it.format == ModelFormat.ONNX }
            val importedCount = models.count { it.isImported }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("model_format_filters_row"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedFormatFilter == null && !showOnlyImported && !showOnlyDownloaded,
                            onClick = {
                                selectedFormatFilter = null
                                showOnlyImported = false
                                showOnlyDownloaded = false
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Layers,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text("All ($allCount)", fontSize = 12.sp) },
                            modifier = Modifier.testTag("filter_all_frameworks")
                        )
                    }
                    item {
                        FilterChip(
                            selected = showOnlyDownloaded,
                            onClick = {
                                showOnlyDownloaded = !showOnlyDownloaded
                                if (showOnlyDownloaded) {
                                    showOnlyImported = false
                                    selectedFormatFilter = null
                                }
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (showOnlyDownloaded) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            label = { Text("Offline ($downloadedCount)", fontSize = 12.sp) },
                            modifier = Modifier.testTag("filter_downloaded")
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedFormatFilter == ModelFormat.GGUF && !showOnlyImported && !showOnlyDownloaded,
                            onClick = {
                                showOnlyImported = false
                                showOnlyDownloaded = false
                                selectedFormatFilter = if (selectedFormatFilter == ModelFormat.GGUF) null else ModelFormat.GGUF
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Memory,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = Color(0xFF06B6D4)
                                )
                            },
                            label = { Text("GGUF ($ggufCount)", fontSize = 12.sp) },
                            modifier = Modifier.testTag("filter_gguf")
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedFormatFilter == ModelFormat.TFLITE && !showOnlyImported && !showOnlyDownloaded,
                            onClick = {
                                showOnlyImported = false
                                showOnlyDownloaded = false
                                selectedFormatFilter = if (selectedFormatFilter == ModelFormat.TFLITE) null else ModelFormat.TFLITE
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = Color(0xFFF59E0B)
                                )
                            },
                            label = { Text("LiteRT ($tfliteCount)", fontSize = 12.sp) },
                            modifier = Modifier.testTag("filter_tflite")
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedFormatFilter == ModelFormat.ONNX && !showOnlyImported && !showOnlyDownloaded,
                            onClick = {
                                showOnlyImported = false
                                showOnlyDownloaded = false
                                selectedFormatFilter = if (selectedFormatFilter == ModelFormat.ONNX) null else ModelFormat.ONNX
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = Color(0xFF8B5CF6)
                                )
                            },
                            label = { Text("ONNX ($onnxCount)", fontSize = 12.sp) },
                            modifier = Modifier.testTag("filter_onnx")
                        )
                    }
                    item {
                        FilterChip(
                            selected = showOnlyImported,
                            onClick = {
                                showOnlyImported = !showOnlyImported
                                if (showOnlyImported) {
                                    selectedFormatFilter = null
                                    showOnlyDownloaded = false
                                }
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = Color(0xFFA855F7)
                                )
                            },
                            label = { Text("Imported ($importedCount)", fontSize = 12.sp) },
                            modifier = Modifier.testTag("filter_imported")
                        )
                    }
                }

                // Active filter reset banner if filters are active
                if (selectedFormatFilter != null || showOnlyImported || showOnlyDownloaded || selectedCategoryFilter != null || searchQuery.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Showing ${filteredModels.size} of ${models.size} models",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = {
                                selectedFormatFilter = null
                                showOnlyImported = false
                                showOnlyDownloaded = false
                                selectedCategoryFilter = null
                                searchQuery = ""
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset Filters", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Empty state when filtered
        if (filteredModels.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (showOnlyImported) "No local models imported yet" else "No matching models found",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (showOnlyImported) {
                                "Tap 'Import Models' above to select a folder or file containing .gguf, .tflite, or .onnx models."
                            } else {
                                "Try resetting the filters or import your own custom models."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        if (showOnlyImported) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { showImportSheet = true },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Select Folder to Import")
                            }
                        }
                    }
                }
            }
        }

        // Model items
        items(filteredModels, key = { it.id }) { model ->
            val compatibility = viewModel.getCompatibility(model)
            ModelItemCard(
                model = model,
                compatibility = compatibility,
                onDownload = { viewModel.downloadModel(model.id) },
                onPauseDownload = { viewModel.pauseDownload(model.id) },
                onResumeDownload = { viewModel.downloadModel(model.id) },
                onCancelDownload = { viewModel.cancelDownload(model.id) },
                onDelete = { viewModel.deleteModel(model.id) },
                onSetActive = { viewModel.setActiveModel(model.id) },
                onVerifyChecksum = { onResult -> viewModel.verifyModelChecksum(model.id, onResult) },
                onOpenChat = onNavigateToChat
            )
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Benchmark Bottom Sheet Dialog
    if (showBenchmarkSheet) {
        HardwareBenchmarkSheet(
            viewModel = viewModel,
            onDismiss = { showBenchmarkSheet = false },
            onSelectRecommendedTier = { tierParam ->
                activeParamFilter = tierParam
            }
        )
    }

    // Model Import Bottom Sheet Dialog
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
            onDownloadCustomUrl = {
                showDownloadCustomModelDialog = true
            },
            onDismiss = { showImportSheet = false }
        )
    }

    // Custom Model Download Dialog
    if (showDownloadCustomModelDialog) {
        DownloadCustomModelDialog(
            onDismiss = { showDownloadCustomModelDialog = false },
            onDownloadCustomModel = { name, url, format, paramCount, quant, sizeMb, category ->
                viewModel.addCustomModel(name, url, format, paramCount, quant, sizeMb, category)
            },
            memorySafetyManager = com.example.engine.MemorySafetyManager(androidx.compose.ui.platform.LocalContext.current)
        )
    }

    // Quantization & Perplexity Calculator Bottom Sheet
    if (showQuantizationSheet) {
        QuantizationCalculatorSheet(
            deviceHardware = hardware,
            onDismiss = { showQuantizationSheet = false }
        )
    }
}
