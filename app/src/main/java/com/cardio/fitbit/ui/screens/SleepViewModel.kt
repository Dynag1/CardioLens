package com.cardio.fitbit.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cardio.fitbit.data.models.MinuteData
import com.cardio.fitbit.data.models.SleepData
import com.cardio.fitbit.data.provider.HealthDataProvider
import com.cardio.fitbit.utils.DateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

sealed class SleepUiState {
    object Loading : SleepUiState()
    data class Success(val data: SleepData?, val heartRateData: List<MinuteData>? = null) : SleepUiState() // Null if no sleep found
    data class Error(val message: String) : SleepUiState()
}

@HiltViewModel
class SleepViewModel @Inject constructor(
    private val healthRepository: com.cardio.fitbit.data.repository.HealthRepository,
    private val userPreferencesRepository: com.cardio.fitbit.data.repository.UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<SleepUiState>(SleepUiState.Loading)
    val uiState: StateFlow<SleepUiState> = _uiState

    private val _selectedDate = MutableStateFlow<Date>(Date())
    val selectedDate: StateFlow<Date> = _selectedDate
    
    private val _sleepStats = MutableStateFlow<SleepStats?>(null)
    val sleepStats: StateFlow<SleepStats?> = _sleepStats

    fun setDate(date: Date) {
        // Only update if date is different to avoid loops if needed, 
        // but here we trust the caller (only called once by LaunchedEffect in screen init)
        if (_selectedDate.value != date) {
            _selectedDate.value = date
            loadSleepData(date)
        }
    }

    fun goToPreviousDay() {
        val newDate = DateUtils.getDaysAgo(1, _selectedDate.value)
        setDate(newDate)
    }

    fun goToNextDay() {
        val newDate = DateUtils.getDaysAgo(-1, _selectedDate.value)
        setDate(newDate)
    }

    fun refresh() {
        loadSleepData(_selectedDate.value, forceRefresh = true)
    }

    private fun loadSleepData(date: Date, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = SleepUiState.Loading
            
            // Re-fetch sleep data from repository (handles provider selection)
            // We could rely on MainViewModel but independent fetch is reliable for a detail screen
            val result = healthRepository.getSleepData(date, forceRefresh)
            
            result.onSuccess { list ->
                // Usually we care about the "main" sleep (longest)
                val mainSleep = list.maxByOrNull { it.duration }
                
                var hrData: List<MinuteData>? = null
                 if (mainSleep != null) {
                     val hrResult = healthRepository.getHeartRateSeries(mainSleep.startTime, mainSleep.endTime)
                     hrData = hrResult.getOrNull()
                 }

                _uiState.value = SleepUiState.Success(mainSleep, hrData)
            }.onFailure { e ->
                _uiState.value = SleepUiState.Error(e.message ?: "Failed to load sleep data")
            }
            
            // Calculate Sleep Stats
            calculateSleepStats(date)
        }
    }
    
    private suspend fun calculateSleepStats(anchorDate: Date) {
        try {
            // End date is anchor date (inclusive for history usually covers the night ending on that date)
            val endDate = DateUtils.getStartOfDay(anchorDate)
            // 7 days window
            val startDate = DateUtils.getDaysAgo(6, endDate)
            
            val goalMinutes = userPreferencesRepository.sleepGoalMinutes.first()
            
            // Fetch history
            val historyResult = healthRepository.getSleepHistory(startDate, endDate)
            val history = historyResult.getOrNull() ?: emptyList()
            
            // Map history by Date to ensure we have unique days if multiple records per day
            // We want one duration per day (main sleep).
            val historyPerDay = history.groupBy { 
                DateUtils.formatForApi(it.startTime) 
            }.mapValues { (_, list) ->
                val stages = list.maxByOrNull { it.duration }?.stages
                if (stages != null) {
                    (stages.deep + stages.light + stages.rem)
                } else {
                    list.maxByOrNull { it.duration }?.minutesAsleep ?: 0
                }
            }
            
            val validDurations = historyPerDay.values.filter { it > 0 }
            val avgMinutes = if (validDurations.isNotEmpty()) {
                (validDurations.sum() / validDurations.size) // attributes are already in minutes
            } else {
                0
            }

            _sleepStats.value = SleepStats(
                goalMinutes = goalMinutes,
                average7DaysMinutes = avgMinutes
            )
        } catch (e: Exception) {
             android.util.Log.e("SleepVM", "Error calculating stats", e)
        }
    }
}

data class SleepStats(
    val goalMinutes: Int,
    val average7DaysMinutes: Int
)
