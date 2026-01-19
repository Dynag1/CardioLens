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
                // Check if enabled
                val driveEnabled = userPreferencesRepository.googleDriveBackupEnabled.firstOrNull() ?: false
                
                if (driveEnabled) {
                     // Try upload
                     val uploadResult = googleDriveRepository.uploadBackup(backupFile)
                     if (uploadResult.isSuccess) {
                         // Perform rotation on Drive (Keep 3)
                         googleDriveRepository.cleanOldBackups(3)
                     } else {
                         // We don't fail the whole worker if cloud backup fails, but maybe log it?
                         // For now, silent fail is acceptable or use Result.retry() if critical.
                         // Let's not retry indefinitely for network issues to save battery.
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
