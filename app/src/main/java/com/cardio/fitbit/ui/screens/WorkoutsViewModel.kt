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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.util.Date
import java.util.Calendar

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
    
    // Activity Type Filter
    private val _selectedActivityType = MutableStateFlow<String>("Tous")
    val selectedActivityType: StateFlow<String> = _selectedActivityType.asStateFlow()
    
    // Available activity types from loaded workouts
    private val _availableActivityTypes = MutableStateFlow<List<String>>(listOf("Tous"))
    val availableActivityTypes: StateFlow<List<String>> = _availableActivityTypes.asStateFlow()
    
    // Sort order
    enum class SortOrder { RECENT, DURATION, INTENSITY }
    private val _sortOrder = MutableStateFlow(SortOrder.RECENT)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()
    
    // Filtered activities based on selected type
    private var filteredActivities: List<ActivityItem> = emptyList()
    
    // Monthly statistics
    data class MonthlyStats(
        val totalActivities: Int = 0,
        val avgDuration: Long = 0, // in milliseconds
        val avgIntensity: Double = 0.0, // 1-5 scale
        val totalCalories: Int = 0
    )
    
    private val _monthlyStats = MutableStateFlow(MonthlyStats())
    val monthlyStats: StateFlow<MonthlyStats> = _monthlyStats.asStateFlow()
    
    // Search query
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    // Grouped activities by week
    data class WeekGroup(
        val weekLabel: String,
        val weekNumber: Int,
        val year: Int,
        val activities: List<ActivityItem>,
        val totalActivities: Int,
        val totalDuration: Long,
        val totalCalories: Int
    )
    
    // Grouped Activities derived from current UI State (paged)
    val groupedActivities: StateFlow<List<WeekGroup>> = _uiState.map { state ->
        if (state is WorkoutsUiState.Success) {
            groupActivitiesByWeek(state.activities)
        } else {
            emptyList()
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
    // Normalize activity type names (group Walk/Marche, Run/Course, etc.)
    private fun normalizeActivityType(activityName: String): String {
        return when {
            activityName.contains("walk", ignoreCase = true) || activityName.contains("marche", ignoreCase = true) -> "Marche"
            activityName.contains("run", ignoreCase = true) || activityName.contains("course", ignoreCase = true) -> "Course"
            activityName.contains("bike", ignoreCase = true) || activityName.contains("vélo", ignoreCase = true) || activityName.contains("cycling", ignoreCase = true) -> "Vélo"
            activityName.contains("swim", ignoreCase = true) || activityName.contains("natation", ignoreCase = true) -> "Natation"
            activityName.contains("workout", ignoreCase = true) || activityName.contains("exercice", ignoreCase = true) || activityName.contains("exercise", ignoreCase = true) -> "Exercice"
            else -> activityName.trim() // Keep original for other types, trimmed
        }
    }

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
                filteredActivities = flatList
                
                // Extract unique activity types (normalized)
                val types = flatList
                    .map { normalizeActivityType(it.activity.activityName) }
                    .distinct()
                    .sorted()
                _availableActivityTypes.value = listOf("Tous") + types
                
                calculateWeeklySummaries(flatList)
                calculateMonthlyStats(flatList)
                currentPage = 0
                loadNextPage() // Load first page
            } else {
                _uiState.value = WorkoutsUiState.Error(result.exceptionOrNull()?.message ?: "Unknown Error")
            }
        }
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
        applyFiltersAndSort()
    }

    fun setActivityTypeFilter(type: String) {
        _selectedActivityType.value = type
        applyFiltersAndSort()
    }
    
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        applyFiltersAndSort()
    }
    
    private fun applyFiltersAndSort() {
        // First filter by type
        var filtered = if (_selectedActivityType.value == "Tous") {
            allActivities
        } else {
            allActivities.filter { item ->
                normalizeActivityType(item.activity.activityName) == _selectedActivityType.value
            }
        }
        
        // Then filter by search query
        val query = _searchQuery.value.trim()
        if (query.isNotEmpty()) {
            filtered = filtered.filter { item ->
                item.activity.activityName.contains(query, ignoreCase = true) ||
                DateUtils.formatForDisplay(item.fullDateOfActivity).contains(query, ignoreCase = true)
            }
        }
        
        // Then sort
        filteredActivities = when (_sortOrder.value) {
            SortOrder.RECENT -> filtered.sortedByDescending { it.fullDateOfActivity }
            SortOrder.DURATION -> filtered.sortedByDescending { it.activity.duration }
            SortOrder.INTENSITY -> filtered.sortedByDescending { it.activity.averageHeartRate ?: 0 }
        }
        
        // Reset pagination and immediately show first page
        currentPage = 0
        val displayList = filteredActivities.take(pageSize)
        currentPage = 1
        _uiState.value = WorkoutsUiState.Success(displayList)
        
        // Trigger fetch for intraday data
        fetchIntradayForItems(displayList)
    }
    
    private fun groupActivitiesByWeek(activities: List<ActivityItem>): List<WeekGroup> {
        val cal = Calendar.getInstance()
        val groups = activities.groupBy { item ->
            cal.time = item.date
            val week = cal.get(Calendar.WEEK_OF_YEAR)
            val year = cal.get(Calendar.YEAR)
            Pair(year, week)
        }.map { (yearWeek, items) ->
            val (year, week) = yearWeek
            cal.clear()
            cal.set(Calendar.YEAR, year)
            cal.set(Calendar.WEEK_OF_YEAR, week)
            cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            
            val weekStart = cal.time
            val sdf = java.text.SimpleDateFormat("d MMM", java.util.Locale.FRENCH)
            val weekLabel = "Semaine du ${sdf.format(weekStart)}"
            
            WeekGroup(
                weekLabel = weekLabel,
                weekNumber = week,
                year = year,
                activities = items,
                totalActivities = items.size,
                totalDuration = items.sumOf { it.activity.duration },
                totalCalories = items.sumOf { it.activity.calories }
            )
        }.sortedByDescending { it.year * 100 + it.weekNumber }
        
        return groups
    }

    fun loadNextPage() {
        // If already showing all, do nothing
        val currentState = _uiState.value
        if (currentState is WorkoutsUiState.Success) {
            if (currentState.activities.size >= filteredActivities.size) return
        }

        val nextIndex = (currentPage + 1) * pageSize
        val displayList = filteredActivities.take(nextIndex)
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
            
            // Avg Speed (for all acts with distance)
            val speedActs = acts.filter { 
                (it.activity.distance ?: 0.0) > 0.0 && it.activity.duration > 0
            }
            val avgSpeed = if (speedActs.isNotEmpty()) {
                // Total Distance / Total Duration (hours)
                val totalDist = speedActs.sumOf { it.activity.distance!! }
                val totalDurHours = speedActs.sumOf { it.activity.duration } / 3600000.0
                if (totalDurHours > 0) totalDist / totalDurHours else 0.0
            } else 0.0
            
            // Avg Steps (for all acts with steps)
            val stepActs = acts.filter { (it.activity.steps ?: 0) > 0 }
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
    
    private fun calculateMonthlyStats(activities: List<ActivityItem>) {
        val now = Calendar.getInstance()
        val currentMonth = now.get(Calendar.MONTH)
        val currentYear = now.get(Calendar.YEAR)
        
        val monthActivities = activities.filter { item ->
            val cal = Calendar.getInstance().apply { time = item.date }
            cal.get(Calendar.MONTH) == currentMonth && cal.get(Calendar.YEAR) == currentYear
        }
        
        val totalActivities = monthActivities.size
        val avgDuration = if (totalActivities > 0) monthActivities.sumOf { it.activity.duration } / totalActivities else 0L
        val totalCalories = monthActivities.sumOf { it.activity.calories }
        
        // Calculate average manual intensity (1-5)
        val activitiesWithIntensity = monthActivities.filter { (it.activity.intensity ?: 0) > 0 }
        val avgIntensity = if (activitiesWithIntensity.isNotEmpty()) {
            activitiesWithIntensity.map { it.activity.intensity!! }.average()
        } else 0.0
        
        _monthlyStats.value = MonthlyStats(
            totalActivities = totalActivities,
            avgDuration = avgDuration,
            avgIntensity = avgIntensity,
            totalCalories = totalCalories
        )
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
    
    // Delete activity
    fun deleteActivity(activityId: Long) {
        viewModelScope.launch {
            try {
                // Remove from allActivities
                allActivities = allActivities.filter { it.activity.activityId != activityId }
                // Reapply filters
                applyFiltersAndSort()
                // Optionally: delete from backend/cache if needed
            } catch (e: Exception) {
                android.util.Log.e("WorkoutsViewModel", "Error deleting activity", e)
            }
        }
    }
    
    // Share activity summary
    fun getActivityShareText(activity: Activity, date: Date): String {
        val dateStr = java.text.SimpleDateFormat("dd MMMM yyyy 'à' HH:mm", java.util.Locale.FRENCH).format(date)
        val duration = activity.duration / (1000 * 60) // minutes
        val hours = duration / 60
        val minutes = duration % 60
        
        return buildString {
            appendLine("🏃 ${activity.activityName}")
            appendLine("📅 $dateStr")
            appendLine("⏱️ Durée: ${hours}h ${minutes}min")
            activity.distance?.let { appendLine("📏 Distance: ${String.format("%.2f", it)} km") }
            appendLine("🔥 Calories: ${activity.calories} kcal")
            activity.averageHeartRate?.let { appendLine("❤️ FC moy: $it bpm") }
            activity.steps?.let { appendLine("👟 Pas: $it") }
        }
    }

    fun saveWorkoutIntensity(activityId: Long, intensity: Int) {
        viewModelScope.launch {
            healthRepository.saveWorkoutIntensity(activityId, intensity)
            // Reload workouts to reflect the change
            loadWorkouts()
        }
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
