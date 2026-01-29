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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    val moodRating: Int?,
    val steps: Int?,
    val workoutDurationMinutes: Int?
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
                // End at end of today to capture all of today's data
                val endDate = DateUtils.getEndOfDay(calendar.time)
                
                // Calculate Start Date (N days ago, starting at 00:00)
                val startCalendar = Calendar.getInstance()
                startCalendar.add(Calendar.DAY_OF_YEAR, -(days - 1))
                val startDate = DateUtils.getStartOfDay(startCalendar.time)
                
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
                // Activity History (for excluding workouts from RHR)
                val activityHistoryResult = healthRepository.getActivityHistory(startDate, endDate)
                val activityMap = activityHistoryResult.getOrNull()?.associateBy { DateUtils.formatForApi(it.date) } ?: emptyMap()

                // Steps History
                val stepsResult = healthRepository.getStepsData(startDate, endDate)
                val stepsMap = stepsResult.getOrNull()?.associateBy { DateUtils.formatForApi(it.date) } ?: emptyMap()
                
                
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
                val repairCandidates = mutableListOf<Date>()
                
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
                    

                    val rhrResult = HeartRateAnalysisUtils.calculateDailyRHR(
                        date = targetDate,
                        intraday = dailyIntraday,
                        sleep = dailySleep,
                        activity = dailyActivity, // NOW PASSING ACTIVITY DATA
                        preMidnightHeartRates = emptyList(), 
                        nativeRhr = nativeRhr
                    )

                    // Repair Check
                    // Aggressive: If we have no Intraday data for a past day, try to fetch it.
                    // This covers cases where we have NO data at all (nativeRhr is null) or just summary.
                    // Accessing 'steps' to verify if the user was active could be smart, but 'forceRefresh' 
                    // on missing Intraday is the robust way to ensure we have tried everything.
                    if (dailyIntraday.isEmpty()) {
                        repairCandidates.add(targetDate)
                    }

                    val dailySteps = stepsMap[dateStr]?.steps

                    // Calculate Workout Duration
                    // Sum duration of all activities for the day
                    val workoutDurationMinutes = dailyActivity?.activities?.sumOf { it.duration }?.let { millis ->
                        (millis / 1000 / 60).toInt()
                    } ?: 0

                    val point = TrendPoint(
                        date = targetDate,
                        rhrNight = rhrResult.rhrNight,
                        rhrDay = rhrResult.rhrDay, 
                        rhrAvg = rhrResult.rhrAvg, 
                        hrv = hrvValue,
                        moodRating = moodRating,
                        steps = dailySteps,
                        workoutDurationMinutes = if (workoutDurationMinutes > 0) workoutDurationMinutes else null
                    )
                    
                    trendPoints.add(point)
                    
                    // Move back one day
                    processingCalendar.add(Calendar.DAY_OF_YEAR, -1)
                }
                
                // Sort by date ascending
                _uiState.value = TrendsUiState.Success(trendPoints.sortedBy { it.date }, days)
                
                if (repairCandidates.isNotEmpty()) {
                    repairMissingData(repairCandidates, days)
                }
                
            } catch (e: Exception) {
                _uiState.value = TrendsUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun repairMissingData(candidates: List<Date>, daysToReload: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                candidates.forEach { date ->
                    healthRepository.getIntradayData(date, forceRefresh = true)
                    healthRepository.getSleepData(date, forceRefresh = true) // Also repair Sleep
                }
                withContext(Dispatchers.Main) {
                    loadTrends(daysToReload)
                }
            } catch (e: Exception) {
               // Silent fail
            }
        }
    }


}
