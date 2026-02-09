package com.cardio.fitbit.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.res.stringResource
import com.cardio.fitbit.R
import androidx.compose.material.icons.filled.CalendarToday

@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    syncInterval: Int,
    onSyncIntervalChange: (Int) -> Unit,
    currentLanguage: String,
    onLanguageChange: (String) -> Unit,
    currentTheme: String,
    onThemeChange: (String) -> Unit,
    onNavigateToBackup: () -> Unit = {},
    onShowAbout: () -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            ) {
                // Appearance Selector
                Column {
                    Text("Apparence", style = MaterialTheme.typography.bodyMedium)
                    
                    val themes = listOf(
                        "system" to "Système",
                        "light" to "Clair",
                        "dark" to "Sombre"
                    )
                    
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        themes.forEach { (code, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .clickable { onThemeChange(code) },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = currentTheme == code,
                                    onClick = { onThemeChange(code) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
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
                    
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        languages.forEach { (code, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .clickable { onLanguageChange(code) },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = currentLanguage == code,
                                    onClick = { onLanguageChange(code) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
                
                Divider()
                
                // Navigation Buttons
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onNavigateToBackup()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Sauvegarde / Restauration")
                    }
                    
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onShowAbout()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.menu_about))
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
