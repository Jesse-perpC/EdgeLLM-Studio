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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.HfLibraryFilter
import com.example.data.model.HfModelItem
import com.example.data.model.HfParamFilter
import com.example.data.model.HfPipelineTag
import com.example.data.model.ModelFormat
import com.example.ui.MainViewModel

@Composable
fun HfExplorerScreen(
    viewModel: MainViewModel,
    onNavigateToChat: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedPipeline by remember { mutableStateOf(HfPipelineTag.ALL) }
    var selectedLibrary by remember { mutableStateOf(HfLibraryFilter.ALL) }
    var selectedParamSize by remember { mutableStateOf(HfParamFilter.ALL) }

    // Curated catalog of verified mobile-compatible Hugging Face on-device models
    val hfModels = remember {
        mutableStateListOf(
            HfModelItem(
                id = "hf_llama32_1b",
                modelId = "bartowski/Llama-3.2-1B-Instruct-GGUF",
                author = "bartowski",
                modelName = "Llama 3.2 1B Instruct",
                pipelineTag = HfPipelineTag.TEXT_GENERATION,
                library = "GGUF",
                quantizations = listOf("Q4_K_M", "Q8_0", "Q2_K"),
                downloads = 148200,
                likes = 1240,
                parameterCount = "1.23B",
                sizeFormatted = "762 MB",
                license = "Llama 3.2 Community",
                isGated = false,
                description = "Meta's lightweight mobile-first multilingual instruction-tuned model. Highly capable in tool calling and reasoning."
            ),
            HfModelItem(
                id = "hf_gemma2_2b",
                modelId = "google/gemma-2-2b-it-mediapipe",
                author = "google",
                modelName = "Gemma 2 2B IT",
                pipelineTag = HfPipelineTag.TEXT_GENERATION,
                library = "MediaPipe",
                quantizations = listOf("INT4", "INT8", "FP16"),
                downloads = 94500,
                likes = 890,
                parameterCount = "2.61B",
                sizeFormatted = "1.42 GB",
                license = "Gemma Open",
                isGated = false,
                description = "Google DeepMind's Gemma 2 architecture optimized with LiteRT for GPU Vulkan and NPU on Android devices."
            ),
            HfModelItem(
                id = "hf_qwen25_15b",
                modelId = "Qwen/Qwen2.5-1.5B-Instruct-MNN",
                author = "Qwen",
                modelName = "Qwen 2.5 1.5B Instruct",
                pipelineTag = HfPipelineTag.TEXT_GENERATION,
                library = "MNN",
                quantizations = listOf("W4A16", "FP16"),
                downloads = 112000,
                likes = 1105,
                parameterCount = "1.54B",
                sizeFormatted = "980 MB",
                license = "Apache-2.0",
                isGated = false,
                description = "Exceptional multilingual reasoning across 29 languages with Alibaba MNN mobile neural network acceleration."
            ),
            HfModelItem(
                id = "hf_afrislm_translate",
                modelId = "TranslatePsy/AfriSLM-0.8B-Q4_K_M",
                author = "TranslatePsy",
                modelName = "AfriSLM 0.8B Translation",
                pipelineTag = HfPipelineTag.TEXT2TEXT,
                library = "GGUF",
                quantizations = listOf("Q4_K_M", "Q5_K_M"),
                downloads = 28400,
                likes = 340,
                parameterCount = "0.8B",
                sizeFormatted = "480 MB",
                license = "MIT",
                isGated = false,
                description = "Specialized high-speed neural translator for 40+ African languages and English pairs. Zero cloud latency."
            ),
            HfModelItem(
                id = "hf_paligemma_3b",
                modelId = "google/paligemma-3b-pt-224-task",
                author = "google",
                modelName = "PaliGemma 3B Multimodal",
                pipelineTag = HfPipelineTag.VLM,
                library = "MediaPipe",
                quantizations = listOf("INT4", "INT8"),
                downloads = 67300,
                likes = 760,
                parameterCount = "2.9B",
                sizeFormatted = "1.85 GB",
                license = "Gemma",
                isGated = false,
                description = "Vision-Language model pairing SigLIP image encoder with Gemma 2B for local captioning and visual inspection."
            ),
            HfModelItem(
                id = "hf_bge_small_en",
                modelId = "BAAI/bge-small-en-v1.5-onnx",
                author = "BAAI",
                modelName = "BGE Small Embedding",
                pipelineTag = HfPipelineTag.FEATURE_EXTRACTION,
                library = "ONNX",
                quantizations = listOf("FP16", "INT8"),
                downloads = 432000,
                likes = 2150,
                parameterCount = "33M",
                sizeFormatted = "67 MB",
                license = "MIT",
                isGated = false,
                description = "State-of-the-art embedding model for local RAG chunk indexing and sub-10ms semantic retrieval."
            )
        )
    }

    val filteredModels = hfModels.filter { model ->
        val matchesQuery = searchQuery.isBlank() ||
                model.modelName.contains(searchQuery, ignoreCase = true) ||
                model.author.contains(searchQuery, ignoreCase = true) ||
                model.description.contains(searchQuery, ignoreCase = true)

        val matchesPipeline = selectedPipeline == HfPipelineTag.ALL || model.pipelineTag == selectedPipeline
        val matchesLibrary = selectedLibrary == HfLibraryFilter.ALL || model.library.equals(selectedLibrary.name, ignoreCase = true)
        val matchesParam = when (selectedParamSize) {
            HfParamFilter.ALL -> true
            HfParamFilter.TINY -> model.parameterCount.contains("M", ignoreCase = true) || model.parameterCount.startsWith("0.")
            HfParamFilter.BALANCED -> model.parameterCount.startsWith("1.") || model.parameterCount.startsWith("2.") || model.parameterCount.startsWith("3.")
            HfParamFilter.LARGE -> model.parameterCount.startsWith("7.") || model.parameterCount.startsWith("8.") || model.parameterCount.startsWith("9.")
        }

        matchesQuery && matchesPipeline && matchesLibrary && matchesParam
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("hf_explorer_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header Banner
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
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
                                    .background(Color(0xFFFFD21E).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🤗", fontSize = 18.sp)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "HuggingFace Hub Explorer",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Direct On-Device GGUF, MNN & MediaPipe Models",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = "${hfModels.size} Models",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2. Search Field
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hf_search_input"),
                placeholder = { Text("Search by model, author, or architecture...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
        }

        // 3. Pipeline Filter Chips
        item {
            Column {
                Text("Pipeline Type", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(HfPipelineTag.entries) { tag ->
                        FilterChip(
                            selected = selectedPipeline == tag,
                            onClick = { selectedPipeline = tag },
                            label = { Text(tag.label) },
                            modifier = Modifier.testTag("chip_hf_pipeline_${tag.name.lowercase()}")
                        )
                    }
                }
            }
        }

        // 4. Library & Size Filter Chips
        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Framework", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(HfLibraryFilter.entries) { lib ->
                            FilterChip(
                                selected = selectedLibrary == lib,
                                onClick = { selectedLibrary = lib },
                                label = { Text(lib.label, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }
        }

        // 5. Model Items List
        items(filteredModels) { model ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hf_model_card_${model.id}")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = model.author,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = model.modelName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = model.library,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = model.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Metrics row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⚡ ${model.parameterCount}", style = MaterialTheme.typography.labelSmall)
                        Text("💾 ${model.sizeFormatted}", style = MaterialTheme.typography.labelSmall)
                        Text("⬇ ${model.downloads / 1000}k", style = MaterialTheme.typography.labelSmall)
                        Text("📜 ${model.license}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Quantization badges
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(model.quantizations) { quant ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Text(
                                    text = quant,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Download Action
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (model.isDownloaded) "Downloaded • Active" else "Ready to Ingest",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (model.isDownloaded) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Button(
                            onClick = {
                                val format = when (model.library.uppercase()) {
                                    "GGUF" -> ModelFormat.GGUF
                                    "MNN" -> ModelFormat.MNN_LLM
                                    "MEDIAPIPE" -> ModelFormat.MEDIAPIPE_TASK
                                    "ONNX" -> ModelFormat.ONNX
                                    else -> ModelFormat.GGUF
                                }
                                viewModel.addCustomModelFromUrl(
                                    name = model.modelName,
                                    url = "https://huggingface.co/${model.modelId}",
                                    format = format,
                                    parameterCount = model.parameterCount,
                                    quantization = model.quantizations.firstOrNull() ?: "Q4_K_M"
                                )
                                val index = hfModels.indexOf(model)
                                if (index != -1) {
                                    hfModels[index] = model.copy(isDownloaded = true)
                                }
                            },
                            enabled = !model.isDownloaded,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (model.isDownloaded) Color(0xFF10B981) else MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("download_hf_model_btn_${model.id}")
                        ) {
                            Icon(
                                if (model.isDownloaded) Icons.Default.CheckCircle else Icons.Default.CloudDownload,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (model.isDownloaded) "Installed" else "1-Tap Download")
                        }
                    }
                }
            }
        }
    }
}
