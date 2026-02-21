package com.cardio.fitbit.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cardio.fitbit.ui.screens.DashboardViewModel

@Composable
fun GoalProgressCard(
    goals: List<DashboardViewModel.GoalProgress>
) {
    if (goals.isEmpty()) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Objectifs Personnalisés",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            goals.forEach { goal ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val label = when (goal.type) {
                            DashboardViewModel.GoalType.STEPS -> "Pas aujourd'hui"
                            DashboardViewModel.GoalType.WORKOUTS -> "Entraînements cette semaine"
                        }
                        Text(label, style = MaterialTheme.typography.bodySmall)
                        Text(
                            "${goal.current} / ${goal.goal}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    LinearProgressIndicator(
                        progress = { goal.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = if (goal.progress >= 1f) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }
            }
        }
    }
}
