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
import com.github.mikephil.charting.components.LimitLine
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
    selectedDate: Date,
    dateOfBirth: Long?
) {
    // Determine context for notification (optional trigger)
    val context = androidx.compose.ui.platform.LocalContext.current
    
    // Calculate Age for Max HR (Consistent with Main Chart)
    val age = if (dateOfBirth != null && dateOfBirth > 0) {
        val dob = Calendar.getInstance().apply { timeInMillis = dateOfBirth }
        val now = Calendar.getInstance()
        var a = now.get(Calendar.YEAR) - dob.get(Calendar.YEAR)
        if (now.get(Calendar.DAY_OF_YEAR) < dob.get(Calendar.DAY_OF_YEAR)) {
            a--
        }
        a
    } else {
        30 // Default age
    }
    val userMaxHr = 220 - age
    
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
            modifier = Modifier.padding(12.dp)
        ) {
            // --- High Precision Data Processing ---
            val cal = Calendar.getInstance()
            cal.time = activity.startTime
            // Don't zero out seconds! Activity start time might be precise.
            val startTimeMs = activity.startTime.time 
            val durationMs = activity.duration
            val endTimeMs = startTimeMs + durationMs

            // Filter data within range (with 10 min margin)
            val relevantData = allMinuteData.filter { 
                val dataTime = DateUtils.parseTimeToday(it.time)?.time ?: 0L
                // Adjust dataTime to match the activity date (since MinuteData only has time)
                val fullDataTime = DateUtils.combineDateAndTime(selectedDate, dataTime)
                fullDataTime >= startTimeMs && fullDataTime <= (endTimeMs + 10 * 60 * 1000)
            }
            
            // If empty (e.g. no data synced yet), fallback or show empty
            val continuousMinutes = if (relevantData.isNotEmpty()) relevantData else emptyList()

            // Calculate duration in minutes (float) for Stats
            val durationMinutes = durationMs / 60000.0
            
            // Calculate Average HR
            val avgHr = if (activity.averageHeartRate != null && activity.averageHeartRate > 0) {
                activity.averageHeartRate
            } else if (continuousMinutes.isNotEmpty()) {
                 continuousMinutes.map { it.heartRate }.filter { it > 0 }.average().toInt()
            } else 0

            // --- Header ---
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
            
            Spacer(modifier = Modifier.height(8.dp))

            // --- Basic Stats ---
            // Sum steps correctly (avoid double counting if high freq data implies density)
            // But steps are usually 1-min aggregated. If we have multiple points per minute,
            // we risk summing the SAME 1-min step count multiple times if we merged poorly?
            // In Provider, we map distinct times. If Step is at 12:00:00, it appears once.
            // If HR is 12:00:01, Step is 0. So summing is safe.
            val calculatedSteps = continuousMinutes.sumOf { it.steps }
            val displaySteps = if (activity.steps != null && activity.steps > 0) activity.steps else calculatedSteps

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(label = "Pas", value = displaySteps.toString())
                StatItem(label = "Calories", value = "${activity.calories} kcal")
                // DEBUG: Force showing Distance even if null/0 for diagnosis
                val distStr = if (activity.distance != null) String.format("%.2f km", activity.distance) else "N/A"
                StatItem(label = "Distance", value = distStr)
            }

            // --- Speed Stats (Any activity with Distance) ---
            // DEBUG: Relax condition to debug why it might fail. 
            // If distance is explicitly 0 or null, it won't be calculated, but we want to see if we satisfy display conditions.
            
            if (activity.distance != null && activity.distance > 0.0 && durationMinutes > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(8.dp))

                // Calculations
                val hours = durationMs / 3600000.0
                val avgSpeedKmph = activity.distance / hours
                val paceMinPerKm = if (avgSpeedKmph > 0) 60 / avgSpeedKmph else 0.0
                val paceSeconds = ((paceMinPerKm - paceMinPerKm.toInt()) * 60).toInt()

                // Max Speed Estimation
                val avgStrideLengthM = if (displaySteps > 0) (activity.distance * 1000) / displaySteps else 0.0
                // Max steps needs 1-min window aggregation if data is 1sec!
                // We should bucket relevantData by minute to find max steps per minute.
                val maxStepsPerMin = continuousMinutes
                    .groupBy { it.time.substring(0, 5) } // Group by HH:mm
                    .values
                    .maxOfOrNull { list -> list.sumOf { it.steps } } ?: 0
                
                val maxSpeedKmph = (maxStepsPerMin * avgStrideLengthM * 60) / 1000

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Show Both Speed and Pace
                    StatItem(label = "Vitesse", value = String.format("%.1f km/h", avgSpeedKmph))
                    StatItem(label = "Allure", value = String.format("%d'%02d\" /km", paceMinPerKm.toInt(), paceSeconds))
                    if (maxSpeedKmph > 0) {
                       StatItem(label = "Vitesse Max", value = String.format("%.1f km/h", maxSpeedKmph))
                    }
                }
                
                // NOTIFICATION LOGIC
                androidx.compose.runtime.LaunchedEffect(Unit) {
                     try {
                         val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
                             context.applicationContext,
                             NotificationHelperEntryPoint::class.java
                         )
                         entryPoint.getNotificationHelper().showWorkoutSummary(
                             activityName = activity.activityName,
                             duration = DateUtils.formatDuration(activity.duration),
                             distance = String.format("%.2f km", activity.distance),
                             avgHr = avgHr,
                             calories = activity.calories
                         )
                     } catch (e: Exception) {
                         // Fallback
                     }
                }
            } else {
                 // DEBUG: Show why speed isn't showing
                 if (activity.distance == null || activity.distance == 0.0) {
                     Text("Pas de données de distance pour le calcul de vitesse.", style = MaterialTheme.typography.bodySmall, color = androidx.compose.ui.graphics.Color.Gray, modifier = Modifier.padding(top = 4.dp))
                 }
            }
            
            // --- Recovery Stats ---
            // Calculate HR at End, +1 min, +2 min
            val endHrEntry = continuousMinutes.minByOrNull { kotlin.math.abs((DateUtils.combineDateAndTime(selectedDate, DateUtils.parseTimeToday(it.time)?.time ?: 0) - (startTimeMs + durationMs))) }
            
            // Only show recovery if we have data AFTER the end
            if (continuousMinutes.any { (DateUtils.combineDateAndTime(selectedDate, DateUtils.parseTimeToday(it.time)?.time ?: 0)) > (startTimeMs + durationMs + 30000) }) { 
                
                 val oneMinPost = startTimeMs + durationMs + 60000
                 val twoMinPost = startTimeMs + durationMs + 120000
                 
                 val hr1Min = continuousMinutes.minByOrNull { kotlin.math.abs((DateUtils.combineDateAndTime(selectedDate, DateUtils.parseTimeToday(it.time)?.time ?: 0) - oneMinPost)) }?.heartRate
                 val hr2Min = continuousMinutes.minByOrNull { kotlin.math.abs((DateUtils.combineDateAndTime(selectedDate, DateUtils.parseTimeToday(it.time)?.time ?: 0) - twoMinPost)) }?.heartRate
                 
                 val endHr = endHrEntry?.heartRate ?: 0
                 
                 if (endHr > 0 && (hr1Min != null || hr2Min != null)) {
                     Spacer(modifier = Modifier.height(8.dp))
                     HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                     Spacer(modifier = Modifier.height(8.dp))
                     
                     Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                         Text("Récupération", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                         Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                             if (hr1Min != null && hr1Min > 0) {
                                 val drop = endHr - hr1Min
                                 Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                     Text("$hr1Min bpm", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                     Text("1 min (${if(drop>=0) "-" else "+"}$drop)", style = MaterialTheme.typography.labelSmall, color = if(drop>0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                                 }
                             }
                             if (hr2Min != null && hr2Min > 0) {
                                 val drop = endHr - hr2Min
                                 Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                     Text("$hr2Min bpm", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                     Text("2 min (${if(drop>=0) "-" else "+"}$drop)", style = MaterialTheme.typography.labelSmall, color = if(drop>0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                                 }
                             }
                         }
                     }
                 }
            }

            // --- Chart ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(top = 16.dp)
            ) {
                ActivityHeartRateChart(
                    activityMinutes = continuousMinutes,
                    activityStartTime = activity.startTime.time, // Pass start time for X-axis ref
                    cutoffIndex = durationMinutes.toFloat(), // Float minutes
                    userMaxHr = userMaxHr,
                    selectedDate = selectedDate
                )
            }
        }
    }
}

