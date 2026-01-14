package com.cardio.fitbit.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.browser.customtabs.CustomTabsIntent
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.cardio.fitbit.R
import com.cardio.fitbit.data.models.TokenResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import com.google.gson.Gson
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

import kotlinx.coroutines.flow.firstOrNull

@Singleton
class FitbitAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: com.cardio.fitbit.data.repository.UserPreferencesRepository
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs = EncryptedSharedPreferences.create(
        context,
        "fitbit_auth_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    // Removed hardcoded credentials
    private val redirectUri = context.getString(R.string.fitbit_redirect_uri)
    private val authUrl = context.getString(R.string.fitbit_auth_url)
    private val tokenUrl = context.getString(R.string.fitbit_token_url)
    private val scopes = context.getString(R.string.fitbit_scopes)

    private var codeVerifier: String? = null

    init {
        // Check if already authenticated
        val accessToken = getAccessToken()
        if (accessToken != null) {
            _authState.value = AuthState.Authenticated(accessToken)
        }
    }

    /**
     * Start OAuth 2.0 authorization flow with PKCE
     */
    suspend fun startAuthorization(context: Context) {
        val clientId = userPreferencesRepository.clientId.firstOrNull()
        
        if (clientId.isNullOrBlank()) {
             _authState.value = AuthState.Error("ID Client Fitbit manquant. Veuillez configurer l'API.")
            return
        }

        // Generate PKCE code verifier and challenge
        codeVerifier = generateCodeVerifier()
        val codeChallenge = generateCodeChallenge(codeVerifier!!)

        // Save code verifier for later use
        encryptedPrefs.edit().putString(KEY_CODE_VERIFIER, codeVerifier).apply()

        // Build authorization URL
        val authUri = Uri.parse(authUrl).buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", scopes)
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .build()

        // Launch Chrome Custom Tabs
        val customTabsIntent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()

        customTabsIntent.launchUrl(context, authUri)
    }

    /**
     * Handle OAuth callback with authorization code
     */
    suspend fun handleAuthorizationCallback(code: String): Result<Unit> {
        return try {
            val savedCodeVerifier = encryptedPrefs.getString(KEY_CODE_VERIFIER, null)
                ?: return Result.failure(Exception("Code verifier not found"))

            val tokenResponse = exchangeCodeForToken(code, savedCodeVerifier)
            
            // Save tokens
            saveTokens(tokenResponse)
            
            _authState.value = AuthState.Authenticated(tokenResponse.accessToken)
            
            Result.success(Unit)
        } catch (e: Exception) {
            _authState.value = AuthState.Error(e.message ?: "Authentication failed")
            Result.failure(e)
        }
    }

    /**
     * Exchange authorization code for access token
     */
    private suspend fun exchangeCodeForToken(code: String, codeVerifier: String): TokenResponse {
        val clientId = userPreferencesRepository.clientId.firstOrNull() ?: throw Exception("Client ID missing")
        val clientSecret = userPreferencesRepository.clientSecret.firstOrNull() ?: throw Exception("Client Secret missing")

        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val client = OkHttpClient()
            
            val credentials = "$clientId:$clientSecret"
            val encodedCredentials = Base64.encodeToString(
                credentials.toByteArray(),
                Base64.NO_WRAP
            )

            val requestBody = FormBody.Builder()
                .add("client_id", clientId)
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", redirectUri)
                .add("code_verifier", codeVerifier)
                .build()

            val request = Request.Builder()
                .url(tokenUrl)
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .addHeader("Authorization", "Basic $encodedCredentials") // Some Fitbit endpoints require Basic Auth even for code exchange
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
                ?: throw Exception("Réponse vide du serveur Fitbit")

            android.util.Log.d("FitbitAuth", "Token response code: ${response.code}")
            // Don't log full response body in production if it contains tokens, but for debugging it's useful
            
            if (!response.isSuccessful) {
                android.util.Log.e("FitbitAuth", "Auth Failed: Code=${response.code}, Body=$responseBody")
                throw Exception("Échec (${response.code}): $responseBody")
            }

            val tokenResponse = Gson().fromJson(responseBody, TokenResponse::class.java)
            tokenResponse
        }
    }

    /**
     * Refresh access token using refresh token
     */
    suspend fun refreshAccessToken(): Result<String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val refreshToken = getRefreshToken()
                ?: return@withContext Result.failure(Exception("No refresh token available"))
                
            val clientId = userPreferencesRepository.clientId.firstOrNull() ?: throw Exception("Client ID missing")
            val clientSecret = userPreferencesRepository.clientSecret.firstOrNull() ?: throw Exception("Client Secret missing")

            val client = OkHttpClient()
            
            val credentials = "$clientId:$clientSecret"
            val encodedCredentials = Base64.encodeToString(
                credentials.toByteArray(),
                Base64.NO_WRAP
            )

            val requestBody = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .build()

            val request = Request.Builder()
                .url(tokenUrl)
                .addHeader("Authorization", "Basic $encodedCredentials")
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
                ?: throw Exception("Empty response from token endpoint")

            if (!response.isSuccessful) {
                throw Exception("Token refresh failed: $responseBody")
            }

            val tokenResponse = Gson().fromJson(responseBody, TokenResponse::class.java)
            saveTokens(tokenResponse)
            
            _authState.value = AuthState.Authenticated(tokenResponse.accessToken)
            
            Result.success(tokenResponse.accessToken)
        } catch (e: Exception) {
            _authState.value = AuthState.Error(e.message ?: "Token refresh failed")
            Result.failure(e)
        }
    }

    /**
     * Save tokens securely
     */
    private fun saveTokens(tokenResponse: TokenResponse) {
        android.util.Log.d("FitbitAuth", "Saving tokens: accessToken=${tokenResponse.accessToken?.take(10)}..., refreshToken=${tokenResponse.refreshToken?.take(10)}..., expiresIn=${tokenResponse.expiresIn}, userId=${tokenResponse.userId}")
        encryptedPrefs.edit().apply {
            putString(KEY_ACCESS_TOKEN, tokenResponse.accessToken)
            putString(KEY_REFRESH_TOKEN, tokenResponse.refreshToken)
            putLong(KEY_EXPIRES_AT, System.currentTimeMillis() + (tokenResponse.expiresIn * 1000))
            putString(KEY_USER_ID, tokenResponse.userId)
            apply()
        }
        android.util.Log.d("FitbitAuth", "Tokens saved successfully")
    }

    /**
     * Get current access token
     */
    fun getAccessToken(): String? {
        val token = encryptedPrefs.getString(KEY_ACCESS_TOKEN, null)
        val expiresAt = encryptedPrefs.getLong(KEY_EXPIRES_AT, 0)
        
        // Check if token is expired
        if (token != null && System.currentTimeMillis() >= expiresAt) {
            return null // Token expired
        }
        
        return token
    }

    /**
     * Get refresh token
     */
    private fun getRefreshToken(): String? {
        return encryptedPrefs.getString(KEY_REFRESH_TOKEN, null)
    }

    /**
     * Check if user is authenticated
     */
    fun isAuthenticated(): Boolean {
        return getAccessToken() != null
    }

    /**
     * Logout user
     */
    fun logout() {
        encryptedPrefs.edit().clear().apply()
        _authState.value = AuthState.Unauthenticated
    }

    /**
     * Generate PKCE code verifier
     */
    private fun generateCodeVerifier(): String {
        val secureRandom = SecureRandom()
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    /**
     * Generate PKCE code challenge from verifier
     */
    private fun generateCodeChallenge(verifier: String): String {
        val bytes = verifier.toByteArray()
        val messageDigest = MessageDigest.getInstance("SHA-256")
        val digest = messageDigest.digest(bytes)
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_CODE_VERIFIER = "code_verifier"
    }
}

/**
 * Authentication state
 */
sealed class AuthState {
    object Unauthenticated : AuthState()
    data class Authenticated(val accessToken: String) : AuthState()
    data class Error(val message: String) : AuthState()
}
