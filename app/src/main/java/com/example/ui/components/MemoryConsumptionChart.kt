package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MemoryConsumptionBreakdown

@Composable
fun MemoryConsumptionChart(
    breakdown: MemoryConsumptionBreakdown,
    modifier: Modifier = Modifier
) {
    val modelColor = Color(0xFF06B6D4) // Cyan (Model Weights)
    val kvColor = Color(0xFFA855F7)    // Violet (KV Cache)
    val osColor = Color(0xFF64748B)    // Slate (OS/App buffer)
    val headroomColor = Color(0xFF10B981) // Green (Free Headroom)

    val animatedUsedPercent by animateFloatAsState(
        targetValue = breakdown.usedPercentage,
        animationSpec = tween(600),
        label = "animated_used_percent"
    )

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("memory_consumption_chart_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "LLM Memory Allocation",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "VRAM, Attention KV Cache & Silicon Headroom",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // OOM Safety status badge
                val isLowHeadroom = breakdown.availableHeadroomGb < 0.8f
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isLowHeadroom) Color(0xFFEF4444).copy(alpha = 0.15f) else Color(0xFF10B981).copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isLowHeadroom) Icons.Default.Warning else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (isLowHeadroom) Color(0xFFEF4444) else Color(0xFF10B981),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isLowHeadroom) "Tight Margin" else "Zero OOM Risk",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isLowHeadroom) Color(0xFFEF4444) else Color(0xFF10B981)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Donut Chart & Key Metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Donut Ring Gauge
                Box(
                    modifier = Modifier.size(110.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(100.dp)) {
                        val strokeWidth = 14f
                        val totalGb = breakdown.totalRamGb.coerceAtLeast(1f)

                        val modelAngle = (breakdown.modelWeightsGb / totalGb) * 360f
                        val kvAngle = (breakdown.kvCacheGb / totalGb) * 360f
                        val osAngle = (breakdown.systemOsGb / totalGb) * 360f
                        val headroomAngle = (breakdown.availableHeadroomGb / totalGb) * 360f

                        var startAngle = -90f

                        // Background track
                        drawCircle(
                            color = Color.DarkGray.copy(alpha = 0.2f),
                            style = Stroke(width = strokeWidth)
                        )

                        // Model weights arc
                        drawArc(
                            color = modelColor,
                            startAngle = startAngle,
                            sweepAngle = modelAngle,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                        startAngle += modelAngle

                        // KV Cache arc
                        drawArc(
                            color = kvColor,
                            startAngle = startAngle,
                            sweepAngle = kvAngle,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                        startAngle += kvAngle

                        // OS arc
                        drawArc(
                            color = osColor,
                            startAngle = startAngle,
                            sweepAngle = osAngle,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                        startAngle += osAngle

                        // Headroom arc
                        drawArc(
                            color = headroomColor,
                            startAngle = startAngle,
                            sweepAngle = headroomAngle,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${animatedUsedPercent.toInt()}%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "RAM LOAD",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 8.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Breakdown list
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MemoryLegendRow(
                        label = "Model Weights (VRAM)",
                        value = "${String.format("%.2f", breakdown.modelWeightsGb)} GB",
                        color = modelColor
                    )
                    MemoryLegendRow(
                        label = "KV Attention Cache",
                        value = "${(breakdown.kvCacheBytes / (1024 * 1024))} MB",
                        color = kvColor
                    )
                    MemoryLegendRow(
                        label = "System & Runtime Buffer",
                        value = "${String.format("%.1f", breakdown.systemOsGb)} GB",
                        color = osColor
                    )
                    MemoryLegendRow(
                        label = "Free RAM Headroom",
                        value = "+${String.format("%.1f", breakdown.availableHeadroomGb)} GB",
                        color = headroomColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Stacked Linear Progress Bar
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Total System Memory",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${String.format("%.1f", breakdown.usedRamGb)} / ${String.format("%.1f", breakdown.totalRamGb)} GB",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))

                // Proportional stacked bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color.DarkGray.copy(alpha = 0.2f))
                ) {
                    val totalGb = breakdown.totalRamGb.coerceAtLeast(1f)
                    val modelWeight = (breakdown.modelWeightsGb / totalGb).coerceIn(0.01f, 1f)
                    val kvWeight = (breakdown.kvCacheGb / totalGb).coerceIn(0.01f, 1f)
                    val osWeight = (breakdown.systemOsGb / totalGb).coerceIn(0.01f, 1f)
                    val headroomWeight = (breakdown.availableHeadroomGb / totalGb).coerceIn(0.01f, 1f)

                    Box(modifier = Modifier.weight(modelWeight).height(10.dp).background(modelColor))
                    Box(modifier = Modifier.weight(kvWeight).height(10.dp).background(kvColor))
                    Box(modifier = Modifier.weight(osWeight).height(10.dp).background(osColor))
                    Box(modifier = Modifier.weight(headroomWeight).height(10.dp).background(headroomColor))
                }
            }
        }
    }
}

@Composable
fun MemoryLegendRow(
    label: String,
    value: String,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp
        )
    }
}
