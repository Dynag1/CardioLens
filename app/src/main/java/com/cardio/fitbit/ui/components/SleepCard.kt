package com.cardio.fitbit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cardio.fitbit.data.models.SleepData
import com.cardio.fitbit.ui.theme.*
import com.cardio.fitbit.utils.DateUtils

@Composable
fun SleepCard(sleepData: SleepData?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "😴",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Sommeil",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = OnSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (sleepData != null) {
                // Total Sleep Duration
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    SleepColor.copy(alpha = 0.2f),
                                    SleepColor.copy(alpha = 0.05f)
                                )
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            text = "Durée totale",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurface.copy(alpha = 0.7f)
                        )
                        Row(
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Text(
                                text = DateUtils.formatDuration(sleepData.duration),
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                color = SleepColor
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${DateUtils.formatTimeForDisplay(sleepData.startTime)} - ${DateUtils.formatTimeForDisplay(sleepData.endTime)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                // Sleep Efficiency
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SleepStatItem(
                        label = "Efficacité",
                        value = "${sleepData.efficiency}%"
                    )
                    SleepStatItem(
                        label = "Endormi",
                        value = DateUtils.formatMinutes(sleepData.minutesAsleep)
                    )
                    SleepStatItem(
                        label = "Éveillé",
                        value = DateUtils.formatMinutes(sleepData.minutesAwake)
                    )
                }

                // Sleep Stages
                sleepData.stages?.let { stages ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Phases de sommeil",
                        style = MaterialTheme.typography.titleSmall,
                        color = OnSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    SleepStageRow("Profond", stages.deep, ChartDeep)
                    Spacer(modifier = Modifier.height(8.dp))
                    SleepStageRow("Léger", stages.light, ChartLight)
                    Spacer(modifier = Modifier.height(8.dp))
                    SleepStageRow("REM", stages.rem, ChartREM)
                    Spacer(modifier = Modifier.height(8.dp))
                    SleepStageRow("Éveillé", stages.wake, ChartAwake)
                }
            } else {
                // No data
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Aucune donnée disponible",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SleepStatItem(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = OnSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = OnSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun SleepStageRow(name: String, minutes: Int, color: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(color, RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurface
            )
        }
        Text(
            text = DateUtils.formatMinutes(minutes),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}
