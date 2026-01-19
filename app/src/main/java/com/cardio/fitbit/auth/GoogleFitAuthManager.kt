package com.cardio.fitbit.auth

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.cardio.fitbit.data.models.TokenResponse
import com.cardio.fitbit.data.repository.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import com.google.gson.Gson
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleFitAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository
) {

    private val redirectUri = "cardioapp://google-auth"
    private val authUrl = "https://accounts.google.com/o/oauth2/v2/auth"
    private val tokenUrl = "https://oauth2.googleapis.com/token"
    
    private val scopes = "https://www.googleapis.com/auth/fitness.activity.read https://www.googleapis.com/auth/fitness.heart_rate.read https://www.googleapis.com/auth/fitness.sleep.read https://www.googleapis.com/auth/fitness.body.read https://www.googleapis.com/auth/drive.file"

    // Reactive Auth State from DataStore
    val authState: StateFlow<AuthState> = userPreferencesRepository.googleAccessToken
        .map { token ->
            if (token != null) AuthState.Authenticated(token) else AuthState.Unauthenticated
        }
        .stateIn(
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO),
            started = SharingStarted.Eagerly,
            initialValue = AuthState.Unauthenticated
        )

    suspend fun startAuthorization(context: Context, clientId: String) {
        if (clientId.isBlank()) {
            return
        }
        
        val authUri = Uri.parse(authUrl).buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", scopes)
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("access_type", "offline") 
            .appendQueryParameter("prompt", "consent") 
            .build()

        val customTabsIntent = CustomTabsIntent.Builder().build()
        customTabsIntent.launchUrl(context, authUri)
    }

    suspend fun handleAuthorizationCallback(code: String, clientSecret: String): Result<Unit> {
        return try {
            val clientId = userPreferencesRepository.googleClientId.firstOrNull()
                ?: return Result.failure(Exception("Client ID not found in preferences"))
            
            val tokenResponse = exchangeCodeForToken(code, clientId, clientSecret)
            saveTokens(tokenResponse)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun exchangeCodeForToken(code: String, clientId: String, clientSecret: String): TokenResponse {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val client = OkHttpClient()
            
            val requestBody = FormBody.Builder()
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", redirectUri)
                .build()

            val request = Request.Builder()
                .url(tokenUrl)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: throw Exception("Empty response")

            if (!response.isSuccessful) {
                throw Exception("Google Auth Failed (${response.code}): $responseBody")
            }

            Gson().fromJson(responseBody, TokenResponse::class.java)
        }
    }
    
    suspend fun refreshAccessToken(): Result<String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val refreshToken = userPreferencesRepository.googleRefreshToken.firstOrNull()
                ?: return@withContext Result.failure(Exception("No refresh token"))
            
            val clientId = userPreferencesRepository.googleClientId.firstOrNull() ?: throw Exception("No Client ID")
            val clientSecret = userPreferencesRepository.googleClientSecret.firstOrNull() ?: throw Exception("No Client Secret")

            val client = OkHttpClient()
            val requestBody = FormBody.Builder()
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .build()

            val request = Request.Builder()
                .url(tokenUrl)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: throw Exception("Empty response on refresh")

            if (!response.isSuccessful) {
                throw Exception("Refresh failed: $responseBody")
            }

            val tokenResponse = Gson().fromJson(responseBody, TokenResponse::class.java)
            saveTokens(tokenResponse)
            
            Result.success(tokenResponse.accessToken)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun saveTokens(tokenResponse: TokenResponse) {
        userPreferencesRepository.saveGoogleTokens(
            accessToken = tokenResponse.accessToken,
            refreshToken = tokenResponse.refreshToken
        )
    }

    fun getAccessToken(): String? {
        // Synchronous fetch for Interceptors
        return kotlinx.coroutines.runBlocking {
            userPreferencesRepository.googleAccessToken.firstOrNull()
        }
    }
    
    fun isAuthenticated(): Boolean {
         val state = authState.value
         return state is AuthState.Authenticated
    }
    
    // Kept for compatibility if used elsewhere, but effectively unused logic inside
    suspend fun checkAuthStatus() {
        // No-op as authState is reactive now
    }
}
