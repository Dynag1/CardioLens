package com.cardio.fitbit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cardio.fitbit.data.models.HeartRateData
import com.cardio.fitbit.ui.theme.*

@Composable
fun HeartRateCard(heartRateData: HeartRateData?) {
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
                        text = "❤️",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Rythme cardiaque",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = OnSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (heartRateData != null) {
                // Resting Heart Rate
                heartRateData.restingHeartRate?.let { rhr ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        HeartRateColor.copy(alpha = 0.2f),
                                        HeartRateColor.copy(alpha = 0.05f)
                                    )
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(16.dp)
                    ) {
                        Column {
                            Text(
                                text = "Au repos",
                                style = MaterialTheme.typography.bodyMedium,
                                color = OnSurface.copy(alpha = 0.7f)
                            )
                            Row(
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Text(
                                    text = "$rhr",
                                    style = MaterialTheme.typography.displayMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = HeartRateColor
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "bpm",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = OnSurface.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                        }
                    }
                }

                // Heart Rate Zones
                if (heartRateData.heartRateZones.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Zones de fréquence cardiaque",
                        style = MaterialTheme.typography.titleSmall,
                        color = OnSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    heartRateData.heartRateZones.forEach { zone ->
                        if (zone.minutes > 0) {
                            HeartRateZoneRow(
                                name = zone.name,
                                minutes = zone.minutes,
                                range = "${zone.min}-${zone.max} bpm"
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
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
private fun HeartRateZoneRow(name: String, minutes: Int, range: String) {
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
                text = range,
                style = MaterialTheme.typography.bodySmall,
                color = OnSurface.copy(alpha = 0.6f)
            )
        }
        Text(
            text = "${minutes} min",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = HeartRateColor
        )
    }
}
