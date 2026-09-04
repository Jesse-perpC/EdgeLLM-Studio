package com.example.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
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
import com.example.data.model.ModelFormat
import com.example.ui.MainViewModel
import com.example.ui.components.HardwareBenchmarkSheet
import com.example.ui.components.HardwareHeaderCard
import com.example.ui.components.ModelItemCard

@Composable
fun DeviceAndModelsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val hardware by viewModel.hardwareInfo.collectAsState()
    val models by viewModel.models.collectAsState()
    val benchmarkState by viewModel.benchmarkState.collectAsState()

    var selectedFormatFilter by remember { mutableStateOf<ModelFormat?>(null) }
    var activeParamFilter by remember { mutableStateOf<String?>(null) }
    var showBenchmarkSheet by remember { mutableStateOf(false) }

    val filteredModels = remember(models, selectedFormatFilter, activeParamFilter) {
        models.filter { model ->
            val matchesFormat = selectedFormatFilter == null || model.format == selectedFormatFilter
            val matchesParam = activeParamFilter == null || model.parameterCount.contains(activeParamFilter!!, ignoreCase = true)
            matchesFormat && matchesParam
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

        // Catalog Header
        item {
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "On-Device Model Catalog",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${models.count { it.isDownloaded }} of ${models.size} Ready",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = "Models run 100% offline directly in device RAM using hardware acceleration delegates.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Framework filter chips
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFormatFilter == null,
                    onClick = { selectedFormatFilter = null },
                    label = { Text("All Frameworks") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
                FilterChip(
                    selected = selectedFormatFilter == ModelFormat.GGUF,
                    onClick = {
                        selectedFormatFilter = if (selectedFormatFilter == ModelFormat.GGUF) null else ModelFormat.GGUF
                    },
                    label = { Text("GGUF") }
                )
                FilterChip(
                    selected = selectedFormatFilter == ModelFormat.TFLITE,
                    onClick = {
                        selectedFormatFilter = if (selectedFormatFilter == ModelFormat.TFLITE) null else ModelFormat.TFLITE
                    },
                    label = { Text("TFLite") }
                )
                FilterChip(
                    selected = selectedFormatFilter == ModelFormat.ONNX,
                    onClick = {
                        selectedFormatFilter = if (selectedFormatFilter == ModelFormat.ONNX) null else ModelFormat.ONNX
                    },
                    label = { Text("ONNX") }
                )
            }
        }

        // Model items
        items(filteredModels, key = { it.id }) { model ->
            val compatibility = viewModel.getCompatibility(model)
            ModelItemCard(
                model = model,
                compatibility = compatibility,
                onDownload = { viewModel.downloadModel(model.id) },
                onCancelDownload = { viewModel.cancelDownload(model.id) },
                onDelete = { viewModel.deleteModel(model.id) },
                onSetActive = { viewModel.setActiveModel(model.id) }
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
}
