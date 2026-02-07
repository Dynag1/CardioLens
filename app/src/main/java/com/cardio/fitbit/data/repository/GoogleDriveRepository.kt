package com.cardio.fitbit.data.repository

import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton
import java.io.File
import okhttp3.MediaType.Companion.toMediaTypeOrNull

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

    // --- API BASED METHODS (Direct Google Drive) ---
    @Inject
    lateinit var apiClient: com.cardio.fitbit.data.api.ApiClient

    suspend fun uploadToDriveApi(file: java.io.File): Result<Unit> {
        return try {
            val jsonType = "application/json".toMediaTypeOrNull()
            
            // 1. Create File Metadata (Empty content first)
            val metadataJson = "{\"name\": \"${file.name}\", \"mimeType\": \"application/json\"}"
            val metadataBody = okhttp3.RequestBody.create(jsonType, metadataJson)
            
            val createResponse = apiClient.googleDriveApi.createFileMetadata(metadataBody)
            
            if (!createResponse.isSuccessful || createResponse.body() == null) {
                val errorBody = createResponse.errorBody()?.string() ?: "No details"
                return Result.failure(Exception("Metadata create failed: ${createResponse.code()} \n$errorBody"))
            }
            
            val fileId = createResponse.body()!!.id
            
            // 2. Upload Content via PATCH to upload endpoint
            val uploadUrl = "https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media"
            val fileBody = okhttp3.RequestBody.create(jsonType, file)
            
            val uploadResponse = apiClient.googleDriveApi.uploadFileMedia(uploadUrl, fileBody)
            
            if (uploadResponse.isSuccessful) {
                Result.success(Unit)
            } else {
                val errorBody = uploadResponse.errorBody()?.string() ?: "No details"
                Result.failure(Exception("Content upload failed: ${uploadResponse.code()} \n$errorBody"))
            }
        } catch (e: Exception) {
             Result.failure(e)
        }
    }

    suspend fun listBackupsFromDriveApi(): Result<List<com.cardio.fitbit.data.api.DriveFile>> {
        return try {
            // Query: name contains 'CardioLens' and not trashed
            // Removed spaces="appDataFolder" to search in standard Drive
            val query = "name contains 'CardioLens' and name contains '.json' and trashed = false"
            val response = apiClient.googleDriveApi.listFiles(
                query = query
            )
            
            if (response.isSuccessful) {
                Result.success(response.body()?.files ?: emptyList())
            } else {
                 val errorBody = response.errorBody()?.string() ?: "No details"
                 Result.failure(Exception("List failed: ${response.code()} \n$errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun downloadFromDriveApi(fileId: String, targetFile: java.io.File): Result<Unit> {
        return try {
            val response = apiClient.googleDriveApi.downloadFile(fileId)
            
            if (response.isSuccessful && response.body() != null) {
                val inputStream = response.body()!!.byteStream()
                targetFile.outputStream().use { output ->
                    inputStream.copyTo(output)
                }
                Result.success(Unit)
            } else {
                Result.failure(Exception("Download failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun cleanOldBackupsDriveApi(keepCount: Int = 3) {
        try {
            val result = listBackupsFromDriveApi()
            if (result.isSuccess) {
                val files = result.getOrNull() ?: emptyList()
                // Assuming API returns sorted, but safe to re-sort if needed. 
                // 'createdTime' is RFC 3339 date-time.
                if (files.size > keepCount) {
                    val toDelete = files.drop(keepCount)
                    toDelete.forEach { file ->
                        apiClient.googleDriveApi.deleteFile(file.id)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
