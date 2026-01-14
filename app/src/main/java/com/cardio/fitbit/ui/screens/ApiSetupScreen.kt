package com.cardio.fitbit.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiSetupScreen(
    onCredentialsSaved: () -> Unit,
    viewModel: ApiSetupViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val clientId by viewModel.clientId.collectAsState()
    val clientSecret by viewModel.clientSecret.collectAsState()
    val isSaved by viewModel.isSaved.collectAsState()

    LaunchedEffect(isSaved) {
        if (isSaved) {
            onCredentialsSaved()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Configuration API") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Bienvenue dans Cardio !",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Pour accéder à vos données détaillées (minute par minute), cette application nécessite vos propres clés développeur Fitbit (mode Personnel).",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Instructions :",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text("1. Connectez-vous sur dev.fitbit.com")
                    Text("2. Créez une nouvelle App 'Personal'")
                    Text("3. Entrez n'importe quelle URL pour le site web")
                    Text("4. Entrez 'cardioapp://fitbit-auth' comme Redirect URL")
                    Text("5. Copiez votre Client ID et Client Secret ci-dessous")
                    
                    Text(
                        text = "Ouvrir dev.fitbit.com",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .clickable {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://dev.fitbit.com/apps/new"))
                                context.startActivity(intent)
                            }
                    )
                }
            }

            OutlinedTextField(
                value = clientId,
                onValueChange = viewModel::onClientIdChange,
                label = { Text("OAuth 2.0 Client ID") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = clientSecret,
                onValueChange = viewModel::onClientSecretChange,
                label = { Text("Client Secret") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Button(
                onClick = viewModel::saveCredentials,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = clientId.isNotBlank() && clientSecret.isNotBlank()
            ) {
                Text("Enregistrer et Continuer")
            }
        }
    }
}
