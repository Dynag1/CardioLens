package com.cardio.fitbit.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cardio.fitbit.auth.AuthState
import com.cardio.fitbit.auth.FitbitAuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authManager: FitbitAuthManager
) : ViewModel() {

    val authState: StateFlow<AuthState> = authManager.authState

    fun startLogin(context: android.content.Context) {
        viewModelScope.launch {
            authManager.startAuthorization(context)
        }
    }
}
