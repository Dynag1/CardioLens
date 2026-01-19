package com.cardio.fitbit.data.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface GoogleDriveApiService {

    @Multipart
    @POST("files?uploadType=multipart")
    suspend fun uploadFile(
        @Part("metadata") metadata: RequestBody,
        @Part file: MultipartBody.Part
    ): Response<DriveFile>

    @GET("files")
    suspend fun listFiles(
        @Query("q") query: String,
        @Query("spaces") spaces: String = "drive",
        @Query("fields") fields: String = "files(id, name, createdTime)",
        @Query("orderBy") orderBy: String = "createdTime desc"
    ): Response<FileList>

    @DELETE("files/{fileId}")
    suspend fun deleteFile(
        @Path("fileId") fileId: String
    ): Response<ResponseBody>
}

data class DriveFile(
    val id: String,
    val name: String,
    val createdTime: String? = null
)

data class FileList(
    val files: List<DriveFile>
)
