package com.cardio.fitbit.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cardio.fitbit.R
import com.cardio.fitbit.utils.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthSettingsDialog(
    onDismiss: () -> Unit,
    notificationsEnabled: Boolean,
    onNotificationsChange: (Boolean) -> Unit,
    highThreshold: Int,
    onHighThresholdChange: (Int) -> Unit,
    lowThreshold: Int,
    onLowThresholdChange: (Int) -> Unit,
    sleepGoalMinutes: Int,
    onSleepGoalChange: (Int) -> Unit,
    weeklyWorkoutGoal: Int,
    onWeeklyWorkoutGoalChange: (Int) -> Unit,
    dailyStepGoal: Int,
    onDailyStepGoalChange: (Int) -> Unit,
    dateOfBirthState: Long?,
    onDateOfBirthChange: (Long) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Paramètres Santé") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
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
                
                // Show thresholds only if notifications are enabled
                if (notificationsEnabled) {
                    Divider()

                    // High Threshold Slider
                    Column {
                        Text(stringResource(R.string.settings_high_threshold, highThreshold), style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = highThreshold.toFloat(),
                            onValueChange = { onHighThresholdChange(it.toInt()) },
                            valueRange = 100f..200f,
                            steps = 19
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
                }
                
                Divider()

                // Weekly Workout Goal
                Column {
                    Text("Objectif Entraînements : $weeklyWorkoutGoal / semaine", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = weeklyWorkoutGoal.toFloat(),
                        onValueChange = { onWeeklyWorkoutGoalChange(it.toInt()) },
                        valueRange = 1f..7f,
                        steps = 5
                    )
                }

                // Daily Step Goal
                Column {
                    Text("Objectif Pas : $dailyStepGoal / jour", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = dailyStepGoal.toFloat(),
                        onValueChange = { onDailyStepGoalChange(it.toInt()) },
                        valueRange = 1000f..20000f,
                        steps = 19 // increments of 1000
                    )
                }

                Divider()

                // Sleep Goal Slider
                Column {
                    val hours = sleepGoalMinutes / 60
                    val minutes = sleepGoalMinutes % 60
                    Text("Objectif de Sommeil : ${hours}h ${if (minutes > 0) "${minutes}m" else ""}", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = sleepGoalMinutes.toFloat(),
                        onValueChange = { onSleepGoalChange(it.toInt()) },
                        valueRange = 300f..600f, // 5h to 10h
                        steps = 9 // (600-300)/30 = 10 intervals = 9 steps (5:00, 5:30, ... 10:00)
                    )
                }
                
                Divider()
                
                // Date of Birth Selector
                Column {
                    Text("Date de naissance", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val displayText = if (dateOfBirthState != null && dateOfBirthState > 0) {
                         DateUtils.formatForDisplay(java.util.Date(dateOfBirthState))
                    } else {
                        "Non définie"
                    }
                    
                    var showDatePicker by remember { mutableStateOf(false) }

                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(displayText)
                    }
                    
                    if (showDatePicker) {
                        val datePickerState = rememberDatePickerState(
                            initialSelectedDateMillis = if (dateOfBirthState != null && dateOfBirthState > 0) dateOfBirthState else null,
                            yearRange = 1900..java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                        )
                        
                        DatePickerDialog(
                            onDismissRequest = { showDatePicker = false },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        datePickerState.selectedDateMillis?.let { onDateOfBirthChange(it) }
                                        showDatePicker = false
                                    }
                                ) {
                                    Text("OK")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDatePicker = false }) {
                                    Text("Annuler")
                                }
                            }
                        ) {
                            DatePicker(state = datePickerState)
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
