package com.perpcorp.edgellm.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.perpcorp.edgellm.ui.theme.LocalEdgeGradients
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private data class CapabilityNode(
    val label: String,
    val icon: ImageVector,
    val active: Boolean,
    val detail: String,
)

/**
 * Hero visual for the Aether Grid theme: an orbital capability lattice whose
 * nodes are lit from real engine state (model, assistant, API server, vault,
 * tool grid) with live throughput telemetry at the core.
 */
@Composable
fun CapabilityLatticeCard(
    modelName: String?,
    modelReady: Boolean,
    apiRunning: Boolean,
    assistantReady: Boolean,
    tokensPerSecond: Float,
    timeToFirstTokenMs: Long,
    gpuPercent: Float,
    onOpenModels: () -> Unit,
    onOpenApi: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gradients = LocalEdgeGradients.current
    val accent = gradients.accentA
    val accentB = gradients.accentB
    val accentC = gradients.accentC
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val transition = rememberInfiniteTransition(label = "capability-lattice")
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 36000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "lattice-spin",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "lattice-pulse",
    )

    val nodes = listOf(
        CapabilityNode(
            label = "LLM",
            icon = Icons.Default.Memory,
            active = modelReady,
            detail = modelName?.take(14) ?: "No model",
        ),
        CapabilityNode(
            label = "VISION",
            icon = Icons.Default.Image,
            active = modelReady,
            detail = if (modelReady) "Multimodal" else "Standby",
        ),
        CapabilityNode(
            label = "VOICE",
            icon = Icons.Default.Mic,
            active = assistantReady,
            detail = if (assistantReady) "Assistant" else "Standby",
        ),
        CapabilityNode(
            label = "API",
            icon = Icons.Default.Hub,
            active = apiRunning,
            detail = if (apiRunning) "Serving" else "Offline",
        ),
        CapabilityNode(
            label = "VAULT",
            icon = Icons.Default.Lock,
            active = true,
            detail = "Encrypted",
        ),
        CapabilityNode(
            label = "TOOLS",
            icon = Icons.Default.AutoAwesome,
            active = true,
            detail = "Agent grid",
        ),
    )

    val shape = RoundedCornerShape(30.dp)
    val borderBrush = Brush.sweepGradient(
        listOf(
            accent.copy(alpha = 0.55f),
            accentB.copy(alpha = 0.18f),
            Color.Transparent,
            accentC.copy(alpha = 0.42f),
            accent.copy(alpha = 0.55f),
        ),
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.86f else 0.94f))
            .border(1.dp, borderBrush, shape),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(14.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(accent),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CAPABILITY LATTICE",
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                            letterSpacing = 1.8.sp,
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (modelReady) "Local intelligence online" else "Awaiting local model",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                StatusPill(active = modelReady, color = if (modelReady) accent else MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.height(10.dp))

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(296.dp),
            ) {
                val radius = minOf(maxWidth, maxHeight) / 2 - 46.dp

                Canvas(modifier = Modifier.matchParentSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val outer = radius.toPx()
                    val dash = PathEffect.dashPathEffect(floatArrayOf(12f, 14f), spin / 8f)

                    drawCircle(
                        color = accent.copy(alpha = 0.07f),
                        radius = outer,
                        center = center,
                        style = Stroke(width = 1.dp.toPx()),
                    )
                    for (ring in 1..2) {
                        drawCircle(
                            color = accent.copy(alpha = if (ring == 1) 0.12f else 0.09f),
                            radius = outer * (0.4f + ring * 0.26f),
                            center = center,
                            style = Stroke(width = 1.dp.toPx(), pathEffect = dash),
                        )
                    }

                    nodes.forEachIndexed { index, node ->
                        val angle = (-90.0 + index * 60.0) * PI / 180.0
                        val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
                        val start = center + direction * outer * 0.46f
                        val end = center + direction * outer * 0.86f
                        drawLine(
                            color = if (node.active) accent.copy(alpha = 0.34f) else accent.copy(alpha = 0.08f),
                            start = start,
                            end = end,
                            strokeWidth = if (node.active) 1.6f else 1f,
                        )
                        drawCircle(
                            color = if (node.active) accent else accent.copy(alpha = 0.22f),
                            radius = if (node.active) 2.4.dp.toPx() * (0.8f + pulse * 0.4f) else 2.dp.toPx(),
                            center = end,
                        )
                    }

                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(Color.Transparent, accent.copy(alpha = 0.55f), Color.Transparent),
                        ),
                        startAngle = spin,
                        sweepAngle = 120f,
                        useCenter = false,
                        topLeft = Offset(center.x - outer, center.y - outer),
                        size = Size(outer * 2f, outer * 2f),
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(122.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    accent.copy(alpha = 0.26f + pulse * 0.14f),
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                                ),
                            ),
                        )
                        .border(
                            width = 1.dp,
                            color = accent.copy(alpha = 0.32f + pulse * 0.3f),
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "EDGE",
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                            letterSpacing = 2.sp,
                        )
                        Text(
                            text = if (modelReady) String.format(Locale.US, "%.1f", tokensPerSecond) else "--",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "TOK/S",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                nodes.forEachIndexed { index, node ->
                    val angle = (-90.0 + index * 60.0) * PI / 180.0
                    val x = radius * cos(angle).toFloat()
                    val y = radius * sin(angle).toFloat()
                    CapabilityNodePill(
                        node = node,
                        tint = accent,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(x = x, y = y),
                        onClick = when (node.label) {
                            "LLM" -> onOpenModels
                            "API" -> onOpenApi
                            else -> null
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MetricCell(
                    label = "LATENCY",
                    value = "$timeToFirstTokenMs ms",
                    modifier = Modifier.weight(1f),
                )
                MetricCell(
                    label = "GPU LOAD",
                    value = "${gpuPercent.toInt()}%",
                    modifier = Modifier.weight(1f),
                )
                MetricCell(
                    label = "NODES",
                    value = "${nodes.count { it.active }}/${nodes.size}",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StatusPill(active: Boolean, color: Color) {
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (active) "CORE ONLINE" else "CORE IDLE",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                color = color,
            )
        }
    }
}

@Composable
private fun CapabilityNodePill(
    node: CapabilityNode,
    tint: Color,
    modifier: Modifier,
    onClick: (() -> Unit)?,
) {
    val nodeTint = if (node.active) tint else MaterialTheme.colorScheme.onSurfaceVariant
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .widthIn(min = 72.dp, max = 98.dp)
            .clip(shape)
            .background(
                if (node.active) tint.copy(alpha = 0.13f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            )
            .border(
                width = 1.dp,
                color = nodeTint.copy(alpha = if (node.active) 0.42f else 0.2f),
                shape = shape,
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = node.icon,
            contentDescription = node.label,
            tint = nodeTint,
            modifier = Modifier.size(14.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(
                text = node.label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                letterSpacing = 1.1.sp,
                color = nodeTint,
                maxLines = 1,
            )
            Text(
                text = node.detail,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 8.sp,
                fontFamily = FontFamily.Default,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MetricCell(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 8.5.sp,
                letterSpacing = 1.2.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
