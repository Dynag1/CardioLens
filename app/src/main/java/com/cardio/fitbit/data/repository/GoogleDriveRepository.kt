package com.cardio.fitbit.data.repository

import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton
import java.io.File

@Singleton
class GoogleDriveRepository @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val userPreferencesRepository: UserPreferencesRepository
) {

    suspend fun uploadBackup(file: java.io.File): Result<Unit> {
        return try {
            val uriString = userPreferencesRepository.backupUri.firstOrNull()
            if (uriString.isNullOrBlank()) {
                return Result.failure(Exception("Backup URI not configured"))
            }

            val treeUri = android.net.Uri.parse(uriString)
            val pickedDir = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, treeUri)
            
            if (pickedDir == null || !pickedDir.canWrite()) {
                 return Result.failure(Exception("Cannot write to selected directory"))
            }

            // Create new file
            // Use same name convention or just append
            val mimeType = "application/json"
            val newFile = pickedDir.createFile(mimeType, file.name)
            
            if (newFile == null) {
                 return Result.failure(Exception("Failed to create file in SAF storage"))
            }
            
            context.contentResolver.openOutputStream(newFile.uri)?.use { output ->
                java.io.FileInputStream(file).use { input ->
                    input.copyTo(output)
                }
            } ?: return Result.failure(Exception("Failed to open output stream"))

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun cleanOldBackups(keepCount: Int = 3): Result<Unit> {
        return try {
            val uriString = userPreferencesRepository.backupUri.firstOrNull() ?: return Result.success(Unit)
            val treeUri = android.net.Uri.parse(uriString)
            val pickedDir = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, treeUri) ?: return Result.success(Unit)

            val files = pickedDir.listFiles()
                .filter { it.name?.startsWith("CardioLens-") == true && it.name?.endsWith(".json") == true }
                .sortedByDescending { it.lastModified() }

            if (files.size > keepCount) {
                files.drop(keepCount).forEach { f -> f.delete() }
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
