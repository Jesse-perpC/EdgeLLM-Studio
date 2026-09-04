package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PluginSpec
import com.example.ui.MainViewModel

@Composable
fun PluginPipelineScreen(
    viewModel: MainViewModel,
    onNavigateToExport: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val plugins by viewModel.plugins.collectAsState()
    val lastResult by viewModel.lastPluginResult.collectAsState()

    var showAddCustomPluginDialog by remember { mutableStateOf(false) }
    var testInputText by remember { mutableStateOf("") }
    var selectedPluginForTest by remember { mutableStateOf<PluginSpec?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("plugin_pipeline_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Modular Local Processing Plugins",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Extend the on-device inference runtime with privacy redactors, threat detectors, summarizers, and custom rule transforms without external dependencies.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Installed Plugins (${plugins.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                OutlinedButton(
                    onClick = { showAddCustomPluginDialog = true },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("add_custom_plugin_btn")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Plugin", fontSize = 12.sp)
                }
            }
        }

        items(plugins, key = { it.id }) { plugin ->
            PluginItemCard(
                plugin = plugin,
                onToggle = { viewModel.togglePlugin(plugin.id, it) },
                onRunTest = {
                    selectedPluginForTest = plugin
                    testInputText = when (plugin.id) {
                        "plugin_pii_redactor" -> "Email: ceo@company.com, Phone: +1-415-555-0199, IP: 10.0.0.1 visited system."
                        "plugin_log_anomaly" -> "2026-09-04 ERROR Crash in TensorDelegate at address 0xDEADBEEF\n2026-09-04 WARN Socket timeout on port 8080"
                        "plugin_meeting_notes" -> "Discussed on-device LLM latency. Bob to benchmark Q4_K_M vs Q8_0 weights by Friday."
                        "plugin_code_security" -> "val apiKey = \"sk-test-1234567890abcdef\"\nval query = \"SELECT * FROM users WHERE name = '\" + input + \"'\""
                        else -> "Sample text to match keywords against custom filter."
                    }
                }
            )
        }

        // Display Last Plugin Test Result if available
        lastResult?.let { res ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Execution Result: ${res.pluginId}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${res.durationMs}ms latency",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = res.processedOutput,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(10.dp),
                                fontSize = 11.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { onNavigateToExport(res.processedOutput) }) {
                                Text("Encrypt & Export Result")
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    // Run Test Dialog
    selectedPluginForTest?.let { plugin ->
        AlertDialog(
            onDismissRequest = { selectedPluginForTest = null },
            title = { Text("Run Plugin: ${plugin.name}") },
            text = {
                Column {
                    Text(
                        text = "Enter raw input to process strictly on-device:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = testInputText,
                        onValueChange = { testInputText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                        maxLines = 5
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.runPlugin(plugin.id, testInputText)
                        selectedPluginForTest = null
                    }
                ) {
                    Text("Execute Local Plugin")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedPluginForTest = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add Custom Plugin Dialog
    if (showAddCustomPluginDialog) {
        var name by remember { mutableStateOf("") }
        var category by remember { mutableStateOf("Custom Filtering") }
        var description by remember { mutableStateOf("") }
        var keywordFilter by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddCustomPluginDialog = false },
            title = { Text("Add Modular Plugin") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Plugin Name") },
                        placeholder = { Text("e.g. Audit Header Scanner") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Category") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = keywordFilter,
                        onValueChange = { keywordFilter = it },
                        label = { Text("Filter Keyword / Tag") },
                        placeholder = { Text("e.g. CRITICAL, JWT, or Auth") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            viewModel.addCustomPlugin(
                                name = name,
                                category = category,
                                description = if (description.isNotBlank()) description else "User custom data pipeline filter.",
                                filterKeyword = keywordFilter
                            )
                        }
                        showAddCustomPluginDialog = false
                    }
                ) {
                    Text("Install Plugin")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCustomPluginDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun PluginItemCard(
    plugin: PluginSpec,
    onToggle: (Boolean) -> Unit,
    onRunTest: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = when (plugin.iconKey) {
                            "Shield" -> Icons.Default.Shield
                            "Analytics" -> Icons.Default.Analytics
                            "Notes" -> Icons.Default.Notes
                            "Code" -> Icons.Default.Code
                            else -> Icons.Default.Extension
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = plugin.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${plugin.category} • v${plugin.version}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = plugin.isEnabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.testTag("toggle_plugin_${plugin.id}")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = plugin.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onRunTest,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("test_plugin_${plugin.id}")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Run Test", fontSize = 11.sp)
                }
            }
        }
    }
}
