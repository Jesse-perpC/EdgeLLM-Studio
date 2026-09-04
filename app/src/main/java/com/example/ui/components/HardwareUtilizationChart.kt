package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.HardwareUsagePoint

@Composable
fun HardwareUtilizationChart(
    history: List<HardwareUsagePoint>,
    currentGpu: Float,
    currentNpu: Float,
    currentCpu: Float,
    modifier: Modifier = Modifier
) {
    var showGpu by remember { mutableStateOf(true) }
    var showNpu by remember { mutableStateOf(true) }
    var showCpu by remember { mutableStateOf(true) }
    var scrubbedPoint by remember { mutableStateOf<HardwareUsagePoint?>(null) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_point")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 4f,
        targetValue = 9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val gpuColor = Color(0xFF06B6D4) // Cyan
    val npuColor = Color(0xFFA855F7) // Violet
    val cpuColor = Color(0xFFF59E0B) // Amber
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("hardware_utilization_chart_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Title & Scrubbed/Latest Readout
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Silicon Utilization Trend",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (scrubbedPoint != null) "Inspecting historical point" else "Real-time hardware acceleration load",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Active Readouts
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (showGpu) {
                        MetricPill(
                            label = "GPU",
                            value = "${(scrubbedPoint?.gpuPercent ?: currentGpu).toInt()}%",
                            color = gpuColor
                        )
                    }
                    if (showNpu) {
                        MetricPill(
                            label = "NPU",
                            value = "${(scrubbedPoint?.npuPercent ?: currentNpu).toInt()}%",
                            color = npuColor
                        )
                    }
                    if (showCpu) {
                        MetricPill(
                            label = "CPU",
                            value = "${(scrubbedPoint?.cpuPercent ?: currentCpu).toInt()}%",
                            color = cpuColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // The Chart Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
                    .pointerInput(history) {
                        detectTapGestures(
                            onPress = { offset ->
                                if (history.isNotEmpty()) {
                                    val index = ((offset.x / size.width) * (history.size - 1))
                                        .toInt()
                                        .coerceIn(0, history.size - 1)
                                    scrubbedPoint = history[index]
                                }
                            },
                            onTap = { scrubbedPoint = null }
                        )
                    }
                    .pointerInput(history) {
                        detectDragGestures(
                            onDrag = { change, _ ->
                                change.consume()
                                if (history.isNotEmpty()) {
                                    val index = ((change.position.x / size.width) * (history.size - 1))
                                        .toInt()
                                        .coerceIn(0, history.size - 1)
                                    scrubbedPoint = history[index]
                                }
                            },
                            onDragEnd = { scrubbedPoint = null },
                            onDragCancel = { scrubbedPoint = null }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                    val width = size.width
                    val height = size.height
                    val paddingBottom = 16f
                    val chartHeight = height - paddingBottom

                    // Draw Horizontal Grid Lines (0%, 25%, 50%, 75%, 100%)
                    val steps = 4
                    val pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                    for (i in 0..steps) {
                        val y = chartHeight * (i.toFloat() / steps)
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(width, y),
                            strokeWidth = 1.5f,
                            pathEffect = pathEffect
                        )
                    }

                    if (history.size >= 2) {
                        val stepX = width / (history.size - 1)

                        fun drawSeries(
                            color: Color,
                            getValue: (HardwareUsagePoint) -> Float
                        ) {
                            val linePath = Path()
                            val areaPath = Path()

                            val points = history.mapIndexed { idx, pt ->
                                val x = idx * stepX
                                val normalizedVal = (getValue(pt) / 100f).coerceIn(0f, 1f)
                                val y = chartHeight - (normalizedVal * chartHeight)
                                Offset(x, y)
                            }

                            // Build smooth Bézier curve
                            linePath.moveTo(points.first().x, points.first().y)
                            areaPath.moveTo(points.first().x, chartHeight)
                            areaPath.lineTo(points.first().x, points.first().y)

                            for (i in 0 until points.size - 1) {
                                val p0 = points[i]
                                val p1 = points[i + 1]
                                val ctrl1 = Offset(p0.x + (p1.x - p0.x) / 2f, p0.y)
                                val ctrl2 = Offset(p0.x + (p1.x - p0.x) / 2f, p1.y)

                                linePath.cubicTo(ctrl1.x, ctrl1.y, ctrl2.x, ctrl2.y, p1.x, p1.y)
                                areaPath.cubicTo(ctrl1.x, ctrl1.y, ctrl2.x, ctrl2.y, p1.x, p1.y)
                            }

                            areaPath.lineTo(points.last().x, chartHeight)
                            areaPath.close()

                            // Fill gradient area under curve
                            drawPath(
                                path = areaPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0.01f)),
                                    startY = 0f,
                                    endY = chartHeight
                                )
                            )

                            // Stroke the line
                            drawPath(
                                path = linePath,
                                color = color,
                                style = Stroke(width = 3.5f, cap = StrokeCap.Round)
                            )

                            // Draw the current point pulse
                            val lastPt = points.last()
                            drawCircle(
                                color = color.copy(alpha = 0.35f),
                                radius = pulseScale,
                                center = lastPt
                            )
                            drawCircle(
                                color = color,
                                radius = 4f,
                                center = lastPt
                            )
                        }

                        if (showGpu) drawSeries(gpuColor) { it.gpuPercent }
                        if (showNpu) drawSeries(npuColor) { it.npuPercent }
                        if (showCpu) drawSeries(cpuColor) { it.cpuPercent }

                        // Draw scrubber line if user is touching
                        scrubbedPoint?.let { sp ->
                            val scrubIndex = history.indexOf(sp).coerceAtLeast(0)
                            val scrubX = scrubIndex * stepX
                            drawLine(
                                color = Color.White.copy(alpha = 0.7f),
                                start = Offset(scrubX, 0f),
                                end = Offset(scrubX, chartHeight),
                                strokeWidth = 2f
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Interactive Toggle Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Past 25 seconds (1s poll)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = showGpu,
                        onClick = { showGpu = !showGpu },
                        label = { Text("GPU", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = gpuColor.copy(alpha = 0.2f),
                            selectedLabelColor = gpuColor
                        )
                    )
                    FilterChip(
                        selected = showNpu,
                        onClick = { showNpu = !showNpu },
                        label = { Text("NPU", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = npuColor.copy(alpha = 0.2f),
                            selectedLabelColor = npuColor
                        )
                    )
                    FilterChip(
                        selected = showCpu,
                        onClick = { showCpu = !showCpu },
                        label = { Text("CPU", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = cpuColor.copy(alpha = 0.2f),
                            selectedLabelColor = cpuColor
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun MetricPill(
    label: String,
    value: String,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = "$label: $value",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}
