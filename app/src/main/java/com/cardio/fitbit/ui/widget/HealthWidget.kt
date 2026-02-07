package com.cardio.fitbit.ui.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.text.FontWeight
import androidx.glance.unit.ColorProvider
import com.cardio.fitbit.data.repository.HealthRepository
import com.cardio.fitbit.utils.DateUtils
import com.cardio.fitbit.utils.HeartRateAnalysisUtils
import com.cardio.fitbit.ui.MainActivity
import androidx.compose.ui.graphics.Color

class HealthWidget(private val repository: HealthRepository) : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                WidgetContent(context)
            }
        }
    }

    @Composable
    private fun WidgetContent(context: Context) {
        // State for data
        val rhr = remember { mutableStateOf<Int?>(null) }
        val sleepDuration = remember { mutableStateOf<String?>(null) }
        val lastSync = remember { mutableStateOf<String>("...") }

        LaunchedEffect(Unit) {
             try {
                // Helper to fetch Data
                suspend fun tryFetch(date: java.util.Date): Triple<Int?, Int?, String?> {
                     // 1. Fetch Sleep (Needed for RHR Calc)
                     var sleep = repository.getSleepData(date, forceRefresh = false).getOrNull()
                     if (sleep.isNullOrEmpty()) {
                         sleep = repository.getSleepData(date, forceRefresh = true).getOrNull()
                     }
                     
                     val mainSleep = sleep?.maxByOrNull { it.duration }
                     var computedRhr: Int? = null
                     var computedLastHr: Int? = null
                     var computedLastTime: String? = null
                     
                     // 2. Fetch Intraday (Needed for RHR & Last HR)
                     // Try Today first with forced refresh if needed? 
                     // Users expect widget to be kinda fresh.
                     var intraday = repository.getIntradayData(date, forceRefresh = false).getOrNull()
                     
                     // If empty or old, try refresh? Maybe too expensive for widget every time?
                     // Let's force refresh once if empty.
                     if (intraday == null || intraday.minuteData.isEmpty()) {
                          intraday = repository.getIntradayData(date, forceRefresh = true).getOrNull()
                     }

                     // -- Logic for Last HR --
                     if (intraday != null && intraday.minuteData.isNotEmpty()) {
                         // Get the very last data point available
                         val lastPoint = intraday.minuteData.lastOrNull { it.heartRate > 0 }
                         if (lastPoint != null) {
                             computedLastHr = lastPoint.heartRate
                             computedLastTime = lastPoint.time
                         }
                     }
                     
                     // -- Logic for RHR --
                     // Run RHR Calc safely so it doesn't block
                     if (mainSleep != null && mainSleep.duration > 3 * 3600 * 1000) {
                         try {
                             val startPart = mainSleep.startTime
                             val endPart = mainSleep.endTime
                             val startOfDay = DateUtils.getStartOfDay(date) // 00:00 today
                             
                             val fullHeartRateList = mutableListOf<com.cardio.fitbit.data.models.MinuteData>()

                             // Format times for string comparison (HH:mm:ss)
                             val timeFormatter = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                             val startTimeStr = timeFormatter.format(startPart)
                             val endTimeStr = timeFormatter.format(endPart)

                             // If we have intraday for today, we can use it.
                             // But RHR calc requires sleep cross-check which might be yesterday.
                             // Re-using simplified logic from before:
                             
                             // Case A: Sleep started Yesterday (Cross-Midnight)
                             if (startPart.before(startOfDay)) {
                                 // 1. Fetch Yesterday's FULL Intraday Data
                                 val yesterdayDate = DateUtils.getDaysAgo(1, date)
                                 var intradayYesterday = repository.getIntradayData(yesterdayDate, forceRefresh = false).getOrNull()
                                 if (intradayYesterday == null || intradayYesterday.minuteData.isEmpty()) {
                                     intradayYesterday = repository.getIntradayData(yesterdayDate, forceRefresh = true).getOrNull()
                                 }
                                 
                                 if (intradayYesterday != null) {
                                     val filtered = intradayYesterday.minuteData.filter { point -> 
                                          point.time >= startTimeStr
                                     }
                                     fullHeartRateList.addAll(filtered)
                                 }

                                 // 2. Use Today's Intraday Data (already fetched above)
                                 if (intraday != null) {
                                     val filtered = intraday.minuteData.filter { point -> 
                                          point.time <= endTimeStr
                                     }
                                     fullHeartRateList.addAll(filtered)
                                 }

                             } else {
                                 // Case B: Sleep started Today
                                 if (intraday != null) {
                                      val filtered = intraday.minuteData.filter { point ->
                                          point.time >= startTimeStr && point.time <= endTimeStr
                                      }
                                      fullHeartRateList.addAll(filtered)
                                 }
                             }
                             
                             if (fullHeartRateList.isNotEmpty()) {
                                 val result = HeartRateAnalysisUtils.calculateDailyRHR(date, fullHeartRateList, sleep ?: emptyList(), null)
                                 computedRhr = result.rhrNight
                             }
                         } catch (e: Exception) {
                             e.printStackTrace()
                         }
                     }
                     return Triple(computedRhr, computedLastHr, computedLastTime)
                }

                var displayRhr: Int? = null
                var displayLastHr: Int? = null
                var displayLastTime: String? = null
                
                // 1. Try Today
                val today = java.util.Date()
                val resultToday = tryFetch(today)
                
                displayRhr = resultToday.first
                displayLastHr = resultToday.second
                displayLastTime = resultToday.third
                
                // 2. Fallback to Yesterday if BOTH missing?
                // Or if just one missing? Usually if no data today, check yesterday.
                if (displayRhr == null && displayLastHr == null) {
                    val yesterday = DateUtils.getDaysAgo(1, today)
                    val resultYesterday = tryFetch(yesterday)
                    
                    if (resultYesterday.first != null) displayRhr = resultYesterday.first
                    if (resultYesterday.second != null) {
                        displayLastHr = resultYesterday.second
                        displayLastTime = resultYesterday.third
                    }
                }
                
                // Update State
                rhr.value = displayRhr
                
                if (displayLastHr != null) {
                    // Update Last Sync / Sleep text area to be Last HR
                    lastSync.value = "$displayLastTime" // We'll put time here
                    sleepDuration.value = displayLastHr.toString() // Reuse variable or rename? Better reuse to avoid huge diffs, but better logic to rename.
                    // Actually I will just put the Value in sleepDuration variable for now to minimize state refactor, 
                    // realizing I should probably rename it in a real refactor but here we are patching.
                    // Wait, let's just use the state variables as "Slot 1" and "Slot 2".
                } else {
                    sleepDuration.value = "--"
                    lastSync.value = "..."
                }
                
             } catch (e: Exception) {
                 lastSync.value = "Err"
                 e.printStackTrace()
             }
        }

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .padding(8.dp)
                .clickable(
                    actionStartActivity(
                        android.content.Intent(
                            androidx.glance.LocalContext.current, 
                            MainActivity::class.java
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically
            ) {
                 
                 Row(
                     horizontalAlignment = Alignment.CenterHorizontally,
                     verticalAlignment = Alignment.CenterVertically
                 ) {
                     // RHR
                     MetricItem("RHR", rhr.value?.toString() ?: "--", "")
                     
                     Spacer(GlanceModifier.width(24.dp))
                     
                     // Last HR
                     MetricItem("Dernier", sleepDuration.value ?: "--", "")
                 }
                 
                 Spacer(GlanceModifier.size(4.dp))
                 
                 // Time centered below values
                 Text(
                     text = lastSync.value,
                     style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant)
                 )
                 
                 // Removed separate "Maj" text since it's now integrated in "Dernier" time or redundant.
                 // Or we could keep "Maj" if we want to know when widget updated?
                 // User asked "Dernier" value so simpler is better.
            }
        }
    }
    
    @Composable
    private fun MetricItem(label: String, value: String, unit: String) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label, 
                style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant)
            )
            Text(
                text = value, 
                style = TextStyle(
                    fontWeight = FontWeight.Bold, 
                    fontSize = 24.sp, 
                    color = GlanceTheme.colors.onSurface
                )
            )
            if (unit.isNotEmpty()) {
                 Text(
                     text = unit, 
                     style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant)
                 )
            }
        }
    }
}
