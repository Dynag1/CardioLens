package com.cardio.fitbit.ui.components

import android.graphics.Color
import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.cardio.fitbit.data.models.ActivityData
import com.cardio.fitbit.data.models.MinuteData
import com.cardio.fitbit.data.models.SleepData
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.charts.ScatterChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener

@Composable
fun HeartRateDetailChart(
    minuteData: List<MinuteData>,
    aggregatedData: List<MinuteData>, // 5min buckets
    sleepData: SleepData? = null,
    activityData: ActivityData? = null,
    selectedDate: java.util.Date,
    modifier: Modifier = Modifier,
    onChartInteraction: (Boolean) -> Unit = {} // true = chart is being touched, false = touch released
) {
    // State to track selection
    var selectedPoint by remember { mutableStateOf<MinuteData?>(null) }
    
    // State to track Zoom level to switch datasets
    // We can't easily detect zoom change in Compose state from View, so we handle it in `update` block 
    // or we assume a default and let the View handle the data swap implicitly if we could, 
    // but swapping data requires clearing chart. 
    // Let's rely on `scaleX` inside the view update simply.

    Column {
        // Debug only if empty activities (User troubleshooting)
        if (activityData?.activities.isNullOrEmpty() && !activityData?.debugInfo.isNullOrBlank()) {
             Text(text = activityData!!.debugInfo!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
        }

        AndroidView(
            factory = { context ->
                CombinedChart(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    
                    setBackgroundColor(Color.WHITE)
                    description.isEnabled = false
                    legend.isEnabled = false
                    
                    setTouchEnabled(true)
                    isDragEnabled = false // Default to false so 1 finger = highlight
                    setScaleEnabled(true)
                    setPinchZoom(true)
                    isHighlightPerDragEnabled = true // Ensure highlight follows finger
                    
                    // Enable Drag (Pan) ONLY with 2 fingers, keep 1 finger for Highlight
                    setOnTouchListener { v, event ->
                        when (event.action and android.view.MotionEvent.ACTION_MASK) {
                            android.view.MotionEvent.ACTION_DOWN -> {
                                // Stop parent (LazyColumn) from stealing touch immediately
                                v.parent.requestDisallowInterceptTouchEvent(true)
                            }
                            android.view.MotionEvent.ACTION_MOVE -> {
                                if (event.pointerCount >= 2) {
                                    isDragEnabled = true
                                } else {
                                    isDragEnabled = false
                                    // Force highlight update for 1-finger scrubbing
                                    val h = getHighlightByTouchPoint(event.x, event.y)
                                    if (h != null) {
                                        highlightValue(h, true)
                                    }
                                }
                            }
                            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                                isDragEnabled = false
                                v.parent.requestDisallowInterceptTouchEvent(false)
                            }
                        }
                        false // Let the chart consume the event
                    }
                    
                    // Enable zoom on X axis only (horizontal)
                    isScaleXEnabled = true
                    isScaleYEnabled = false
                    
                    // Add gesture listener to handle drawer interaction
                    onChartGestureListener = object : com.github.mikephil.charting.listener.OnChartGestureListener {
                        override fun onChartGestureStart(me: android.view.MotionEvent?, lastPerformedGesture: com.github.mikephil.charting.listener.ChartTouchListener.ChartGesture?) {
                            onChartInteraction(true) // Disable drawer gestures
                            // Prevent parent (LazyColumn) from scrolling while interacting with chart
                            parent.requestDisallowInterceptTouchEvent(true)
                        }

                        override fun onChartGestureEnd(me: android.view.MotionEvent?, lastPerformedGesture: com.github.mikephil.charting.listener.ChartTouchListener.ChartGesture?) {
                            onChartInteraction(false) // Re-enable drawer gestures
                            // Allow parent scroll again
                            parent.requestDisallowInterceptTouchEvent(false)
                        }

                        override fun onChartLongPressed(me: android.view.MotionEvent?) {}
                        override fun onChartDoubleTapped(me: android.view.MotionEvent?) {}
                        override fun onChartSingleTapped(me: android.view.MotionEvent?) {}
                        override fun onChartFling(me1: android.view.MotionEvent?, me2: android.view.MotionEvent?, velocityX: Float, velocityY: Float) {}
                        override fun onChartScale(me: android.view.MotionEvent?, scaleX: Float, scaleY: Float) {}
                        override fun onChartTranslate(me: android.view.MotionEvent?, dX: Float, dY: Float) {}
                    }
                    
                    // Listeners & Marker
                    val markerView = CustomMarkerView(context, com.cardio.fitbit.R.layout.marker_view)
                    markerView.chartView = this
                    marker = markerView

                    drawOrder = arrayOf(
                        CombinedChart.DrawOrder.LINE, // Sleep zone first (background)
                        CombinedChart.DrawOrder.BAR,  // HR bars
                        CombinedChart.DrawOrder.SCATTER // Bubbles if any
                    )

                    // X-Axis
                    xAxis.position = XAxis.XAxisPosition.BOTTOM
                    xAxis.setDrawGridLines(false)
                    xAxis.textColor = Color.DKGRAY
                    xAxis.textSize = 10f
                    
                    // Left Y (HR)
                    axisLeft.textColor = Color.DKGRAY
                    axisLeft.axisMinimum = 0f
                    axisLeft.axisMaximum = 200f
                    axisLeft.setDrawGridLines(true)
                    axisLeft.gridColor = Color.parseColor("#EEEEEE")
                    
                    // Right Y (Steps) - Scaled
                    axisRight.isEnabled = false 
                    axisRight.axisMinimum = 0f
                }
            },
            update = { chart ->
                if (minuteData.isEmpty()) {
                    chart.clear()
                    return@AndroidView
                }

                // Detect zoom level and choose appropriate data granularity
                val scaleX = chart.scaleX
                val activeList = when {
                    scaleX > 8f -> minuteData  // Very zoomed: use 1-minute data
                    scaleX > 3f -> minuteData  // Medium zoom: use 1-minute data
                    else -> aggregatedData     // Normal view: use 5-minute aggregated data
                }
                
                android.util.Log.d("HeartRateChart", "Zoom level: $scaleX, using ${if (activeList == minuteData) "minute" else "aggregated"} data (${activeList.size} points)")
                
                val combinedData = CombinedData()

                // Helper function to convert time string to index (1-minute granularity)
                fun timeToIndex(time: String): Float {
                    val parts = time.split(":")
                    if (parts.size < 2) return 0f
                    val hours = parts[0].toInt()
                    val minutes = parts[1].toInt()
                    return (hours * 60 + minutes).toFloat()
                }

                // 1. HR Bars (With Gradient)
                val hrEntries = activeList.mapNotNull { data ->
                    if (data.heartRate > 0) {
                        BarEntry(timeToIndex(data.time), data.heartRate.toFloat(), data)
                    } else {
                        null
                    }
                }
                
                val hrDataSet = BarDataSet(hrEntries, "HR").apply {
                    setDrawValues(false)
                    // Gradient Colors
                    val colors = hrEntries.map { entry ->
                        val y = entry.y
                         when {
                             y < 60 -> Color.GREEN
                             y < 80 -> Color.YELLOW
                             y < 100 -> Color.rgb(255, 165, 0) // Orange
                             y < 120 -> Color.rgb(255, 69, 0) // Red-Orange
                             else -> Color.RED
                         }
                    }
                    setColors(colors)
                    isHighlightEnabled = true
                }

                // 2. Steps (As BarDataSet superimposed on the BPM bars)
                // Height is fixed to 10f if steps > 0 to be visible but not cover the whole bar
                val stepEntries = activeList.mapNotNull { data ->
                    if (data.steps > 0) {
                        BarEntry(timeToIndex(data.time), 10f, data)
                    } else {
                        null
                    }
                }

                val stepDataSet = BarDataSet(stepEntries, "Steps").apply {
                    color = Color.parseColor("#4CAF50") // Green
                    setDrawValues(false)
                    isHighlightEnabled = false // Highlight HR dataset only
                }

                // Combine both in BarData (Order: HR first, then Steps on top)
                val barData = BarData(hrDataSet, stepDataSet)
                barData.barWidth = 0.8f 

                // 3. Sleep Zone (Purple background)
                val sleepLineData = LineData()
                
                if (sleepData != null) {
                    val sleepStart = sleepData.startTime.time
                    val sleepEnd = sleepData.endTime.time
                    val sleepZoneEntries = mutableListOf<Entry>()
                    val cal = java.util.Calendar.getInstance()
                    
                    cal.time = selectedDate
                    val year = cal.get(java.util.Calendar.YEAR)
                    val month = cal.get(java.util.Calendar.MONTH)
                    val day = cal.get(java.util.Calendar.DAY_OF_MONTH)
                    
                    activeList.forEach { data ->
                        val parts = data.time.split(":")
                        if (parts.size >= 2) {
                            cal.set(year, month, day, parts[0].toInt(), parts[1].toInt(), 0)
                            cal.set(java.util.Calendar.MILLISECOND, 0)
                            val ts = cal.timeInMillis
                            
                            if (ts >= sleepStart && ts <= sleepEnd) {
                                sleepZoneEntries.add(Entry(timeToIndex(data.time), 200f))
                            } else {
                                sleepZoneEntries.add(Entry(timeToIndex(data.time), 0f))
                            }
                        }
                    }
                    
                    if (sleepZoneEntries.any { it.y > 0 }) {
                        val sleepZoneDataSet = LineDataSet(sleepZoneEntries, "Sommeil").apply {
                            setDrawCircles(false)
                            setDrawValues(false)
                            mode = LineDataSet.Mode.STEPPED
                            setDrawFilled(true)
                            fillColor = Color.parseColor("#E1BEE7")
                            color = Color.TRANSPARENT
                            fillAlpha = 120
                            lineWidth = 0f
                            isHighlightEnabled = false
                        }
                        sleepLineData.addDataSet(sleepZoneDataSet)
                    }
                }
                
                // 4. Activity/Exercise Zones (Orange background)
                if (activityData != null && activityData.activities.isNotEmpty()) {
                    val cal = java.util.Calendar.getInstance()
                    cal.time = selectedDate
                    val year = cal.get(java.util.Calendar.YEAR)
                    val month = cal.get(java.util.Calendar.MONTH)
                    val day = cal.get(java.util.Calendar.DAY_OF_MONTH)
                    
                    activityData.activities.forEach { activity ->
                        val activityStart = (activity.startTime.time / 60000) * 60000
                        val activityEnd = ((activity.startTime.time + activity.duration + 59999) / 60000) * 60000
                        val activityZoneEntries = mutableListOf<Entry>()
                        var matches = 0
                        
                        activeList.forEach { data ->
                            val parts = data.time.split(":")
                            if (parts.size >= 2) {
                                cal.set(year, month, day, parts[0].toInt(), parts[1].toInt(), 0)
                                cal.set(java.util.Calendar.MILLISECOND, 0)
                                val ts = cal.timeInMillis
                                if (ts in activityStart..activityEnd) {
                                    activityZoneEntries.add(Entry(timeToIndex(data.time), 200f))
                                    matches++
                                } else {
                                    activityZoneEntries.add(Entry(timeToIndex(data.time), 0f))
                                }
                            }
                        }
                        
                        if (activityZoneEntries.any { it.y > 0 }) {
                            val activityZoneDataSet = LineDataSet(activityZoneEntries, activity.activityName).apply {
                                setDrawCircles(false)
                                setDrawValues(false)
                                mode = LineDataSet.Mode.STEPPED
                                setDrawFilled(true)
                                fillColor = Color.parseColor("#2196F3") // Blue for activity
                                color = Color.TRANSPARENT
                                fillAlpha = 120
                                lineWidth = 0f
                                isHighlightEnabled = false
                            }
                            sleepLineData.addDataSet(activityZoneDataSet)
                        }
                    }
                }
                
                // Final Assembly
                combinedData.setData(sleepLineData)
                combinedData.setData(barData)
                
                chart.data = combinedData
                
                // XAxis Formatter for 1-minute granularity
                chart.xAxis.valueFormatter = object : IndexAxisValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val minuteOffset = value.toInt()
                        val hour = minuteOffset / 60
                        val minute = minuteOffset % 60
                        
                        // Show labels only for full hours or every 30 mins
                        return if (minute == 0) {
                            String.format("%02d:00", hour)
                        } else if (minute == 30) {
                            String.format("%02d:30", hour)
                        } else {
                            ""
                        }
                    }
                }
                
                chart.invalidate()
            },
            modifier = modifier
        )
        
        // Tooltip / Selection Info
        selectedPoint?.let { data ->
             Card(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) { 
                Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceAround) {
                     Text("Heure: ${data.time}")
                     Text("❤️ ${data.heartRate} BPM")
                     Text("👟 ${data.steps} pas")
                }
            }
        }
    }
}
