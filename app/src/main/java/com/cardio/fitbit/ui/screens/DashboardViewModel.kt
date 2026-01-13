package com.cardio.fitbit.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cardio.fitbit.auth.FitbitAuthManager
import com.cardio.fitbit.data.models.*
import com.cardio.fitbit.data.repository.HealthRepository
import com.cardio.fitbit.utils.DateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val healthRepository: HealthRepository,
    private val authManager: FitbitAuthManager,
    private val userPreferencesRepository: com.cardio.fitbit.data.repository.UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _heartRateData = MutableStateFlow<HeartRateData?>(null)
    val heartRateData: StateFlow<HeartRateData?> = _heartRateData.asStateFlow()

    private val _sleepData = MutableStateFlow<SleepData?>(null)
    val sleepData: StateFlow<SleepData?> = _sleepData.asStateFlow()

    private val _stepsData = MutableStateFlow<List<StepsData>>(emptyList())
    val stepsData: StateFlow<List<StepsData>> = _stepsData.asStateFlow()

    private val _activityData = MutableStateFlow<ActivityData?>(null)
    val activityData: StateFlow<ActivityData?> = _activityData.asStateFlow()

    private val _intradayData = MutableStateFlow<com.cardio.fitbit.data.models.IntradayData?>(null)
    val intradayData: StateFlow<com.cardio.fitbit.data.models.IntradayData?> = _intradayData.asStateFlow()

    private val _selectedDate = MutableStateFlow(DateUtils.getToday())
    val selectedDate: StateFlow<java.util.Date> = _selectedDate.asStateFlow()

    // Settings
    val highHrThreshold = userPreferencesRepository.highHrThreshold
    val lowHrThreshold = userPreferencesRepository.lowHrThreshold
    val notificationsEnabled = userPreferencesRepository.notificationsEnabled
    val syncIntervalMinutes = userPreferencesRepository.syncIntervalMinutes

    // Derived Metrics
    private val _rhrDay = MutableStateFlow<Int?>(null)
    val rhrDay = _rhrDay.asStateFlow()

    private val _rhrNight = MutableStateFlow<Int?>(null)
    val rhrNight = _rhrNight.asStateFlow()

    private val _aggregatedMinuteData = MutableStateFlow<List<MinuteData>>(emptyList())
    val aggregatedMinuteData = _aggregatedMinuteData.asStateFlow()

    init {
        loadAllData()
    }

    fun changeDate(daysOffset: Int) {
        val calendar = java.util.Calendar.getInstance()
        calendar.time = _selectedDate.value
        calendar.add(java.util.Calendar.DAY_OF_YEAR, daysOffset)
        val newDate = calendar.time
        _selectedDate.value = newDate
        
        android.util.Log.d("DashboardVM", "Changing date to: ${DateUtils.formatForApi(newDate)}")

        // Reset state & Set UI to Loading to provide feedback
        _uiState.value = DashboardUiState.Loading
        _sleepData.value = null
        _activityData.value = null
        _intradayData.value = null
        _aggregatedMinuteData.value = emptyList()
        _rhrDay.value = null
        _rhrNight.value = null

        // Only reload date-dependent data
        viewModelScope.launch {
            try {
                // Launch loads in parallel
                // forceRefresh is false by default to use CACHE
                val jobs = listOf(
                    launch { loadHeartRate(newDate) },
                    launch { loadSleep(newDate, forceRefresh = false) },
                    launch { loadActivity(newDate, forceRefresh = false) }, 
                    launch { loadIntradayData(newDate, forceRefresh = false) }
                )
                jobs.forEach { it.join() } // Wait for all data
                computeDerivedMetrics()
                
                // Switch back to success
                _uiState.value = DashboardUiState.Success
            } catch (e: Exception) {
                android.util.Log.e("DashboardVM", "Error changing date", e)
                _uiState.value = DashboardUiState.Error(e.message ?: "Erreur de navigation")
            }
        }
    }

    fun loadAllData(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = DashboardUiState.Loading
            
            try {
                val today = DateUtils.getToday()
                val sevenDaysAgo = DateUtils.getDaysAgo(7)
                val selectedDate = _selectedDate.value

                android.util.Log.d("DashboardVM", "loadAllData called with forceRefresh=$forceRefresh")

                // Reset state to prevent stales from other days or previous failed loads
                _sleepData.value = null
                _activityData.value = null
                _intradayData.value = null
                _aggregatedMinuteData.value = emptyList()
                _rhrDay.value = null
                _rhrNight.value = null

                // Load all data in parallel
                val jobs = listOf(
                    launch { loadHeartRate(today) },
                    launch { loadSleep(selectedDate, forceRefresh) },
                    launch { loadSteps(sevenDaysAgo, today) },
                    launch { loadActivity(selectedDate, forceRefresh = true) }, 
                    launch { loadIntradayData(selectedDate, forceRefresh) }
                )
                jobs.forEach { it.join() }
                
                computeDerivedMetrics()

                _uiState.value = DashboardUiState.Success
            } catch (e: Exception) {
                _uiState.value = DashboardUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun computeDerivedMetrics() {
        val intraday = _intradayData.value?.minuteData
        if (intraday == null) {
            // Clear metrics if no intraday data is available for current date
            _rhrDay.value = null
            _rhrNight.value = null
            _aggregatedMinuteData.value = emptyList()
            return
        }
        val sleep = _sleepData.value
        val activity = _activityData.value
        val date = _selectedDate.value

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
             // Helper to parse HH:mm to timestamp for the selected date
             fun getTimestamp(timeStr: String): Long {
                 val parts = timeStr.split(":")
                 val cal = java.util.Calendar.getInstance()
                 cal.time = date
                 cal.set(java.util.Calendar.HOUR_OF_DAY, parts[0].toInt())
                 cal.set(java.util.Calendar.MINUTE, parts[1].toInt())
                 cal.set(java.util.Calendar.SECOND, 0)
                 return cal.timeInMillis
             }

             // Parse all minutes
             val parsedMinutes = intraday.map { 
                 val ts = getTimestamp(it.time)
                 Triple(it, ts, it.heartRate)
             }

             // 1. RHR Logic
             // Sleep Logic: Range check
             val sleepStart = sleep?.startTime?.time ?: 0L
             val sleepEnd = sleep?.endTime?.time ?: 0L
             
             // Activity Ranges
             val activityRanges = activity?.activities?.map { 
                 it.startTime.time..(it.startTime.time + it.duration) 
             } ?: emptyList()

             val nightRates = mutableListOf<Int>()
             val dayRates = mutableListOf<Int>()

             // Sort chronologically to handle cooldown
             val sortedMinutes = parsedMinutes.sortedBy { it.second }
             var lastActiveTimestamp = 0L
             val cooldownMs = 5 * 60 * 1000L // 5 minutes buffer after activity

             sortedMinutes.filter { it.third > 0 }.forEach { (data, ts, hr) ->
                 val isSleep = (ts in sleepStart..sleepEnd)
                 
                 // Mark active if steps > 0 or in specific activity range
                 val isPhysicallyActive = (data.steps > 0) || activityRanges.any { range -> ts in range }
                 
                 if (isPhysicallyActive) {
                     lastActiveTimestamp = ts
                 }

                 if (isSleep) {
                     nightRates.add(hr)
                 } else {
                     // Check Day conditions: Not active AND recovery period passed
                     val inCooldown = (ts - lastActiveTimestamp) < cooldownMs
                     
                     if (!isPhysicallyActive && !inCooldown) {
                         dayRates.add(hr)
                     }
                 }
             }

             android.util.Log.d("DashboardVM", "RHR Stats Raw: Night avg=${if(nightRates.isNotEmpty()) nightRates.average() else 0}, Day avg=${if(dayRates.isNotEmpty()) dayRates.average() else 0}")
             
             _rhrNight.value = if (nightRates.isNotEmpty()) nightRates.average().toInt() else null
             
             // For Day RHR, use the MEDIAN of resting values.
             // Median is scientifically more robust than simple average (sensitive to stress spikes)
             // and less aggressive than lowest 20% (which mimics Sleeping HR).
             // Ideally reflects "Awake, Still, and Calm" state.
             _rhrDay.value = if (dayRates.isNotEmpty()) {
                 val sortedDay = dayRates.sorted()
                 val size = sortedDay.size
                 if (size % 2 == 0) {
                     ((sortedDay[size / 2 - 1] + sortedDay[size / 2]) / 2.0).toInt()
                 } else {
                     sortedDay[size / 2]
                 }
             } else {
                 null
             }

              // 2. Aggregation Logic (Now Pass-through Minute-by-Minute)
              _aggregatedMinuteData.value = intraday.sortedBy { it.time }
         }
    }

    private suspend fun loadHeartRate(date: java.util.Date) {
        val result = healthRepository.getHeartRateData(date)
        result.onSuccess { data ->
            _heartRateData.value = data
        }
    }

    private suspend fun loadSleep(date: java.util.Date, forceRefresh: Boolean = false) {
        android.util.Log.d("DashboardVM", "Loading sleep data for: ${DateUtils.formatForApi(date)}")
        val result = healthRepository.getSleepData(date, forceRefresh)
        result.onSuccess { data ->
            android.util.Log.d("DashboardVM", "Sleep data loaded: ${data?.startTime} - ${data?.endTime}")
            _sleepData.value = data
        }
        result.onFailure { e ->
            android.util.Log.e("DashboardVM", "Failed to load sleep data", e)
        }
    }

    private suspend fun loadSteps(startDate: java.util.Date, endDate: java.util.Date) {
        val result = healthRepository.getStepsData(startDate, endDate)
        result.onSuccess { data ->
            _stepsData.value = data
        }
    }

    private suspend fun loadActivity(date: java.util.Date, forceRefresh: Boolean = false) {
        val result = healthRepository.getActivityData(date, forceRefresh)
        result.onSuccess { data ->
            _activityData.value = data
        }
    }

    private suspend fun loadIntradayData(date: java.util.Date, forceRefresh: Boolean = false) {
        android.util.Log.d("DashboardVM", "Loading intraday data...")
        val result = healthRepository.getIntradayData(date, forceRefresh)
        result.onSuccess { data ->
            android.util.Log.d("DashboardVM", "Intraday data loaded: ${data?.minuteData?.size ?: 0} points")
            _intradayData.value = data
        }
        result.onFailure { e ->
            android.util.Log.e("DashboardVM", "Failed to load intraday data", e)
        }
    }

    fun updateHighHrThreshold(value: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setHighHrThreshold(value)
        }
    }

    fun updateLowHrThreshold(value: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setLowHrThreshold(value)
        }
    }

    fun toggleNotifications(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setNotificationsEnabled(enabled)
        }
    }
    
    fun updateSyncInterval(minutes: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setSyncIntervalMinutes(minutes)
        }
    }

    fun logout() {
        authManager.logout()
    }
}

sealed class DashboardUiState {
    object Loading : DashboardUiState()
    object Success : DashboardUiState()
    data class Error(val message: String) : DashboardUiState()
}
