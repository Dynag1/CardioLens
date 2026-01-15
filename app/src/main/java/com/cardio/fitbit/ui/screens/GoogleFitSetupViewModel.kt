package com.cardio.fitbit.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cardio.fitbit.auth.GoogleFitAuthManager
import com.cardio.fitbit.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GoogleFitSetupViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val googleFitAuthManager: GoogleFitAuthManager
) : ViewModel() {

    fun onConnectClicked(context: Context, clientId: String, clientSecret: String) {
        viewModelScope.launch {
            // 0. Ensure Health Connect priority is disabled
            userPreferencesRepository.setUseHealthConnect(false)

            // 1. Save credentials
            userPreferencesRepository.setGoogleCredentials(clientId, clientSecret)
            
            // 2. Start Authorization
            googleFitAuthManager.startAuthorization(context, clientId)
        }
    }
}
