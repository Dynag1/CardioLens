package com.cardio.fitbit.ui.screens

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cardio.fitbit.data.repository.BackupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

sealed class BackupUiState {
    object Idle : BackupUiState()
    object Loading : BackupUiState()
    object Success : BackupUiState()
    data class Error(val message: String) : BackupUiState()
    object AuthRequired : BackupUiState()
    object MissingCredentials : BackupUiState()
}

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupRepository: BackupRepository,
    private val userPreferencesRepository: com.cardio.fitbit.data.repository.UserPreferencesRepository,
    private val googleDriveRepository: com.cardio.fitbit.data.repository.GoogleDriveRepository,
    private val googleFitAuthManager: com.cardio.fitbit.auth.GoogleFitAuthManager,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {
// ... (keep existing properties) ...

    fun getSignInIntent(): android.content.Intent {
        return googleFitAuthManager.getSignInIntent()
    }

    fun handleSignInResult(intent: android.content.Intent?) {
        viewModelScope.launch {
            val result = googleFitAuthManager.handleSignInResult(intent)
            if (result.isSuccess) {
                 // Refresh backups immediately
                 loadDriveBackups()
                 _uiState.value = BackupUiState.Success
            } else {
                 _uiState.value = BackupUiState.Error("Connexion échouée: ${result.exceptionOrNull()?.message}")
            }
        }
    }
    
    // Deprecated but kept to avoid instant compilation error if UI not updated recursively yet, 
    // although we will update UI immediately.
    fun triggerReAuth(context: android.content.Context) {
        // No-op, UI handles it via Intent
    }

// ... (keep loadDriveBackups) ...

    fun startDriveApiBackup() {
        _uiState.value = BackupUiState.Loading
        viewModelScope.launch {
            try {
                 // ... (keep temp file creation) ...
                 val dateFormat = java.text.SimpleDateFormat("yyyy_MM_dd_HHmmss", java.util.Locale.getDefault())
                 val timestamp = dateFormat.format(java.util.Date())
                 val filename = "CardioLens-Cloud-$timestamp.json"
                 val tempFile = java.io.File(context.cacheDir, filename)
                 
                 val fileResult = java.io.FileOutputStream(tempFile).use { fileOut ->
                     backupRepository.exportData(fileOut)
                 }
                 
                 if (fileResult.isSuccess) {
                     val uploadResult = googleDriveRepository.uploadToDriveApi(tempFile)
                     if (uploadResult.isSuccess) {
                         _uiState.value = BackupUiState.Success
                         loadDriveBackups() // Refresh list
                         googleDriveRepository.cleanOldBackupsDriveApi(3)
                     } else {
                         val errorMsg = uploadResult.exceptionOrNull()?.message ?: ""
                         if (errorMsg.contains("401") || errorMsg.contains("403")) {
                             _uiState.value = BackupUiState.AuthRequired
                         } else {
                             _uiState.value = BackupUiState.Error("Upload Failed: $errorMsg")
                         }
                     }
                 } else {
                     _uiState.value = BackupUiState.Error("Export Failed")
                 }
                 tempFile.delete()
            } catch (e: Exception) {
                if (e.message?.contains("401") == true || e.message?.contains("403") == true) {
                     _uiState.value = BackupUiState.AuthRequired
                } else {
                    _uiState.value = BackupUiState.Error("Drive Backup Error: ${e.message}")
                }
            }
        }
    }

    private val _uiState = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    val googleDriveBackupEnabled = userPreferencesRepository.googleDriveBackupEnabled.stateIn(
        viewModelScope,
        kotlinx.coroutines.flow.SharingStarted.Lazily,
        false
    )
    
    val backupUri = userPreferencesRepository.backupUri.stateIn(
        viewModelScope,
        kotlinx.coroutines.flow.SharingStarted.Lazily,
        null
    )

    fun onCloudBackupToggled(enabled: Boolean) {
        viewModelScope.launch {
            if (!enabled) {
                userPreferencesRepository.setGoogleDriveBackupEnabled(false)
            }
        }
    }

    fun setBackupFolder(uri: android.net.Uri) {
         viewModelScope.launch {
             try {
                 val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                 
                 context.contentResolver.takePersistableUriPermission(uri, flags)
                 
                 userPreferencesRepository.setBackupUri(uri.toString())
                 userPreferencesRepository.setGoogleDriveBackupEnabled(true)
             } catch (e: Exception) {
                 _uiState.value = BackupUiState.Error("Erreur permission dossier: ${e.message}")
             }
         }
    }
    
    // Explicit enable if URI exists
    fun enableCloudBackup() {
        viewModelScope.launch {
            userPreferencesRepository.setGoogleDriveBackupEnabled(true)
        }
    }

    fun resetState() {
        _uiState.value = BackupUiState.Idle
    }

    fun exportData(outputStream: OutputStream) {
        _uiState.value = BackupUiState.Loading
        viewModelScope.launch {
            // 1. Export to User Selected Stream
            val result = backupRepository.exportData(outputStream)
            
            if (result.isSuccess) {
                 // 2. Check if Drive Backup is enabled
                 val driveEnabled = userPreferencesRepository.googleDriveBackupEnabled.firstOrNull() ?: false
                 
                 if (driveEnabled) {
                     try {
                         // Create a temp file to upload
                         val dateFormat = java.text.SimpleDateFormat("yyyy_MM_dd_HHmmss", java.util.Locale.getDefault())
                         val timestamp = dateFormat.format(java.util.Date())
                         val filename = "CardioLens-Manual-$timestamp.json"
                         val tempFile = java.io.File(context.cacheDir, filename)
                         
                         // Export to temp file
                         val fileResult = java.io.FileOutputStream(tempFile).use { fileOut ->
                             backupRepository.exportData(fileOut)
                         }
                         
                         if (fileResult.isSuccess) {
                             // Upload
                             val uploadResult = googleDriveRepository.uploadBackup(tempFile)
                             if (uploadResult.isSuccess) {
                                 // Rotate
                                 googleDriveRepository.cleanOldBackups(3)
                             }
                         }
                         tempFile.delete()
                         
                     } catch (e: Exception) {
                         // Log error but don't fail the user operation if local export succeeded
                         e.printStackTrace()
                     }
                 }
                 
                _uiState.value = BackupUiState.Success
            } else {
                _uiState.value = BackupUiState.Error(result.exceptionOrNull()?.message ?: "Unknown export error")
            }
        }
    }

    // --- Drive API Logic ---
    private val _driveFiles = MutableStateFlow<List<com.cardio.fitbit.data.api.DriveFile>>(emptyList())
    val driveFiles = _driveFiles.asStateFlow()

    fun loadDriveBackups() {
        viewModelScope.launch {
            if (userPreferencesRepository.googleAccessToken.firstOrNull() != null) {
                val result = googleDriveRepository.listBackupsFromDriveApi()
                if (result.isSuccess) {
                    _driveFiles.value = result.getOrNull() ?: emptyList()
                } else {
                    _driveFiles.value = emptyList()
                }
            }
        }
    }

    fun importData(inputStream: InputStream) {
        _uiState.value = BackupUiState.Loading
        viewModelScope.launch {
            val result = backupRepository.importData(inputStream)
            _uiState.value = if (result.isSuccess) {
                BackupUiState.Success
            } else {
                BackupUiState.Error(result.exceptionOrNull()?.message ?: "Unknown import error")
            }
        }
    }



    fun restoreFromDrive(fileId: String) {
        _uiState.value = BackupUiState.Loading
        viewModelScope.launch {
            try {
                val tempFile = java.io.File(context.cacheDir, "restore_temp.json")
                val downloadResult = googleDriveRepository.downloadFromDriveApi(fileId, tempFile)
                
                if (downloadResult.isSuccess) {
                    // Import
                    java.io.FileInputStream(tempFile).use { input ->
                        val importResult = backupRepository.importData(input)
                         if (importResult.isSuccess) {
                            _uiState.value = BackupUiState.Success
                        } else {
                            _uiState.value = BackupUiState.Error("Import Failed: ${importResult.exceptionOrNull()?.message}")
                        }
                    }
                } else {
                     _uiState.value = BackupUiState.Error("Download Failed")
                }
                tempFile.delete()
            } catch (e: Exception) {
                 _uiState.value = BackupUiState.Error("Restore Error: ${e.message}")
            }
        }
    }
}
