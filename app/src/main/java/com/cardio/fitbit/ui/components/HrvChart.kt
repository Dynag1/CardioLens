package com.cardio.fitbit.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.cardio.fitbit.data.models.HrvRecord
import com.cardio.fitbit.utils.DateUtils
import java.util.Date

@Composable
fun HrvChart(
    hrvRecords: List<HrvRecord>,
    modifier: Modifier = Modifier
) {
    if (hrvRecords.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "Pas de données HRV disponible",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface

    // State for interaction
    var showTooltip by remember { mutableStateOf(false) }
    var touchX by remember { mutableFloatStateOf(0f) }
    var selectedRecord by remember { mutableStateOf<HrvRecord?>(null) }

    Column(modifier = modifier) {
        
        Box(modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .pointerInput(hrvRecords) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        showTooltip = true
                        touchX = offset.x
                    },
                    onDragEnd = {
                        showTooltip = false
                        selectedRecord = null
                    },
                    onHorizontalDrag = { change, _ ->
                        touchX = change.position.x
                    }
                )
            }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val paddingStart = 40f
                val paddingEnd = 40f
                val paddingBottom = 40f
                val paddingTop = 20f

                val effectiveWidth = width - (paddingStart + paddingEnd)
                val effectiveHeight = height - (paddingBottom + paddingTop)
                
                // Scale Y
                val minRmssd = (hrvRecords.minOfOrNull { it.rmssd } ?: 0.0).toFloat().coerceAtLeast(0f)
                val maxRmssd = (hrvRecords.maxOfOrNull { it.rmssd } ?: 100.0).toFloat().coerceAtLeast(minRmssd + 20f)
                val rangeY = maxRmssd - minRmssd

                // Scale X (00:00 to 23:59) for consistency with hourly request
                // Use Start/End of the DAY of the first record
                val firstRecordDate = hrvRecords.first().time
                val startOfDay = DateUtils.getStartOfDay(firstRecordDate).time
                val endOfDay = DateUtils.getEndOfDay(firstRecordDate).time
                val totalDuration = (endOfDay - startOfDay).toFloat()

                // Draw X Axis Labels (Every 6 hours: 00, 06, 12, 18)
                val textPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.GRAY
                    textSize = 24f
                    textAlign = android.graphics.Paint.Align.CENTER
                }

                for (hour in 0..24 step 6) {
                    val hourMillis = hour * 60 * 60 * 1000L
                    val x = paddingStart + (hourMillis / totalDuration * effectiveWidth)
                    drawContext.canvas.nativeCanvas.drawText(
                        String.format("%02d:00", hour % 24),
                        x,
                        height - 10f,
                        textPaint
                    )
                }
                
                // Helper to get X coordinate
                fun getX(time: Date): Float {
                    val offset = time.time - startOfDay
                    return paddingStart + (offset / totalDuration * effectiveWidth)
                }

                // Helper to get Y coordinate
                fun getY(value: Double): Float {
                    return height - paddingBottom - ((value.toFloat() - minRmssd) / rangeY * effectiveHeight)
                }

                // Draw Lines & Points
                val points = hrvRecords.map { record ->
                    Offset(getX(record.time), getY(record.rmssd))
                }

                if (points.isNotEmpty()) {
                    val path = Path()
                    path.moveTo(points.first().x, points.first().y)
                    for (i in 1 until points.size) {
                        path.lineTo(points[i].x, points[i].y)
                    }
                    
                    drawPath(
                        path = path,
                        color = primaryColor,
                        style = Stroke(width = 4f)
                    )
                    
                    points.forEach { point ->
                        drawCircle(
                            color = primaryColor,
                            radius = 6f,
                            center = point
                        )
                    }
                }

                // Interactive Tooltip Logic
                if (showTooltip) {
                    // Find closest record to touchX
                    // Reverse map touchX to time
                    val touchOffset = touchX - paddingStart
                    val touchTimeRatio = (touchOffset / effectiveWidth).coerceIn(0f, 1f)
                    val touchTimeMillis = startOfDay + (touchTimeRatio * totalDuration).toLong()

                    // Find closest record
                    selectedRecord = hrvRecords.minByOrNull { kotlin.math.abs(it.time.time - touchTimeMillis) }
                    
                    selectedRecord?.let { record ->
                        val px = getX(record.time)
                        val py = getY(record.rmssd)

                        // Draw selection line
                        drawLine(
                            color = Color.Gray,
                            start = Offset(px, paddingTop),
                            end = Offset(px, height - paddingBottom),
                            strokeWidth = 2f,
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                        )
                        
                        // Draw highlight circle
                        drawCircle(
                            color = Color.White,
                            radius = 10f,
                            center = Offset(px, py)
                        )
                        drawCircle(
                            color = primaryColor,
                            radius = 8f,
                            center = Offset(px, py)
                        )
                    }
                }
            }
        }
        
        // Tooltip Info Below
        if (selectedRecord != null) {
             Card(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) { 
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(), 
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                     Text("Heure: ${DateUtils.formatTimeForDisplay(selectedRecord!!.time)}")
                     Text("HRV: ${selectedRecord!!.rmssd.toInt()} ms")
                }
            }
        } else {
             Spacer(modifier = Modifier.height(66.dp)) // Placeholder to prevent jump
        }
    }
}
