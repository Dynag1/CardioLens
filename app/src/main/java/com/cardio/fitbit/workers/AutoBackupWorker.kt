package com.cardio.fitbit.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cardio.fitbit.data.repository.BackupRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.firstOrNull
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@HiltWorker
class AutoBackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val backupRepository: BackupRepository,
    private val googleDriveRepository: com.cardio.fitbit.data.repository.GoogleDriveRepository,
    private val userPreferencesRepository: com.cardio.fitbit.data.repository.UserPreferencesRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val dateFormat = SimpleDateFormat("yyyy_MM_dd", Locale.getDefault())
            val today = dateFormat.format(Date())
            val filename = "CardioLens-$today.json"
            
            // Use external files dir (Documents) - this is app-specific directory accessible to user
            val backupDir = applicationContext.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
            
            if (backupDir != null) {
                if (!backupDir.exists()) {
                    backupDir.mkdirs()
                }

                val backupFile = File(backupDir, filename)
                
                // Perform Export
                FileOutputStream(backupFile).use { output ->
                    val result = backupRepository.exportData(output)
                    if (result.isFailure) {
                        return Result.retry()
                    }
                }

                // Cleanup old backups (Keep last 2)
                val files = backupDir.listFiles { file ->
                    file.name.startsWith("CardioLens-") && file.name.endsWith(".json")
                }

                if (files != null && files.size > 2) {
                    files.sortByDescending { it.lastModified() }
                    
                    // Keep the first 2, delete the rest
                    for (i in 2 until files.size) {
                        files[i].delete()
                    }
                }
                
                // --- Google Drive Backup ---
                // Check if enabled (Using the preference we will expose in UI)
                val driveEnabled = userPreferencesRepository.googleDriveBackupEnabled.firstOrNull() ?: false
                
                if (driveEnabled) {
                     // Try upload using Drive API (Root folder)
                     val uploadResult = googleDriveRepository.uploadToDriveApi(backupFile)
                     
                     if (uploadResult.isSuccess) {
                         // Perform rotation on Drive (Keep 3 as requested)
                         googleDriveRepository.cleanOldBackupsDriveApi(3)
                     } else {
                         // potential retry logic for network failure
                         if (runAttemptCount < 3) {
                             return Result.retry()
                         }
                         // otherwise fail silently or log
                         android.util.Log.e("AutoBackupWorker", "Drive Upload Failed: ${uploadResult.exceptionOrNull()?.message}")
                     }
                }
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }
}
