package com.cardio.fitbit.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cardio.fitbit.ui.screens.WeeklySummary
import com.cardio.fitbit.utils.DateUtils
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.Alignment

@Composable
fun WeeklyWorkoutSummaryCard(summary: WeeklySummary, onExportClick: (WeeklySummary) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header: Date Range + Export Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                 Text(
                    text = "Semaine ${summary.week} (${DateUtils.formatForDisplay(summary.startDate)} - ${DateUtils.formatForDisplay(summary.endDate)})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f)
                )
                
                IconButton(onClick = { onExportClick(summary) }) {
                    Icon(
                        imageVector = Icons.Default.Share, 
                        contentDescription = "Exporter PDF",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Count
                SummaryStatItem("Entraînements", "${summary.count}", Icons.Default.FitnessCenter)
                
                // Avg Duration
                SummaryStatItem("Durée Moy.", DateUtils.formatMinutes((summary.avgDuration / 60000).toInt()), Icons.Default.Timer)
                
                // Intensitée (Cal/min)
                SummaryStatItem("Intensité", String.format("%.1f cal/min", summary.avgIntensity), Icons.Default.LocalFireDepartment)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween /*SpaceEvenly*/
            ) {
                // Avg HR
                if (summary.avgHeartRate > 0) {
                    SummaryStatItem("Pouls Moy.", "${summary.avgHeartRate} bpm", Icons.Default.Favorite)
                }
                
                // Avg Steps
                if (summary.avgSteps > 0) {
                     SummaryStatItem("Pas Moy.", "${summary.avgSteps}", Icons.Default.DirectionsRun)
                }

                // Avg Speed
                if (summary.avgSpeed > 0) {
                     SummaryStatItem("Vitesse Moy.", String.format("%.1f km/h", summary.avgSpeed), Icons.Default.Speed)
                }
            }
        }
    }
}

@Composable
private fun SummaryStatItem(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
