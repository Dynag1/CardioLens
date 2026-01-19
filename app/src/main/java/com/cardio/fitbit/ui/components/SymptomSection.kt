package com.cardio.fitbit.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.cardio.fitbit.R

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SymptomSection(
    symptoms: String?,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.label_symptoms),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
            
            TextButton(onClick = { showDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.btn_add_note))
            }
        }
        
        if (!symptoms.isNullOrEmpty()) {
            Spacer(Modifier.height(8.dp))
            val tags = remember(symptoms) { symptoms.split(",") }
            
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tags.forEach { tag ->
                     if (tag.isNotBlank()) {
                         SuggestionChip(
                             onClick = { showDialog = true },
                             label = { Text(tag) },
                             colors = SuggestionChipDefaults.suggestionChipColors(
                                 containerColor = MaterialTheme.colorScheme.primaryContainer,
                                 labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                             ),
                             border = null
                         )
                     }
                }
            }
        }
    }
    
    if (showDialog) {
        SymptomDialog(
            initialSelection = symptoms ?: "",
            onDismiss = { showDialog = false },
            onSave = { 
                onSave(it)
                showDialog = false
            }
        )
    }
}

@Composable
fun SymptomDialog(
    initialSelection: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var currentSelection by remember { mutableStateOf(initialSelection) }
    var noteText by remember { mutableStateOf("") }
    
    // Parse initial
    val selectedTags = remember(initialSelection) {
        initialSelection.split(",").filter { it.isNotBlank() }.toMutableStateList()
    }
    
    // Available default tags (Resource IDs would be better but keeping simple logic here)
    // We will use string resources for display but store keys or raw strings
    val options = listOf(
        stringResource(R.string.symptom_sick),
        stringResource(R.string.symptom_fever),
        stringResource(R.string.symptom_fatigue),
        stringResource(R.string.symptom_stress),
        stringResource(R.string.symptom_headache),
        stringResource(R.string.symptom_nausea)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_symptom_title)) },
        text = {
            Column {
                Text(stringResource(R.string.dialog_symptom_subtitle), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    options.forEach { option ->
                        val isSelected = selectedTags.contains(option)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (isSelected) selectedTags.remove(option)
                                else selectedTags.add(option)
                            },
                            label = { Text(option) }
                        )
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text(stringResource(R.string.label_custom_note)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val finalSet = selectedTags.toMutableSet()
                    if (noteText.isNotBlank()) {
                         finalSet.add(noteText.trim())
                    }
                    onSave(finalSet.joinToString(","))
                }
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
