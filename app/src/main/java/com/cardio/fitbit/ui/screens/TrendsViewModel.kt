package com.cardio.fitbit.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cardio.fitbit.data.models.ActivityData
import com.cardio.fitbit.data.models.IntradayHeartRate
import com.cardio.fitbit.data.models.SleepData
import com.cardio.fitbit.data.repository.HealthRepository
import com.cardio.fitbit.utils.DateUtils
import com.cardio.fitbit.utils.HeartRateAnalysisUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import javax.inject.Inject

data class TrendPoint(
    val date: Date,
    val rhrNight: Int?,
    val rhrDay: Int?,
    val rhrAvg: Int?,
    val hrv: Int?, // Daily RMSSD
    val moodRating: Int?
)

sealed class TrendsUiState {
    object Loading : TrendsUiState()
    data class Success(val data: List<TrendPoint>, val selectedDays: Int) : TrendsUiState()
    data class Error(val message: String) : TrendsUiState()
}

@HiltViewModel
class TrendsViewModel @Inject constructor(
    private val healthRepository: HealthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<TrendsUiState>(TrendsUiState.Loading)
    val uiState: StateFlow<TrendsUiState> = _uiState.asStateFlow()

    init {
        loadTrends(7)
    }

    fun loadTrends(days: Int = 7) {
        viewModelScope.launch {
            _uiState.value = TrendsUiState.Loading
            try {
                val calendar = Calendar.getInstance()
                val endDate = calendar.time
                
                // Calculate Start Date
                val startCalendar = Calendar.getInstance()
                startCalendar.add(Calendar.DAY_OF_YEAR, -(days - 1))
                val startDate = startCalendar.time
                
                // 1. Fetch History Data in Bulk (Parallelizable, but sequential is fine for now)
                
                // HRV History
                val hrvResult = healthRepository.getHrvHistory(startDate, endDate)
                val hrvMap = hrvResult.getOrNull()?.associateBy { DateUtils.formatForApi(it.time) }?.toMutableMap() ?: mutableMapOf()

                // Mood History
                val moodHistory = healthRepository.getMoodHistory(startDate, endDate)
                val moodMap = moodHistory.associateBy { it.date }

                // Heart Rate History (Daily Summaries) - OPTIMIZATION
                val hrHistoryResult = healthRepository.getHeartRateHistory(startDate, endDate)
                val hrMap = hrHistoryResult.getOrNull()?.associateBy { DateUtils.formatForApi(it.date) } ?: emptyMap()

                // Sleep History - OPTIMIZATION
                // Note: Sleep history from Fitbit might return all sleep logs in the range. 
                // We need to associate them with the relevant "Date of Sleep".
                val sleepHistoryResult = healthRepository.getSleepHistory(startDate, endDate)
                val sleepLogs = sleepHistoryResult.getOrNull() ?: emptyList()
                val sleepMap = sleepLogs.groupBy { DateUtils.formatForApi(it.date) }
                
                
                // HOTFIX: Explicitly fetch Today's HRV (same as before)
                try {
                    val today = Calendar.getInstance().time
                    val todayStr = DateUtils.formatForApi(today)
                    // We don't force refresh here to respect Dashboard's cache, but if missing it will fetch single
                    val todayResult = healthRepository.getHrvData(today)
                    val todayRecords = todayResult.getOrNull() ?: emptyList()
                    
                    if (todayRecords.isNotEmpty()) {
                        val avgRmssd = todayRecords.map { it.rmssd }.average()
                        val syntheticRecord = com.cardio.fitbit.data.models.HrvRecord(
                            time = todayRecords.first().time, 
                            rmssd = avgRmssd
                        )
                        hrvMap[todayStr] = syntheticRecord
                    }
                } catch (e: Exception) { /* Ignore */ }

                val trendPoints = mutableListOf<TrendPoint>()
                
                // Iterate to build points
                val processingCalendar = Calendar.getInstance()
                processingCalendar.time = endDate
                
                for (i in 0 until days) {
                    val targetDate = processingCalendar.time
                    val dateStr = DateUtils.formatForApi(targetDate)
                    
                    val hrvValue = hrvMap[dateStr]?.rmssd?.toInt()
                    val moodRating = moodMap[dateStr]?.rating
                    
                    // Use bulk fetched data
                    val dailyHr = hrMap[dateStr]
                    val dailySleep = sleepMap[dateStr] ?: emptyList()
                    
                    // We only need intraday if we REALLY want to calculate precise Night RHR from samples.
                    // But wait, TrendsViewModel previously called calculateDailyRHR which fetches Intraday...
                    // The "Optimizing" request implies we should avoid that if possible.
                    // However, our current logic *relies* on Intraday for "Night RHR" calculation if Fitbit doesn't provide it?
                    // Actually, Fitbit provides "Resting Heart Rate" (RHR) in the daily summary (`dailyHr.restingHeartRate`).
                    // The whole point of the previous fix was to fallback to this Native RHR.
                    // If we use Native RHR from history, we avoid fetching Intraday entirely!
                    // BUT, `calculateDailyRHR` logic in `HeartRateAnalysisUtils` does complex "pre-midnight" checks etc.
                    // If we want to be pure optimization: We use the Native RHR from the bulk fetch for "rhrDay" (and maybe "rhrAvg").
                    // What about "rhrNight"? Fitbit doesn't give a "Night Only" RHR explicitly in the summary, just one "Resting Heart Rate".
                    // The app distinguishes Day/Night.
                    // IF we skip intraday, we can simply say: rhrDay = Native RHR, rhrNight = null? 
                    // Or we just use Native RHR for everything?
                    // The user said "optimize retrieval... I hit api limit".
                    // Fetching Intraday (1 request per day) * 7 days = 7 requests.
                    // Fetching History (1 request per range) = 1 request.
                    // Huge win.
                    // So we should construct TrendPoint using the Summary data if available, and ONLY fetch Intraday if absolutely needed (or not at all).
                    // Given the goal is optimization, let's use the summary data.
                    
                    // Note: HealthConnectProvider's getHeartRateHistory returns a calculated "Resting Heart Rate" too.
                    
                    val nativeRhr = dailyHr?.restingHeartRate
                    
                    // For Night RHR: We can look at the sleep logs. Sleep logs *sometimes* contain efficiency/restlessness but not explicit "Sleeping Heart Rate" unless detailed.
                    // However, we can use the "nativeRhr" as the fallback for "Avg RHR" or "Day RHR".
                    // If we skip `calculateDailyRHR` (which does the heavy lifting), we lose the custom logic.
                    // Compomise: If we have Native RHR, use it. If not, maybe skip or fetch single day?
                    // Let's rely on Native RHR to avoid the heavy API calls.
                    
                    val point = TrendPoint(
                        date = targetDate,
                        rhrNight = null, // We lose specific "Night RHR" calculation without intraday, unless we have it elsewhere.
                        rhrDay = nativeRhr, 
                        rhrAvg = nativeRhr, 
                        hrv = hrvValue,
                        moodRating = moodRating
                    )
                    
                    // Wait, if we return null for rhrNight, the graph might look empty for "Night".
                    // The previous logic `calculateDailyRHR` returned `RhrResult(rhrNight, rhrDay, rhrAvg)`.
                    // And it used Intraday + Sleep + Activity.
                    // If we prioritize Optimization, we must accept some precision loss OR
                    // we accept that we CANNOT calculate custom Night RHR without Intraday.
                    // BUT, `TrendsViewModel` existing logic: 
                    // `heartRateResult.getOrNull()?.restingHeartRate` IS the Native RHR.
                    // So we can use that.
                    
                    trendPoints.add(point)
                    
                    // Move back one day
                    processingCalendar.add(Calendar.DAY_OF_YEAR, -1)
                }
                
                // Sort by date ascending
                _uiState.value = TrendsUiState.Success(trendPoints.sortedBy { it.date }, days)
                
            } catch (e: Exception) {
                _uiState.value = TrendsUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private suspend fun calculateDailyRHR(date: Date, hrvValue: Int?, moodRating: Int?): TrendPoint {
        // 1. Load Data
        // Initial attempt (uses cache if available)
        var intradayResult = healthRepository.getIntradayData(date) 
        var sleepResult = healthRepository.getSleepData(date)
        var activityResult = healthRepository.getActivityData(date)
        var heartRateResult = healthRepository.getHeartRateData(date)
        
        var intraday = intradayResult.getOrNull()?.minuteData ?: emptyList()
        var sleep = sleepResult.getOrNull() ?: emptyList()
        var activity = activityResult.getOrNull()

        // 2b. Merge "Next Day" sleep starting today (Fitbit fix for Day RHR exclusion)
        val cal = Calendar.getInstance()
        cal.time = date
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val resultNextDay = healthRepository.getSleepData(cal.time)
        val nextDaySleep = resultNextDay.getOrNull() ?: emptyList()
        val dayStart = DateUtils.getStartOfDay(date).time
        val dayEnd = DateUtils.getEndOfDay(date).time
        
        val extraSleep = nextDaySleep.filter { it.startTime.time in dayStart..dayEnd }
        sleep = (sleep + extraSleep).distinctBy { it.startTime.time }

        if (intraday.isEmpty()) {
            return TrendPoint(date, null, null, null, hrvValue, moodRating)
        }

        // --- CALCULATION LOGIC (Delegated to HeartRateAnalysisUtils) ---

        // 3. Calculate Night RHR (Median of Sleep HR)
        // Check if any sleep session started YESTERDAY/Before midnight?
        val startOfDayTs = DateUtils.getStartOfDay(date).time
        val preMidnightSessions = sleep.filter { it.startTime.time < startOfDayTs }
        
        val preMidnightHeartRates = mutableListOf<Int>()

        if (preMidnightSessions.isNotEmpty()) {
            val earliestStart = preMidnightSessions.minOf { it.startTime }
            try {
                 // Fetch generic series
                 val result = healthRepository.getHeartRateSeries(earliestStart, Date(startOfDayTs))
                 val extraData = result.getOrNull() ?: emptyList()
                 
                 extraData.forEach { point ->
                     if (point.heartRate > 0) {
                         preMidnightHeartRates.add(point.heartRate)
                     }
                 }
            } catch (e: Exception) {
                // Ignore error in trend calculation to avoid crashing layout
            }
        }

                val rhrResult = HeartRateAnalysisUtils.calculateDailyRHR(
            date,
            intraday,
            sleep,
            activity,
            preMidnightHeartRates,
            nativeRhr = heartRateResult.getOrNull()?.restingHeartRate
        )

        return TrendPoint(date, rhrResult.rhrNight, rhrResult.rhrDay, rhrResult.rhrAvg, hrvValue, moodRating)
    }
}
