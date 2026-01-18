package com.cardio.fitbit.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cardio.fitbit.data.models.ActivityData
import com.cardio.fitbit.data.models.IntradayHeartRate
import com.cardio.fitbit.data.models.SleepData
import com.cardio.fitbit.data.repository.HealthRepository
import com.cardio.fitbit.utils.DateUtils
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

        // --- CALCULATION LOGIC (Copied/Adapted from DashboardViewModel) ---

        // Helper to get timestamp from HH:mm or HH:mm:ss string for THIS date
        fun getTimestamp(timeStr: String): Long {
            val parts = timeStr.split(":")
            val c = Calendar.getInstance().apply { time = date }
            c.set(Calendar.HOUR_OF_DAY, parts[0].toInt())
            c.set(Calendar.MINUTE, parts[1].toInt())
            c.set(Calendar.SECOND, 0)
            c.set(Calendar.MILLISECOND, 0)
            return c.timeInMillis
        }

        // Parse and Sort Intraday Data
        val sortedMinutes = intraday.map { data -> 
            val ts = getTimestamp(data.time)
            Triple(data, ts, data.heartRate)
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

        sortedMinutes.forEachIndexed { index, triple ->
            val data = triple.first
            val ts = triple.second
            val hr = triple.third
            
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
        // Check if any sleep session started YESTERDAY/Before midnight?
        val startOfDayTs = DateUtils.getStartOfDay(date).time
        val preMidnightSessions = sleep.filter { it.startTime.time < startOfDayTs }
        
        if (preMidnightSessions.isNotEmpty()) {
            val earliestStart = preMidnightSessions.minOf { it.startTime }
            try {
                 // Fetch generic series
                 val result = healthRepository.getHeartRateSeries(earliestStart, Date(startOfDayTs))
                 val extraData = result.getOrNull() ?: emptyList()
                 
                 extraData.forEach { point ->
                     if (point.heartRate > 0) {
                         nightHeartRates.add(point.heartRate)
                     }
                 }
            } catch (e: Exception) {
                // Ignore error in trend calculation to avoid crashing layout
            }
        }

        val rhrNight = if (nightHeartRates.isNotEmpty()) {
            nightHeartRates.average().toInt()
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

            // Require density (at least 8 samples for a 10m window)
            if (isValidWindow && sampleCount >= 8) {
                windowAverages.add(sumHr / sampleCount)
            }
        }

        // 5. Select Resting Baseline (Median of Valid Windows)
        val rhrDay = if (windowAverages.isNotEmpty()) {
            val sorted = windowAverages.sorted()
            val mid = sorted.size / 2
            if (sorted.size % 2 == 0) ((sorted[mid-1] + sorted[mid]) / 2).toInt() else sorted[mid].toInt()
        } else null
        
        // 6. Calculate Average (Simple average of available metrics)
        val rhrAvg = when {
            rhrNight != null && rhrDay != null -> (rhrNight + rhrDay) / 2
            rhrNight != null -> rhrNight
            rhrDay != null -> rhrDay
            else -> null
        }

        return TrendPoint(date, rhrNight, rhrDay, rhrAvg, hrvValue, moodRating)
    }
}
