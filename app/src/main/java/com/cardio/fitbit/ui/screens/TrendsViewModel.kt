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

                // Heart Rate History (Daily Summaries - Native RHR Fallback)
                val hrHistoryResult = healthRepository.getHeartRateHistory(startDate, endDate)
                val hrMap = hrHistoryResult.getOrNull()?.associateBy { DateUtils.formatForApi(it.date) } ?: emptyMap()

                // Intraday History (Detailed Data - The Source of "True RHR")
                val intradayHistoryResult = healthRepository.getIntradayHistory(startDate, endDate)
                val intradayMap = intradayHistoryResult.getOrNull()?.associateBy { DateUtils.formatForApi(it.date) } ?: emptyMap()

                // Sleep History
                val sleepHistoryResult = healthRepository.getSleepHistory(startDate, endDate)
                val sleepLogs = sleepHistoryResult.getOrNull() ?: emptyList() 
                val sleepMap = sleepLogs.groupBy { DateUtils.formatForApi(it.date) } // Note: Sleep date logic might be tricky, assuming API returns 'dateOfSleep' correctly
                
                // Activity History (for excluding workouts from RHR)
                val activityHistoryResult = healthRepository.getActivityHistory(startDate, endDate)
                val activityMap = activityHistoryResult.getOrNull()?.associateBy { DateUtils.formatForApi(it.date) } ?: emptyMap()
                
                
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
                    
                    // Retrieve Datasets
                    val dailyHr = hrMap[dateStr]
                    val dailySleep = sleepMap[dateStr] ?: emptyList()
                    val dailyIntraday = intradayMap[dateStr]?.minuteData ?: emptyList()
                    val dailyActivity = activityMap[dateStr]
                    
                    val nativeRhr = dailyHr?.restingHeartRate
                    

                    // --- RESTORED LOGIC: Calculate "True" Day RHR using Intraday ----
                    // This uses the custom algorithm (Lowest 10-min sedentary avg)
                    // Using 'nativeRhr' as the fallback if calculation fails (e.g. no data)
                    
                    val rhrResult = HeartRateAnalysisUtils.calculateDailyRHR(
                        date = targetDate,
                        intraday = dailyIntraday,
                        sleep = dailySleep,
                        activity = dailyActivity, // NOW PASSING ACTIVITY DATA
                        preMidnightHeartRates = emptyList(), 
                        nativeRhr = nativeRhr
                    )

                    val point = TrendPoint(
                        date = targetDate,
                        rhrNight = rhrResult.rhrNight,
                        rhrDay = rhrResult.rhrDay, 
                        rhrAvg = rhrResult.rhrAvg, 
                        hrv = hrvValue,
                        moodRating = moodRating
                    )
                    
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
