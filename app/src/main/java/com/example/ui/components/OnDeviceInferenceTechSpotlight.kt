package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ModelFormat

enum class InferenceTechTab(val title: String, val brandColor: Long, val badge: String) {
    AICORE("Android AICore", 0xFF34A853, "Gemini Nano"),
    MEDIAPIPE("MediaPipe GenAI", 0xFF4285F4, "Google Task API"),
    MNN("Alibaba MNN", 0xFFFF6A00, "MNN-LLM")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnDeviceInferenceTechSpotlight(
    onSelectFormatFilter: (ModelFormat) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(InferenceTechTab.AICORE) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(1.dp, Color(selectedTab.brandColor).copy(alpha = 0.45f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("on_device_tech_spotlight_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Title & Quick Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(selectedTab.brandColor)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (selectedTab) {
                                InferenceTechTab.AICORE -> Icons.Default.Security
                                InferenceTechTab.MEDIAPIPE -> Icons.Default.FlashOn
                                InferenceTechTab.MNN -> Icons.Default.Bolt
                            },
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Next-Gen On-Device Runtimes",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(selectedTab.brandColor).copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "3 Engines Integrated",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(selectedTab.brandColor),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = "Android AICore • Google MediaPipe GenAI • Alibaba MNN",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Tab Selector Chips
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InferenceTechTab.values().forEach { tab ->
                    val isSelected = selectedTab == tab
                    val tabColor = Color(tab.brandColor)

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) tabColor.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) tabColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                selectedTab = tab
                                isExpanded = true
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = tab.title,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) tabColor else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = tab.badge,
                                fontSize = 9.sp,
                                color = if (isSelected) tabColor.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Expanded Detailed Tab Content
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 14.dp)) {
                    when (selectedTab) {
                        InferenceTechTab.AICORE -> AndroidAICoreDetailsView(
                            onFilterModels = { onSelectFormatFilter(ModelFormat.ANDROID_AICORE) }
                        )
                        InferenceTechTab.MEDIAPIPE -> MediaPipeGenAIDetailsView(
                            onFilterModels = { onSelectFormatFilter(ModelFormat.MEDIAPIPE_TASK) }
                        )
                        InferenceTechTab.MNN -> AlibabaMnnDetailsView(
                            onFilterModels = { onSelectFormatFilter(ModelFormat.MNN_LLM) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AndroidAICoreDetailsView(onFilterModels: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF34A853).copy(alpha = 0.1f),
            border = BorderStroke(1.dp, Color(0xFF34A853).copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF34A853),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Android AICore System Service Active",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF34A853)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Runs Google's Gemini Nano foundation model natively inside the OS sandbox. Managed via Google Play Services for zero APK footprint and instant NPU/TPU hardware acceleration.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Feature Highlights Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AICoreSpecItem(
                title = "0 MB APK Size",
                subtitle = "Pre-loaded in OS",
                modifier = Modifier.weight(1f)
            )
            AICoreSpecItem(
                title = "Hardware Sandbox",
                subtitle = "Tensor TPU / NPU",
                modifier = Modifier.weight(1f)
            )
            AICoreSpecItem(
                title = "Zero Egress",
                subtitle = "100% Private",
                modifier = Modifier.weight(1f)
            )
        }

        // Native APIs Supported
        Text(
            text = "Supported Android AICore APIs:",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "• Prompt API (Text-to-text general reasoning)\n" +
                    "• Native Summarization API with chunked context\n" +
                    "• Proofreading & Tone Rewriting\n" +
                    "• Smart Reply & Zero-shot Intent Classification",
            style = MaterialTheme.typography.bodySmall,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Button(
            onClick = onFilterModels,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34A853)),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Show Android AICore Models", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

@Composable
private fun MediaPipeGenAIDetailsView(onFilterModels: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF4285F4).copy(alpha = 0.1f),
            border = BorderStroke(1.dp, Color(0xFF4285F4).copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FlashOn,
                        contentDescription = null,
                        tint = Color(0xFF4285F4),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Google MediaPipe LLM Inference API",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4285F4)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Official MediaPipe GenAI runtime for Gemma 2B, Gemma 2 2B, Falcon-RW-1B, StableLM-3B, and Phi-2. Features GPU OpenCL/Vulkan delegate and dynamic LoRA rank adapter merging.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // MediaPipe Highlights
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AICoreSpecItem(
                title = "GPU OpenCL",
                subtitle = "Vulkan Shaders",
                modifier = Modifier.weight(1f)
            )
            AICoreSpecItem(
                title = "Dynamic LoRA",
                subtitle = "On-the-fly rank 4-16",
                modifier = Modifier.weight(1f)
            )
            AICoreSpecItem(
                title = "Dynamic KV",
                subtitle = "INT8 Cache Quant",
                modifier = Modifier.weight(1f)
            )
        }

        // Code architecture note
        Text(
            text = "MediaPipe Tasks SDK Pipeline:",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "• Model Format: `.task` bundles and `.bin` quantized weights\n" +
                    "• Native Asynchronous Streaming: `generateResponseAsync()`\n" +
                    "• Parameter control: MaxTokens, TopK, Temperature, RandomSeed",
            style = MaterialTheme.typography.bodySmall,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Button(
            onClick = onFilterModels,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4)),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Show MediaPipe GenAI Models", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

@Composable
private fun AlibabaMnnDetailsView(onFilterModels: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFFFF6A00).copy(alpha = 0.1f),
            border = BorderStroke(1.dp, Color(0xFFFF6A00).copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = Color(0xFFFF6A00),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Alibaba MNN Mobile Neural Network (MNN-LLM)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF6A00)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Ultra-fast mobile inference engine powering Alibaba's official MNN app. Unmatched prefill speeds with operator fusion, W4A16 weight quantization, and disk-backed prompt caching.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // MNN Highlights
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AICoreSpecItem(
                title = "W4A16 Quant",
                subtitle = "INT8 KV Cache",
                modifier = Modifier.weight(1f)
            )
            AICoreSpecItem(
                title = "Prompt Cache",
                subtitle = "Zero-delay resume",
                modifier = Modifier.weight(1f)
            )
            AICoreSpecItem(
                title = "Dual Profiler",
                subtitle = "Prefill + Decode",
                modifier = Modifier.weight(1f)
            )
        }

        Text(
            text = "MNN-LLM Architecture Capabilities:",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "• Model Architectures: Qwen 2.5 (0.5B-7B), Qwen2-VL, Llama 3.2\n" +
                    "• Assembly Micro-kernels: ARMv8.2-A+ dot-product & FP16 NEON\n" +
                    "• Multimodal Vision: Visual grounding & document OCR via ViT\n" +
                    "• Up to 280 tok/s prefill speed with OpenCL/Vulkan dispatch",
            style = MaterialTheme.typography.bodySmall,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Button(
            onClick = onFilterModels,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6A00)),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Show Alibaba MNN Models", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

@Composable
private fun AICoreSpecItem(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