// Hilt Entry Point Interface for UI-injected access (Defining here nicely or it needs to be in a separate file)
@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface NotificationHelperEntryPoint {
    fun getNotificationHelper(): com.cardio.fitbit.utils.NotificationHelper
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ActivityHeartRateChart(
    activityMinutes: List<MinuteData>, 
    activityStartTime: Long,
    cutoffIndex: Float,
    userMaxHr: Int,
    selectedDate: Date
) {
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
                setExtraOffsets(0f, 0f, 0f, 0f)
                minOffset = 0f
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
                    
                    // Add LimitLine for Activity End
                    removeAllLimitLines()
                    val limitLine = LimitLine(cutoffIndex, "Fin").apply {
                        lineWidth = 1f
                        lineColor = Color.DKGRAY
                        enableDashedLine(10f, 10f, 0f)
                        labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
                        textSize = 10f
                    }
                    addLimitLine(limitLine)
                    
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            // value is Minutes from Start
                            val timeMs = activityStartTime + (value * 60000).toLong()
                            val date = Date(timeMs)
                            val format = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                            return format.format(date)
                        }
                    }
                }

                axisLeft.apply {
                    setDrawGridLines(true)
                    gridColor = Color.LTGRAY
                    textColor = Color.GRAY
                    textSize = 10f
                    axisMinimum = 40f
                    val maxHrInGraph = activityMinutes.maxOfOrNull { it.heartRate } ?: 150
                    axisMaximum = (maxHrInGraph + 10).toFloat().coerceAtLeast(100f)
                }

                axisRight.apply {
                    isEnabled = true
                    setDrawLabels(false)
                    setDrawGridLines(false)
                    textColor = Color.GRAY
                    axisMinimum = 0f
                    val maxSteps = activityMinutes.maxOfOrNull { it.steps } ?: 50
                    axisMaximum = (maxSteps * 1.5f).coerceAtLeast(10f)
                }
                
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

            // 1. Bar Data (HR) High Precision
            val hrEntries = mutableListOf<BarEntry>()
            
            activityMinutes.forEach { data ->
                if (data.heartRate > 0) {
                    val dataTime = DateUtils.parseTimeToday(data.time)?.time ?: 0L
                    val fullDataTime = DateUtils.combineDateAndTime(selectedDate, dataTime)
                    val diffMin = (fullDataTime - activityStartTime) / 60000f // Float index
                    
                    if (diffMin >= 0) {
                        hrEntries.add(BarEntry(diffMin, data.heartRate.toFloat(), data))
                    }
                }
            }

            // Color Interpolation Function (Copied from HeartRateDetailChart)
            fun interpolateColor(color1: Int, color2: Int, fraction: Float): Int {
                val a = (Color.alpha(color1) + (Color.alpha(color2) - Color.alpha(color1)) * fraction).toInt()
                val r = (Color.red(color1) + (Color.red(color2) - Color.red(color1)) * fraction).toInt()
                val g = (Color.green(color1) + (Color.green(color2) - Color.green(color1)) * fraction).toInt()
                val b = (Color.blue(color1) + (Color.blue(color2) - Color.blue(color1)) * fraction).toInt()
                return Color.argb(a, r, g, b)
            }

            fun getHeartRateColor(bpm: Float): Int {
                val maxHr = userMaxHr.toFloat()
                
                val zoneStart = maxHr * 0.30f
                val zone1End = maxHr * 0.40f
                val zone2End = maxHr * 0.50f
                val zone3End = maxHr * 0.65f
                val zone4End = maxHr * 0.80f

                val colorBlue = Color.parseColor("#42A5F5")
                val colorCyan = Color.parseColor("#06B6D4")
                val colorGreen = Color.parseColor("#10B981")
                val colorYellow = Color.parseColor("#FFD600")
                val colorOrange = Color.parseColor("#F59E0B")
                val colorRed = Color.parseColor("#EF4444")

                return when {
                    bpm < zoneStart -> colorBlue
                    bpm < zone1End -> interpolateColor(colorCyan, colorGreen, (bpm - zoneStart) / (zone1End - zoneStart))
                    bpm < zone2End -> interpolateColor(colorGreen, colorYellow, (bpm - zone1End) / (zone2End - zone1End))
                    bpm < zone3End -> interpolateColor(colorYellow, colorOrange, (bpm - zone2End) / (zone3End - zone2End))
                    bpm < zone4End -> interpolateColor(colorOrange, colorRed, (bpm - zone3End) / (zone4End - zone3End))
                    else -> colorRed
                }
            }

            if (hrEntries.isNotEmpty()) {
                val hrDataSet = BarDataSet(hrEntries, "Heart Rate").apply {
                    // Apply fluid colors
                    val colors = hrEntries.map { entry -> getHeartRateColor(entry.y) }
                    setColors(colors)
                    
                    setDrawValues(false)
                    axisDependency = YAxis.AxisDependency.LEFT
                    isHighlightEnabled = true
                    highLightColor = Color.GRAY
                }
                val barData = BarData(hrDataSet)
                // Bar width optimization for visibility
                val interval = if (activityMinutes.size > 1) {
                    val t1 = (DateUtils.parseTimeToday(activityMinutes[0].time)?.time ?: 0)
                    val t2 = (DateUtils.parseTimeToday(activityMinutes[1].time)?.time ?: 0)
                    ((t2 - t1) / 60000f).coerceAtLeast(0.001f)
                } else 0.016f // Default 1 sec
                
                // Use slightly wider bars to prevent gaps/antialiasing fade
                barData.barWidth = interval * 1.0f 
                combinedData.setData(barData)
            }

            // 2. Steps Line (Purple)
            val stepEntries = mutableListOf<Entry>()
             activityMinutes.forEach { data ->
                if (data.steps > 0) {
                     val dataTime = DateUtils.parseTimeToday(data.time)?.time ?: 0L
                    val fullDataTime = DateUtils.combineDateAndTime(selectedDate, dataTime)
                    val diffMin = (fullDataTime - activityStartTime) / 60000f
                    
                    if (diffMin >= 0) {
                        stepEntries.add(Entry(diffMin, data.steps.toFloat(), data))
                    }
                }
            }

            if (stepEntries.isNotEmpty()) {
                val stepDataSet = LineDataSet(stepEntries, "Steps").apply {
                    color = Color.parseColor("#9C27B0")
                    setCircleColor(Color.parseColor("#9C27B0"))
                    circleRadius = 1.5f
                    setDrawCircles(false)
                    setDrawValues(false)
                    lineWidth = 2f
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                    axisDependency = YAxis.AxisDependency.RIGHT
                    isHighlightEnabled = false
                }
                combinedData.setData(LineData(stepDataSet))
            }

            chart.data = combinedData
            chart.invalidate()
        }
    )
}
