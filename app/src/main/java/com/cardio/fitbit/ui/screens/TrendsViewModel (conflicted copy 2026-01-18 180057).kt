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
                
                // Fetch HRV History for the whole range
                val hrvResult = healthRepository.getHrvHistory(startDate, endDate)
                val hrvMap = hrvResult.getOrNull()?.associateBy { DateUtils.formatForApi(it.time) }?.toMutableMap() ?: mutableMapOf()

                // Fetch Mood History
                val moodHistory = healthRepository.getMoodHistory(startDate, endDate)
                val moodMap = moodHistory.associateBy { it.date }

                // HOTFIX: Explicitly fetch Today's HRV using single-day endpoint to ensure consistency with Dashboard
                // (Range endpoint sometimes returns stale data for current day)
                try {
                    val today = Calendar.getInstance().time
                    val todayStr = DateUtils.formatForApi(today)
                    // We don't force refresh here to respect Dashboard's cache, but if missing it will fetch single
                    val todayResult = healthRepository.getHrvData(today)
                    val todayRecords = todayResult.getOrNull() ?: emptyList()
                    
                    if (todayRecords.isNotEmpty()) {
                        // Aggregate if multiple records (match Dashboard logic)
                        val avgRmssd = todayRecords.map { it.rmssd }.average()
                        val syntheticRecord = com.cardio.fitbit.data.models.HrvRecord(
                            time = todayRecords.first().time, // Use time from first record or just 'today'
                            rmssd = avgRmssd
                        )
                        hrvMap[todayStr] = syntheticRecord
                    }
                } catch (e: Exception) {
                    // Ignore
                }

                val trendPoints = mutableListOf<TrendPoint>()
                
                // Iterate 0 to days-1
                for (i in 0 until days) {
                    val targetDate = calendar.time
                    val dateStr = DateUtils.formatForApi(targetDate)
                    val hrvValue = hrvMap[dateStr]?.rmssd?.toInt()
                    val moodRating = moodMap[dateStr]?.rating
                    
                    // Fetch data for this day (passing HRV and Mood)
                    val rhrValues = calculateDailyRHR(targetDate, hrvValue, moodRating)
                    trendPoints.add(rhrValues)
                    
                    // Move back one day
                    calendar.add(Calendar.DAY_OF_YEAR, -1)
                }
                
                // Sort by date ascending (oldest to newest)
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
            preMidnightHeartRates
        )

        return TrendPoint(date, rhrResult.rhrNight, rhrResult.rhrDay, rhrResult.rhrAvg, hrvValue, moodRating)
    }
}
