package com.cardio.fitbit.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cardio.fitbit.data.repository.BackupRepository
import com.cardio.fitbit.data.repository.GoogleDriveRepository
import com.cardio.fitbit.data.repository.UserPreferencesRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.firstOrNull
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@HiltWorker
class CloudBackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val backupRepository: BackupRepository,
    private val googleDriveRepository: GoogleDriveRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            // Check if enabled (although arguably we shouldn't schedule it if not enabled)
            val isEnabled = userPreferencesRepository.googleDriveBackupEnabled.firstOrNull() ?: false
            if (!isEnabled) {
                 return Result.success()
            }
            
            // Or if we decide to force it as per user request "met une sauvegarde automatique"
            // Let's assume user turns it on via UI, or we enable it by default?
            // User said "met une sauvegarde automatique". I should probably auto-schedule it.
            
            // 1. Export
            val dateFormat = SimpleDateFormat("yyyy_MM_dd_HHmmss", Locale.getDefault())
            val timestamp = dateFormat.format(Date())
            val filename = "CardioLens-Auto-$timestamp.json"
            val tempFile = File(applicationContext.cacheDir, filename)
            
            val fileStream = java.io.FileOutputStream(tempFile)
            val exportResult = backupRepository.exportData(fileStream)
            fileStream.close()
            
            if (exportResult.isSuccess) {
                // 2. Upload
                val uploadResult = googleDriveRepository.uploadToDriveApi(tempFile)
                if (uploadResult.isSuccess) {
                     // 3. Retention
                     googleDriveRepository.cleanOldBackupsDriveApi(3)
                     tempFile.delete()
                     Result.success()
                } else {
                     tempFile.delete()
                     Result.retry()
                }
            } else {
                tempFile.delete()
                Result.failure()
            }
        } catch (e: Exception) {
            e.printStackTrace()
             Result.failure()
        }
    }
}
