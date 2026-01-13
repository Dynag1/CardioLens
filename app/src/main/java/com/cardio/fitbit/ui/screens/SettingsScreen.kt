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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    highThreshold: Int,
    lowThreshold: Int,
    notificationsEnabled: Boolean,
    syncInterval: Int,
    onHighThresholdChange: (Int) -> Unit,
    onLowThresholdChange: (Int) -> Unit,
    onNotificationsChange: (Boolean) -> Unit,
    onSyncIntervalChange: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Paramètres") },
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
                    Text("Notifications")
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
                    Text("Seuil FC Élevé: $highThreshold BPM", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = highThreshold.toFloat(),
                        onValueChange = { onHighThresholdChange(it.toInt()) },
                        valueRange = 100f..200f,
                        steps = 19 // (200-100)/5 - 1 ? No, steps = ticks in between
                    )
                }

                // Low Threshold Slider
                Column {
                    Text("Seuil FC Bas: $lowThreshold BPM", style = MaterialTheme.typography.bodyMedium)
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
                    Text("Intervalle de Synchro: $syncInterval min", style = MaterialTheme.typography.bodyMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf(5, 10, 15, 30).forEach { mins ->
                            FilterChip(
                                selected = syncInterval == mins,
                                onClick = { onSyncIntervalChange(mins) },
                                label = { Text("${mins}m") }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}
