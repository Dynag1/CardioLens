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
import com.cardio.fitbit.data.models.ActivityData
import com.cardio.fitbit.ui.theme.*
import com.cardio.fitbit.utils.DateUtils

@Composable
fun ActivityCard(activityData: ActivityData?) {
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
                        text = "🏃",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Activités",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = OnSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (activityData != null) {
                // Activity Summary
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    ActivityColor.copy(alpha = 0.2f),
                                    ActivityColor.copy(alpha = 0.05f)
                                )
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ActivityStatItem(
                            label = "Calories",
                            value = "${activityData.summary.caloriesOut}"
                        )
                        ActivityStatItem(
                            label = "Distance",
                            value = String.format("%.1f km", activityData.summary.distance)
                        )
                        ActivityStatItem(
                            label = "Actif",
                            value = "${activityData.summary.activeMinutes} min"
                        )
                    }
                }

                // Recorded Activities
                if (activityData.activities.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Exercices enregistrés",
                        style = MaterialTheme.typography.titleSmall,
                        color = OnSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    activityData.activities.forEach { activity ->
                        ActivityRow(
                            name = activity.activityName,
                            duration = DateUtils.formatDuration(activity.duration),
                            calories = activity.calories
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                } else {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Aucun exercice enregistré aujourd'hui",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurface.copy(alpha = 0.5f)
                    )
                }

                // Additional Stats
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Étages",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "${activityData.summary.floors}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = OnSurface
                        )
                    }
                    Column {
                        Text(
                            text = "Sédentaire",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = DateUtils.formatMinutes(activityData.summary.sedentaryMinutes),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = OnSurface
                        )
                    }
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
private fun ActivityStatItem(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = ActivityColor
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = OnSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun ActivityRow(name: String, duration: String, calories: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = OnSurface
            )
            Text(
                text = duration,
                style = MaterialTheme.typography.bodySmall,
                color = OnSurface.copy(alpha = 0.6f)
            )
        }
        Text(
            text = "$calories cal",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = ActivityColor
        )
    }
}
