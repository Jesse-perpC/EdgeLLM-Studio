package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush as GradientBrush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AspectRatioOption
import com.example.data.model.GeneratedImageItem
import com.example.data.model.ImageTaskMode
import com.example.data.model.SdModel
import com.example.ui.MainViewModel
import com.example.ui.components.MaskPainterDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

@Composable
fun ImageTaskScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    var selectedMode by remember { mutableStateOf(ImageTaskMode.TEXT_TO_IMAGE) }
    var selectedModel by remember { mutableStateOf(SdModel.SD_TURBO) }
    var selectedRatio by remember { mutableStateOf(AspectRatioOption.SQUARE) }

    var promptText by remember { mutableStateOf("Cybernetic android terminal with neon holographic glyphs, 8k raytracing, hyperdetailed cinematic render") }
    var negativePrompt by remember { mutableStateOf("blurry, low quality, artifacts, distorted, out of focus") }
    var steps by remember { mutableIntStateOf(15) }
    var cfgScale by remember { mutableFloatStateOf(7.5f) }
    var seed by remember { mutableLongStateOf(424242L) }
    var isRandomSeed by remember { mutableStateOf(true) }

    var isGenerating by remember { mutableStateOf(false) }
    var generationProgress by remember { mutableFloatStateOf(0f) }
    var generationStatusText by remember { mutableStateOf("") }
    var showMaskDialog by remember { mutableStateOf(false) }
    var paintedStrokesCount by remember { mutableIntStateOf(0) }
    var selectedImageForPreview by remember { mutableStateOf<GeneratedImageItem?>(null) }

    val promptPresets = listOf(
        "Futuristic cybernetic edge compute server with glowing neon conduits",
        "Minimalist architectural glass pavilion in misty alpine forest at twilight",
        "Photorealistic macro shot of golden circuit board with microcrystalline solder",
        "Cyberpunk street vendor in rainy Tokyo with vibrant holographic banners"
    )

    val imageHistory = remember {
        mutableStateListOf(
            GeneratedImageItem(
                id = "img-1",
                prompt = "Cybernetic android terminal with neon holographic glyphs",
                mode = ImageTaskMode.TEXT_TO_IMAGE,
                model = SdModel.SD_TURBO,
                steps = 12,
                cfgScale = 7.0f,
                seed = 104293L,
                generationTimeMs = 1240L,
                sampleDrawableColorHex = 0xFF1E1B4B,
                labelText = "SD Turbo • 12 it/s",
                isUpscaled = false
            ),
            GeneratedImageItem(
                id = "img-2",
                prompt = "Minimalist architectural pavilion in alpine forest at dusk",
                mode = ImageTaskMode.SUPER_RESOLUTION,
                model = SdModel.SD_1_5,
                steps = 20,
                cfgScale = 8.0f,
                seed = 981245L,
                generationTimeMs = 2850L,
                sampleDrawableColorHex = 0xFF0F291E,
                labelText = "4× Neural Upscale",
                isUpscaled = true
            )
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("image_task_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header & Hardware Acceleration Card (ToolNeuron :ai_sd engine)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
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
                                    .background(Color(0xFF8B5CF6).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color(0xFF8B5CF6),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "On-Device Stable Diffusion",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "ToolNeuron :ai_sd Engine • Zero Cloud Egress",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981))
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "NPU / QNN Active",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF10B981),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Generates high-fidelity visual assets, inpainting replacements, and 4× super-resolution upscales entirely inside device hardware memory using INT4/FP16 quantized diffusion models.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 2. Task Mode Selector Tabs
        item {
            TabRow(
                selectedTabIndex = selectedMode.ordinal,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            ) {
                ImageTaskMode.entries.forEach { mode ->
                    Tab(
                        selected = selectedMode == mode,
                        onClick = { selectedMode = mode },
                        text = {
                            Text(
                                text = mode.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selectedMode == mode) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        modifier = Modifier.testTag("tab_image_mode_${mode.name.lowercase()}")
                    )
                }
            }
        }

        // 3. Model & Dimension Selection
        item {
            Column {
                Text(
                    text = "Diffusion Model Architecture",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(SdModel.entries) { model ->
                        FilterChip(
                            selected = selectedModel == model,
                            onClick = { selectedModel = model },
                            label = {
                                Column {
                                    Text(model.displayName, fontWeight = FontWeight.SemiBold)
                                    Text("${model.format} • ${model.speedRating}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            modifier = Modifier.testTag("chip_sd_model_${model.id}")
                        )
                    }
                }
            }
        }

        // 4. Inpainting Mask Trigger (when Inpainting mode active)
        if (selectedMode == ImageTaskMode.INPAINTING) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Inpaint Mask Canvas",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (paintedStrokesCount > 0) "$paintedStrokesCount stroke(s) configured" else "No mask drawn yet",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = { showMaskDialog = true },
                            modifier = Modifier.testTag("open_mask_brush_btn")
                        ) {
                            Icon(Icons.Default.Brush, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Draw Mask")
                        }
                    }
                }
            }
        }

        // 5. Prompt Input
        item {
            Column {
                Text(
                    text = "Prompt",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = promptText,
                    onValueChange = { promptText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("image_prompt_input"),
                    placeholder = { Text("Describe the visual you want to generate...") },
                    minLines = 3,
                    maxLines = 5,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Prompt suggestions
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(promptPresets) { preset ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { promptText = preset }
                        ) {
                            Text(
                                text = preset.take(35) + "...",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // 6. Negative Prompt & Settings Accordion
        item {
            Column {
                Text(
                    text = "Negative Prompt",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = negativePrompt,
                    onValueChange = { negativePrompt = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("image_negative_prompt_input"),
                    placeholder = { Text("Elements to avoid...") },
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        // 7. Advanced Sliders: Steps & CFG
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Inference Steps: $steps", style = MaterialTheme.typography.labelMedium)
                        Text(if (selectedModel == SdModel.SD_TURBO) "Turbo (1-4 opt)" else "Standard (15-30 opt)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Slider(
                        value = steps.toFloat(),
                        onValueChange = { steps = it.toInt() },
                        valueRange = 1f..35f,
                        steps = 34,
                        modifier = Modifier.testTag("image_steps_slider")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Guidance Scale (CFG): ${String.format("%.1f", cfgScale)}", style = MaterialTheme.typography.labelMedium)
                        Text("Prompt Adherence", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Slider(
                        value = cfgScale,
                        onValueChange = { cfgScale = it },
                        valueRange = 1.0f..15.0f,
                        modifier = Modifier.testTag("image_cfg_slider")
                    )
                }
            }
        }

        // 8. Generate Action Button & In-Flight Status
        item {
            Column {
                Button(
                    onClick = {
                        if (!isGenerating && promptText.isNotBlank()) {
                            isGenerating = true
                            generationProgress = 0f
                            generationStatusText = "Loading ${selectedModel.displayName} into NPU / GPU memory..."

                            coroutineScope.launch {
                                val simulatedSteps = if (selectedModel == SdModel.SD_TURBO) 4 else steps.coerceAtMost(20)
                                for (i in 1..simulatedSteps) {
                                    delay(120)
                                    generationProgress = i.toFloat() / simulatedSteps.toFloat()
                                    generationStatusText = "Diffusion denoising step $i/$simulatedSteps (${selectedModel.speedRating})..."
                                }
                                delay(200)
                                generationStatusText = "Decoding latent tensor through VAE..."
                                delay(150)

                                val newImage = GeneratedImageItem(
                                    id = "img-${System.currentTimeMillis()}",
                                    prompt = promptText,
                                    negativePrompt = negativePrompt,
                                    mode = selectedMode,
                                    model = selectedModel,
                                    steps = steps,
                                    cfgScale = cfgScale,
                                    seed = if (isRandomSeed) Random.nextLong(100000L, 999999L) else seed,
                                    generationTimeMs = (simulatedSteps * 140L) + 180L,
                                    sampleDrawableColorHex = when (Random.nextInt(4)) {
                                        0 -> 0xFF1E1B4B
                                        1 -> 0xFF064E3B
                                        2 -> 0xFF4C1D95
                                        else -> 0xFF172554
                                    },
                                    labelText = "${selectedModel.displayName} • ${selectedMode.title}",
                                    isUpscaled = (selectedMode == ImageTaskMode.SUPER_RESOLUTION)
                                )
                                imageHistory.add(0, newImage)
                                selectedImageForPreview = newImage
                                isGenerating = false
                                generationStatusText = "Generation complete"
                            }
                        }
                    },
                    enabled = !isGenerating && promptText.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("generate_image_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isGenerating) "Synthesizing Tensors..." else "Generate on Hardware",
                        fontWeight = FontWeight.Bold
                    )
                }

                if (isGenerating) {
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { generationProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .testTag("image_generation_progress")
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = generationStatusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // 9. Output Gallery Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Generated Assets (${imageHistory.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (imageHistory.isNotEmpty()) {
                    Text(
                        text = "100% On-Device Vault",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF10B981)
                    )
                }
            }
        }

        items(imageHistory) { item ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("image_history_card_${item.id}")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Visual Canvas Simulation Area with High-Tech Shader Aesthetics
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                GradientBrush.verticalGradient(
                                    listOf(
                                        Color(item.sampleDrawableColorHex),
                                        Color(0xFF090D16)
                                    )
                                )
                            )
                            .clickable { selectedImageForPreview = item }
                    ) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = if (item.isUpscaled) Icons.Default.HighQuality else Icons.Default.Image,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = item.labelText,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${item.model.displayName} • ${item.generationTimeMs}ms",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }

                        // Top Badges
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.Black.copy(alpha = 0.65f),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                        ) {
                            Text(
                                text = item.mode.title,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.Black.copy(alpha = 0.65f),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "Seed: ${item.seed}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = item.prompt,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${item.steps} steps • CFG ${item.cfgScale}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row {
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(item.prompt))
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Prompt", modifier = Modifier.size(16.dp))
                            }
                            IconButton(
                                onClick = {
                                    imageHistory.remove(item)
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showMaskDialog) {
        MaskPainterDialog(
            onDismiss = { showMaskDialog = false },
            onMaskConfirmed = { count ->
                paintedStrokesCount = count
            }
        )
    }
}
