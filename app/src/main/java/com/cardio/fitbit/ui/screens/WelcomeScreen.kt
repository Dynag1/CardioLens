package com.cardio.fitbit.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cardio.fitbit.R

@Composable
fun WelcomeScreen(
    onNavigateToFitbitSetup: () -> Unit,
    onNavigateToGoogleFitSetup: () -> Unit,
    onNavigateToDashboard: () -> Unit,
    viewModel: WelcomeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    
    // UI Structure
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_launcher_icon),
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Bienvenue sur CardioLens",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Choisissez votre source de données pour commencer.",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Fitbit Option
        Button(
            onClick = {
                viewModel.onFitbitSelected()
                onNavigateToFitbitSetup()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF00B0B9)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "Connecter avec Fitbit", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(text = "Clés API requises (Intraday)", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Google Fit (Cloud) Option
        Button(
            onClick = {
                viewModel.onGoogleFitSelected()
                onNavigateToGoogleFitSetup()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4285F4)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "Connecter avec Google Fit", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(text = "Via Google Cloud API (Web)", fontSize = 12.sp)
            }
        }
    }
}
