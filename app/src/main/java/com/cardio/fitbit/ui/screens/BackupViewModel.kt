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
}

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupRepository: BackupRepository,
    private val userPreferencesRepository: com.cardio.fitbit.data.repository.UserPreferencesRepository,
    private val googleDriveRepository: com.cardio.fitbit.data.repository.GoogleDriveRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {

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
}
