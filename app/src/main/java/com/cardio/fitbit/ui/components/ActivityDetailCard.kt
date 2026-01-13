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
            val activityMinutes = allMinuteData.filter { data ->
                val cal = Calendar.getInstance()
                cal.time = activity.startTime 
                val parts = data.time.split(":")
                if (parts.size < 2) return@filter false
                cal.set(Calendar.HOUR_OF_DAY, parts[0].toInt())
                cal.set(Calendar.MINUTE, parts[1].toInt())
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val ts = cal.timeInMillis
                
                val rangeStart = (activity.startTime.time / 60000) * 60000
                val rangeEnd = ((activity.startTime.time + activity.duration + 59999) / 60000) * 60000
                ts in rangeStart..rangeEnd
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
                
                val avgHr = if (activity.averageHeartRate != null && activity.averageHeartRate > 0) {
                    activity.averageHeartRate
                } else if (activityMinutes.isNotEmpty()) {
                    val validMinutes = activityMinutes.filter { it.heartRate > 0 }
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

            val calculatedSteps = activityMinutes.sumOf { it.steps }
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
                ActivityHeartRateChart(activity, activityMinutes)
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
fun ActivityHeartRateChart(activity: Activity, activityMinutes: List<MinuteData>) {
    if (activityMinutes.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Pas de données détaillées (${activity.activityName})", style = MaterialTheme.typography.labelSmall)
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

                // Interactivity: Finger following (scrubbing)
                setOnTouchListener { v, event ->
                    when (event.action) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            v.parent.requestDisallowInterceptTouchEvent(true)
                            isDragEnabled = false
                            val h = getHighlightByTouchPoint(event.x, event.y)
                            if (h != null) highlightValue(h, true)
                        }
                        android.view.MotionEvent.ACTION_MOVE -> {
                            isDragEnabled = false
                            val h = getHighlightByTouchPoint(event.x, event.y)
                            if (h != null) highlightValue(h, true)
                        }
                        android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                            isDragEnabled = false
                            v.parent.requestDisallowInterceptTouchEvent(false)
                        }
                    }
                    false 
                }

                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false)
                    textColor = Color.GRAY
                    textSize = 10f
                    labelCount = 4
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

                axisRight.isEnabled = false
                
                // Add marker
                val mv = CustomMarkerView(context, R.layout.marker_view)
                mv.chartView = this
                marker = mv

                drawOrder = arrayOf(
                    CombinedChart.DrawOrder.BAR,
                    CombinedChart.DrawOrder.LINE
                )
            }
        },
        update = { chart ->
            val combinedData = CombinedData()

            // 1. HR Line Data
            val hrEntries = activityMinutes.mapIndexed { index, data ->
                Entry(index.toFloat(), data.heartRate.toFloat(), data)
            }
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

            // 2. Steps Bar Data
            val stepEntries = activityMinutes.mapIndexedNotNull { index, data ->
                if (data.steps > 0) {
                    BarEntry(index.toFloat(), data.steps.toFloat(), data) // REAL step magnitude
                } else null
            }
            if (stepEntries.isNotEmpty()) {
                val stepDataSet = BarDataSet(stepEntries, "Steps").apply {
                    color = Color.parseColor("#4CAF50")
                    setDrawValues(false)
                    isHighlightEnabled = false
                }
                val barData = BarData(stepDataSet)
                barData.barWidth = 0.6f
                combinedData.setData(barData)
            }

            chart.data = combinedData
            chart.invalidate()
        },
        modifier = Modifier.fillMaxSize()
    )
}
