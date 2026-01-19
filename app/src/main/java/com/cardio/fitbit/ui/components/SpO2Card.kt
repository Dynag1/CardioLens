package com.cardio.fitbit.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cardio.fitbit.data.models.SpO2Data
import com.cardio.fitbit.utils.DateUtils
import java.util.Date

@Composable
fun SpO2Card(
    currentData: SpO2Data?,
    history: List<SpO2Data>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color.Gray.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Saturation en Oxygène (SpO2)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (currentData != null) "${currentData.avg.toInt()}%" else "--%",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Moyenne journalière",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (history.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                SpO2Chart(history = history, modifier = Modifier.fillMaxWidth().height(150.dp))
            }
        }
    }
}

@Composable
fun SpO2Chart(
    history: List<SpO2Data>,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    var showTooltip by remember { mutableStateOf(false) }
    var touchX by remember { mutableFloatStateOf(0f) }
    var selectedRecord by remember { mutableStateOf<SpO2Data?>(null) }

    Column(modifier = modifier) {
        Box(modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .pointerInput(history) {
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

                // Scale Y (Fixed 90-100 usually, but adapt if lower)
                val minVal = (history.minOfOrNull { it.avg } ?: 90.0).toFloat().coerceAtMost(90f)
                val maxVal = 100f
                val rangeY = maxVal - minVal

                // Scale X (Time)
                val minTime = history.first().date.time
                val maxTime = history.last().date.time
                // Avoid divide by zero if single point
                val timeRange = if (maxTime - minTime > 0) (maxTime - minTime).toFloat() else 1f

                // Draw Y Axis Labels (90, 95, 100)
                val textPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.GRAY
                    textSize = 24f
                    textAlign = android.graphics.Paint.Align.RIGHT
                }
                
                // Draw 100%
                drawContext.canvas.nativeCanvas.drawText("100", paddingStart - 10f, paddingTop + 10f, textPaint)
                // Draw Min
                drawContext.canvas.nativeCanvas.drawText(minVal.toInt().toString(), paddingStart - 10f, height - paddingBottom, textPaint)


                fun getX(date: Date): Float {
                    if (history.size == 1) return paddingStart + effectiveWidth / 2
                    val offset = date.time - minTime
                    return paddingStart + (offset / timeRange * effectiveWidth)
                }

                fun getY(value: Double): Float {
                    return height - paddingBottom - ((value.toFloat() - minVal) / rangeY * effectiveHeight)
                }

                val points = history.map { Offset(getX(it.date), getY(it.avg)) }

                if (points.isNotEmpty()) {
                    if (points.size > 1) {
                        val path = Path()
                        path.moveTo(points.first().x, points.first().y)
                        for (i in 1 until points.size) {
                            path.lineTo(points[i].x, points[i].y)
                        }
                        drawPath(path, primaryColor, style = Stroke(width = 4f))
                    }

                    points.forEach { point ->
                        drawCircle(primaryColor, radius = 6f, center = point)
                    }
                }

                // Tooltip
                if (showTooltip) {
                     val touchOffset = (touchX - paddingStart).coerceIn(0f, effectiveWidth)
                     val touchRatio = touchOffset / effectiveWidth
                     val touchTime = minTime + (touchRatio * timeRange).toLong()

                     selectedRecord = history.minByOrNull { kotlin.math.abs(it.date.time - touchTime) }

                     selectedRecord?.let { record ->
                         val px = getX(record.date)
                         val py = getY(record.avg)

                         drawLine(
                             Color.Gray,
                             start = Offset(px, paddingTop),
                             end = Offset(px, height - paddingBottom),
                             strokeWidth = 2f,
                             pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                         )
                         drawCircle(Color.White, radius = 10f, center = Offset(px, py))
                         drawCircle(primaryColor, radius = 8f, center = Offset(px, py))
                     }
                }
            }
        }
        
        if (selectedRecord != null) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.padding(8.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("${DateUtils.formatDate(selectedRecord!!.date)}: ${selectedRecord!!.avg.toInt()}%")
                }
            }
        } else {
            Spacer(modifier = Modifier.height(36.dp))
        }
    }
}
