package com.cardio.fitbit.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun ProviderSelectionScreen(
    onNavigateToFitbitSetup: () -> Unit,
    onNavigateToGoogleFitSetup: () -> Unit,
    onNavigateToHealthConnectPermissions: () -> Unit,
    viewModel: WelcomeViewModel = hiltViewModel()
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Choisissez votre compte",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Sélectionnez la source de données que vous souhaitez utiliser.",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Health Connect Option (Recommended)
        Button(
            onClick = {
                viewModel.onHealthConnectSelected()
                onNavigateToHealthConnectPermissions()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF34A853)  // Green color for Health Connect
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "Health Connect (Recommandé)", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(text = "Données locales, toutes sources", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

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
                Text(text = "Compte Fitbit", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(text = "Connexion API Cloud", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

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
                Text(text = "Compte Google Fit", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(text = "Connexion API Cloud", fontSize = 12.sp)
            }
        }
    }
}
