package com.cardio.fitbit.ui.auth

import androidx.lifecycle.ViewModel
import com.cardio.fitbit.auth.FitbitAuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authManager: FitbitAuthManager
) : ViewModel() {

    suspend fun handleAuthorizationCode(code: String): Result<Unit> {
        android.util.Log.d("CardioAuth", "AuthViewModel: calling authManager.handleAuthorizationCallback")
        return authManager.handleAuthorizationCallback(code)
    }
}
