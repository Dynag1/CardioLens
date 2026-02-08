package com.cardio.fitbit.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cardio.fitbit.auth.FitbitAuthManager
import com.cardio.fitbit.data.models.*
import com.cardio.fitbit.data.repository.HealthRepository
import com.cardio.fitbit.utils.DateUtils
import com.cardio.fitbit.utils.HeartRateAnalysisUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
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

    private val _hrvData = MutableStateFlow<List<HrvRecord>>(emptyList())
    val hrvData: StateFlow<List<HrvRecord>> = _hrvData.asStateFlow()

    private val _spo2Data = MutableStateFlow<SpO2Data?>(null)
    val spo2Data: StateFlow<SpO2Data?> = _spo2Data.asStateFlow()

    private val _spo2History = MutableStateFlow<List<SpO2Data>>(emptyList())
    val spo2History: StateFlow<List<SpO2Data>> = _spo2History.asStateFlow()
        
    // Daily HRV Average (derived from hrvData)
    private val _hrvDailyAverage = MutableStateFlow<Int?>(null)
    val hrvDailyAverage: StateFlow<Int?> = _hrvDailyAverage.asStateFlow()
    
    private val _dailySymptoms = MutableStateFlow<String?>(null)
    val dailySymptoms: StateFlow<String?> = _dailySymptoms.asStateFlow()

    // Comparison Stats (Today vs 7-Day Avg)
    data class ComparisonStats(
        val hrvAvg: Int?,
        val rhrNightAvg: Int?,
        val rhrDayAvg: Int?,
        val stepsAvg: Int?
    )

    private val _comparisonStats = MutableStateFlow<ComparisonStats?>(null)
    val comparisonStats: StateFlow<ComparisonStats?> = _comparisonStats.asStateFlow()

    // Settings
    val highHrThreshold = userPreferencesRepository.highHrThreshold
    val lowHrThreshold = userPreferencesRepository.lowHrThreshold
    val notificationsEnabled = userPreferencesRepository.notificationsEnabled
    val syncIntervalMinutes = userPreferencesRepository.syncIntervalMinutes
    val appLanguage = userPreferencesRepository.appLanguage
    val appTheme = userPreferencesRepository.appTheme
    val sleepGoalMinutes = userPreferencesRepository.sleepGoalMinutes
    
    // Dynamic HR Zones
    val dateOfBirth = userPreferencesRepository.dateOfBirth
    // Calculate Age and MaxHR
    // We combine the dateOfBirth flow to map it to MaxHR
    val userMaxHr: kotlinx.coroutines.flow.Flow<Int> = dateOfBirth.map { dob: Long? ->
        if (dob == null || dob == 0L) {
            220 // Default if unknown
        } else {
            val birthDate = java.util.Date(dob)
            val today = java.util.Date()
            val age = DateUtils.getAge(birthDate, today)
            220 - age
        }
    }

    // Derived Metrics
    private val _rhrDay = MutableStateFlow<Int?>(null)
    val rhrDay = _rhrDay.asStateFlow()

    private val _rhrNight = MutableStateFlow<Int?>(null)
    val rhrNight = _rhrNight.asStateFlow()

    private val _aggregatedMinuteData = MutableStateFlow<List<MinuteData>>(emptyList())
    val aggregatedMinuteData = _aggregatedMinuteData.asStateFlow()

    // Daily Min/Max HR
    private val _minHr = MutableStateFlow<MinuteData?>(null)
    val minHr = _minHr.asStateFlow()

    private val _maxHr = MutableStateFlow<MinuteData?>(null)
    val maxHr = _maxHr.asStateFlow()

    // Current Provider ID (for showing the appropriate icon on logout button)
    private val _currentProviderId = MutableStateFlow<String>("FITBIT")
    val currentProviderId = _currentProviderId.asStateFlow()

    // Last Sync Time
    // Last Sync Time
    val lastSyncTimestamp = userPreferencesRepository.lastSyncTimestamp

    private val _dailyMood = MutableStateFlow<Int?>(null)
    val dailyMood = _dailyMood.asStateFlow()

    private val _isAuthorized = MutableStateFlow(true)
    val isAuthorized = _isAuthorized.asStateFlow()

    init {
        loadAllData()
        loadCurrentProvider()
    }

    fun saveMood(rating: Int) {
        viewModelScope.launch {
            val date = _selectedDate.value
            healthRepository.saveMood(date, rating)
            _dailyMood.value = rating
        }
    }

    fun changeDate(daysOffset: Int) {
        val calendar = java.util.Calendar.getInstance()
        calendar.time = _selectedDate.value
        calendar.add(java.util.Calendar.DAY_OF_YEAR, daysOffset)
        val newDate = calendar.time
        setDate(newDate)
    }

    fun setDate(newDate: java.util.Date) {
        // If same date (ignore time), do nothing? Or force reload?
        // For now, let's just reload.
        
        _selectedDate.value = newDate
        
        // Reset state & Set UI to Loading to provide feedback
        _uiState.value = DashboardUiState.Loading
        _sleepData.value = emptyList()
        _activityData.value = null
        _intradayData.value = null
        _aggregatedMinuteData.value = emptyList()
        _rhrDay.value = null
        _rhrNight.value = null
        _hrvData.value = emptyList()
        _hrvDailyAverage.value = null
        _dailyMood.value = null
        _spo2History.value = emptyList()
        _spo2History.value = emptyList()
        _dailySymptoms.value = null
        _comparisonStats.value = null // Reset comparison

        // Only reload date-dependent data
        viewModelScope.launch {
            try {
                // Launch loads in parallel
                // forceRefresh is false by default to use CACHE
                val jobs = listOf(
                    launch { loadHeartRate(newDate) },
                    launch { loadSleep(newDate, forceRefresh = false) },
                    launch { loadActivity(newDate, forceRefresh = false) }, 
                    launch { loadIntradayData(newDate, forceRefresh = false) },
                    launch { loadHrvData(newDate, forceRefresh = false) },
                    launch { loadMood(newDate) },
                    launch { loadSymptoms(newDate) },
                    launch { loadSpO2(newDate, forceRefresh = false) },
                    launch { loadComparisonStats(newDate) } // Load comparison stats
                )
                jobs.forEach { it.join() } // Wait for all data
                computeDerivedMetrics()
                
                // Switch back to success
                _uiState.value = DashboardUiState.Success
            } catch (e: Exception) {
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




                // Check authorization status (e.g. for new permissions)
                _isAuthorized.value = healthRepository.isAuthorized()

                // Reset state to prevent stales from other days or previous failed loads
                _sleepData.value = emptyList()
                _activityData.value = null
                _intradayData.value = null
                _aggregatedMinuteData.value = emptyList()
                _rhrDay.value = null
                _rhrNight.value = null
                _hrvData.value = emptyList()
                _hrvDailyAverage.value = null

                // Load all data in parallel
                val jobs = listOf(
                    launch { loadHeartRate(selectedDate, forceRefresh) },
                    launch { loadSleep(selectedDate, forceRefresh) },
                    launch { loadSteps(sevenDaysAgo, today) },
                    launch { loadActivity(selectedDate, forceRefresh) }, 
                    launch { loadActivity(selectedDate, forceRefresh) }, 
                    launch { loadIntradayData(selectedDate, forceRefresh) },
                    launch { loadIntradayData(selectedDate, forceRefresh) },
                    launch { loadHrvData(selectedDate, forceRefresh) },
                    launch { loadMood(selectedDate) },
                    launch { loadHrvData(selectedDate, forceRefresh) },
                    launch { loadMood(selectedDate) },
                    launch { loadSymptoms(selectedDate) },
                    launch { loadSpO2(selectedDate, forceRefresh) },
                    launch { loadComparisonStats(selectedDate) }
                )
                jobs.forEach { it.join() }
                
                computeDerivedMetrics()

                computeDerivedMetrics()

                _uiState.value = DashboardUiState.Success
                
                // Update Last Sync Time (only if we actually refreshed or loaded successfully)
                // We consider a successful loadAllData as a sync point
                // Use the timestamp of the LATEST valid data retrieved (HR > 0)
                // If checking "Today", getting current time is annoying if watch hasn't synced.
                // We want the actual last point.
                
                val intraday = _intradayData.value?.minuteData ?: emptyList()
                val latestPoint = intraday.filter { it.heartRate > 0 || it.steps > 0 }.maxByOrNull { it.time }
                
                val latestDataTime = latestPoint?.let { point ->
                    DateUtils.parseFitbitTimeOrDateTime(point.time, selectedDate)?.time
                } ?: System.currentTimeMillis() // Fallback only if ABSOLUTELY no data found (rare for today if using app)
                
                // If we found data, use it. If not, keeping old sync time might be better? 
                // But for now, user asked for "last info recovered". if no info, current time of check seems okay-ish, or maybe 0?
                // Let's stick to calculated time.
                
                userPreferencesRepository.setLastSyncTimestamp(latestDataTime)
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
             // 3. Calculate Night RHR (Average of Sleep HR)
             // Check if any sleep session started YESTERDAY/Before midnight?
             val startOfDayTs = DateUtils.getStartOfDay(date).time
             val preMidnightSessions = sleep.filter { it.startTime.time < startOfDayTs }
             
             val preMidnightHeartRates = mutableListOf<Int>()

             if (preMidnightSessions.isNotEmpty()) {
                 val earliestStart = preMidnightSessions.minOf { it.startTime }
                 
                 try {
                      // Fetch extra data for previous day part of sleep
                      // Since we are in a coroutine, we can call suspend functions
                      // Note: We need to handle this carefully to not block or crash if repository is not available or throws
                      val result = healthRepository.getHeartRateSeries(earliestStart, java.util.Date(startOfDayTs))
                      val extraData = result.getOrNull() ?: emptyList()
                      
                      extraData.forEach { point ->
                          if (point.heartRate > 0) {
                              preMidnightHeartRates.add(point.heartRate)
                          }
                      }

                 } catch (e: Exception) {
                     android.util.Log.e("DashboardVM", "Failed to load pre-midnight HR", e)
                 }
             }

             val rhrResult = HeartRateAnalysisUtils.calculateDailyRHR(
                 date, 
                 intraday, 
                 sleep, 
                 activity,
                 preMidnightHeartRates
             )

             _rhrDay.value = rhrResult.rhrDay
             _rhrNight.value = rhrResult.rhrNight


              // Aggregation & Min/Max
              _aggregatedMinuteData.value = intraday.sortedBy { it.time }
              val validData = intraday.filter { it.heartRate > 0 }
              if (validData.isNotEmpty()) {
                  _minHr.value = validData.minByOrNull { it.heartRate }
                  _maxHr.value = validData.maxByOrNull { it.heartRate }
              } else {
                  _minHr.value = null
                  _maxHr.value = null
              }
        }
    }

    private suspend fun loadHeartRate(date: java.util.Date, forceRefresh: Boolean = false) {
        val result = healthRepository.getHeartRateData(date, forceRefresh)
        result.onSuccess { data ->
            _heartRateData.value = data
        }
    }

    private suspend fun loadSleep(date: java.util.Date, forceRefresh: Boolean = false) {

        
        // 1. Fetch Today's Sleep (Standard)
        val resultToday = healthRepository.getSleepData(date, forceRefresh)
        
        // 2. Fetch Tomorrow's Sleep (To catch sessions starting late today, e.g. 23:00, which Fitbit assigns to tomorrow)
        val cal = java.util.Calendar.getInstance()
        cal.time = date
        cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
        val nextDay = cal.time
        
        // Don't force refresh next day blindly to save calls/time, unless needed
        val resultNextDay = healthRepository.getSleepData(nextDay, forceRefresh = false) // Usually cached or quick
        
        val combinedSleep = mutableListOf<SleepData>()
        
        if (resultToday.isSuccess) {
            combinedSleep.addAll(resultToday.getOrNull() ?: emptyList())
        }
        
        if (resultNextDay.isSuccess) {
            val nextDaySleep = resultNextDay.getOrNull() ?: emptyList()
            val startOfCurrentDay = DateUtils.getStartOfDay(date).time
            val endOfCurrentDay = DateUtils.getEndOfDay(date).time
            
            // Filter: Starts within CURRENT DAY (e.g. 22:00 today)
            val overlapping = nextDaySleep.filter { 
                it.startTime.time in startOfCurrentDay..endOfCurrentDay 
            }
            combinedSleep.addAll(overlapping)
        }

        if (resultToday.isSuccess || resultNextDay.isSuccess) {
             // Deduplicate just in case provider returns overlap (like Health Connect)
            val uniqueSleep = combinedSleep.distinctBy { it.startTime.time }

            _sleepData.value = uniqueSleep
        } else {
            // Only log failure if main request failed
             if (resultToday.isFailure) {
                 android.util.Log.e("DashboardVM", "Failed to load sleep data", resultToday.exceptionOrNull())
             }
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

        val result = healthRepository.getIntradayData(date, forceRefresh)
        result.onSuccess { data ->

            _intradayData.value = data
        }
        result.onFailure { e ->

            if (e is com.cardio.fitbit.data.api.RateLimitException) {
                _uiState.value = DashboardUiState.Error("Trop de requêtes. Réessayez dans 1h.")
            }
        }
    }

    private suspend fun loadHrvData(date: java.util.Date, forceRefresh: Boolean = false) {
        val result = healthRepository.getHrvData(date, forceRefresh)
        result.onSuccess { data ->
            _hrvData.value = data
            // Calculate daily average RMSSD
            if (data.isNotEmpty()) {
                val average = data.map { it.rmssd }.average()
                _hrvDailyAverage.value = average.toInt()
            } else {
                _hrvDailyAverage.value = null
            }
        }
    }

    private suspend fun loadMood(date: java.util.Date) {
        _dailyMood.value = healthRepository.getMood(date)
    }

    private suspend fun loadSpO2(date: java.util.Date, forceRefresh: Boolean = false) {
        // Load Today's SpO2
        val result = healthRepository.getSpO2Data(date, forceRefresh)
        result.onSuccess { data ->
            _spo2Data.value = data
        }

        // Load History (Last 7 days)
        val startDate = DateUtils.getDaysAgo(7, date)
        val historyResult = healthRepository.getSpO2History(startDate, date, forceRefresh)
        historyResult.onSuccess { list ->
            _spo2History.value = list
        }
    }

    private suspend fun loadSymptoms(date: java.util.Date) {
        _dailySymptoms.value = healthRepository.getSymptoms(date)
    }

    fun saveSymptoms(symptoms: String) {
        viewModelScope.launch {
            val date = _selectedDate.value
            healthRepository.saveSymptoms(date, symptoms)
            _dailySymptoms.value = symptoms
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

    fun updateAppLanguage(languageCode: String) {
        viewModelScope.launch {
            userPreferencesRepository.setAppLanguage(languageCode)
        }
    }

    fun updateAppTheme(theme: String) {
        viewModelScope.launch {
            userPreferencesRepository.setAppTheme(theme)
        }
    }
    
    fun updateSleepGoalMinutes(minutes: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setSleepGoalMinutes(minutes)
        }
    }
    
    fun setDateOfBirth(timestamp: Long) {
        viewModelScope.launch {
            userPreferencesRepository.setDateOfBirth(timestamp)
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

            } catch (e: Exception) {

            }
        }
    }

    private suspend fun loadComparisonStats(currentDate: java.util.Date) {
        // Calculate 7-day average (Previous 7 days: [Current-7 ... Current-1])
        // Strict "previous 7 days" is better for "trend vs today".
        val cal = java.util.Calendar.getInstance()
        cal.time = currentDate
        cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        val endDate = cal.time // Yesterday (relative to selected date)
        
        cal.add(java.util.Calendar.DAY_OF_YEAR, -6) // Go back 6 more days (total 7)
        val startDate = cal.time
        
        // Parallel Fetch History
        val hrvHistoryDeferred = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { /* async fetch */ }
        // We need return values, so using explicit async block or relying on repository calls directly?
        // Repository calls are suspend, so better done sequentially or with async/await pattern if inside coroutine scope.
        // Since we are already in suspend function, let's use coroutineScope { async... }
        
        try {
            kotlinx.coroutines.coroutineScope {
                val hrvDeferred = async { healthRepository.getHrvHistory(startDate, endDate) }
                val intradayDeferred = async { healthRepository.getIntradayHistory(startDate, endDate) } // Heavy?
                val sleepDeferred = async { healthRepository.getSleepHistory(startDate, endDate) }
                val stepsDeferred = async { healthRepository.getStepsData(startDate, endDate) }
                
                val hrvResult = hrvDeferred.await()
                val intradayResult = intradayDeferred.await()
                val sleepResult = sleepDeferred.await()
                val stepsResult = stepsDeferred.await()
                
                // Process HRV
                val hrvAvg = if (hrvResult.isSuccess) {
                    val records = hrvResult.getOrNull() ?: emptyList<com.cardio.fitbit.data.models.HrvRecord>()
                    // Calculate Daily Averages first
                    val dailyAvgs = records.groupBy { DateUtils.formatForApi(it.time) }
                        .mapValues { entry -> entry.value.map { r -> r.rmssd }.average() }
                        .values
                    
                    if (dailyAvgs.isNotEmpty()) dailyAvgs.average().toInt() else null
                } else null

                // Process Steps
                val stepsAvg = if (stepsResult.isSuccess) {
                    val stepsList = stepsResult.getOrNull() ?: emptyList<StepsData>()
                    if (stepsList.isNotEmpty()) {
                        stepsList.map { it.steps }.average().toInt()
                    } else null
                } else null
                
                // Process RHR (Day/Night)
                
                // Process RHR (Day/Night)
                // We need to run calculateDailyRHR for each day in range
                var rhrNightSum = 0
                var rhrNightCount = 0
                var rhrDaySum = 0
                var rhrDayCount = 0
                
                if (intradayResult.isSuccess && sleepResult.isSuccess) {
                    val intradayList = intradayResult.getOrNull() ?: emptyList<IntradayData>()
                    val intradayMap = intradayList.associateBy { DateUtils.formatForApi(it.date) }
                    
                    val sleepList = sleepResult.getOrNull() ?: emptyList<SleepData>()
                    // Sleep needs to be grouped by day? Or passed as list to utility? Utility takes full list and filters? checks implementation...
                    // HeartRateAnalysisUtils.calculateDailyRHR(date, intraday, sleep, activity?, preMidnight?)
                    // It filters sleep sessions overlapping with 'date'.
                    
                    // Iterate each day in range
                    val loopCal = java.util.Calendar.getInstance()
                    loopCal.time = startDate
                    val endCal = java.util.Calendar.getInstance()
                    endCal.time = endDate
                    
                    while (!loopCal.time.after(endCal.time)) {
                        val d = loopCal.time
                        val dStr = DateUtils.formatForApi(d)
                        
                        val dailyIntraday = intradayMap[dStr]?.minuteData ?: emptyList()
                        // If no intraday, we can't calc distinctive RHR cleanly. Skip or use fallback?
                        if (dailyIntraday.isNotEmpty()) {
                            val rhr = HeartRateAnalysisUtils.calculateDailyRHR(
                                date = d,
                                intraday = dailyIntraday,
                                sleep = sleepList, 
                                activity = null, // simplified, ignoring activity exclusion for history check to start
                                preMidnightHeartRates = emptyList() // simplified
                            )
                            
                            if (rhr.rhrNight != null) {
                                rhrNightSum += rhr.rhrNight
                                rhrNightCount++
                            }
                            if (rhr.rhrDay != null) {
                                rhrDaySum += rhr.rhrDay
                                rhrDayCount++
                            }
                        }
                        
                        loopCal.add(java.util.Calendar.DAY_OF_YEAR, 1)
                    }
                }
                
                val rhrNightAvg = if (rhrNightCount > 0) rhrNightSum / rhrNightCount else null
                val rhrDayAvg = if (rhrDayCount > 0) rhrDaySum / rhrDayCount else null
                
                _comparisonStats.value = ComparisonStats(hrvAvg, rhrNightAvg, rhrDayAvg, stepsAvg)
            }
        } catch (e: Exception) {
            // fail silently, just no comparison shown
            _comparisonStats.value = null
        }
    }
}

sealed class DashboardUiState {
    object Loading : DashboardUiState()
    object Success : DashboardUiState()
    data class Error(val message: String) : DashboardUiState()
}
