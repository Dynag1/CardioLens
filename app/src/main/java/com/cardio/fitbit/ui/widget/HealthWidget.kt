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
                // Helper to fetch and calculate RHR matching DashboardViewModel logic
                suspend fun tryFetch(date: java.util.Date): Pair<Int?, com.cardio.fitbit.data.models.SleepData?> {
                     // 1. Fetch Sleep
                     var sleep = repository.getSleepData(date, forceRefresh = false).getOrNull()
                     if (sleep.isNullOrEmpty()) {
                         sleep = repository.getSleepData(date, forceRefresh = true).getOrNull()
                     }
                     
                     val mainSleep = sleep?.maxByOrNull { it.duration }
                     var computedRhr: Int? = null
                     
                     // Run RHR Calc safely so it doesn't block Sleep return
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

                             // Case A: Sleep started Yesterday (Cross-Midnight)
                             if (startPart.before(startOfDay)) {
                                 // 1. Fetch Yesterday's FULL Intraday Data
                                 val yesterdayDate = DateUtils.getDaysAgo(1, date)
                                 var intradayYesterday = repository.getIntradayData(yesterdayDate, forceRefresh = false).getOrNull()
                                 if (intradayYesterday == null || intradayYesterday.minuteData.isEmpty()) {
                                     intradayYesterday = repository.getIntradayData(yesterdayDate, forceRefresh = true).getOrNull()
                                 }
                                 
                                 // Filter: time >= startTimeStr
                                 // Note: time in DB is usually HH:mm:ss.
                                 if (intradayYesterday != null) {
                                     val filtered = intradayYesterday.minuteData.filter { point -> 
                                          // Robust compare: "23:00:00" >= "22:30:00"
                                          point.time >= startTimeStr
                                     }
                                     fullHeartRateList.addAll(filtered)
                                 }

                                 // 2. Fetch Today's FULL Intraday Data
                                 var intradayToday = repository.getIntradayData(date, forceRefresh = false).getOrNull()
                                 // We use existing call or fetch
                                 if (intradayToday == null || intradayToday.minuteData.isEmpty()) {
                                     intradayToday = repository.getIntradayData(date, forceRefresh = true).getOrNull()
                                 }
                                 
                                 // Filter: time <= endTimeStr (midnight to wake)
                                 if (intradayToday != null) {
                                     val filtered = intradayToday.minuteData.filter { point -> 
                                          point.time <= endTimeStr
                                     }
                                     fullHeartRateList.addAll(filtered)
                                 }

                             } else {
                                 // Case B: Sleep started Today
                                 var intradayToday = repository.getIntradayData(date, forceRefresh = false).getOrNull()
                                 if (intradayToday == null || intradayToday.minuteData.isEmpty()) {
                                     intradayToday = repository.getIntradayData(date, forceRefresh = true).getOrNull()
                                 }
                                 
                                 if (intradayToday != null) {
                                      val filtered = intradayToday.minuteData.filter { point ->
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
                             // Swallow RHR error to ensure Sleep is returned
                         }
                     }
                     return Pair(computedRhr, mainSleep)
                }

                var displayRhr: Int? = null
                var displaySleep: com.cardio.fitbit.data.models.SleepData? = null
                
                // 1. Try Today (Last Night)
                val today = java.util.Date()
                val resultToday = tryFetch(today)
                displayRhr = resultToday.first
                displaySleep = resultToday.second
                
                // 2. Fallback to Yesterday if RHR is missing AND Sleep missing?
                // Actually if Today has sleep but NO rhr (failed calc), we might want to try Yesterday?
                // But usually we prefer Today's sleep even if RHR failed.
                // But user wants RHR.
                // Let's say: If RHR is null, check Yesterday completely.
                
                if (displayRhr == null) {
                    val yesterday = DateUtils.getDaysAgo(1, today)
                    val resultYesterday = tryFetch(yesterday)
                    
                    if (resultYesterday.first != null) {
                        displayRhr = resultYesterday.first
                        // Use yesterday's sleep if we are engaging fallback
                         if (displaySleep == null) {
                            displaySleep = resultYesterday.second
                         }
                    }
                }
                
                rhr.value = displayRhr

                if (displaySleep != null) {
                   val hours = displaySleep!!.duration / (1000 * 60 * 60)
                   val mins = (displaySleep!!.duration / (1000 * 60)) % 60
                   sleepDuration.value = "${hours}h${mins}m"
                }
                
                val now = java.util.Date()
                val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                lastSync.value = timeFormat.format(now)
                
             } catch (e: Exception) {
                 lastSync.value = "Err: ${e.message?.take(5)}"
                 e.printStackTrace()
             }
        }

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Color.White)
                .padding(8.dp) // Reduced padding
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
                 // Removed Title "CardioLens" to save space for 2x1
                 
                 Row(
                     horizontalAlignment = Alignment.CenterHorizontally,
                     verticalAlignment = Alignment.CenterVertically
                 ) {
                     // RHR
                     MetricItem("RHR", rhr.value?.toString() ?: "--", "")
                     Spacer(GlanceModifier.width(16.dp))
                     // Sleep
                     MetricItem("Sommeil", sleepDuration.value ?: "--", "")
                 }
                 
                 // Tiny update time
                 Text("Maj: ${lastSync.value}", style = TextStyle(fontSize = 10.sp, color = ColorProvider(Color.Gray)))
            }
        }
    }
    
    @Composable
    private fun MetricItem(label: String, value: String, unit: String) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = TextStyle(fontSize = 12.sp, color = ColorProvider(Color.Gray)))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, color = ColorProvider(Color.Black)))
                if (unit.isNotEmpty()) {
                    Spacer(GlanceModifier.width(2.dp))
                     Text(unit, style = TextStyle(fontSize = 12.sp, color = ColorProvider(Color.Gray)))
                }
            }
        }
    }
}
