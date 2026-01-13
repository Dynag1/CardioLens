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
import com.cardio.fitbit.data.models.StepsData
import com.cardio.fitbit.ui.theme.*
import com.cardio.fitbit.utils.DateUtils

@Composable
fun StepsCard(stepsData: List<StepsData>) {
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
                        text = "👟",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Pas",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = OnSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (stepsData.isNotEmpty()) {
                // Today's steps (last item)
                val todaySteps = stepsData.lastOrNull()
                todaySteps?.let { today ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        StepsColor.copy(alpha = 0.2f),
                                        StepsColor.copy(alpha = 0.05f)
                                    )
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(16.dp)
                    ) {
                        Column {
                            Text(
                                text = "Aujourd'hui",
                                style = MaterialTheme.typography.bodyMedium,
                                color = OnSurface.copy(alpha = 0.7f)
                            )
                            Row(
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Text(
                                    text = String.format("%,d", today.steps),
                                    style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = StepsColor
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "pas",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = OnSurface.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                        }
                    }
                }

                // Weekly average
                val avgSteps = stepsData.map { it.steps }.average().toInt()
                val totalSteps = stepsData.sumOf { it.steps }

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StepsStatItem(
                        label = "Moyenne",
                        value = String.format("%,d", avgSteps)
                    )
                    StepsStatItem(
                        label = "Total 7j",
                        value = String.format("%,d", totalSteps)
                    )
                }

                // Recent days
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Derniers jours",
                    style = MaterialTheme.typography.titleSmall,
                    color = OnSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(12.dp))

                stepsData.takeLast(5).reversed().forEach { daySteps ->
                    StepsDayRow(
                        date = DateUtils.formatForDisplay(daySteps.date),
                        steps = daySteps.steps
                    )
                    Spacer(modifier = Modifier.height(8.dp))
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
private fun StepsStatItem(label: String, value: String) {
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
private fun StepsDayRow(date: String, steps: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = date,
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurface
        )
        Text(
            text = String.format("%,d pas", steps),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = StepsColor
        )
    }
}
