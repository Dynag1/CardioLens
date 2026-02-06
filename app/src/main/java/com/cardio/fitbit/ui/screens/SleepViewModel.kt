package com.cardio.fitbit.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cardio.fitbit.data.models.MinuteData
import com.cardio.fitbit.data.models.SleepData
import com.cardio.fitbit.data.provider.HealthDataProvider
import com.cardio.fitbit.utils.DateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
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
    
    private val _sleepDebtMinutes = MutableStateFlow<Int?>(null)
    val sleepDebtMinutes: StateFlow<Int?> = _sleepDebtMinutes

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
            
            // Calculate Sleep Debt
            calculateSleepDebt(date)
        }
    }
    
    private suspend fun calculateSleepDebt(anchorDate: Date) {
        try {
            val endDate = DateUtils.getStartOfDay(anchorDate)
            val startDate = DateUtils.getDaysAgo(7, endDate)
            
            val goalMinutes = kotlinx.coroutines.flow.first(userPreferencesRepository.sleepGoalMinutes)
            var totalDebt = 0
            
            // Fetch history
            val historyResult = healthRepository.getSleepHistory(startDate, endDate)
            val history = historyResult.getOrNull() ?: emptyList()
            
            // Map history by Date
            val historyMap = history.groupBy { 
                DateUtils.formatForApi(it.startTime)
            }
            
            val cal = java.util.Calendar.getInstance()
            cal.time = startDate
            
            while (!cal.time.after(endDate)) {
                 val dStr = DateUtils.formatForApi(cal.time)
                 val sleeps = historyMap[dStr]
                 
                 if (sleeps != null) {
                     val totalSleep = sleeps.sumOf { it.duration }
                     val minutes = totalSleep / (1000 * 60)
                     val debt = goalMinutes - minutes
                     totalDebt += debt
                 }
                 cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
            _sleepDebtMinutes.value = totalDebt
        } catch (e: Exception) {
             android.util.Log.e("SleepVM", "Error calculating debt", e)
        }
    }
}
