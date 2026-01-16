package com.cardio.fitbit.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.res.stringResource
import com.cardio.fitbit.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    notificationsEnabled: Boolean,
    onNotificationsChange: (Boolean) -> Unit,
    highThreshold: Int,
    onHighThresholdChange: (Int) -> Unit,
    lowThreshold: Int,
    onLowThresholdChange: (Int) -> Unit,
    syncInterval: Int,
    onSyncIntervalChange: (Int) -> Unit,
    currentLanguage: String,
    onLanguageChange: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Notifications Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.settings_notifications))
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = onNotificationsChange,
                        thumbContent = {
                            Icon(
                                imageVector = if (notificationsEnabled) Icons.Filled.Notifications else Icons.Filled.NotificationsOff,
                                contentDescription = null,
                                modifier = Modifier.size(SwitchDefaults.IconSize)
                            )
                        }
                    )
                }
                
                Divider()

                // High Threshold Slider
                Column {
                    Text(stringResource(R.string.settings_high_threshold, highThreshold), style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = highThreshold.toFloat(),
                        onValueChange = { onHighThresholdChange(it.toInt()) },
                        valueRange = 100f..200f,
                        steps = 19 // (200-100)/5 - 1 ? No, steps = ticks in between
                    )
                }

                // Low Threshold Slider
                Column {
                    Text(stringResource(R.string.settings_low_threshold, lowThreshold), style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = lowThreshold.toFloat(),
                        onValueChange = { onLowThresholdChange(it.toInt()) },
                        valueRange = 30f..100f,
                        steps = 13
                    )
                }
                
                Divider()
                
                // Sync Interval
                Column {
                    val displayText = when (syncInterval) {
                        0 -> stringResource(R.string.interval_never)
                        in 1..59 -> "$syncInterval ${stringResource(R.string.unit_minutes_short)}"
                        else -> "${syncInterval / 60} ${stringResource(R.string.unit_hours_short)}"
                    }
                    Text(stringResource(R.string.settings_sync_interval, displayText), style = MaterialTheme.typography.bodyMedium)
                    
                    // First row: 15min, 30min, 1h
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf(
                            15 to "15${stringResource(R.string.unit_minutes_short)}",
                            30 to "30${stringResource(R.string.unit_minutes_short)}",
                            60 to "1${stringResource(R.string.unit_hours_short)}"
                        ).forEach { (mins, label) ->
                            FilterChip(
                                selected = syncInterval == mins,
                                onClick = { onSyncIntervalChange(mins) },
                                label = { Text(label) }
                            )
                        }
                    }
                    
                    // Second row: 6h, Jamais
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf(
                            360 to "6${stringResource(R.string.unit_hours_short)}",
                            0 to stringResource(R.string.interval_never)
                        ).forEach { (mins, label) ->
                            FilterChip(
                                selected = syncInterval == mins,
                                onClick = { onSyncIntervalChange(mins) },
                                label = { Text(label) }
                            )
                        }
                        // Empty spacer to balance the row
                        Spacer(modifier = Modifier.width(80.dp))
                    }
                }
                
                Divider()
                
                // Language Selector
                Column {
                    Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.bodyMedium)
                    
                    val languages = listOf(
                        "system" to stringResource(R.string.language_system),
                        "en" to stringResource(R.string.language_english),
                        "fr" to stringResource(R.string.language_french)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        languages.forEach { (code, label) ->
                             FilterChip(
                                selected = currentLanguage == code,
                                onClick = { onLanguageChange(code) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ok_button))
            }
        }
    )
}
