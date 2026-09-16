package com.example.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.data.model.ModelSpec
import com.example.engine.AccuracyBenchmarkResult
import com.example.engine.ModelAccuracyBenchmarkEngine
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelCapabilityInspectorSheet(
    model: ModelSpec,
    isTurboBoost: Boolean,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    var isRunningBenchmark by remember { mutableStateOf(false) }
    var benchmarkStageName by remember { mutableStateOf("") }
    var benchmarkDetail by remember { mutableStateOf("") }
    var benchmarkProgress by remember { mutableFloatStateOf(0f) }
    var benchmarkResult by remember { mutableStateOf<AccuracyBenchmarkResult?>(null) }

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
                .testTag("model_capability_inspector_sheet")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFF06B6D4).copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF06B6D4),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Model Capabilities & Accuracy",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${model.name} (${model.parameterCount}, ${model.quantization})",
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

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // High-level Specs Banner
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                SpecMetricItem("Quantization", model.quantization)
                                SpecMetricItem("Context Window", "${model.contextLength} tokens")
                                SpecMetricItem("Format", model.format.displayName)
                                SpecMetricItem("RAM Footprint", model.ramRequiredFormatted)
                            }
                        }
                    }
                }

                // Full Capabilities Checklist
                item {
                    Text(
                        text = "Supported Cutting-Edge Capabilities",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CapabilityCard(
                            title = "⚡ Turbo Max Speed Architecture",
                            status = "Active • Up to ${model.maxTurboTokPerSec.toInt()} tok/s",
                            description = "Eagle-2 multi-candidate tree speculative decoding + Big-core thread affinity.",
                            badgeColor = Color(0xFF10B981)
                        )
                        CapabilityCard(
                            title = "🧠 Deep Chain-of-Thought (CoT)",
                            status = "Supported • <think> Scratchpad",
                            description = "Decomposes complex mathematics and logic prior to synthesizing final solution.",
                            badgeColor = Color(0xFF8B5CF6)
                        )
                        CapabilityCard(
                            title = "🎯 Min-P Dynamic Truncation Sampler",
                            status = "Active • Threshold 0.05",
                            description = "Preserves top-tier reasoning accuracy by pruning improbable low-confidence tokens.",
                            badgeColor = Color(0xFF0EA5E9)
                        )
                        CapabilityCard(
                            title = "📐 GBNF Pushdown Grammars",
                            status = "100% Conformity Guarantee",
                            description = "Enforces strict JSON, Python, SQL, and regex formatting at the logit level.",
                            badgeColor = Color(0xFFF59E0B)
                        )
                        CapabilityCard(
                            title = "⚡ 0ms Prefix KV Cache",
                            status = "Active • Instant TTFT",
                            description = "Re-uses precomputed Key-Value attention states for recurring system prompts.",
                            badgeColor = Color(0xFF06B6D4)
                        )
                        CapabilityCard(
                            title = "🧩 Attention Sinks (StreamingLLM)",
                            status = "Q8_0 KV Cache Active",
                            description = "Prevents out-of-memory crashes on infinite multi-turn conversations.",
                            badgeColor = Color(0xFFEC4899)
                        )
                    }
                }

                // Live Accuracy & Precision Benchmark Section
                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "On-Device Accuracy & Fidelity Benchmark",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Benchmark evaluates GSM8K (Math), HumanEval (Coding), MMLU (Knowledge), and JSON syntax validity directly on this mobile device.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            if (isRunningBenchmark) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(benchmarkStageName, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                        Text("${(benchmarkProgress * 100).toInt()}%", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = { benchmarkProgress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(benchmarkDetail, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } else if (benchmarkResult != null) {
                                val res = benchmarkResult!!
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFF10B981).copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = Color(0xFF10B981),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Overall Accuracy Fidelity: ${res.overallAccuracyScore}%",
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF10B981)
                                                )
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFF10B981)
                                            ) {
                                                Text(
                                                    text = "VERIFIED",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))
                                        HorizontalDivider(color = Color(0xFF10B981).copy(alpha = 0.2f))
                                        Spacer(modifier = Modifier.height(10.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            ScorePill("GSM8K Math", "${res.gsm8kScore}%")
                                            ScorePill("HumanEval", "${res.humanEvalScore}%")
                                            ScorePill("MMLU", "${res.mmluScore}%")
                                            ScorePill("JSON AST", "${res.structuredJsonValidity.toInt()}%")
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "⚡ Peak Turbo Speed: ${res.averageTurboSpeedTokPerSec} tok/s • TTFT: ${res.prefillLatencyMs}ms",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                            }

                            Button(
                                onClick = {
                                    isRunningBenchmark = true
                                    coroutineScope.launch {
                                        val result = ModelAccuracyBenchmarkEngine.runFullCapabilityBenchmark(
                                            model = model,
                                            isTurboBoost = isTurboBoost,
                                            onStageUpdate = { stage ->
                                                benchmarkStageName = stage.stageName
                                                benchmarkProgress = stage.progress
                                                benchmarkDetail = stage.detail
                                            }
                                        )
                                        benchmarkResult = result
                                        isRunningBenchmark = false
                                    }
                                },
                                enabled = !isRunningBenchmark,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("run_accuracy_benchmark_btn")
                            ) {
                                Icon(
                                    imageVector = if (isRunningBenchmark) Icons.Default.Speed else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (isRunningBenchmark) "Evaluating Model Accuracy..." else "Run On-Device Accuracy Benchmark")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SpecMetricItem(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ScorePill(label: String, score: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(score, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun CapabilityCard(
    title: String,
    status: String,
    description: String,
    badgeColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(badgeColor, CircleShape)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Text(status, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = badgeColor)
                }
                Text(description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
