package com.cardio.fitbit.auth

import android.content.Context
import android.content.Intent
import com.cardio.fitbit.data.repository.UserPreferencesRepository
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import com.google.android.gms.auth.GoogleAuthUtil

@Singleton
class GoogleFitAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository
) {

    private val fitnessOptions: GoogleSignInOptions
        get() {
            // Include Drive Scope and Fitness Scopes
            // Note: Scopes must match what we requested before to ensure compatibility
             return GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(
                    Scope("https://www.googleapis.com/auth/fitness.activity.read"),
                    Scope("https://www.googleapis.com/auth/fitness.heart_rate.read"),
                    Scope("https://www.googleapis.com/auth/fitness.sleep.read"),
                    Scope("https://www.googleapis.com/auth/fitness.body.read"),
                    Scope("https://www.googleapis.com/auth/drive.file")
                )
                // We don't need requestIdToken for REST API if we use GoogleAuthUtil, 
                // but we might want it for backend. For now, we focus on AccessToken.
                .build()
        }

    // Reactive Auth State
    val authState: StateFlow<AuthState> = userPreferencesRepository.googleAccessToken
        .map { token ->
            if (token != null) AuthState.Authenticated(token) else AuthState.Unauthenticated
        }
        .stateIn(
            scope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO),
            started = SharingStarted.Eagerly,
            initialValue = AuthState.Unauthenticated
        )

    fun getSignInIntent(): Intent {
        val client = GoogleSignIn.getClient(context, fitnessOptions)
        return client.signInIntent
    }

    suspend fun handleSignInResult(intent: Intent?): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(intent)
                val account = task.getResult(ApiException::class.java)
                
                // Get Access Token using GoogleAuthUtil
                if (account != null && account.account != null) {
                    val scopes = "oauth2:https://www.googleapis.com/auth/fitness.activity.read https://www.googleapis.com/auth/fitness.heart_rate.read https://www.googleapis.com/auth/fitness.sleep.read https://www.googleapis.com/auth/fitness.body.read https://www.googleapis.com/auth/drive.file"
                    val token = GoogleAuthUtil.getToken(context, account.account!!, scopes)
                    
                    userPreferencesRepository.saveGoogleTokens(
                        accessToken = token,
                        refreshToken = "" // GMS manages refresh internally usually, or we don't get it easily without server auth code. 
                                          // For now, we rely on silentSignIn or getToken to refresh.
                    )
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Account is null"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    // Attempt silent sign-in to refresh token
    suspend fun refreshAccessToken(): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 0. Clear old token from GMS cache to force fresh fetch
            val oldToken = userPreferencesRepository.googleAccessToken.firstOrNull()
            if (!oldToken.isNullOrBlank()) {
                try {
                    GoogleAuthUtil.clearToken(context, oldToken)
                    android.util.Log.d("GoogleFitAuth", "Cleared old token")
                } catch (e: Exception) {
                    android.util.Log.w("GoogleFitAuth", "Failed to clear old token: ${e.message}")
                }
            }
            
            // First try silentSignIn to refresh the account state if possible
            val client = GoogleSignIn.getClient(context, fitnessOptions)
            val task = client.silentSignIn()
            
            var account: GoogleSignInAccount? = null
            try {
                 // Wait for the Task synchronously
                 val googleSignInAccount = com.google.android.gms.tasks.Tasks.await(task)
                 account = googleSignInAccount
            } catch (e: Exception) {
                 // Silent sign in failed, fallback to last signed in account
                 android.util.Log.e("GoogleFitAuth", "Silent sign in failed: ${e.message}")
                 account = GoogleSignIn.getLastSignedInAccount(context)
            }

            if (account != null && account.account != null) {
                 // Verify permissions first
                 if (!GoogleSignIn.hasPermissions(account, *fitnessOptions.scopeArray)) {
                     android.util.Log.e("GoogleFitAuth", "Missing permissions")
                     return@withContext Result.failure(Exception("Missing Google Drive permissions (Scope check failed)"))
                 }

                 val scopes = "oauth2:https://www.googleapis.com/auth/fitness.activity.read https://www.googleapis.com/auth/fitness.heart_rate.read https://www.googleapis.com/auth/fitness.sleep.read https://www.googleapis.com/auth/fitness.body.read https://www.googleapis.com/auth/drive.file"
                 
                 // getToken handles refresh automatically if expired or fetches new token
                 // Note: This call blocks network ops, so we are in IO context.
                 val token = GoogleAuthUtil.getToken(context, account.account!!, scopes)
                 
                 userPreferencesRepository.saveGoogleTokens(accessToken = token, refreshToken = "")
                 Result.success(token)
            } else {
                android.util.Log.e("GoogleFitAuth", "No account found for refresh")
                Result.failure(Exception("Not signed in"))
            }
        } catch (e: Exception) {
            android.util.Log.e("GoogleFitAuth", "Refresh failed: ${e.message}")
            Result.failure(e)
        }
    }
    
    // Deprecated methods kept/stubbed for compatibility if needed, but we should remove usages
    suspend fun startAuthorization(context: Context, clientId: String) {
        // No-op or throw, as UI should use getSignInIntent
        android.util.Log.e("GoogleFitAuth", "startAuthorization called but deprecated. Use getSignInIntent.")
    }
    
    suspend fun handleAuthorizationCallback(code: String, clientSecret: String): Result<Unit> {
         return Result.failure(Exception("Deprecated manual auth"))
    }

    fun getAccessToken(): String? {
         // Best effort sync fetch? Or preference
         // Better to rely on preference which we update.
         return kotlinx.coroutines.runBlocking {
             userPreferencesRepository.googleAccessToken.firstOrNull()
         }
    }
    
    fun isAuthenticated(): Boolean {
         return authState.value is AuthState.Authenticated
    }
}
