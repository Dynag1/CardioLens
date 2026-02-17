package com.cardio.fitbit.ui.components

import android.graphics.Color
import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import java.util.*

@Composable
fun ActivityDetailCard(
    activity: Activity,
    allMinuteData: List<MinuteData>,
    selectedDate: Date,
    dateOfBirth: Long?,
    onIntensityChange: ((Long, Int) -> Unit)? = null
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
    
    // State for Reference Expansion
    var isExpanded by remember { androidx.compose.runtime.mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .animateContentSize()
            .clickable { isExpanded = !isExpanded },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // --- High Precision Data Processing ---
            val startTimeMs = activity.startTime.time 
            val durationMs = activity.duration
            val endTimeMs = startTimeMs + durationMs

            // Filter data within range (with 10 min margin) - OPTIMIZED: Only if expanded
            val relevantData = remember(activity.startTime, activity.duration, allMinuteData, selectedDate, isExpanded) {
                if (isExpanded) {
                     allMinuteData.filter { 
                        val dataTime = DateUtils.parseTimeToday(it.time)?.time ?: 0L
                        val fullDataTime = DateUtils.combineDateAndTime(selectedDate, dataTime)
                        // Extend window to 20 mins to ensuring capturing the 10-min recovery point properly
                        fullDataTime >= startTimeMs && fullDataTime <= (endTimeMs + 20 * 60 * 1000)
                    }
                } else {
                    emptyList()
                }
            }
            
            val continuousMinutes = if (relevantData.isNotEmpty()) relevantData else emptyList()

            // Calculate duration in minutes (float) for Stats
            val durationMinutes = durationMs / 60000.0
            
            // Calculate Average HR
            val avgHr = remember(activity.averageHeartRate, continuousMinutes) {
                 if (activity.averageHeartRate != null && activity.averageHeartRate > 0) {
                    activity.averageHeartRate
                } else if (continuousMinutes.isNotEmpty()) {
                     continuousMinutes.map { it.heartRate }.filter { it > 0 }.average().toInt()
                } else 0
            }

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
                    // Intensity Display
                    val intensity = activity.intensity
                    if (intensity != null) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text(
                                "Intensité: ",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            repeat(5) { index ->
                                val starIndex = index + 1 // 1-5 scale
                                Icon(
                                    imageVector = if (starIndex <= (intensity ?: 0)) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                    contentDescription = "Intensité $starIndex",
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clickable(enabled = onIntensityChange != null) {
                                            onIntensityChange?.invoke(activity.activityId, starIndex)
                                        }
                                        .padding(horizontal = 1.dp),
                                    tint = if (starIndex <= (intensity ?: 0)) {
                                        when {
                                            (intensity ?: 0) >= 4 -> androidx.compose.ui.graphics.Color(0xFFEF4444) // Red for high intensity
                                            (intensity ?: 0) >= 3 -> androidx.compose.ui.graphics.Color(0xFFF59E0B) // Orange for moderate
                                            else -> androidx.compose.ui.graphics.Color(0xFF10B981) // Green for light
                                        }
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    }
                                )
                            }
                        }
                    } else if (onIntensityChange != null) {
                        // Show empty stars if no intensity is set yet, but editing is enabled
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text(
                                "Intensité: ",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            repeat(5) { index ->
                                val starIndex = index + 1
                                Icon(
                                    imageVector = Icons.Outlined.StarOutline,
                                    contentDescription = "Définir intensité $starIndex",
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clickable {
                                            onIntensityChange.invoke(activity.activityId, starIndex)
                                        }
                                        .padding(horizontal = 1.dp),
                                    tint = MaterialTheme.colorScheme.outlineVariant
                                )
                            }
                        }
                    }
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
                        // Precision Indicator
                        val isHighPrecision = continuousMinutes.size > durationMinutes + 2 // Heuristic: more points than minutes
                        if (isExpanded) {
                             Text(
                                text = if (isHighPrecision) "Précision: 1s" else "Précision: 1min",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isHighPrecision) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
            
            // --- Zones Distribution (New) ---
            if (avgHr > 0) {
                 val maxHr = userMaxHr.toFloat()
                 val zoneStart = maxHr * 0.30f
                 val zone1End = maxHr * 0.40f
                 val zone2End = maxHr * 0.50f
                 val zone3End = maxHr * 0.65f
                 val zone4End = maxHr * 0.80f
                 
                 // Calculate time in each zone
                 var z0 = 0 // < 30% (Sedentary/Rest)
                 var z1 = 0 // 30-40% 
                 var z2 = 0 // 40-50% (Fat Burn)
                 var z3 = 0 // 50-65% (Cardio)
                 var z4 = 0 // 65-80% (Peak)
                 var z5 = 0 // > 80% (Max) - Sometimes merged with Peak
                 
                 continuousMinutes.forEach { 
                     val hr = it.heartRate
                     if (hr > 0) {
                         when {
                             hr < zoneStart -> z0++
                             hr < zone1End -> z1++
                             hr < zone2End -> z2++
                             hr < zone3End -> z3++
                             hr < zone4End -> z4++
                             else -> z5++
                         }
                     }
                 }
                 
                 val total = (z0+z1+z2+z3+z4+z5).coerceAtLeast(1)
                 
                 if (isExpanded) {
                     Spacer(modifier = Modifier.height(12.dp))
                     Text("Zones Cardiaques", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                     Spacer(modifier = Modifier.height(4.dp))
                     
                     Row(modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp))) {
                         // Drawing Segments
                         val weights = listOf(z0, z1, z2, z3, z4, z5)
                         val colors = listOf(
                             android.graphics.Color.LTGRAY, 
                             android.graphics.Color.parseColor("#42A5F5"), 
                             android.graphics.Color.parseColor("#10B981"), 
                             android.graphics.Color.parseColor("#FFD600"), 
                             android.graphics.Color.parseColor("#F59E0B"), 
                             android.graphics.Color.parseColor("#EF4444")
                         )
                         
                         weights.forEachIndexed { index, count ->
                             if (count > 0) {
                                 Box(modifier = Modifier.weight(count.toFloat()).fillMaxHeight().background(androidx.compose.ui.graphics.Color(colors[index])))
                             }
                         }
                     }
                     Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                         // Only show labels for dominant zones (>10%)
                         // Simplified Labels underneath
                         val cardioSum = z3+z4+z5
                         val fatBurnSum = z1+z2
                         
                         val totalF = total.toFloat()
                         Text("FatBurn ${(fatBurnSum*100/totalF).toInt()}%", style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color(0xFF10B981))
                         Text("Cardio ${(cardioSum*100/totalF).toInt()}%", style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color(0xFFF59E0B))
                     }
                 }
            }
            
            Spacer(modifier = Modifier.height(8.dp))



            // --- Basic Stats ---
            val calculatedSteps = continuousMinutes.sumOf { it.steps }
            val displaySteps = if (activity.steps != null && activity.steps > 0) activity.steps else calculatedSteps

            // Logic to determine if activity is "walking" based on step density
            // User requirement: < 60% steps on duration treated as non-walking
            // Threshold: 0.6 steps per second (36 steps/min)
            val durationSeconds = durationMs / 1000.0
            val stepsPerSecond = if (durationSeconds > 0) displaySteps / durationSeconds else 0.0
            val isWalking = stepsPerSecond >= 0.6

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                if (isWalking) {
                    StatItem(label = "Pas", value = displaySteps.toString(), icon = Icons.Filled.DirectionsRun)
                }
                StatItem(label = "Calories", value = "${activity.calories}", icon = Icons.Filled.LocalFireDepartment)
                
                if (isWalking) {
                    val distStr = if (activity.distance != null) String.format("%.2f km", activity.distance) else "N/A"
                    StatItem(label = "Distance", value = distStr, icon = Icons.Filled.Straighten)
                }
            }

            // --- Speed Stats ---
            if (isWalking && activity.distance != null && activity.distance > 0.0 && durationMinutes > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(8.dp))

                val hours = durationMs / 3600000.0
                val avgSpeedKmph = activity.distance / hours
                val paceMinPerKm = if (avgSpeedKmph > 0) 60 / avgSpeedKmph else 0.0
                val paceSeconds = ((paceMinPerKm - paceMinPerKm.toInt()) * 60).toInt()

                // Max Speed Estimation
                val maxSpeedKmph = if (continuousMinutes.isNotEmpty()) {
                    val avgStrideLengthM = if (displaySteps > 0) (activity.distance * 1000) / displaySteps else 0.0
                    val maxStepsPerMin = continuousMinutes
                        .groupBy { it.time.substring(0, 5) }
                        .values
                        .maxOfOrNull { list -> list.sumOf { it.steps } } ?: 0
                    
                    (maxStepsPerMin * avgStrideLengthM * 60) / 1000
                } else 0.0

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(label = "Vitesse", value = String.format("%.1f", avgSpeedKmph), icon = Icons.Filled.Speed)
                    StatItem(label = "Allure", value = String.format("%d'%02d\"", paceMinPerKm.toInt(), paceSeconds), icon = Icons.Filled.Timer)
                    if (maxSpeedKmph > 0) {
                       StatItem(label = "V.Max", value = String.format("%.1f", maxSpeedKmph), icon = Icons.Filled.Speed)
                    }
                }
            } else {
                 if (activity.distance == null || activity.distance == 0.0) {
                     Text(" ", style = MaterialTheme.typography.bodySmall)
                 }
            }

            // --- Recovery Stats ---
            if (isExpanded) {
                // Calculate HR at End, +1 min, +2 min
                val endHrEntry = continuousMinutes.minByOrNull { kotlin.math.abs((DateUtils.combineDateAndTime(selectedDate, DateUtils.parseTimeToday(it.time)?.time ?: 0) - (startTimeMs + durationMs))) }
                
                // Only show recovery if we have data AFTER the end
                if (continuousMinutes.any { (DateUtils.combineDateAndTime(selectedDate, DateUtils.parseTimeToday(it.time)?.time ?: 0)) > (startTimeMs + durationMs + 30000) }) { 
                    
                     val oneMinPost = startTimeMs + durationMs + 60000
                     val twoMinPost = startTimeMs + durationMs + 120000
                     val fiveMinPost = startTimeMs + durationMs + 300000
                     val tenMinPost = startTimeMs + durationMs + 600000
                     
                     val getHr = { target: Long -> 
                         continuousMinutes.minByOrNull { 
                            kotlin.math.abs((DateUtils.combineDateAndTime(selectedDate, DateUtils.parseTimeToday(it.time)?.time ?: 0) - target)) 
                         }?.let { match ->
                            val matchTime = DateUtils.combineDateAndTime(selectedDate, DateUtils.parseTimeToday(match.time)?.time ?: 0)
                            if (kotlin.math.abs(matchTime - target) <= 90000) match.heartRate else null // Allow up to 90s tolerance
                         }
                     }

                     val hr1Min = getHr(oneMinPost)
                     val hr2Min = getHr(twoMinPost)
                     val hr5Min = getHr(fiveMinPost)
                     val hr10Min = getHr(tenMinPost)
                     
                     val endHr = endHrEntry?.heartRate ?: 0
                     
                     if (endHr > 0 && (hr1Min != null || hr2Min != null || hr5Min != null || hr10Min != null)) {
                         Spacer(modifier = Modifier.height(8.dp))
                         HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                         Spacer(modifier = Modifier.height(8.dp))
                         
                         Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                             Text("Récupération", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                             
                             Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceAround) {
                                 // Helper Composable for Recovery Item
                                 @Composable
                                 fun RecoveryItem(label: String, valHr: Int?) {
                                     val drop = if (valHr != null) endHr - valHr else 0
                                     Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                         Text(
                                             if (valHr != null) "$valHr bpm" else "--", 
                                             style = MaterialTheme.typography.titleMedium, 
                                             fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                             color = if (valHr != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.5f)
                                         )
                                         Text(
                                             if (valHr != null) "$label (${if(drop>=0) "-" else "+"}$drop)" else label, 
                                             style = MaterialTheme.typography.labelSmall, 
                                             color = if (valHr != null) (if(drop>0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.5f)
                                         )
                                     }
                                 }

                                 RecoveryItem("1 min", hr1Min)
                                 RecoveryItem("2 min", hr2Min)
                                 RecoveryItem("5 min", hr5Min)
                                 RecoveryItem("10 min", hr10Min)
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
                        activityStartTime = activity.startTime.time,
                        cutoffIndex = durationMinutes.toFloat(),
                        userMaxHr = userMaxHr,
                        selectedDate = selectedDate,
                        showSteps = isWalking // Pass restriction
                    )
                }
            } else {
                 Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(2.dp))
        }
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
    selectedDate: Date,
    showSteps: Boolean = true
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
                    isEnabled = showSteps
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
            if (activityMinutes.isEmpty()) {
                 if (chart.data != null && chart.data.entryCount > 0) {
                     chart.clear()
                 }
                return@AndroidView
            }
            
            // OPTIMIZATION: Check tag. If tag == activityMinutes.hashCode(), skip.
             val dataHash = activityMinutes.hashCode().toString()
            
            if (chart.tag == dataHash) {
                return@AndroidView 
            }
            chart.tag = dataHash

            val combinedData = CombinedData()

            // Optimized Bar Width Logic & Dynamic Density Handling
            // If we have high density data (approx > 1 point per minute), use thin bars (1 sec = ~0.016 min)
            val isHighDensity = activityMinutes.size > (cutoffIndex * 2)
            val barWidth = if (isHighDensity) 0.02f else 0.8f

            // 1. Bar Data (HR)
            // Sort to ensure sequential gap calculation
            val sortedMinutes = activityMinutes.sortedBy { it.time }
            val hrEntries = mutableListOf<BarEntry>()
            
            for (i in sortedMinutes.indices) {
                val data = sortedMinutes[i]
                if (data.heartRate > 0) {
                    val dataTime = DateUtils.parseTimeToday(data.time)?.time ?: 0L
                    val fullDataTime = DateUtils.combineDateAndTime(selectedDate, dataTime)
                    val diffMin = (fullDataTime - activityStartTime) / 60000f // Float index
                    
                    if (diffMin >= 0) {
                        // Calculate gap to next point to detect density change (e.g. switch from 1s to 1min resolution)
                        var gapToNext = 1.0f // Default 1 min for last point
                        if (i < sortedMinutes.size - 1) {
                             val nextTime = DateUtils.combineDateAndTime(selectedDate, DateUtils.parseTimeToday(sortedMinutes[i+1].time)?.time ?: 0L)
                             gapToNext = (nextTime - fullDataTime) / 60000f
                        }
                        
                        // Special Handling:
                        // If we are in High Density mode (thin bars), but this specific point has a large gap (> 30s)
                        // it means we fell back to low resolution (e.g. Recovery data).
                        // We visually "widen" this bar by stacking thin bars to fill the gap.
                        if (isHighDensity && gapToNext > 0.5f) {
                            val count = (gapToNext / barWidth).toInt().coerceAtMost(60) // Cap to ~1 min width max to prevent filling massive gaps
                            // Fill mostly, leave 10% gap
                            val fillCount = (count * 0.9f).toInt().coerceAtLeast(1)
                            
                            for (j in 0 until fillCount) {
                                hrEntries.add(BarEntry(diffMin + (j * barWidth), data.heartRate.toFloat(), data))
                            }
                        } else {
                            // Standard entry (Thin or Wide depending on isHighDensity)
                            hrEntries.add(BarEntry(diffMin, data.heartRate.toFloat(), data))
                        }
                    }
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
                barData.barWidth = barWidth
                
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

            if (stepEntries.isNotEmpty() && showSteps) {
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
