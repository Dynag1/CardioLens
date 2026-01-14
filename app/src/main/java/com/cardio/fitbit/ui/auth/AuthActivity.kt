package com.cardio.fitbit.ui.auth

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.cardio.fitbit.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.material3.CircularProgressIndicator
import com.cardio.fitbit.ui.theme.CardioTheme
import kotlinx.coroutines.launch

/**
 * Activity to handle OAuth callback from Fitbit
 */
@AndroidEntryPoint
class AuthActivity : ComponentActivity() {

    private val viewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Show a loading UI while processing
        setContent {
            CardioTheme {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        // Handle the OAuth callback
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data
        Log.d("CardioAuth", "handleIntent called with URI: $uri")
        
        if (uri != null && uri.scheme == "cardioapp") {
            val host = uri.host
            if (host == "fitbit-auth" || host == "google-auth") {
                val code = uri.getQueryParameter("code")
                val error = uri.getQueryParameter("error")
                Log.d("CardioAuth", "Received code: ${code?.take(10)}..., error: $error")

                when {
                    code != null -> {
                        // Authorization successful, exchange code for token
                        Log.d("CardioAuth", "Starting token exchange...")
                        lifecycleScope.launch {
                            try {
                                val result = if (host == "google-auth") {
                                    viewModel.handleGoogleAuthCode(code)
                                } else {
                                    viewModel.handleAuthorizationCode(code)
                                }
                                
                                Log.d("CardioAuth", "Token exchange result: $result")
                                navigateToMain()
                            } catch (e: Exception) {
                                Log.e("CardioAuth", "Token exchange failed", e)
                                runOnUiThread {
                                    android.widget.Toast.makeText(
                                        this@AuthActivity, 
                                        "Échec connexion: ${e.message}", 
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                                navigateToMain()
                            }
                        }
                    }
                    error != null -> {
                        // Authorization failed
                        Log.e("CardioAuth", "OAuth error: $error")
                         runOnUiThread {
                            android.widget.Toast.makeText(
                                this@AuthActivity, 
                                "Erreur Google: $error", 
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                        navigateToMain()
                    }
                    else -> {
                        Log.w("CardioAuth", "No code or error in callback")
                        navigateToMain()
                    }
                }
            } else {
                Log.w("CardioAuth", "Invalid/Unknown host: $host")
                navigateToMain()
            }
        } else {
            Log.w("CardioAuth", "Invalid URI scheme or null intent")
            navigateToMain()
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        finish()
    }
}
