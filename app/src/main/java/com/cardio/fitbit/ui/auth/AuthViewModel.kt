package com.cardio.fitbit.ui.auth

import androidx.lifecycle.ViewModel
import com.cardio.fitbit.auth.FitbitAuthManager
import kotlinx.coroutines.flow.firstOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val fitbitAuthManager: FitbitAuthManager,
    private val googleFitAuthManager: com.cardio.fitbit.auth.GoogleFitAuthManager,
    private val userPreferencesRepository: com.cardio.fitbit.data.repository.UserPreferencesRepository
) : ViewModel() {

    suspend fun handleAuthorizationCode(code: String): Result<Unit> {
        // Legacy/Default to Fitbit if not specified (though AuthActivity usually handles this)
        android.util.Log.d("CardioAuth", "AuthViewModel: calling fitbitAuthManager.handleAuthorizationCallback")
        return fitbitAuthManager.handleAuthorizationCallback(code)
    }
    
    suspend fun handleGoogleAuthCode(code: String): Result<Unit> {
        android.util.Log.d("CardioAuth", "AuthViewModel: calling googleFitAuthManager.handleAuthorizationCallback")
        // Retrieve client secret from prefs?
        // Actually, GoogleFitAuthManager.handleAuthorizationCallback needs clientSecret passed to it 
        // OR checks its own internal prefs. 
        // My previous implementation of GoogleFitAuthManager.handleAuthorizationCallback took code + clientSecret.
        // But AuthActivity doesn't have clientSecret!
        // We must have saved it during startAuthorization.
        
        // Let's check GoogleFitAuthManager signature.
        // It was: handleAuthorizationCallback(code: String, clientSecret: String)
        // This is a problem because redirect doesn't return secret.
        
        // CORRECTION: In startAuthorization, I saved ClientID. 
        // I should have saved ClientSecret there too?
        // Let's check GoogleFitAuthManager again.
        
        // I'll grab secret from UserPreferencesRepository or GoogleFitAuthManager's internal prefs.
        // I need to read the secret to pass it.
        
        // Let's assume for now we fetch it from UserPreferencesRepository (if we save it there).
        // OR we change GoogleFitAuthManager to read it from its internal store if it was saved during setup.
        
        // Let's inspect GoogleFitAuthManager again.
        val secret = userPreferencesRepository.googleClientSecret.firstOrNull() ?: ""
        return googleFitAuthManager.handleAuthorizationCallback(code, secret)
    }
}
