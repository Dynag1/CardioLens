package com.cardio.fitbit.workers

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cardio.fitbit.data.repository.HealthRepository
import com.cardio.fitbit.R
import com.cardio.fitbit.ui.widget.HealthWidget
import com.cardio.fitbit.utils.DateUtils
import com.cardio.fitbit.utils.HeartRateAnalysisUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Date
import java.util.Locale

@HiltWorker
class WidgetUpdateWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val healthRepository: HealthRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val (rhr, lastHr, steps, lastTime) = fetchData()

            val manager = GlanceAppWidgetManager(applicationContext)
            val glanceIds = manager.getGlanceIds(HealthWidget::class.java)

            if (glanceIds.isNotEmpty()) {
                glanceIds.forEach { glanceId ->
                    updateAppWidgetState(applicationContext, glanceId) { prefs ->
                        if (rhr != null) {
                            prefs[HealthWidget.KEY_RHR] = rhr
                        } else {
                            prefs.minusAssign(HealthWidget.KEY_RHR)
                        }

                        if (lastHr != null) {
                            prefs[HealthWidget.KEY_LAST_HR] = lastHr
                        } else {
                            prefs.minusAssign(HealthWidget.KEY_LAST_HR)
                        }

                        if (steps != null) {
                            prefs[HealthWidget.KEY_STEPS] = steps
                        } else {
                            prefs.minusAssign(HealthWidget.KEY_STEPS)
                        }

                        if (lastTime != null) {
                            prefs[HealthWidget.KEY_LAST_TIME] = lastTime
                        } else {
                            prefs.minusAssign(HealthWidget.KEY_LAST_TIME)
                        }
                        
                        // Clear error status if successful
                        prefs.minusAssign(HealthWidget.KEY_LAST_SYNC_STATUS)
                    }
                }
                HealthWidget().updateAll(applicationContext)
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            // Try to set error state
            try {
                val manager = GlanceAppWidgetManager(applicationContext)
                val glanceIds = manager.getGlanceIds(HealthWidget::class.java)
                glanceIds.forEach { glanceId ->
                    updateAppWidgetState(applicationContext, glanceId) { prefs ->
                        prefs[HealthWidget.KEY_LAST_SYNC_STATUS] = applicationContext.getString(R.string.widget_error)
                    }
                }
                HealthWidget().updateAll(applicationContext)
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
            Result.failure()
        }
    }

    private suspend fun fetchData(): Quad<Int?, Int?, Int?, String?> {
        // 1. Try Today
        val today = Date()
        var result = tryFetchForDate(today)

        // 2. Fallback to Yesterday if BOTH RHR and LastHR are missing
        if (result.first == null && result.second == null) {
            val yesterday = DateUtils.getDaysAgo(1, today)
            val resultYesterday = tryFetchForDate(yesterday)
            
            // Use yesterday's data if found
            if (resultYesterday.first != null || resultYesterday.second != null) {
                return resultYesterday
            }
        }
        return result
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    private suspend fun tryFetchForDate(date: Date): Quad<Int?, Int?, Int?, String?> {
        // 1. Fetch Sleep (Needed for RHR Calc)
        var sleep = healthRepository.getSleepData(date, forceRefresh = false).getOrNull()
        if (sleep.isNullOrEmpty()) {
            sleep = healthRepository.getSleepData(date, forceRefresh = true).getOrNull()
        }

        val mainSleep = sleep?.maxByOrNull { it.duration }
        var computedRhr: Int? = null
        var computedLastHr: Int? = null
        var computedSteps: Int? = null
        var computedLastTime: String? = null

        // 2. Fetch Intraday (Needed for RHR & Last HR)
        var intraday = healthRepository.getIntradayData(date, forceRefresh = false).getOrNull()

        // If empty or old, try refresh
        if (intraday == null || intraday.minuteData.isEmpty()) {
            intraday = healthRepository.getIntradayData(date, forceRefresh = true).getOrNull()
        }

        // -- Logic for Last HR --
        if (intraday != null && intraday.minuteData.isNotEmpty()) {
            val lastPoint = intraday.minuteData.lastOrNull { it.heartRate > 0 }
            if (lastPoint != null) {
                computedLastHr = lastPoint.heartRate
                computedLastTime = lastPoint.time
            }
        }

        // -- Logic for Steps --
        // Try getting daily summary first (more reliable)
        val activityData = healthRepository.getActivityData(date, forceRefresh = false).getOrNull() 
            ?: healthRepository.getActivityData(date, forceRefresh = true).getOrNull()

        if (activityData != null) {
            computedSteps = activityData.summary.steps
        } else if (intraday != null && intraday.minuteData.isNotEmpty()) {
            // Fallback to intraday sum
            computedSteps = intraday.minuteData.sumOf { it.steps }
        }

        // -- Logic for RHR --
        if (mainSleep != null && mainSleep.duration > 3 * 3600 * 1000) {
            try {
                val startPart = mainSleep.startTime
                val endPart = mainSleep.endTime
                val startOfDay = DateUtils.getStartOfDay(date)

                val fullHeartRateList = mutableListOf<com.cardio.fitbit.data.models.MinuteData>()
                val timeFormatter = java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                val startTimeStr = timeFormatter.format(startPart)
                val endTimeStr = timeFormatter.format(endPart)

                if (startPart.before(startOfDay)) {
                    // Case A: Sleep started Yesterday
                    val yesterdayDate = DateUtils.getDaysAgo(1, date)
                    var intradayYesterday = healthRepository.getIntradayData(yesterdayDate, forceRefresh = false).getOrNull()
                    if (intradayYesterday == null || intradayYesterday.minuteData.isEmpty()) {
                         intradayYesterday = healthRepository.getIntradayData(yesterdayDate, forceRefresh = true).getOrNull()
                    }

                    if (intradayYesterday != null) {
                        val filtered = intradayYesterday.minuteData.filter { point ->
                            point.time >= startTimeStr
                        }
                        fullHeartRateList.addAll(filtered)
                    }

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
        return Quad(computedRhr, computedLastHr, computedSteps, computedLastTime)
    }
}
