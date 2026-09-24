package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

data class MaskStroke(
    val points: List<Offset>,
    val strokeWidth: Float
)

@Composable
fun MaskPainterDialog(
    onDismiss: () -> Unit,
    onMaskConfirmed: (strokeCount: Int) -> Unit
) {
    val strokes = remember { mutableStateListOf<MaskStroke>() }
    var currentStrokePoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var brushSize by remember { mutableFloatStateOf(28f) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .testTag("mask_painter_dialog")
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Inpaint Mask Brush",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Paint over the area you want to replace",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = {
                            strokes.clear()
                        },
                        modifier = Modifier.testTag("clear_mask_btn")
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear Mask")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Canvas area for drawing inpaint mask
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.0f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0F172A))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                ) {
                    Canvas(
                        modifier = Modifier
                            .matchParentSize()
                            .pointerInput(brushSize) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        currentStrokePoints = listOf(offset)
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        currentStrokePoints = currentStrokePoints + change.position
                                    },
                                    onDragEnd = {
                                        if (currentStrokePoints.isNotEmpty()) {
                                            strokes.add(MaskStroke(currentStrokePoints, brushSize))
                                            currentStrokePoints = emptyList()
                                        }
                                    },
                                    onDragCancel = {
                                        currentStrokePoints = emptyList()
                                    }
                                )
                            }
                            .testTag("mask_drawing_canvas")
                    ) {
                        // Draw guide background grid
                        val step = size.width / 8f
                        for (i in 0..8) {
                            drawLine(
                                color = Color.White.copy(alpha = 0.05f),
                                start = Offset(i * step, 0f),
                                end = Offset(i * step, size.height),
                                strokeWidth = 1f
                            )
                            drawLine(
                                color = Color.White.copy(alpha = 0.05f),
                                start = Offset(0f, i * step),
                                end = Offset(size.width, i * step),
                                strokeWidth = 1f
                            )
                        }

                        // Draw completed strokes
                        for (stroke in strokes) {
                            val pts = stroke.points
                            val ptCount = pts.size
                            if (ptCount > 1) {
                                for (i in 0 until ptCount - 1) {
                                    drawLine(
                                        color = Color(0xFFFF5252).copy(alpha = 0.85f),
                                        start = pts[i],
                                        end = pts[i + 1],
                                        strokeWidth = stroke.strokeWidth,
                                        cap = StrokeCap.Round
                                    )
                                }
                            } else if (ptCount == 1) {
                                drawCircle(
                                    color = Color(0xFFFF5252).copy(alpha = 0.85f),
                                    radius = stroke.strokeWidth / 2f,
                                    center = pts[0]
                                )
                            }
                        }

                        // Draw in-progress stroke
                        val activePts = currentStrokePoints
                        val activeCount = activePts.size
                        if (activeCount > 1) {
                            for (i in 0 until activeCount - 1) {
                                drawLine(
                                    color = Color(0xFFFF5252),
                                    start = activePts[i],
                                    end = activePts[i + 1],
                                    strokeWidth = brushSize,
                                    cap = StrokeCap.Round
                                )
                            }
                        }
                    }

                    // Stroke indicator count
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "${strokes.size} strokes painted",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Brush size controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Brush,
                        contentDescription = "Brush Size",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Size: ${brushSize.toInt()}px",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Slider(
                        value = brushSize,
                        onValueChange = { brushSize = it },
                        valueRange = 10f..60f,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("mask_brush_size_slider")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("cancel_mask_btn")
                    ) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onMaskConfirmed(strokes.size)
                            onDismiss()
                        },
                        modifier = Modifier.testTag("confirm_mask_btn")
                    ) {
                        Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Apply Mask")
                    }
                }
            }
        }
    }
}
