package com.example.ui.components

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
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DeviceHardwareInfo

data class QuantizationTier(
    val name: String,
    val bitsPerWeight: Float,
    val perplexityDelta: String,
    val qualityLevel: String,
    val color: Color,
    val isRecommended: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuantizationCalculatorSheet(
    deviceHardware: DeviceHardwareInfo,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val parameterSizes = listOf(
        Pair("1.1B", 1.1f),
        Pair("3.2B", 3.2f),
        Pair("7.0B", 7.0f),
        Pair("14.0B", 14.0f)
    )

    var selectedParamSize by remember { mutableStateOf(parameterSizes[1]) } // Default 3.2B

    val tiers = listOf(
        QuantizationTier(
            name = "FP16 (Half Precision)",
            bitsPerWeight = 16.0f,
            perplexityDelta = "+0.0000 (Baseline)",
            qualityLevel = "100% Uncompressed Reference",
            color = Color(0xFF64748B)
        ),
        QuantizationTier(
            name = "Q8_0 (8-bit Quant)",
            bitsPerWeight = 8.5f,
            perplexityDelta = "+0.0004",
            qualityLevel = "Lossless Precision (Near-Zero Loss)",
            color = Color(0xFF8B5CF6)
        ),
        QuantizationTier(
            name = "Q5_K_M (5-bit Medium)",
            bitsPerWeight = 5.5f,
            perplexityDelta = "+0.0122",
            qualityLevel = "High Accuracy (Optimal for 8GB+)",
            color = Color(0xFF06B6D4)
        ),
        QuantizationTier(
            name = "Q4_K_M (4-bit Medium)",
            bitsPerWeight = 4.5f,
            perplexityDelta = "+0.0384",
            qualityLevel = "Best Balance (Speed + Memory)",
            color = Color(0xFF10B981),
            isRecommended = true
        ),
        QuantizationTier(
            name = "Q2_K (2-bit Quant)",
            bitsPerWeight = 2.6f,
            perplexityDelta = "+0.6720",
            qualityLevel = "Ultra-Compact (Degraded Logic)",
            color = Color(0xFFEF4444)
        )
    )

    val totalRamGb = deviceHardware.totalRamBytes / (1024f * 1024f * 1024f)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .testTag("quantization_calculator_sheet")
        ) {
            // Header
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
                            imageVector = Icons.Default.Calculate,
                            contentDescription = null,
                            tint = Color(0xFF06B6D4),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Quantization & Perplexity Matrix",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "RAM sizing and precision trade-offs for on-device SoCs",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Hardware info banner
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Detected Physical RAM: ${"%.1f".format(totalRamGb)} GB",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "${deviceHardware.cpuCores} Cores (${deviceHardware.cpuArchitecture})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Parameter Size Selector
            Text(
                text = "Select Parameter Scale:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                parameterSizes.forEach { param ->
                    val isSelected = selectedParamSize == param
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedParamSize = param },
                        label = { Text(param.first, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(10.dp))

            // Quantization Tier List
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(tiers, key = { it.name }) { tier ->
                    // Formula: (params_in_billions * bitsPerWeight * 1.15 buffer) / 8 + 0.5GB OS KV buffer
                    val rawModelGb = (selectedParamSize.second * 1_000_000_000.0 * tier.bitsPerWeight / 8.0) / (1024.0 * 1024.0 * 1024.0)
                    val totalRequiredGb = rawModelGb + 0.75 // Add context KV cache + OS headroom
                    val fitsInRam = totalRequiredGb <= totalRamGb
                    val headroomGb = totalRamGb - totalRequiredGb

                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        border = if (tier.isRecommended) {
                            androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF10B981))
                        } else null,
                        modifier = Modifier.fillMaxWidth()
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
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(tier.color)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = tier.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (tier.isRecommended) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(0xFF10B981).copy(alpha = 0.2f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "RECOMMENDED",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF10B981)
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = "${tier.bitsPerWeight} bpw",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = tier.color
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = tier.qualityLevel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Model Size: ${"%.2f".format(rawModelGb)} GB",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Perplexity Δ: ${tier.perplexityDelta}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Status badge
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (fitsInRam) Color(0xFF10B981).copy(alpha = 0.15f)
                                            else Color(0xFFEF4444).copy(alpha = 0.15f)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = if (fitsInRam) "✓ Fits (+${"%.1f".format(headroomGb)}GB)" else "✗ OOM Risk (-${"%.1f".format(-headroomGb)}GB)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (fitsInRam) Color(0xFF10B981) else Color(0xFFEF4444)
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
