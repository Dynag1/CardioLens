package com.cardio.fitbit.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cardio.fitbit.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    fun onFitbitSelected() {
        viewModelScope.launch {
            userPreferencesRepository.setUseHealthConnect(false)
            // Navigation handled by composable event
        }
    }

    fun onGoogleFitSelected() {
        viewModelScope.launch {
            // Reusing the boolean flag effectively now means "Use Google Fit (Cloud)"
            // Ideally we should rename this preference later, but for now true = Google Fit (Cloud)
            userPreferencesRepository.setUseHealthConnect(true)
        }
    }
    
    fun onHealthConnectSelected() {
        viewModelScope.launch {
            userPreferencesRepository.setUseHealthConnect(true)
        }
    }
}
