package com.cardio.fitbit.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cardio.fitbit.auth.AuthState
import com.cardio.fitbit.auth.FitbitAuthManager
import com.cardio.fitbit.data.repository.UserPreferencesRepository
import com.cardio.fitbit.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authManager: FitbitAuthManager,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val startDestination: StateFlow<String> = combine(
        authManager.authState,
        userPreferencesRepository.areKeysSet,
        userPreferencesRepository.useHealthConnect
    ) { authState, areKeysSet, useHealthConnect ->
        when {
            useHealthConnect -> Screen.Dashboard.route
            !areKeysSet -> Screen.Welcome.route
            authState is AuthState.Authenticated -> Screen.Dashboard.route
            else -> Screen.Login.route
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = Screen.Login.route // Default fallback, but will quickly update
    )
}
