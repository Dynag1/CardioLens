package com.cardio.fitbit.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import kotlinx.coroutines.launch

@Composable
fun HealthConnectPermissionsScreen(
    onPermissionsGranted: () -> Unit,
    onPermissionsDenied: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Define permissions we need (Source of Truth from Provider)
    val permissions = com.cardio.fitbit.data.provider.HealthConnectProvider.PERMISSIONS

    // Check availability
    val availability = HealthConnectClient.getSdkStatus(context)
    
    var uiState by remember { mutableStateOf<String>("") }
    var showRequestButton by remember { mutableStateOf(true) }

    // Launcher for permission request
    val requestPermissionActivityContract = PermissionController.createRequestPermissionResultContract()
    val requestPermissions = rememberLauncherForActivityResult(requestPermissionActivityContract) { granted ->
        if (granted.containsAll(permissions)) {
            onPermissionsGranted()
        } else {
            // Check if we have at least heart rate and sleep (minimum for core functionality)
            val hasMin = granted.contains(HealthPermission.getReadPermission(HeartRateRecord::class)) &&
                         granted.contains(HealthPermission.getReadPermission(SleepSessionRecord::class))
            if (hasMin) {
                onPermissionsGranted()
            } else {
                uiState = "Certaines permissions sont manquantes pour un fonctionnement optimal."
                showRequestButton = true
            }
        }
    }

    LaunchedEffect(Unit) {
        if (availability == HealthConnectClient.SDK_AVAILABLE) {
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            if (granted.containsAll(permissions)) {
                onPermissionsGranted()
            }
        } else {
            uiState = "Health Connect n'est pas disponible sur cet appareil."
            showRequestButton = false
            if (availability == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) {
                uiState = "Une mise à jour de Health Connect est requise."
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Configuration de Health Connect",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Pour fonctionner, CardioLens a besoin d'accéder à vos données via Health Connect. Voici pourquoi :",
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Justification points
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                InfoPoint(
                    "❤️ Fréquence Cardiaque & VRC", 
                    "Nous lisons votre pouls pour afficher des graphiques interactifs et analysons votre VRC pour évaluer votre récupération physique."
                )
                InfoPoint(
                    "💤 Sommeil", 
                    "Nous suivons vos cycles de sommeil pour vous aider à comprendre la qualité de votre repos."
                )
                InfoPoint(
                    "🏃 Activité & Calories", 
                    "Nous utilisons vos pas et les calories actives pour calculer votre dépense énergétique quotidienne."
                )
                InfoPoint(
                    "🏋️ Sessions d'Exercice", 
                    "Nous accédons à vos entraînements pour analyser votre réponse cardiaque durant l'effort."
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            if (uiState.isNotEmpty()) {
                Text(
                    text = uiState,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            if (showRequestButton && availability == HealthConnectClient.SDK_AVAILABLE) {
                Button(
                    onClick = { requestPermissions.launch(permissions) },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text("Autoriser l'Accès", fontSize = 18.sp)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            TextButton(onClick = onPermissionsDenied) {
                Text("Plus tard / Continuer sans Health Connect", color = MaterialTheme.colorScheme.secondary)
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Vos données restent locales et ne sont jamais partagées avec des tiers.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "⚠️ CardioLens n'est PAS un dispositif médical. Cette application est destinée à un usage informatif uniquement.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(8.dp),
                    lineHeight = 14.sp
                )
            }
        }
    }
}

@Composable
private fun InfoPoint(title: String, description: String) {
    Column {
        Text(text = title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(text = description, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
