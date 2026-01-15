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
    
    var uiState by remember { mutableStateOf<String>("Vérification...") }
    var showRetryButton by remember { mutableStateOf(false) }

    // Launcher for permission request
    val requestPermissionActivityContract = PermissionController.createRequestPermissionResultContract()
    val requestPermissions = rememberLauncherForActivityResult(requestPermissionActivityContract) { granted ->
        if (granted.containsAll(permissions)) {
            onPermissionsGranted()
        } else {
            // Check partial grant
            if (granted.contains(HealthPermission.getReadPermission(HeartRateRecord::class))) {
                onPermissionsGranted()
            } else {
                uiState = "Permissions refusées ou incomplètes."
                showRetryButton = true
                // Do NOT auto-navigate to failed state, let user decide
            }
        }
    }

    LaunchedEffect(Unit) {
        if (availability == HealthConnectClient.SDK_AVAILABLE) {
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            if (granted.containsAll(permissions)) {
                onPermissionsGranted()
            } else {
                try {
                    requestPermissions.launch(permissions)
                } catch (e: Exception) {
                    uiState = "Erreur lors du lancement de la demande : ${e.message}"
                    showRetryButton = true
                }
            }
        } else {
            uiState = "Health Connect n'est pas disponible sur cet appareil (Status: $availability)."
            showRetryButton = false // Nothing to retry if SDK unavailable
            // Could actally be NOT_INSTALLED, necessitating functionality to install it.
            if (availability == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) {
                uiState = "Une mise à jour de Health Connect est requise."
            }
        }
    }

    // UI while waiting or if user denied and came back
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            if (showRetryButton || uiState.contains("Erreur") || uiState.contains("pas disponible")) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            Text(
                text = uiState,
                textAlign = TextAlign.Center,
                color = if (uiState.contains("Erreur")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            if (showRetryButton) {
                Button(onClick = { requestPermissions.launch(permissions) }) {
                    Text("Réessayer la demande")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Always offer a way to proceed anyway (maybe they have partial data or want to skip)
            TextButton(onClick = onPermissionsDenied) {
                Text("Continuer sans Health Connect (Dashboard vide)")
            }
        }
    }
}
