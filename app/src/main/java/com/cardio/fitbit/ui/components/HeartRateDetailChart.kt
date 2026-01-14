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
    sleepSessions: List<SleepData> = emptyList(),
    activityData: ActivityData? = null,
    restingHeartRate: Int? = null,
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
                    axisLeft.axisMinimum = 40f
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

                // Calculate dynamic Y-axis max (Ensure space for bubbles at top)
                val maxHr = activeList.maxOfOrNull { it.heartRate } ?: 100
                val finalYMax = (maxHr + 30).toFloat().coerceAtLeast(200f)
                chart.axisLeft.axisMaximum = finalYMax
                val zoneHeight = 40f + (finalYMax - 40f) / 2f // Visual 50% height (taking offset 40f into account)

                // Helper function to convert time string to index (1-minute granularity)
                fun timeToIndex(time: String): Float {
                    val parts = time.split(":")
                    if (parts.size < 2) return 0f
                    val hours = parts[0].toInt()
                    val minutes = parts[1].toInt()
                    return (hours * 60 + minutes).toFloat()
                }

                // 1. HR Bars (With Fluid Gradient)
                val hrEntries = activeList.mapNotNull { data ->
                    if (data.heartRate > 0) {
                        BarEntry(timeToIndex(data.time), data.heartRate.toFloat(), data)
                    } else {
                        null
                    }
                }

                fun interpolateColor(color1: Int, color2: Int, fraction: Float): Int {
                    val a = (Color.alpha(color1) + (Color.alpha(color2) - Color.alpha(color1)) * fraction).toInt()
                    val r = (Color.red(color1) + (Color.red(color2) - Color.red(color1)) * fraction).toInt()
                    val g = (Color.green(color1) + (Color.green(color2) - Color.green(color1)) * fraction).toInt()
                    val b = (Color.blue(color1) + (Color.blue(color2) - Color.blue(color1)) * fraction).toInt()
                    return Color.argb(a, r, g, b)
                }

                fun getHeartRateColor(bpm: Float): Int {
                    val colorCyan = Color.parseColor("#06B6D4")   // < 60 (Rest / Sleep)
                    val colorGreen = Color.parseColor("#10B981")  // 60-100 (Normal)
                    val colorOrange = Color.parseColor("#F59E0B") // 100-140 (Moderate)
                    val colorRed = Color.parseColor("#EF4444")    // > 140 (Intense)
                    val colorDeepRed = Color.parseColor("#B91C1C") // Peak

                    return when {
                        bpm < 60f -> interpolateColor(colorCyan, colorGreen, bpm / 60f) // Fade from Cyan to Green
                        bpm < 100f -> interpolateColor(colorGreen, colorOrange, (bpm - 60f) / 40f)
                        bpm < 140f -> interpolateColor(colorOrange, colorRed, (bpm - 100f) / 40f)
                        else -> interpolateColor(colorRed, colorDeepRed, minOf(1f, (bpm - 140f) / 40f))
                    }
                }
                
                val hrDataSet = BarDataSet(hrEntries, "HR").apply {
                    setDrawValues(false)
                    // Fluid Gradient Colors
                    val colors = hrEntries.map { entry -> getHeartRateColor(entry.y) }
                    setColors(colors)
                    isHighlightEnabled = true
                }

                // 2. Steps (As BarDataSet superimposed on the BPM bars)
                // Height is fixed to 10f if steps > 0 to be visible but not cover the whole bar
                val stepEntries = activeList.mapNotNull { data ->
                    if (data.steps > 0) {
                        BarEntry(timeToIndex(data.time), 20f, data) // Taller bars (20f)
                    } else {
                        null
                    }
                }

                val stepDataSet = BarDataSet(stepEntries, "Steps").apply {
                    color = Color.parseColor("#9C27B0") // Purple
                    setDrawValues(false)
                    axisDependency = YAxis.AxisDependency.RIGHT // Use Right Axis for Steps
                    isHighlightEnabled = false // Highlight HR dataset only
                }
                
                // Configure Right Axis for Steps (Hidden, 0-500 scale)
                chart.axisRight.apply {
                    isEnabled = true // Enable axis logic
                    setDrawLabels(false) // Hide labels
                    setDrawGridLines(false) // Hide grid
                    setDrawAxisLine(false) // Hide line
                    axisMinimum = 0f
                    axisMaximum = 400f // 20f bars will take 5% of height (Very small)
                }

                // Combine both in BarData (Order: HR first, then Steps on top)
                val barData = BarData(hrDataSet, stepDataSet)
                barData.barWidth = 0.9f

                // --- Zone Rendering Logic (Sleep & Activity) ---
                
                // Helper to get start of day timestamp
                val cal = java.util.Calendar.getInstance()
                cal.time = selectedDate
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                cal.set(java.util.Calendar.MILLISECOND, 0)
                val startOfDay = cal.timeInMillis
                val endOfDay = startOfDay + (24 * 60 * 60 * 1000L) // +24h

                // Helper to safely convert range to chart X-values (0..1440)
                // Returns null if the range doesn't intersect with today
                fun getClampedRange(startTs: Long, endTs: Long): Pair<Float, Float>? {
                    // 1. Check intersection
                    if (endTs <= startOfDay || startTs >= endOfDay) return null
                    
                    // 2. Clamp timestamps
                    val clampedStart = maxOf(startTs, startOfDay)
                    val clampedEnd = minOf(endTs, endOfDay)
                    
                    if (clampedStart >= clampedEnd) return null

                    // 3. Convert to minutes
                    val startMin = (clampedStart - startOfDay) / 60_000f
                    val endMin = (clampedEnd - startOfDay) / 60_000f
                    
                    return startMin to endMin
                }

                // 3. Sleep Zone (Blue background, 50% height)
                val sleepLineData = LineData()
                
                if (sleepSessions.isNotEmpty()) {
                    val sleepEntries = mutableListOf<Entry>()
                    // Begin with anchor at 0
                    sleepEntries.add(Entry(0f, 0f))
                    
                    // Sort sessions to ensure monotonic X (required by Chart)
                    sleepSessions.sortedBy { it.startTime }.forEach { session ->
                        val range = getClampedRange(session.startTime.time, session.endTime.time)
                        if (range != null) {
                            val (start, end) = range
                            
                            // To create a distinct block, we need to ensure we return to 0 before starting new block if there's a gap
                            // But simplistic "Square Wave" add:
                            // (Start, 0) -> (Start, 100) -> (End, 100) -> (End, 0)
                            // We must ensure 'Start' > last entry's X to strictly increase, but 
                            // coincident points (x, 0) and (x, 100) are allowed for vertical lines.

                            sleepEntries.add(Entry(start, 0f))
                            sleepEntries.add(Entry(start, zoneHeight))
                            sleepEntries.add(Entry(end, zoneHeight))
                            sleepEntries.add(Entry(end, 0f))
                        }
                    }
                    
                    // Final anchor
                    sleepEntries.add(Entry(1440f, 0f))
                    
                    if (sleepEntries.size > 2) { // Only add if we actually have data points
                         val sleepDataSet = LineDataSet(sleepEntries, "Sommeil").apply {
                            setDrawCircles(false)
                            setDrawValues(false)
                            mode = LineDataSet.Mode.LINEAR 
                            setDrawFilled(true)
                            fillColor = Color.parseColor("#BBDEFB") // Blue for sleep
                            color = Color.TRANSPARENT
                            fillAlpha = 60
                            lineWidth = 0f
                            isHighlightEnabled = false
                        }
                        sleepLineData.addDataSet(sleepDataSet)
                    }
                }
                
                // 4. Activity/Exercise Zones (Purple background, 50% height)
                if (activityData != null && activityData.activities.isNotEmpty()) {
                    val activityEntries = mutableListOf<Entry>()
                    activityEntries.add(Entry(0f, 0f))
                    
                    activityData.activities.sortedBy { it.startTime }.forEach { activity ->
                        val startTs = activity.startTime.time
                        val endTs = activity.startTime.time + activity.duration
                        
                        val range = getClampedRange(startTs, endTs)
                        if (range != null) {
                            val (start, end) = range
                            activityEntries.add(Entry(start, 0f))
                            activityEntries.add(Entry(start, zoneHeight))
                            activityEntries.add(Entry(end, zoneHeight))
                            activityEntries.add(Entry(end, 0f))
                        }
                    }
                    
                    activityEntries.add(Entry(1440f, 0f))
                    
                    if (activityEntries.size > 2) {
                        val activityZoneDataSet = LineDataSet(activityEntries, "Activité").apply {
                            setDrawCircles(false)
                            setDrawValues(false)
                            mode = LineDataSet.Mode.LINEAR
                            setDrawFilled(true)
                            fillColor = Color.parseColor("#8E24AA") // Darker Purple for activity
                            color = Color.TRANSPARENT
                            fillAlpha = 60
                            lineWidth = 0f
                            isHighlightEnabled = false
                        }
                        sleepLineData.addDataSet(activityZoneDataSet)
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
