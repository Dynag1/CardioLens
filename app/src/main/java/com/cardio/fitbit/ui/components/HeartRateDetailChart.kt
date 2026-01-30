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
import androidx.compose.ui.graphics.toArgb
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
    userMaxHr: Int = 220,
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

    // Resolve colors from Theme (Must be outside AndroidView update)
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f).toArgb()

    Column {
        AndroidView(
            factory = { context ->
                CombinedChart(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    
                    setBackgroundColor(Color.TRANSPARENT)
                    description.isEnabled = false
                    legend.isEnabled = false
                    
                    setTouchEnabled(true)
                    isDragEnabled = false // We'll handle everything manually
                    setScaleEnabled(false) // Disable library zoom, we'll do it manually
                    setPinchZoom(false)
                    isDoubleTapToZoomEnabled = false
                    isHighlightPerDragEnabled = true
                    
                    // Variables to track gestures
                    var initialFingerDistance = 0f
                    var lastFingerDistance = 0f
                    var isZoomingActive = false
                    var lastTouchX = 0f
                    var isPanning = false
                    
                    // Helper to calculate distance between two fingers
                    fun getFingerSpacing(event: android.view.MotionEvent): Float {
                        if (event.pointerCount < 2) return 0f
                        val x = event.getX(0) - event.getX(1)
                        val y = event.getY(0) - event.getY(1)
                        return kotlin.math.sqrt(x * x + y * y)
                    }
                    
                    // Get center X of two fingers
                    fun getCenterX(event: android.view.MotionEvent): Float {
                        return if (event.pointerCount >= 2) {
                            (event.getX(0) + event.getX(1)) / 2f
                        } else event.x
                    }
                    
                    // 1 finger = highlight, 2 fingers = manual pan or zoom
                    setOnTouchListener { v, event ->
                        when (event.action and android.view.MotionEvent.ACTION_MASK) {
                            android.view.MotionEvent.ACTION_DOWN -> {
                                v.parent.requestDisallowInterceptTouchEvent(true)
                            }
                            android.view.MotionEvent.ACTION_POINTER_DOWN -> {
                                // Second finger down
                                val distance = getFingerSpacing(event)
                                initialFingerDistance = distance
                                lastFingerDistance = distance
                                lastTouchX = getCenterX(event)
                                isZoomingActive = false
                                isPanning = false
                            }
                            android.view.MotionEvent.ACTION_MOVE -> {
                                if (event.pointerCount >= 2) {
                                    val currentDistance = getFingerSpacing(event)
                                    val distanceChange = kotlin.math.abs(currentDistance - initialFingerDistance)
                                    
                                    // Detect zoom intent
                                    if (distanceChange > 100f) {
                                        // Zoom mode
                                        isZoomingActive = true
                                        isPanning = false
                                        
                                        // Calculate zoom factor
                                        if (lastFingerDistance > 0f) {
                                            val scaleFactor = currentDistance / lastFingerDistance
                                            
                                            // Apply zoom centered on touch point
                                            val centerX = getCenterX(event)
                                            val touchValue = getValuesByTouchPoint(centerX, 0f, YAxis.AxisDependency.LEFT)
                                            
                                            zoom(scaleFactor, 1f, touchValue.x.toFloat(), 0f, YAxis.AxisDependency.LEFT)
                                        }
                                        
                                        lastFingerDistance = currentDistance
                                    } else {
                                        // Pan mode - handle manually
                                        isPanning = true
                                        val currentX = getCenterX(event)
                                        val deltaX = currentX - lastTouchX
                                        
                                        if (kotlin.math.abs(deltaX) > 5f) { // Threshold to avoid jitter
                                            // Calculate how much to scroll
                                            val pixelsToScroll = -deltaX
                                            val valueToScroll = pixelsToScroll / viewPortHandler.scaleX
                                            
                                            // Get current view position
                                            val currentLowest = lowestVisibleX
                                            
                                            // Move view
                                            moveViewToX(currentLowest + valueToScroll)
                                            
                                            lastTouchX = currentX
                                        }
                                    }
                                } else {
                                    // 1 finger: highlight only
                                    val h = getHighlightByTouchPoint(event.x, event.y)
                                    if (h != null) {
                                        highlightValue(h, true)
                                    }
                                }
                            }
                            android.view.MotionEvent.ACTION_POINTER_UP -> {
                                isZoomingActive = false
                                isPanning = false
                            }
                            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                                isZoomingActive = false
                                isPanning = false
                                v.parent.requestDisallowInterceptTouchEvent(false)
                            }
                        }
                        true // Consume all events
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
                        CombinedChart.DrawOrder.BAR,  // HR bars (Data first)
                        CombinedChart.DrawOrder.LINE, // Zones (Foreground)
                        CombinedChart.DrawOrder.SCATTER // Bubbles on very top
                    )

                    // X-Axis
                    xAxis.position = XAxis.XAxisPosition.BOTTOM
                    xAxis.setDrawGridLines(false)
                    xAxis.axisMinimum = 0f
                    xAxis.axisMaximum = 1440f // Full 24 hours (24 * 60)
                    axisLeft.setDrawGridLines(true)
                    
                    // Right Y (Steps) - Scaled
                    axisRight.isEnabled = false 
                    axisRight.axisMinimum = 0f
                    
                    // Value Selected Listener
                    setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                        override fun onValueSelected(e: Entry?, h: Highlight?) {
                            if (e == null) return
                            
                            val data = e.data
                            if (data is com.cardio.fitbit.data.models.MinuteData) {
                                selectedPoint = data
                            } else if (data is Int) {
                                // It's a Step Entry with total steps
                                // We need to construct a pseudo-MinuteData or handle it.
                                // Reconstruct time string from X value
                                val totalMinutes = e.x.toInt()
                                val hours = totalMinutes / 60
                                val minutes = totalMinutes % 60
                                val timeStr = String.format("%02d:%02d:00", hours, minutes)
                                
                                // Create a dummy MinuteData with the total steps
                                selectedPoint = com.cardio.fitbit.data.models.MinuteData(
                                    time = timeStr,
                                    heartRate = 0, // No HR for step bar
                                    steps = data
                                )
                            }
                        }

                        override fun onNothingSelected() {
                            selectedPoint = null
                        }
                    })
                }
            },
            update = { chart ->
                if (minuteData.isEmpty()) {
                    chart.clear()
                    return@AndroidView
                }

                chart.xAxis.textColor = textColor
                chart.xAxis.axisMinimum = 0f
                chart.xAxis.axisMaximum = 1440f
                chart.axisLeft.textColor = textColor
                chart.axisLeft.gridColor = gridColor
                
                // Detect zoom level and choose appropriate data granularity
                val scaleX = chart.scaleX
                val activeList = when {
                    scaleX > 8f -> minuteData  // Very zoomed: use 1-minute data
                    scaleX > 3f -> minuteData  // Medium zoom: use 1-minute data
                    else -> aggregatedData     // Normal view: use 5-minute aggregated data
                }
                
                // OPTIMIZATION: Check tag
                val dataHash = (activeList.hashCode() + scaleX.toInt()).toString() + "_" + selectedPoint?.hashCode()
                
                if (chart.tag == dataHash) {
                     return@AndroidView
                }
                chart.tag = dataHash
                
                
                val combinedData = CombinedData()

                // Calculate dynamic Y-axis max
                val maxHr = activeList.maxOfOrNull { it.heartRate } ?: 100
                val finalYMax = (maxHr + 10).toFloat().coerceAtLeast(110f) // Min 110 to ensure zones visible
                chart.axisLeft.axisMaximum = finalYMax
                chart.axisLeft.axisMinimum = 0f // Force 0 baseline to align with Steps
                val zoneHeight = 40f + (finalYMax - 40f) * 0.75f // Cover 75% of height (taller)

                // Helper function to convert time string to index (float minutes)
                fun timeToIndex(time: String): Float {
                    val parts = time.split(":")
                    if (parts.size < 2) return 0f
                    val hours = parts[0].toInt()
                    val minutes = parts[1].toInt()
                    val seconds = if (parts.size > 2) parts[2].toInt() else 0
                    return (hours * 60 + minutes + seconds / 60f)
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
                    // Force Alpha to 255 (Opaque)
                    val r = (Color.red(color1) + (Color.red(color2) - Color.red(color1)) * fraction).toInt()
                    val g = (Color.green(color1) + (Color.green(color2) - Color.green(color1)) * fraction).toInt()
                    val b = (Color.blue(color1) + (Color.blue(color2) - Color.blue(color1)) * fraction).toInt()
                    return Color.argb(255, r, g, b)
                }

                fun getHeartRateColor(bpm: Float): Int {
                    // Zones based on Max HR (Aggressive Scaling for Visual Feedback)
                    // We want visual variation even at lower heart rates.
                    
                    val maxHr = userMaxHr.toFloat()
                    
                    // Standard Zones are:
                    // Zone 1: 50-60% (Warm Up)
                    // Zone 2: 60-70% (Fat Burn)
                    // Zone 3: 70-80% (Cardio)
                    // Zone 4: 80-90% (Hard)
                    // Zone 5: 90-100% (Peak)
                    
                    // Adjusted for Visuals (Gradient starts earlier):
                    val zoneStart = maxHr * 0.30f // Start showing color changes from 30% (Blue -> Cyan)
                    val zone1End = maxHr * 0.40f  // 40% (Cyan -> Green)
                    val zone2End = maxHr * 0.50f  // 50% (Green -> Yellow) - "Active" starts here
                    val zone3End = maxHr * 0.65f  // 65% (Yellow -> Orange)
                    val zone4End = maxHr * 0.80f  // 80% (Orange -> Red) - "Intense" starts here

                    val colorBlue = Color.parseColor("#42A5F5")   // < 30% (Deep sleep / heavy rest)
                    val colorCyan = Color.parseColor("#06B6D4")   // 30-40% (Resting)
                    val colorGreen = Color.parseColor("#10B981")  // 40-50% (Very Light)
                    val colorYellow = Color.parseColor("#FFD600") // 50-65% (Light / Moderate)
                    val colorOrange = Color.parseColor("#F59E0B") // 65-80% (Moderate / Hard)
                    val colorRed = Color.parseColor("#EF4444")    // > 80% (Peak)

                    return when {
                        // Below Zone 1 (Resting / Sleeping)
                        bpm < zoneStart -> colorBlue
                        
                        // Zone 0-1 Transition (Blue -> Cyan)
                        bpm < zone1End -> interpolateColor(colorCyan, colorGreen, (bpm - zoneStart) / (zone1End - zoneStart))
                        
                        // Zone 1-2 Transition (Green -> Yellow)
                        bpm < zone2End -> interpolateColor(colorGreen, colorYellow, (bpm - zone1End) / (zone2End - zone1End))
                        
                        // Zone 2-3 Transition (Yellow -> Orange)
                        bpm < zone3End -> interpolateColor(colorYellow, colorOrange, (bpm - zone2End) / (zone3End - zone2End))
                        
                        // Zone 3-4 Transition (Orange -> Red)
                        bpm < zone4End -> interpolateColor(colorOrange, colorRed, (bpm - zone3End) / (zone4End - zone3End))
                        
                        // Peak
                        else -> colorRed
                    }
                }
                
                val hrDataSet = BarDataSet(hrEntries, "HR").apply {
                    setDrawValues(false)
                    // Fluid Gradient Colors
                    val colors = hrEntries.map { entry -> getHeartRateColor(entry.y) }
                    setColors(colors)
                    isHighlightEnabled = true
                }

                // 2. Steps (As grouped BarDataSet)
                // Group data into 10-minute buckets
                val groupedSteps = activeList
                    .groupBy { 
                        // Calculate 10-minute bucket key
                        val totalMinutes = (timeToIndex(it.time)).toInt()
                        totalMinutes / 10 
                    }
                    .flatMap { (bucketIndex, entries) ->
                        // Sum steps for the bucket
                        val totalSteps = entries.sumOf { it.steps }
                        
                        if (totalSteps > 0) {
                            // Generate 10 entries (one per minute) to create a visual "block" of 10 minutes width
                            val startMinute = bucketIndex * 10
                            (0 until 10).map { offset ->
                                val timePos = (startMinute + offset).toFloat()
                                // Store total steps in data for color/tooltip
                                // Use fixed height (30f) as requested (was 20f), color indicates intensity
                                BarEntry(timePos, 30f, totalSteps)
                            }
                        } else {
                            emptyList()
                        }
                    }

                val maxStepsInView = groupedSteps.maxByOrNull { it.data as Int }?.data as? Int ?: 1

                val stepDataSet = BarDataSet(groupedSteps, "Steps").apply {
                     setDrawValues(false)
                    axisDependency = YAxis.AxisDependency.RIGHT
                    isHighlightEnabled = false
                    
                    // Dynamic Gradient Colors (Solid opacity)
                    val stepColors = groupedSteps.map { entry ->
                        val steps = entry.data as Int
                        val fraction = (steps.toFloat() / maxStepsInView).coerceIn(0f, 1f)
                        
                        // Light Purple: #E1BEE7 (RGB: 225, 190, 231)
                        // Deep Purple:  #4A148C (RGB: 74, 20, 140)
                        
                        val startR = 225
                        val startG = 190
                        val startB = 231
                        
                        val endR = 74
                        val endG = 20
                        val endB = 140
                        
                        val r = (startR + (endR - startR) * fraction).toInt()
                        val g = (startG + (endG - startG) * fraction).toInt()
                        val b = (startB + (endB - startB) * fraction).toInt()
                        
                        Color.rgb(r, g, b)
                    }
                    setColors(stepColors)
                }
                
                // Configure Right Axis for Steps (Hidden, 0-500 scale)
                chart.axisRight.apply {
                    isEnabled = true // Enable axis logic
                    setDrawLabels(false) // Hide labels
                    setDrawGridLines(false) // Hide grid
                    setDrawAxisLine(false) // Hide line
                    axisMinimum = 0f
                    axisMaximum = 400f // Fixed scale: 20f bars will take 5% of height
                }

                // Combine both in BarData (Order: HR first, then Steps on top)
                val barData = BarData(hrDataSet, stepDataSet)
                
                // Dynamic Bar Width
                // Scan a subset to find minimum interval (e.g. 1sec vs 1min)
                // If we have mixed data, we must use the smallest interval to avoid overlaps.
                val interval = if (activeList.size > 1) {
                    // Check first 20 points to find min delta
                    val checkCount = minOf(activeList.size, 20)
                    var minDelta = 100f // Start large
                    
                    for (i in 0 until checkCount - 1) {
                        val t1 = timeToIndex(activeList[i].time)
                        val t2 = timeToIndex(activeList[i+1].time)
                        val delta = (t2 - t1)
                        if (delta > 0.0001f && delta < minDelta) {
                            minDelta = delta
                        }
                    }
                    // 1 second is ~0.0166 min. 
                    minDelta.coerceAtLeast(0.016f)
                } else 1f
                
                // Dynamic Bar Width
                // Use 1.0f (exact) or slightly less to allow spacing?
                // For high precision (dense), we want them to touch -> 1.0f
                // For 1min with 1min gap -> 0.9f looks better?
                // Complication: If we set width to 0.016 (1 sec) but have 1min gap elsewhere, those 1min bars will be very thin.
                // WE WANT THIN BARS for accuracy or VARIABLE WIDTH?
                // BarChart doesn't support variable width per entry easily in one dataset.
                // So if we have mixed resolution, the low-res (1min) points will appear as thin lines (1sec width).
                // This is actually Desired Behavior per plan: "1-minute data points may appear thinner... compared to the dense... workout data"
                
                barData.barWidth = (interval * 1.0f)

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
                    sleepSessions.sortedBy { it.startTime }.forEach { session ->
                        val range = getClampedRange(session.startTime.time, session.endTime.time)
                        if (range != null) {
                            val (start, end) = range
                            
                            val duration = end - start
                            val slant = minOf(5f, duration * 0.3f)
                            
                            val sleepEntries = listOf(
                                Entry(start, 0f),
                                Entry(start + slant, zoneHeight),
                                Entry(end - slant, zoneHeight),
                                Entry(end, 0f)
                            )

                            val sleepDataSet = LineDataSet(sleepEntries, "Sommeil").apply {
                                setDrawCircles(false)
                                setDrawValues(false)
                                mode = LineDataSet.Mode.LINEAR 
                                setDrawFilled(true)
                                fillColor = Color.parseColor("#42A5F5") // Solid Blue for sleep
                                color = Color.parseColor("#1565C0")
                                fillAlpha = 40
                                lineWidth = 0f
                                isHighlightEnabled = false
                            }
                            sleepLineData.addDataSet(sleepDataSet)
                        }
                    }
                }
                
                // 4. Activity/Exercise Zones (Purple background, 50% height)
                // 4. Activity/Exercise Zones (Purple background, 50% height)
                if (activityData != null && activityData.activities.isNotEmpty()) {
                    
                    activityData.activities.sortedBy { it.startTime }.forEach { activity ->
                        val startTs = activity.startTime.time
                        val endTs = activity.startTime.time + activity.duration
                        
                        val range = getClampedRange(startTs, endTs)
                        if (range != null) {
                            val (start, end) = range
                            
                            val duration = end - start
                            val slant = minOf(5f, duration * 0.3f)

                            val activityEntries = listOf(
                                Entry(start, 0f),
                                Entry(start + slant, zoneHeight),
                                Entry(end - slant, zoneHeight),
                                Entry(end, 0f)
                            )
                            
                            val activityZoneDataSet = LineDataSet(activityEntries, "Activité").apply {
                                setDrawCircles(false)
                                setDrawValues(false)
                                mode = LineDataSet.Mode.LINEAR
                                setDrawFilled(true)
                                fillColor = Color.parseColor("#AB47BC") // Lighter Purple
                                color = Color.parseColor("#7B1FA2")
                                fillAlpha = 50 // Slightly transparent fill
                                lineWidth = 1.5f // Thinner solid border (was 3f)
                                isHighlightEnabled = false
                            }
                            sleepLineData.addDataSet(activityZoneDataSet)
                        }
                    }
                }
                
                // 5. Min/Max Markers (Green/Red Triangles)
                val scatterData = ScatterData()
                
                if (activeList.isNotEmpty()) {
                    val validData = activeList.filter { it.heartRate > 0 }
                    
                    if (validData.isNotEmpty()) {
                        val maxVal = validData.maxByOrNull { it.heartRate }!!
                        val minVal = validData.minByOrNull { it.heartRate }!!
                        
                        // Min Marker (Green)
                        val minEntry = Entry(timeToIndex(minVal.time), minVal.heartRate.toFloat())
                        val minDataSet = ScatterDataSet(listOf(minEntry), "Min").apply {
                            setScatterShape(ScatterChart.ScatterShape.TRIANGLE)
                            color = Color.parseColor("#10B981") // Green
                            scatterShapeSize = 15f
                            setDrawValues(false)
                            isHighlightEnabled = false
                        }
                        
                        // Max Marker (Red)
                        val maxEntry = Entry(timeToIndex(maxVal.time), maxVal.heartRate.toFloat())
                        val maxDataSet = ScatterDataSet(listOf(maxEntry), "Max").apply {
                            setScatterShape(ScatterChart.ScatterShape.TRIANGLE) // Inverted triangle logic would be better but standard triangle is fine
                            color = Color.parseColor("#EF4444") // Red
                            scatterShapeSize = 15f
                            setDrawValues(false)
                            isHighlightEnabled = false
                        }
                        
                        scatterData.addDataSet(minDataSet)
                        scatterData.addDataSet(maxDataSet)
                    }
                }
                
                // Final Assembly
                combinedData.setData(sleepLineData)
                combinedData.setData(barData)
                combinedData.setData(scatterData)
                
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
        

    }
}
