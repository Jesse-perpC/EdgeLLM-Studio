package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.GrammarMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrammarSelectorSheet(
    currentMode: GrammarMode,
    currentCustomRegex: String = "",
    onSelectGrammar: (GrammarMode, String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedMode by remember { mutableStateOf(currentMode) }
    var customRegex by remember { mutableStateOf(currentCustomRegex) }

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
                .testTag("grammar_selector_sheet")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Constrained Grammar Decoding",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "GBNF Pushdown Automaton • 100% Syntax Guarantee",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⚡ Constrained sampling filters candidate token logits at runtime to guarantee that outputs strictly conform to the chosen schema or syntax without hallucinations.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            val modes = listOf(
                GrammarOption(
                    mode = GrammarMode.NONE,
                    title = "Freeform Natural Language",
                    subtitle = "Unconstrained conversational tokens and prose",
                    icon = Icons.Default.Edit,
                    color = Color(0xFF64748B)
                ),
                GrammarOption(
                    mode = GrammarMode.JSON_STRICT,
                    title = "Strict RFC-8259 JSON",
                    subtitle = "Guarantees valid JSON object or array for programmatic integration",
                    icon = Icons.Default.Build,
                    color = Color(0xFF10B981)
                ),
                GrammarOption(
                    mode = GrammarMode.STEP_BY_STEP_REASONING,
                    title = "Deep CoT Reasoning (<think>)",
                    subtitle = "Enforces step-by-step thinking scratchpad before final solution",
                    icon = Icons.Default.PlayArrow,
                    color = Color(0xFF8B5CF6)
                ),
                GrammarOption(
                    mode = GrammarMode.PYTHON_CODE,
                    title = "Python 3 Script",
                    subtitle = "Enforces clean, syntax-validated Python code block",
                    icon = Icons.Default.Edit,
                    color = Color(0xFF0EA5E9)
                ),
                GrammarOption(
                    mode = GrammarMode.SQL_QUERY,
                    title = "ANSI SQL Statement",
                    subtitle = "Enforces SELECT / WITH CTE structured queries",
                    icon = Icons.Default.List,
                    color = Color(0xFFF59E0B)
                ),
                GrammarOption(
                    mode = GrammarMode.REGEX_PATTERN,
                    title = "Custom Regular Expression",
                    subtitle = "Constrains token sequence to a custom finite-state regex",
                    icon = Icons.Default.Build,
                    color = Color(0xFFEC4899)
                )
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(modes) { opt ->
                    val isSelected = selectedMode == opt.mode
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedMode = opt.mode },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) opt.color.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ),
                        border = if (isSelected) BorderStroke(1.5.dp, opt.color) else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(opt.color.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = opt.icon,
                                    contentDescription = null,
                                    tint = opt.color,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = opt.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) opt.color else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = opt.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = opt.color,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (selectedMode == GrammarMode.REGEX_PATTERN) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = customRegex,
                    onValueChange = { customRegex = it },
                    label = { Text("Regex Pattern") },
                    placeholder = { Text("^\\d{4}-\\d{2}-\\d{2}$") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = {
                        onSelectGrammar(selectedMode, customRegex)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Apply Grammar Constraint")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private data class GrammarOption(
    val mode: GrammarMode,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val color: Color
)
