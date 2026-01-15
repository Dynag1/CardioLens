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
import kotlinx.coroutines.flow.first
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

    private val _sleepData = MutableStateFlow<List<SleepData>>(emptyList())
    val sleepData: StateFlow<List<SleepData>> = _sleepData.asStateFlow()

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

    // Daily Min/Max HR
    private val _minHr = MutableStateFlow<Int?>(null)
    val minHr = _minHr.asStateFlow()

    private val _maxHr = MutableStateFlow<Int?>(null)
    val maxHr = _maxHr.asStateFlow()

    // Current Provider ID (for showing the appropriate icon on logout button)
    private val _currentProviderId = MutableStateFlow<String>("FITBIT")
    val currentProviderId = _currentProviderId.asStateFlow()

    init {
        loadAllData()
        loadCurrentProvider()
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
        _sleepData.value = emptyList()
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
                _sleepData.value = emptyList()
                _activityData.value = null
                _intradayData.value = null
                _aggregatedMinuteData.value = emptyList()
                _rhrDay.value = null
                _rhrNight.value = null

                // Load all data in parallel
                val jobs = listOf(
                    launch { loadHeartRate(selectedDate) },
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
            _rhrDay.value = null
            _rhrNight.value = null
            _aggregatedMinuteData.value = emptyList()
            return
        }
        val sleep = _sleepData.value
        val activity = _activityData.value
        val date = _selectedDate.value

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
             // Helper: Parse HH:mm to timestamp
             fun getTimestamp(timeStr: String): Long {
                 val parts = timeStr.split(":")
                 val cal = java.util.Calendar.getInstance()
                 cal.time = date
                 cal.set(java.util.Calendar.HOUR_OF_DAY, parts[0].toInt())
                 cal.set(java.util.Calendar.MINUTE, parts[1].toInt())
                 cal.set(java.util.Calendar.SECOND, 0)
                 return cal.timeInMillis
             }

             // Parse and Sort Intraday Data
             val sortedMinutes = intraday.map { 
                 val ts = getTimestamp(it.time)
                 Triple(it, ts, it.heartRate)
             }.sortedBy { it.second }

             // 1. Identify Activity & Sleep Ranges
             val sleepRanges = sleep.map { it.startTime.time..it.endTime.time }
             val activityRanges = activity?.activities?.map { 
                 it.startTime.time..(it.startTime.time + it.duration) 
             } ?: emptyList()

             // 2. Mark Valid/Invalid Minutes
             val validMinutesMask = BooleanArray(sortedMinutes.size) { false }
             var cooldownUntil = 0L
             val COOLDOWN_MS = 15 * 60 * 1000L

             val nightHeartRates = mutableListOf<Int>()

             sortedMinutes.forEachIndexed { index, (data, ts, hr) ->
                 // A. Sleep Check
                 val isSleep = sleepRanges.any { range -> ts in range }
                 if (isSleep) {
                     if (hr > 0) nightHeartRates.add(hr) // Collect Night HR
                     return@forEachIndexed // Exclude from Day RHR
                 }

                 // B. Activity Check (Logged Activity OR Steps > 0)
                 val isLoggedActivity = activityRanges.any { range -> ts in range }
                 val isStepActivity = data.steps > 0
                 
                 if (isLoggedActivity || isStepActivity) {
                     // Trigger Cooldown
                     cooldownUntil = ts + COOLDOWN_MS
                     return@forEachIndexed
                 }

                 // C. Cooldown Check
                 if (ts < cooldownUntil) {
                     return@forEachIndexed
                 }

                 // D. Invalid Data Check (Noise/Device Off)
                 // < 35 bpm considered physiological outlier/noise
                 if (hr < 35) {
                     return@forEachIndexed
                 }

                 // If passed all checks -> VALID Resting Minute
                 validMinutesMask[index] = true
             }

             // 3. Calculate Night RHR (Median of Sleep HR)
             _rhrNight.value = if (nightHeartRates.isNotEmpty()) {
                 val sorted = nightHeartRates.sorted()
                 val mid = sorted.size / 2
                 if (sorted.size % 2 == 0) (sorted[mid-1] + sorted[mid]) / 2 else sorted[mid]
             } else null

             // 4. Calculate Day RHR (Sliding Window on Valid Minutes)
             // Scientific Standard: Lowest stable 10-minute average
             val windowAverages = mutableListOf<Double>()
             val WINDOW_SIZE_MINUTES = 10
             
             // Iterate through minutes to find 10-min valid blocks
             for (i in 0..sortedMinutes.size - WINDOW_SIZE_MINUTES) {
                 // Optimization: Skip if start is invalid
                 if (!validMinutesMask[i]) continue

                 val startTs = sortedMinutes[i].second
                 val endTsTarget = startTs + (WINDOW_SIZE_MINUTES * 60 * 1000L)
                 
                 var sampleCount = 0
                 var sumHr = 0.0
                 var isValidWindow = true
                 
                 // Scan forward 
                 for (j in i until sortedMinutes.size) {
                     val currTs = sortedMinutes[j].second
                     if (currTs >= endTsTarget) break // Window full
                     
                     // Strict Contiguity: Any invalid minute taints the window
                     if (!validMinutesMask[j]) {
                         isValidWindow = false
                         break
                     }
                     
                     sumHr += sortedMinutes[j].third
                     sampleCount++
                 }

                 // Require density (at least 8 samples for a 10m window to allow very minor gaps/sync freq)
                 if (isValidWindow && sampleCount >= 8) {
                     windowAverages.add(sumHr / sampleCount)
                 }
             }

             // 5. Select Resting Baseline (20th Percentile of Valid Windows)
             // Scientific Method: Lowest 20% represents true resting state
             // Balanced approach between strict minimum and overall average
             _rhrDay.value = if (windowAverages.isNotEmpty()) {
                 val sorted = windowAverages.sorted()
                 val percentile20Index = (sorted.size * 0.20).toInt().coerceAtLeast(0).coerceAtMost(sorted.size - 1)
                 sorted[percentile20Index].toInt()
             } else null

              // Aggregation & Min/Max
              _aggregatedMinuteData.value = intraday.sortedBy { it.time }
              val allHrValues = intraday.map { it.heartRate }.filter { it > 0 }
              if (allHrValues.isNotEmpty()) {
                  _minHr.value = allHrValues.minOrNull()
                  _maxHr.value = allHrValues.maxOrNull()
              } else {
                  _minHr.value = null
                  _maxHr.value = null
              }
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
            android.util.Log.d("DashboardVM", "Sleep sessions loaded: ${data.size}")
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
        viewModelScope.launch {
            if (userPreferencesRepository.useHealthConnect.first()) {
                userPreferencesRepository.setUseHealthConnect(false)
            } else {
                authManager.logout()
            }
        }
    }

    private fun loadCurrentProvider() {
        viewModelScope.launch {
            try {
                val providerId = healthRepository.getCurrentProviderId()
                _currentProviderId.value = providerId
                android.util.Log.d("DashboardVM", "Current provider: $providerId")
            } catch (e: Exception) {
                android.util.Log.e("DashboardVM", "Error loading current provider", e)
            }
        }
    }
}

sealed class DashboardUiState {
    object Loading : DashboardUiState()
    object Success : DashboardUiState()
    data class Error(val message: String) : DashboardUiState()
}
