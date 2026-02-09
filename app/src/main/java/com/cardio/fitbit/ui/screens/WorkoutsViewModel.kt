package com.cardio.fitbit.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cardio.fitbit.data.models.Activity
import com.cardio.fitbit.data.models.ActivityData
import com.cardio.fitbit.data.models.MinuteData
import com.cardio.fitbit.data.repository.HealthRepository
import com.cardio.fitbit.utils.DateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.util.Date

@HiltViewModel
class WorkoutsViewModel @Inject constructor(
    private val healthRepository: HealthRepository
) : ViewModel() {

    // Helper data class to pair Activity with its Date (ActivityData parent)
    data class ActivityItem(
        val activity: Activity,
        val date: Date,
        val fullDateOfActivity: Date // Constructed date + time
    )

    private val _uiState = MutableStateFlow<WorkoutsUiState>(WorkoutsUiState.Loading)
    val uiState: StateFlow<WorkoutsUiState> = _uiState.asStateFlow()

    // Full list of ALL activities sorted by time desc
    private var allActivities: List<ActivityItem> = emptyList()
    
    // Pagination
    private var currentPage = 0
    private val pageSize = 3

    // Cache for Intraday Data: Key = Date String (yyyy-MM-dd), Value = List<MinuteData>
    private val _intradayCache = MutableStateFlow<Map<String, List<MinuteData>>>(emptyMap())
    val intradayCache: StateFlow<Map<String, List<MinuteData>>> = _intradayCache.asStateFlow()
    
    // Track loading state of intraday data
    private val _loadingIntraday = MutableStateFlow<Set<String>>(emptySet())
    val loadingIntraday: StateFlow<Set<String>> = _loadingIntraday.asStateFlow()

    init {
        loadWorkouts()
    }

    fun loadWorkouts() {
        viewModelScope.launch {
            _uiState.value = WorkoutsUiState.Loading
            val result = healthRepository.getAllActivities()
            
            if (result.isSuccess) {
                val activityDataList = result.getOrNull() ?: emptyList()
                
                // Flatten and Sort
                val flatList = activityDataList.flatMap { activityData ->
                    activityData.activities.map { activity ->
                        ActivityItem(
                            activity = activity,
                            date = activityData.date,
                            fullDateOfActivity = Date(DateUtils.combineDateAndTime(activityData.date, activity.startTime.time)) // approximate if startTime is just HH:mm
                        )
                    }
                }.sortedByDescending { it.fullDateOfActivity }

                allActivities = flatList
                calculateWeeklySummaries(flatList)
                currentPage = 0
                loadNextPage() // Load first page
            } else {
                _uiState.value = WorkoutsUiState.Error(result.exceptionOrNull()?.message ?: "Unknown Error")
            }
        }
    }

    fun loadNextPage() {
        // If already showing all, do nothing
        val currentState = _uiState.value
        if (currentState is WorkoutsUiState.Success) {
            if (currentState.activities.size >= allActivities.size) return
        }

        val nextIndex = (currentPage + 1) * pageSize
        val displayList = allActivities.take(nextIndex)
        currentPage++

        _uiState.value = WorkoutsUiState.Success(displayList)
        
        // Trigger fetch for intraday data for these dates
        fetchIntradayForItems(displayList.takeLast(pageSize))
    }

    private fun fetchIntradayForItems(items: List<ActivityItem>) {
        val uniqueDates = items.map { DateUtils.formatForApi(it.date) }.distinct()
        
        uniqueDates.forEach { dateStr ->
            // Check if already cached OR already loading
            if (_intradayCache.value.containsKey(dateStr)) return@forEach
            if (_loadingIntraday.value.contains(dateStr)) return@forEach
            
            viewModelScope.launch {
                _loadingIntraday.value += dateStr
                
                val date = DateUtils.parseApiDate(dateStr)
                if (date != null) {
                    val result = healthRepository.getIntradayData(date)
                    if (result.isSuccess) {
                        val data = result.getOrNull()
                        val minuteData = data?.minuteData ?: data?.preciseData ?: emptyList() // Prefer minute data for charts, precise if available? Chart handles both.
                        // Actually ActivityDetailCard uses 'allMinuteData'. 
                        // IntradayData object contains 'minuteData' (1min) and 'preciseData' (1sec). 
                        // Let's store the BEST available.
                        
                        val bestData = if (!data?.preciseData.isNullOrEmpty()) data?.preciseData!! else (data?.minuteData ?: emptyList())
                        
                        _intradayCache.value += (dateStr to bestData)
                    }
                }
                _loadingIntraday.value -= dateStr
            }
        }
    }

    private fun calculateWeeklySummaries(activities: List<ActivityItem>) {
        val grouped = activities.groupBy { DateUtils.getYearWeek(it.fullDateOfActivity) }
        
        val summaries = grouped.map { (yearWeek, acts) ->
            val parts = yearWeek.split("-")
            val year = parts[0].toIntOrNull() ?: 0
            val week = parts[1].toIntOrNull() ?: 0
            val startDate = DateUtils.getWeekStartDate(year, week)
            val endDate = DateUtils.getDaysAgo(-6, startDate) // +6 days

            val count = acts.size
            val totalDuration = acts.sumOf { it.activity.duration }
            val avgDuration = if (count > 0) totalDuration / count else 0L
            
            // Intensity (Calories / min)
            val totalCalories = acts.sumOf { it.activity.calories }
            // Avoid division by zero for duration (convert ms to min)
            val totalMinutes = totalDuration / 60000.0
            val intensity = if (totalMinutes > 0) totalCalories / totalMinutes else 0.0
            
            // Avg HR (Weighted by duration?? or simple average of averages?)
            // Simple average of non-zero HRs
            val validHrActs = acts.filter { (it.activity.averageHeartRate ?: 0) > 0 }
            val avgHr = if (validHrActs.isNotEmpty()) {
                validHrActs.map { it.activity.averageHeartRate!! }.average().toInt()
            } else 0
            
            // Avg Speed (only for Walk/Run acts with distance)
            val speedActs = acts.filter { 
                val name = it.activity.activityName.lowercase()
                (name.contains("walk") || name.contains("marche") || name.contains("run") || name.contains("course")) &&
                (it.activity.distance ?: 0.0) > 0.0 && it.activity.duration > 0
            }
            val avgSpeed = if (speedActs.isNotEmpty()) {
                // Total Distance / Total Duration (hours)
                val totalDist = speedActs.sumOf { it.activity.distance!! }
                val totalDurHours = speedActs.sumOf { it.activity.duration } / 3600000.0
                if (totalDurHours > 0) totalDist / totalDurHours else 0.0
            } else 0.0
            
            // Avg Steps (only for Walk/Run)
            val stepActs = acts.filter {
                val name = it.activity.activityName.lowercase()
                (name.contains("walk") || name.contains("marche") || name.contains("run") || name.contains("course"))
            }
            val totalSteps = stepActs.sumOf { it.activity.steps ?: 0 }
            val avgSteps = if (stepActs.isNotEmpty()) totalSteps / stepActs.size else 0

            WeeklySummary(
                year = year,
                week = week,
                startDate = startDate,
                endDate = endDate,
                count = count,
                totalDuration = totalDuration,
                avgDuration = avgDuration,
                avgIntensity = intensity,
                avgHeartRate = avgHr,
                avgSpeed = avgSpeed,
                avgSteps = avgSteps
            )
        }.sortedByDescending { it.startDate } // Recent weeks first

        _weeklySummaries.value = summaries
        
        // Auto-select first week (current) if we want? 
        // Or UI handles paging.
    }

    private val _weeklySummaries = MutableStateFlow<List<WeeklySummary>>(emptyList())
    val weeklySummaries: StateFlow<List<WeeklySummary>> = _weeklySummaries.asStateFlow()

    // Export State
    private val _exportEvent = MutableStateFlow<java.io.File?>(null)
    val exportEvent: StateFlow<java.io.File?> = _exportEvent.asStateFlow()
    
    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    fun exportPdf(context: android.content.Context, summary: WeeklySummary) {
        viewModelScope.launch {
            _isExporting.value = true
            
            // 1. Filter activities for this week
            val weekActivities = allActivities.filter { 
                DateUtils.getYearWeek(it.fullDateOfActivity) == "${summary.year}-${summary.week}"
            }.sortedByDescending { it.fullDateOfActivity }

            // 2. Ensure we have intraday data for all
            val dataList = mutableListOf<Pair<Activity, List<MinuteData>>>()
            
            weekActivities.forEach { item ->
                val dateStr = DateUtils.formatForApi(item.date)
                // Check cache first
                var minuteData = _intradayCache.value[dateStr]
                
                if (minuteData == null) {
                    // Fetch if missing
                    val date = DateUtils.parseApiDate(dateStr)
                    if (date != null) {
                        val result = healthRepository.getIntradayData(date)
                        if (result.isSuccess) {
                            val data = result.getOrNull()
                            val fetchedData = if (!data?.preciseData.isNullOrEmpty()) data?.preciseData!! else (data?.minuteData ?: emptyList())
                             _intradayCache.value += (dateStr to fetchedData)
                             minuteData = fetchedData
                        }
                    }
                }
                
                dataList.add(item.activity to (minuteData ?: emptyList()))
            }

            // 3. Generate PDF
            val file = com.cardio.fitbit.utils.PdfGenerator.generateWeeklyReport(context, summary, dataList)
            
            if (file != null) {
                _exportEvent.value = file
            }
            
            _isExporting.value = false
        }
    }
    
    fun clearExportEvent() {
        _exportEvent.value = null
    }
}

data class WeeklySummary(
    val year: Int,
    val week: Int,
    val startDate: Date,
    val endDate: Date,
    val count: Int,
    val totalDuration: Long,
    val avgDuration: Long,
    val avgIntensity: Double, // Cal/min
    val avgHeartRate: Int,
    val avgSpeed: Double, // km/h
    val avgSteps: Int
)

sealed class WorkoutsUiState {
    object Loading : WorkoutsUiState()
    data class Success(val activities: List<WorkoutsViewModel.ActivityItem>) : WorkoutsUiState()
    data class Error(val message: String) : WorkoutsUiState()
}
