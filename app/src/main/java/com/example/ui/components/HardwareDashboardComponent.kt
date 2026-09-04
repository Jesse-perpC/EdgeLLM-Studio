package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ComputeBackend
import com.example.ui.MainViewModel

@Composable
fun HardwareDashboardComponent(
    viewModel: MainViewModel,
    onOpenDiagnostic: () -> Unit,
    modifier: Modifier = Modifier
) {
    val telemetryState by viewModel.telemetryState.collectAsState()
    val accelerationSettings by viewModel.accelerationSettings.collectAsState()
    val models by viewModel.models.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val activeModel = models.firstOrNull { it.isActive }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("hardware_dashboard_component"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Model Selector Carousel
        DashboardModelSelector(
            models = models,
            activeModel = activeModel,
            onSelectModel = { modelId ->
                viewModel.setActiveModel(modelId)
            },
            onDownloadModel = { modelId ->
                viewModel.downloadModel(modelId)
            },
            onOpenDiagnostic = onOpenDiagnostic
        )

        // Telemetry Live Metrics Bar
        TelemetryQuickStatsBar(
            tokensPerSec = telemetryState.currentTokensPerSec,
            ttftMs = telemetryState.timeToFirstTokenMs,
            powerWatts = telemetryState.powerDrawWatts,
            tempCelsius = telemetryState.deviceTemperatureCelsius,
            isGenerating = isGenerating,
            isLivePolling = telemetryState.isLivePolling,
            onTogglePolling = {
                viewModel.setLiveTelemetryPolling(!telemetryState.isLivePolling)
            }
        )

        // Hardware Acceleration (GPU/NPU/CPU) Utilization Chart
        HardwareUtilizationChart(
            history = telemetryState.history,
            currentGpu = telemetryState.currentGpuPercent,
            currentNpu = telemetryState.currentNpuPercent,
            currentCpu = telemetryState.currentCpuPercent
        )

        // Compute Delegate Quick Switcher
        DelegateQuickSwitcher(
            activeBackend = accelerationSettings.computeBackend,
            onSelectBackend = { backend ->
                viewModel.setTelemetryComputeBackend(backend)
            }
        )

        // Memory Consumption Breakdown Chart
        MemoryConsumptionChart(
            breakdown = telemetryState.memoryBreakdown
        )
    }
}

@Composable
fun TelemetryQuickStatsBar(
    tokensPerSec: Float,
    ttftMs: Long,
    powerWatts: Float,
    tempCelsius: Float,
    isGenerating: Boolean,
    isLivePolling: Boolean,
    onTogglePolling: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("telemetry_quick_stats_bar")
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Speed (tok/s)
            StatItem(
                label = "THROUGHPUT",
                value = if (isGenerating) String.format("%.1f", tokensPerSec) else "Standby",
                unit = if (isGenerating) "tok/s" else "",
                color = if (isGenerating) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
            )

            // TTFT Latency
            StatItem(
                label = "PREFILL TTFT",
                value = "$ttftMs",
                unit = "ms",
                color = Color(0xFF06B6D4)
            )

            // Power
            StatItem(
                label = "POWER",
                value = String.format("%.1f", powerWatts),
                unit = "W",
                color = Color(0xFFF59E0B)
            )

            // Temp
            StatItem(
                label = "THERMAL",
                value = String.format("%.1f", tempCelsius),
                unit = "°C",
                color = if (tempCelsius > 40f) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurface
            )

            // Live Pause / Play toggle
            IconButton(
                onClick = onTogglePolling,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .testTag("toggle_live_telemetry_btn")
            ) {
                Icon(
                    imageVector = if (isLivePolling) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isLivePolling) "Pause telemetry" else "Resume telemetry",
                    tint = if (isLivePolling) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun StatItem(
    label: String,
    value: String,
    unit: String,
    color: Color
) {
    Column {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
            if (unit.isNotBlank()) {
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = unit,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun DelegateQuickSwitcher(
    activeBackend: ComputeBackend,
    onSelectBackend: (ComputeBackend) -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Inference Delegate",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    ComputeBackend.GPU_VULKAN to "GPU",
                    ComputeBackend.NPU_NNAPI to "NPU",
                    ComputeBackend.OPENCL to "CL",
                    ComputeBackend.CPU_NEON to "CPU"
                ).forEach { (backend, label) ->
                    val isSelected = activeBackend == backend
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectBackend(backend) },
                        label = { Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }
        }
    }
}
