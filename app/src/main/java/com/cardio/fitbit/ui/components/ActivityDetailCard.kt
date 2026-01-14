package com.cardio.fitbit.ui.components

import android.graphics.Color
import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.cardio.fitbit.R
import com.cardio.fitbit.data.models.Activity
import com.cardio.fitbit.data.models.MinuteData
import com.cardio.fitbit.utils.DateUtils
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.CombinedData
import java.util.*

@Composable
fun ActivityDetailCard(
    activity: Activity,
    allMinuteData: List<MinuteData>,
    selectedDate: Date
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Generate CONTINUOUS timeline
            // 1. Map existing data for quick lookup
            val dataMap = allMinuteData.associateBy { it.time }
            
            // 2. Determine start/end time
            val cal = Calendar.getInstance()
            cal.time = activity.startTime
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val startTimeMs = cal.timeInMillis
            val durationMs = activity.duration
            val endTimeMs = startTimeMs + durationMs

            // 3. Generate full list of minutes
            val continuousMinutes = mutableListOf<MinuteData>()
            var currentMs = startTimeMs
            val dateFormat = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())

            // Loop minute by minute
            while (currentMs <= endTimeMs) {
                val timeStr = dateFormat.format(Date(currentMs))
                // Use existing data or empty default
                val data = dataMap[timeStr] ?: MinuteData(timeStr, 0, 0)
                continuousMinutes.add(data)
                currentMs += 60000 // +1 minute
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = activity.activityName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${DateUtils.formatTimeForDisplay(activity.startTime)} - " +
                                DateUtils.formatDuration(activity.duration),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                // Calculate display average
                 val avgHr = if (activity.averageHeartRate != null && activity.averageHeartRate > 0) {
                    activity.averageHeartRate
                } else if (continuousMinutes.isNotEmpty()) {
                    val validMinutes = continuousMinutes.filter { it.heartRate > 0 }
                    if (validMinutes.isNotEmpty()) validMinutes.map { it.heartRate }.average().toInt() else 0
                } else 0

                if (avgHr > 0) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "$avgHr bpm",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Moyenne",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val calculatedSteps = continuousMinutes.sumOf { it.steps }
            val displaySteps = if (activity.steps != null && activity.steps > 0) activity.steps else calculatedSteps

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(label = "Pas", value = displaySteps.toString())
                StatItem(label = "Calories", value = "${activity.calories} kcal")
                if (activity.distance != null && activity.distance > 0) {
                    StatItem(label = "Distance", value = String.format("%.2f km", activity.distance))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Chart area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            ) {
                ActivityHeartRateChart(continuousMinutes)
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
        Text(text = label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun ActivityHeartRateChart(activityMinutes: List<MinuteData>) {
    if (activityMinutes.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Pas de données détaillées", style = MaterialTheme.typography.labelSmall)
        }
        return
    }

    AndroidView(
        factory = { context ->
            CombinedChart(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                
                description.isEnabled = false
                legend.isEnabled = false
                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(true)
                setPinchZoom(true)
                setDrawGridBackground(false)

                // Interactivity
                setOnTouchListener { v, event ->
                    v.parent.requestDisallowInterceptTouchEvent(true) // Always consume touch
                    false
                }

                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false)
                    textColor = Color.GRAY
                    textSize = 10f
                    labelCount = 5
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            val index = value.toInt()
                            return if (index >= 0 && index < activityMinutes.size) {
                                activityMinutes[index].time
                            } else ""
                        }
                    }
                }

                axisLeft.apply {
                    setDrawGridLines(true)
                    gridColor = Color.LTGRAY
                    textColor = Color.GRAY
                    textSize = 10f
                    axisMinimum = 40f
                    axisMaximum = 200f
                }

                // Right Axis for Steps (Hidden but scaled)
                axisRight.apply {
                    isEnabled = true
                    setDrawLabels(false)
                    setDrawGridLines(false)
                    axisMinimum = 0f
                    axisMaximum = 100f // Scale so steps aren't huge
                }
                
                // Add marker
                val mv = CustomMarkerView(context, R.layout.marker_view)
                mv.chartView = this
                marker = mv

                drawOrder = arrayOf(
                    CombinedChart.DrawOrder.BAR, // Steps first
                    CombinedChart.DrawOrder.LINE // HR on top
                )
            }
        },
        update = { chart ->
            val combinedData = CombinedData()

            // 1. HR Line Data
            val hrEntries = activityMinutes.mapIndexedNotNull { index, data ->
                if (data.heartRate > 0) {
                    Entry(index.toFloat(), data.heartRate.toFloat(), data)
                } else null 
            }
            // Only draw line if we have data
            if (hrEntries.isNotEmpty()) {
                val hrDataSet = LineDataSet(hrEntries, "Heart Rate").apply {
                    setDrawCircles(false)
                    setDrawValues(false)
                    color = Color.parseColor("#EF5350")
                    lineWidth = 2f
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                    setDrawFilled(true)
                    fillColor = Color.parseColor("#EF5350")
                    fillAlpha = 50
                    isHighlightEnabled = true
                    setDrawHorizontalHighlightIndicator(false)
                    highlightLineWidth = 1.5f
                    highLightColor = Color.GRAY
                }
                combinedData.setData(LineData(hrDataSet))
            }

            // 2. Steps Bar Data (Show ALL minutes, even 0, to keep alignment)
            val stepEntries = activityMinutes.mapIndexed { index, data ->
               BarEntry(index.toFloat(), if(data.steps > 0) data.steps.toFloat() else 0f, data)
            }
            
            val stepDataSet = BarDataSet(stepEntries, "Steps").apply {
                color = Color.parseColor("#9C27B0") // Purple
                setDrawValues(false)
                axisDependency = YAxis.AxisDependency.RIGHT // Use Right Axis
                isHighlightEnabled = false
            }
            val barData = BarData(stepDataSet)
            barData.barWidth = 0.6f
            combinedData.setData(barData)

            chart.data = combinedData
            chart.invalidate()
        },
        modifier = Modifier.fillMaxSize()
    )
}
