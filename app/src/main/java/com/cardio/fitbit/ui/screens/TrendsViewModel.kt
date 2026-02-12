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
    val workoutDurationMinutes: Int?,
    val symptoms: String?,
    val sleepMinutes: Int?
)

data class CorrelationResult(
    val title: String,
    val description: String,
    val impact: String,
    val isPositive: Boolean
)

sealed class TrendsUiState {
    object Loading : TrendsUiState()
    data class Success(
        val data: List<TrendPoint>, 
        val selectedDays: Int,
        val correlations: List<CorrelationResult> = emptyList()
    ) : TrendsUiState()
    data class Error(val message: String) : TrendsUiState()
}

@HiltViewModel
class TrendsViewModel @Inject constructor(
    private val healthRepository: HealthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<TrendsUiState>(TrendsUiState.Loading)
    val uiState: StateFlow<TrendsUiState> = _uiState.asStateFlow()

    // Cache pour les points de tendance (Données complétement traitées)
    private var _cachedTrendPoints = listOf<TrendPoint>()
    
    // Cache dédié pour les activités (comme demandé)
    private val _activityCache = mutableMapOf<String, ActivityData>()
    
    private var _isRepairing = false

    init {
        loadTrends(15)
    }

    fun loadTrends(days: Int = 15) {
        viewModelScope.launch {
            // 1. Try to serve from cache immediately if we have ANY data usually
            val cachedForRequest = _cachedTrendPoints.sortedByDescending { it.date }.take(days)
            if (cachedForRequest.isNotEmpty()) {
                 val correlations = computeCorrelations(cachedForRequest)
                _uiState.value = TrendsUiState.Success(cachedForRequest.sortedBy { it.date }, days, correlations)
            } else {
                _uiState.value = TrendsUiState.Loading // Only show loading if we really don't have data
            }

            try {
                val calendar = Calendar.getInstance()
                val endDate = DateUtils.getEndOfDay(calendar.time)
                
                val startCalendar = Calendar.getInstance()
                startCalendar.add(Calendar.DAY_OF_YEAR, -(days - 1))
                val startDate = DateUtils.getStartOfDay(startCalendar.time)
                
                // Fetch Data
                val hrvResult = healthRepository.getHrvHistory(startDate, endDate)
                val hrvMap = hrvResult.getOrNull()?.associateBy { DateUtils.formatForApi(it.time) }?.toMutableMap() ?: mutableMapOf()

                val moodHistory = healthRepository.getMoodHistory(startDate, endDate)
                val moodMap = moodHistory.associateBy { it.date }
                
                val symptomsHistory = healthRepository.getSymptomsHistory(startDate, endDate)
                val symptomsMap = symptomsHistory.associateBy { it.date }

                val hrHistoryResult = healthRepository.getHeartRateHistory(startDate, endDate)
                val hrMap = hrHistoryResult.getOrNull()?.associateBy { DateUtils.formatForApi(it.date) } ?: emptyMap()

                val intradayHistoryResult = healthRepository.getIntradayHistory(startDate, endDate)
                val intradayMap = intradayHistoryResult.getOrNull()?.associateBy { DateUtils.formatForApi(it.date) } ?: emptyMap()

                val sleepHistoryResult = healthRepository.getSleepHistory(startDate, endDate)
                val sleepLogs = sleepHistoryResult.getOrNull() ?: emptyList() 
                val sleepMap = sleepLogs.groupBy { DateUtils.formatForApi(it.date) }

                // Retrieve Activities using our specialized cache
                val activityMap = getActivitiesWithCache(startDate, endDate)

                val stepsResult = healthRepository.getStepsData(startDate, endDate)
                val stepsMap = stepsResult.getOrNull()?.associateBy { DateUtils.formatForApi(it.date) } ?: emptyMap()
                
                // ... (Existing HOTFIX for Today's HRV)
                try {
                    val today = Calendar.getInstance().time
                    val todayStr = DateUtils.formatForApi(today)
                    val todayResult = healthRepository.getHrvData(today)
                    val todayRecords = todayResult.getOrNull() ?: emptyList()
                    if (todayRecords.isNotEmpty()) {
                        val avgRmssd = todayRecords.map { it.rmssd }.average()
                        val syntheticRecord = com.cardio.fitbit.data.models.HrvRecord(time = todayRecords.first().time, rmssd = avgRmssd)
                        hrvMap[todayStr] = syntheticRecord
                    }
                } catch (e: Exception) { }

                val trendPoints = mutableListOf<TrendPoint>()
                val repairCandidates = mutableListOf<Date>()
                
                val processingCalendar = Calendar.getInstance()
                processingCalendar.time = endDate
                
                for (i in 0 until days) {
                    val targetDate = processingCalendar.time
                    val dateStr = DateUtils.formatForApi(targetDate)
                    
                    val hrvValue = hrvMap[dateStr]?.rmssd?.toInt()
                    val moodRating = moodMap[dateStr]?.rating
                    
                    val dailyHr = hrMap[dateStr]
                    val dailySleep = sleepMap[dateStr] ?: emptyList()
                    val dailyIntraday = intradayMap[dateStr]?.minuteData ?: emptyList()
                    val dailyActivity = activityMap[dateStr]
                    
                    val nativeRhr = dailyHr?.restingHeartRate
                    
                    val rhrResult = HeartRateAnalysisUtils.calculateDailyRHR(
                        date = targetDate,
                        intraday = dailyIntraday,
                        sleep = dailySleep,
                        activity = dailyActivity,
                        preMidnightHeartRates = emptyList(), 
                        nativeRhr = nativeRhr
                    )

                    if (dailyIntraday.isEmpty()) {
                        repairCandidates.add(targetDate)
                    }

                    val stepsFromActivity = dailyActivity?.summary?.steps ?: 0
                    val stepsFromTimeSeries = stepsMap[dateStr]?.steps ?: 0
                    val stepsFromIntraday = dailyIntraday.sumOf { it.steps }
                    
                    val dailySteps = when {
                        stepsFromActivity > 0 -> stepsFromActivity
                        stepsFromTimeSeries > 0 -> stepsFromTimeSeries
                        else -> stepsFromIntraday
                    }.takeIf { it > 0 }
                    val dailySymptoms = symptomsMap[dateStr]?.symptoms

                    val workoutDurationMinutes = dailyActivity?.activities?.sumOf { it.duration }?.let { millis ->
                        (millis / 1000 / 60).toInt()
                    } ?: 0

                    val dailySleepMinutes = dailySleep.maxByOrNull { it.duration }?.let { (it.duration / (1000 * 60)).toInt() }

                    val point = TrendPoint(
                        date = targetDate,
                        rhrNight = rhrResult.rhrNight,
                        rhrDay = rhrResult.rhrDay, 
                        rhrAvg = rhrResult.rhrAvg, 
                        hrv = hrvValue,
                        moodRating = moodRating,
                        steps = dailySteps,
                        workoutDurationMinutes = if (workoutDurationMinutes > 0) workoutDurationMinutes else null,
                        symptoms = dailySymptoms,
                        sleepMinutes = dailySleepMinutes
                    )
                    
                    trendPoints.add(point)
                    processingCalendar.add(Calendar.DAY_OF_YEAR, -1)
                }
                
                val sortedPoints = trendPoints.sortedBy { it.date }
                
                // Update Cache
                _cachedTrendPoints = sortedPoints
                
                val correlations = computeCorrelations(sortedPoints)
                
                _uiState.value = TrendsUiState.Success(sortedPoints, days, correlations)
                
                if (repairCandidates.isNotEmpty()) {
                    repairMissingData(repairCandidates, days)
                }
                
            } catch (e: Exception) {
                // If it fails but we have cache, keep showing cache? 
                // Currently errors out.
                // If we have cache, maybe revert to it?
                if (_cachedTrendPoints.isNotEmpty()) {
                     val correlations = computeCorrelations(_cachedTrendPoints)
                    _uiState.value = TrendsUiState.Success(_cachedTrendPoints.sortedBy { it.date }, days, correlations)
                } else {
                    _uiState.value = TrendsUiState.Error(e.message ?: "Unknown error")
                }
            }
        }
    }

    private suspend fun getActivitiesWithCache(startDate: Date, endDate: Date): Map<String, ActivityData> {
        val startCalendar = Calendar.getInstance()
        startCalendar.time = startDate
        val missingDates = mutableListOf<Date>()
        val result = mutableMapOf<String, ActivityData>()

        // 1. Check Memory Cache
        while (!startCalendar.time.after(endDate)) {
            val dStr = DateUtils.formatForApi(startCalendar.time)
            if (_activityCache.containsKey(dStr)) {
                result[dStr] = _activityCache[dStr]!!
            } else {
                missingDates.add(startCalendar.time)
            }
            startCalendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        // 2. Fetch Missing from Repository (Disk Cache or Network)
        if (missingDates.isNotEmpty()) {
            // Group contiguous or just fetch min-max of missing
            val fetchStart = missingDates.minOrNull() ?: startDate
            val fetchEnd = missingDates.maxOrNull() ?: endDate
            
            val apiResult = healthRepository.getActivityHistory(fetchStart, fetchEnd)
            val fetchedList = apiResult.getOrNull() ?: emptyList()
            
            // Update Cache
            fetchedList.forEach { 
                val dStr = DateUtils.formatForApi(it.date)
                _activityCache[dStr] = it
                result[dStr] = it
            }
        }
        
        return result
    }

    private fun computeCorrelations(points: List<TrendPoint>): List<CorrelationResult> {
        val results = mutableListOf<CorrelationResult>()
        
        // 1. Mood vs HRV
        val validMoodHrv = points.filter { it.moodRating != null && it.hrv != null }
        if (validMoodHrv.size >= 3) { 
             val avgMood = validMoodHrv.map { it.moodRating!! }.average()
             
             // Dynamic Pivot: Compare GoodMood Days (>= Avg) vs LowerMood Days (< Avg)
             val highMood = validMoodHrv.filter { it.moodRating!! >= avgMood } 
             val lowMood = validMoodHrv.filter { it.moodRating!! < avgMood }
             
             if (highMood.isNotEmpty() && lowMood.isNotEmpty()) {
                 val avgHrvHigh = highMood.map { it.hrv!! }.average()
                 val avgHrvLow = lowMood.map { it.hrv!! }.average()
                 
                 val diff = avgHrvHigh - avgHrvLow
                 if (kotlin.math.abs(diff) > 2) { 
                     val percent = ((diff / avgHrvLow) * 100).toInt()
                     val positive = diff > 0
                     
                     val desc = if (positive) "Une meilleure humeur est associée à une VRC plus élevée (+${kotlin.math.abs(percent)}%)." else "Étonnamment, votre VRC est plus élevée les jours de moins bonne humeur."
                     
                     results.add(CorrelationResult(
                         title = "Humeur & VRC",
                         description = desc,
                         impact = "${if(positive) "+" else ""}$percent%",
                         isPositive = positive
                     ))
                 }
             }
        }
        
        // 2. Symptoms (Any) vs Sleep Duration
        val symptomDays = points.filter { !it.symptoms.isNullOrEmpty() && it.sleepMinutes != null }
        val healthyDays = points.filter { it.symptoms.isNullOrEmpty() && it.sleepMinutes != null }
        
        // Slightly relaxed constraint: allow comparison if we have data for both
        if (symptomDays.isNotEmpty() && healthyDays.isNotEmpty()) {
            val avgSleepSick = symptomDays.map { it.sleepMinutes!! }.average()
            val avgSleepHealthy = healthyDays.map { it.sleepMinutes!! }.average()
            
            val diff = avgSleepSick - avgSleepHealthy
            if (kotlin.math.abs(diff) > 20) { // Relaxed from 30 to 20 mins
                 val diffMins = kotlin.math.abs(diff.toInt())
                 
                 results.add(CorrelationResult(
                     title = "Symptômes & Sommeil",
                     description = if (diff < 0) "Les symptômes semblent réduire votre sommeil (${diffMins} min en moins)." else "Vous dormez davantage les jours avec symptômes.",
                     impact = "${(diff/60).toInt()}h ${diffMins%60}m",
                     isPositive = diff > 0
                 ))
            }
        }
        
        // 3. Activity (Steps) vs Mood
        val validStepsMood = points.filter { it.steps != null && it.moodRating != null }
         if (validStepsMood.size >= 3) {
             val avgSteps = validStepsMood.map { it.steps!! }.average()
             
             // Dynamic Pivot: Active Days vs Less Active Days
             val highSteps = validStepsMood.filter { it.steps!! >= avgSteps }
             val lowSteps = validStepsMood.filter { it.steps!! < avgSteps }
             
             if (highSteps.isNotEmpty() && lowSteps.isNotEmpty()) {
                 val avgMoodActive = highSteps.map { it.moodRating!! }.average()
                 val avgMoodInactive = lowSteps.map { it.moodRating!! }.average()
                 
                 val diff = avgMoodActive - avgMoodInactive // 1-5 scale
                 if (kotlin.math.abs(diff) >= 0.4) { // Relaxed slightly from 0.5
                     val positive = diff > 0
                     results.add(CorrelationResult(
                         title = "Activité & Moral",
                         description = if(positive) "L'activité physique (> ${avgSteps.toInt()} pas) semble booster votre moral." else "Les jours moins actifs sont liés à une meilleure humeur.",
                         impact = "${if(positive) "+" else ""}${String.format("%.1f", diff)} pts",
                         isPositive = positive
                     ))
                 }
             }
         }

        return results
    }

    private fun repairMissingData(candidates: List<Date>, daysToReload: Int) {
        if (_isRepairing) return
        _isRepairing = true
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                candidates.forEach { date ->
                    healthRepository.getIntradayData(date, forceRefresh = true)
                    healthRepository.getSleepData(date, forceRefresh = true)
                }
                withContext(Dispatchers.Main) {
                    loadTrends(daysToReload)
                }
            } catch (e: Exception) {
               // Silent fail
            } finally {
                _isRepairing = false
            }
        }
    }

    suspend fun generatePdf(context: android.content.Context): java.io.File? = withContext(Dispatchers.IO) {
        val state = uiState.value
        if (state is TrendsUiState.Success) {
            return@withContext com.cardio.fitbit.utils.ReportGenerator.generateReport(context, state.data, state.selectedDays, state.correlations)
        }
        null
    }
}
