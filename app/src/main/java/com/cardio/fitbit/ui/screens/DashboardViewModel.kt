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
    private val userPreferencesRepository: com.cardio.fitbit.data.repository.UserPreferencesRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
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

    private var _hasRepairedGaps = false

    // Comparison Stats (Today vs 15-Day Avg)
    data class ComparisonStats(
        val hrvAvg: Int?,
        val rhrNightAvg: Int?,
        val rhrDayAvg: Int?,
        val stepsAvg: Int?,
        val deepSleepAvg: Int?, // in minutes
        val remSleepAvg: Int?,   // in minutes
        val recentNightRhrs: List<Int> = emptyList() // Last few days for trend detection
    )
    
    data class HealthInsight(
        val type: InsightType,
        val message: String,
        val color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Gray
    )
    
    enum class InsightType { SLEEP_HRV, ACTIVITY_RHR, GENERAL_TREND, CORRELATION, HEALTH_ALERT }
    
    data class ReadinessData(
        val score: Int,
        val message: String,
        val sleepContribution: Int,
        val hrvContribution: Int,
        val rhrContribution: Int,
        val activityContribution: Int
    )

    private val _comparisonStats = MutableStateFlow<ComparisonStats?>(null)
    val comparisonStats: StateFlow<ComparisonStats?> = _comparisonStats.asStateFlow()
    
    private val _readinessData = MutableStateFlow<ReadinessData?>(null)
    val readinessData: StateFlow<ReadinessData?> = _readinessData.asStateFlow()

    data class GoalProgress(
        val type: GoalType,
        val current: Int,
        val goal: Int,
        val progress: Float // 0..1
    )
    enum class GoalType { STEPS, WORKOUTS }

    private val _goalProgress = MutableStateFlow<List<GoalProgress>>(emptyList())
    val goalProgress = _goalProgress.asStateFlow()
    
    private val _insights = MutableStateFlow<List<HealthInsight>>(emptyList())
    val insights: StateFlow<List<HealthInsight>> = _insights.asStateFlow()

    // Settings
    val highHrThreshold = userPreferencesRepository.highHrThreshold
    val lowHrThreshold = userPreferencesRepository.lowHrThreshold
    val notificationsEnabled = userPreferencesRepository.notificationsEnabled
    val syncIntervalMinutes = userPreferencesRepository.syncIntervalMinutes
    val appLanguage = userPreferencesRepository.appLanguage
    val appTheme = userPreferencesRepository.appTheme
    val sleepGoalMinutes = userPreferencesRepository.sleepGoalMinutes
    val weeklyWorkoutGoal = userPreferencesRepository.weeklyWorkoutGoal
    val dailyStepGoal = userPreferencesRepository.dailyStepGoal
    
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

    private val _weeklyWorkoutsCount = MutableStateFlow(0)

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
                val fifteenDaysAgo = DateUtils.getDaysAgo(15)
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
                    launch { loadSteps(fifteenDaysAgo, today) },
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
                    launch { loadComparisonStats(selectedDate) },
                    launch { loadWeeklyWorkouts() }
                )
                jobs.forEach { it.join() }
                
                calculateGoalProgress()
                computeDerivedMetrics()

                computeDerivedMetrics()

                _uiState.value = DashboardUiState.Success
                
                // Proactively check for gaps in the last 7 days and repair them
                if (forceRefresh || !_hasRepairedGaps) {
                    _hasRepairedGaps = true
                    checkAndRepairGaps()
                }
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
                
                // Trigger Widget Update
                val widgetRequest = androidx.work.OneTimeWorkRequest.Builder(com.cardio.fitbit.workers.WidgetUpdateWorker::class.java).build()
                androidx.work.WorkManager.getInstance(context).enqueue(widgetRequest)
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
              
              computeReadinessAndInsights()
        }
    }
    
    private suspend fun computeReadinessAndInsights() {
        val stats = _comparisonStats.value ?: return
        val currentHrv = _hrvDailyAverage.value
        val currentRhrNight = _rhrNight.value
        val currentSleepMinutes = _sleepData.value.sumOf { it.duration } / 60000
        val sleepGoal = userPreferencesRepository.sleepGoalMinutes.first() ?: 480
        
        // --- 1. Readiness Score Calculation (0-100) ---
        var sleepScore = 0
        var hrvScore = 0
        var rhrScore = 0
        var activityScore = 15 // Base stability score
        
        // Sleep (Max 40 pts)
        // Split into Duration (30 pts) and Quality (10 pts)
        val sleepRatio = if (sleepGoal > 0) currentSleepMinutes.toFloat() / sleepGoal else 0f
        val durationScore = when {
            sleepRatio >= 1.0f -> 30
            else -> (sleepRatio * 30).toInt()
        }
        
        // Quality score based on Deep + REM sleep vs 15-day average
        val currentDeepRem = _sleepData.value.sumOf { (it.stages?.deep ?: 0) + (it.stages?.rem ?: 0) }
        val avgDeepRem = (stats.deepSleepAvg ?: 0) + (stats.remSleepAvg ?: 0)
        
        val qualityScore = if (avgDeepRem > 0) {
            val qualityRatio = currentDeepRem.toFloat() / avgDeepRem.toFloat()
            (qualityRatio.coerceAtMost(1.2f) * 10).toInt()
        } else if (currentDeepRem > 90) 8 else 0 // Fallback if no history but > 1.5h deep/rem

        sleepScore = durationScore + qualityScore

        // HRV (Max 30 pts)
        // Baseline (ratio 1.0) = 20 pts. Better than average (> 1.0) = up to 30 pts.
        if (currentHrv != null && stats.hrvAvg != null && stats.hrvAvg > 0) {
            val hrvRatio = currentHrv.toFloat() / stats.hrvAvg
            hrvScore = when {
                hrvRatio >= 1.15f -> 30
                hrvRatio >= 1.0f -> 20 + ((hrvRatio - 1.0f) * 66).toInt().coerceAtMost(10)
                else -> (hrvRatio * 20).toInt().coerceAtLeast(0)
            }
        } else if (currentHrv != null) {
            hrvScore = 15 // Partial if no history
        }

        // RHR (Max 15 pts)
        // Baseline (diff 0) = 10 pts. Lower RHR is better.
        if (currentRhrNight != null && stats.rhrNightAvg != null) {
            val rhrDiff = currentRhrNight - stats.rhrNightAvg
            rhrScore = when {
                rhrDiff <= -3 -> 15
                rhrDiff <= 0 -> 10 + (rhrDiff * -1.6).toInt().coerceAtLeast(0)
                else -> (10 - (rhrDiff * 2)).coerceAtLeast(0)
            }
        } else if (currentRhrNight != null) {
            rhrScore = 8
        }
        
        val totalScore = (sleepScore + hrvScore + rhrScore + activityScore).coerceIn(0, 100)
        val message = when {
            totalScore >= 85 -> "Excellente forme. C'est le jour idéal pour un effort intense !"
            totalScore >= 70 -> "Bonne condition physique. Vous êtes prêt pour la journée."
            totalScore >= 50 -> "État équilibré. Écoutez votre corps pendant l'effort."
            else -> "Besoin de récupération. Privilégiez l'échauffement léger et le repos."
        }

        _readinessData.value = ReadinessData(
            score = totalScore,
            message = message,
            sleepContribution = sleepScore,
            hrvContribution = hrvScore,
            rhrContribution = rhrScore,
            activityContribution = activityScore
        )

        // --- 2. Push to Wear OS Companion ---
        viewModelScope.launch {
            com.cardio.fitbit.utils.WearIntegrationManager.pushStatsToWear(
                context = context,
                    rhr = currentRhrNight,
                    hrv = currentHrv,
                    readiness = totalScore,
                    steps = _stepsData.value.find { DateUtils.isSameDay(it.date, java.util.Date()) }?.steps ?: 0
            )
        }

        // --- 3. Intelligent Insights Generation ---
        generateInsights(stats, currentSleepMinutes, currentHrv, currentRhrNight, currentDeepRem)
    }

    private fun generateInsights(stats: ComparisonStats, currentSleep: Long, currentHrv: Int?, currentRhr: Int?, currentDeepRem: Int) {
        val newInsights = mutableListOf<HealthInsight>()
        
        // --- 1. Health Alert: RHR Increase Trend ---
        if (currentRhr != null && stats.rhrNightAvg != null) {
            val rhrDiff = currentRhr - stats.rhrNightAvg
            
            // A. Sharp Jump Alert (> 6 bpm)
            if (rhrDiff >= 6) {
                newInsights.add(HealthInsight(
                    type = InsightType.HEALTH_ALERT,
                    message = "Alerte Santé : Votre pouls au repos a bondi de +$rhrDiff bpm. Cela peut être un signe précoce de fatigue intense ou d'incubation d'une maladie.",
                    color = androidx.compose.ui.graphics.Color(0xFFD32F2F)
                ))
            } 
            // B. Rising Trend Alert (3+ days of increase)
            else if (stats.recentNightRhrs.size >= 2) {
                val lastValues = stats.recentNightRhrs.takeLast(2)
                if (currentRhr > lastValues[1] && lastValues[1] > lastValues[0]) {
                     newInsights.add(HealthInsight(
                        type = InsightType.HEALTH_ALERT,
                        message = "Alerte Tendance : Votre pouls au repos augmente continuellement depuis 3 jours. Pensez à lever le pied.",
                        color = androidx.compose.ui.graphics.Color(0xFFD32F2F)
                    ))
                } else if (rhrDiff >= 3) {
                    newInsights.add(HealthInsight(
                        type = InsightType.ACTIVITY_RHR,
                        message = "Fréquence cardiaque nocturne en hausse (+${rhrDiff} bpm). Surveillez votre récupération.",
                        color = androidx.compose.ui.graphics.Color(0xFFFFB74D)
                    ))
                }
            }
        }

        // --- 2. Sleep Quality & Stages ---
        val avgDeepRem = (stats.deepSleepAvg ?: 0) + (stats.remSleepAvg ?: 0)
        if (currentDeepRem > 0 && avgDeepRem > 0 && currentDeepRem < avgDeepRem * 0.8) {
             newInsights.add(HealthInsight(
                type = InsightType.SLEEP_HRV,
                message = "Qualité de sommeil en baisse : votre temps en sommeil profond/REM est 20% inférieur à votre moyenne.",
                color = androidx.compose.ui.graphics.Color(0xFFFFB74D)
            ))
        }

        // --- 3. HRV Insights ---
        if (currentHrv != null && stats.hrvAvg != null && stats.hrvAvg > 0) {
            if (currentHrv < stats.hrvAvg * 0.85) {
                newInsights.add(HealthInsight(
                    type = InsightType.SLEEP_HRV,
                    message = "VFC sensiblement basse today. Risque de fatigue nerveuse ; privilégiez une séance légère.",
                    color = androidx.compose.ui.graphics.Color(0xFFE57373)
                ))
            } else if (currentHrv > stats.hrvAvg * 1.15) {
                newInsights.add(HealthInsight(
                    type = InsightType.GENERAL_TREND,
                    message = "Excellente VFC ! Votre système nerveux est très bien équilibré.",
                    color = androidx.compose.ui.graphics.Color(0xFF4CAF50)
                ))
            }
        }
        
        // --- 4. Correlations ---
        if (currentSleep < 360 && currentHrv != null && stats.hrvAvg != null && currentHrv < stats.hrvAvg) {
            newInsights.add(HealthInsight(
                type = InsightType.CORRELATION,
                message = "Corrélation : Votre VFC baisse toujours quand vous dormez < 6h. Visez 7h ce soir.",
                color = androidx.compose.ui.graphics.Color(0xFF64B5F6)
            ))
        }

        _insights.value = newInsights
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

    private suspend fun loadWeeklyWorkouts() {
        try {
            val now = java.util.Date()
            val cal = java.util.Calendar.getInstance()
            cal.time = now
            cal.set(java.util.Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
            val startOfWeek = cal.time
            
            val result = healthRepository.getActivityHistory(startOfWeek, now) 
            result.onSuccess { data ->
                // Filter activities that are "real" workouts (not just sedentary)
                val workoutCount = data.flatMap { day -> 
                    day.activities.filter { it.duration > 15 * 60 * 1000 } // > 15 min
                }.size
                _weeklyWorkoutsCount.value = workoutCount
            }
        } catch (e: Exception) {
            android.util.Log.e("DashboardVM", "Error loading weekly workouts", e)
        }
    }

    private fun calculateGoalProgress() {
        val currentGoals = mutableListOf<GoalProgress>()
        
        // 1. Steps Goal
        val todaySteps = _stepsData.value.find { DateUtils.isSameDay(it.date, java.util.Date()) }?.steps ?: 0
        // Wait, stepsData is a list of StepsData (history).
        // Let's get the most recent one if today isn't explicitly there or if we just want today's.
        val stepsGoalVal = viewModelScope.async { dailyStepGoal.first() }
        val workoutGoalVal = viewModelScope.async { weeklyWorkoutGoal.first() }
        
        viewModelScope.launch {
            val sGoal = stepsGoalVal.await()
            val wGoal = workoutGoalVal.await()
            
            // Steps Progress
            val stepsProgress = if (sGoal > 0) todaySteps.toFloat() / sGoal else 0f
            currentGoals.add(GoalProgress(
                type = GoalType.STEPS,
                current = todaySteps,
                goal = sGoal,
                progress = stepsProgress.coerceAtMost(1f)
            ))
            
            // Workouts Progress
            val wCount = _weeklyWorkoutsCount.value
            val workoutProgress = if (wGoal > 0) wCount.toFloat() / wGoal else 0f
            currentGoals.add(GoalProgress(
                type = GoalType.WORKOUTS,
                current = wCount,
                goal = wGoal,
                progress = workoutProgress.coerceAtMost(1f)
            ))
            
            _goalProgress.value = currentGoals
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

        val startDate = DateUtils.getDaysAgo(15, date)
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
    
    fun saveWorkoutIntensity(activityId: Long, intensity: Int) {
        viewModelScope.launch {
            healthRepository.saveWorkoutIntensity(activityId, intensity)
            // Reload activity data to reflect the change
            loadActivity(_selectedDate.value, forceRefresh = false)
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

    fun updateWeeklyWorkoutGoal(goal: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setWeeklyWorkoutGoal(goal)
            calculateGoalProgress()
        }
    }

    fun updateDailyStepGoal(goal: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setDailyStepGoal(goal)
            calculateGoalProgress()
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
        // Calculate 15-day average (Previous 15 days: [Current-15 ... Current-1])
        // Strict "previous 15 days" is better for "trend vs today".
        val cal = java.util.Calendar.getInstance()
        cal.time = currentDate
        cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        val endDate = cal.time // Yesterday (relative to selected date)
        
        cal.add(java.util.Calendar.DAY_OF_YEAR, -14) // Go back 14 more days (total 15)
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
                
                // Process Sleep Stages
                var deepAvg: Int? = null
                var remAvg: Int? = null
                if (sleepResult.isSuccess) {
                    val sleepList = sleepResult.getOrNull() ?: emptyList<SleepData>()
                    val validDeep = sleepList.map { it.stages?.deep ?: 0 }.filter { it > 0 }
                    val validRem = sleepList.map { it.stages?.rem ?: 0 }.filter { it > 0 }
                    
                    if (validDeep.isNotEmpty()) deepAvg = validDeep.average().toInt()
                    if (validRem.isNotEmpty()) remAvg = validRem.average().toInt()
                }

                // Process RHR (Day/Night)
                
                // Process RHR (Day/Night)
                // We need to run calculateDailyRHR for each day in range
                var rhrNightSum = 0
                var rhrNightCount = 0
                var rhrDaySum = 0
                var rhrDayCount = 0
                val dailyNightRhrs = mutableListOf<Int>()
                
                if (intradayResult.isSuccess && sleepResult.isSuccess) {
                    val intradayList = intradayResult.getOrNull() ?: emptyList<IntradayData>()
                    val intradayMap = intradayList.associateBy { DateUtils.formatForApi(it.date) }
                    
                    val sleepList = sleepResult.getOrNull() ?: emptyList<SleepData>()
                    
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
                                dailyNightRhrs.add(rhr.rhrNight)
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
                
                _comparisonStats.value = ComparisonStats(hrvAvg, rhrNightAvg, rhrDayAvg, stepsAvg, deepAvg, remAvg, dailyNightRhrs)
            }
        } catch (e: Exception) {
            // fail silently, just no comparison shown
        }
    }

    private fun checkAndRepairGaps() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Check last 7 days for gaps in intraday data
                val today = DateUtils.getToday()
                val startDate = DateUtils.getDaysAgo(7, today)
                
                val history = healthRepository.getIntradayHistory(startDate, today, forceRefresh = false).getOrNull() ?: emptyList()
                val dayMap = history.associateBy { DateUtils.formatForApi(it.date) }
                
                val calendar = java.util.Calendar.getInstance()
                calendar.time = startDate
                
                while (!calendar.time.after(today)) {
                    val dateStr = DateUtils.formatForApi(calendar.time)
                    val data = dayMap[dateStr]
                    
                    // If data is missing or empty (but not unknown/unauthorized), try to repair
                    if (data == null || data.minuteData.isEmpty()) {
                        android.util.Log.d("DashboardVM", "Repairing gap for $dateStr")
                        healthRepository.getIntradayData(calendar.time, forceRefresh = true)
                        healthRepository.getSleepData(calendar.time, forceRefresh = true)
                    }
                    calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
                }
                
                // Only reload if we are on one of the repaired days or if it's today
                // For simplicity, we just trigger a refresh if the current selected date was repaired
                val selectedDate = _selectedDate.value
                val diffMs = Math.abs(today.time - selectedDate.time)
                if (diffMs <= 8 * 24 * 60 * 60 * 1000L) { // ~8 days to be safe
                    // Refresh current view silently
                     loadAllData(forceRefresh = false) 
                }
            } catch (e: Exception) {
                // Ignore errors during background repair
            }
        }
    }
}

sealed class DashboardUiState {
    object Loading : DashboardUiState()
    object Success : DashboardUiState()
    data class Error(val message: String) : DashboardUiState()
}
