package com.cardio.fitbit.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.material3.ListItem
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cardio.fitbit.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onNavigateBack: () -> Unit,
    onNavigateToGoogleSetup: () -> Unit = {},
    viewModel: BackupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Create file launcher for export
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.let { outputStream ->
                    viewModel.exportData(outputStream)
                } ?: run {
                    Toast.makeText(context, "Impossible d'ouvrir le fichier", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Erreur: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Open file launcher for import
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.let { inputStream ->
                    viewModel.importData(inputStream)
                } ?: run {
                    Toast.makeText(context, "Impossible d'ouvrir le fichier", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Erreur: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.handleSignInResult(result.data)
    }

    LaunchedEffect(uiState) {
        when (uiState) {
            is BackupUiState.Success -> {
                snackbarHostState.showSnackbar("Opération réussie !")
                viewModel.resetState()
            }
            is BackupUiState.Error -> {
                snackbarHostState.showSnackbar("Erreur : ${(uiState as BackupUiState.Error).message}")
                viewModel.resetState()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sauvegarde et Restauration") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            if (uiState is BackupUiState.Loading) {
                CircularProgressIndicator()
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    when (uiState) {
                        is BackupUiState.Error -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                            ) {
                                Text(
                                    text = (uiState as BackupUiState.Error).message,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                        is BackupUiState.AuthRequired -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Permission Google Drive manquante ou expirée.",
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = { 
                                            val intent = viewModel.getSignInIntent()
                                            signInLauncher.launch(intent)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Reconnecter Drive")
                                    }
                                }
                            }
                        }
                        is BackupUiState.MissingCredentials -> {
                             Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Configuration Google API manquante.",
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = onNavigateToGoogleSetup,
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                                    ) {
                                         Text("Configurer l'API Google")
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "console.cloud.google.com/apis/credentials",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.clickable {
                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://console.cloud.google.com/apis/credentials"))
                                            context.startActivity(intent)
                                        }
                                    )
                                }
                            }
                        }
                        else -> {}
                    }

                    Text(
                        text = "Gérez vos données locales",
                        style = MaterialTheme.typography.titleLarge
                    )
                    
                    Text(
                        text = "Vous pouvez exporter toutes vos données (rythme cardiaque, sommeil, humeur, etc.) dans un fichier JSON pour les sauvegarder ou les transférer vers un autre appareil.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Export Button
                    Button(
                        onClick = {
                            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                            exportLauncher.launch("cardiolens_backup_$timestamp.json")
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sauvegarder les données")
                    }

                    // Import Button
                    OutlinedButton(
                        onClick = {
                            importLauncher.launch(arrayOf("application/json"))
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Restaurer une sauvegarde")
                    }
                    
                    Divider()
                    
                    // Cloud Backup Section (SAF - Legacy/Check for Auto)
                    // ... (Keeping SAF section small or renaming it?)
                    // Let's repurpose this area for the NEW Drive API features mostly.
                    
                    val driveFiles by viewModel.driveFiles.collectAsState()
                    var showRestoreDialog by remember { mutableStateOf(false) }

                    Card(
                         colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Google Drive", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // Backup Now Button
                            Button(
                                onClick = { viewModel.startDriveApiBackup() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Sauvegarder sur Drive maintenant")
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Load/Restore Button
                            OutlinedButton(
                                onClick = { 
                                    viewModel.loadDriveBackups()
                                    showRestoreDialog = true
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Restaurer depuis Drive")
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Nécessite que vous soyez connecté avec votre compte Google dans l'application.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    if (showRestoreDialog) {
                        AlertDialog(
                            onDismissRequest = { showRestoreDialog = false },
                            title = { Text("Choisir une sauvegarde") },
                            text = {
                                if (driveFiles.isEmpty()) {
                                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                        Text("Aucune sauvegarde trouvée ou chargement...")
                                    }
                                } else {
                                    androidx.compose.foundation.lazy.LazyColumn {
                                        items(driveFiles.size) { index ->
                                            val file = driveFiles[index]
                                            ListItem(
                                                headlineContent = { Text(file.name) },
                                                supportingContent = { Text(file.createdTime ?: "") },
                                                modifier = Modifier.clickable {
                                                    viewModel.restoreFromDrive(file.id)
                                                    showRestoreDialog = false
                                                }
                                            )
                                            Divider()
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showRestoreDialog = false }) {
                                    Text("Fermer")
                                }
                            }
                        )
                    }

                    Divider()

                    // SAF Backup Section (Legacy / Auto Local)
                    Card(
                        colors = CardDefaults.cardColors(
                             containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                         ),
                         modifier = Modifier.fillMaxWidth()
                     ) {
                         val driveEnabled by viewModel.googleDriveBackupEnabled.collectAsState(initial = false)
                         val backupUri by viewModel.backupUri.collectAsState(initial = null)
                         
                         val safLauncher = rememberLauncherForActivityResult(
                             contract = ActivityResultContracts.OpenDocumentTree()
                         ) { uri ->
                             uri?.let { viewModel.setBackupFolder(it) }
                         }
                         
                         Column(modifier = Modifier.padding(16.dp)) {
                             Row(
                                 modifier = Modifier.fillMaxWidth(),
                                 horizontalArrangement = Arrangement.SpaceBetween,
                                 verticalAlignment = Alignment.CenterVertically
                             ) {
                                 Column(modifier = Modifier.weight(1f)) {
                                     Text("Sauvegarde Locale Auto", style = MaterialTheme.typography.titleMedium)
                                     Text("Dossier local spécifique", style = MaterialTheme.typography.bodySmall)
                                 }
                                 Switch(
                                     checked = driveEnabled,
                                     onCheckedChange = { 
                                         if (it) {
                                             if (backupUri != null) {
                                                 viewModel.enableCloudBackup()
                                             } else {
                                                 safLauncher.launch(null)
                                             }
                                         } else {
                                             viewModel.onCloudBackupToggled(false)
                                         }
                                     }
                                 )
                             }
                        }
                    }
                    
                    if (uiState is BackupUiState.Error) {
                         val errorMsg = (uiState as BackupUiState.Error).message
                         Text(
                             text = errorMsg,
                             color = MaterialTheme.colorScheme.error,
                             style = MaterialTheme.typography.bodySmall,
                             textAlign = androidx.compose.ui.text.style.TextAlign.Center
                         )
                    }
                }
            }
        }
    }
}
