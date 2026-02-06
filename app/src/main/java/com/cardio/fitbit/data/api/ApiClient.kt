package com.cardio.fitbit.data.api

import com.cardio.fitbit.auth.FitbitAuthManager
import com.google.gson.GsonBuilder
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking

/**
 * API Client for Fitbit Web API
 */
@Singleton
class ApiClient @Inject constructor(
    private val authManager: FitbitAuthManager
) {
    private val baseUrl = "https://api.fitbit.com/"

    private val authInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        
        // Get access token (with automatic refresh)
        var accessToken = runBlocking {
            authManager.getAccessToken()
        }
        
        // Add authorization header
        val newRequest = if (accessToken != null) {
            originalRequest.newBuilder()
                .addHeader("Authorization", "Bearer $accessToken")
                .build()
        } else {
            originalRequest
        }
        
        val response = chain.proceed(newRequest)
        
        // Handle 401 Unauthorized - token might still be invalid (edge case)
        if (response.code == 401 && accessToken != null) {
            response.close()
            
            // Force refresh even if token appeared valid
            runBlocking {
                val result = authManager.refreshAccessToken()
                accessToken = result.getOrNull()
            }
            
            // Retry request with new token
            if (accessToken != null) {
                val retryRequest = originalRequest.newBuilder()
                    .addHeader("Authorization", "Bearer $accessToken")
                    .build()
                return@Interceptor chain.proceed(retryRequest)
            }
        }
        
        response
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = GsonBuilder()
        .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS")
        .create()

    private val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    val fitbitApi: FitbitApiService = retrofit.create(FitbitApiService::class.java)

    // --- Google Fit Client ---
    @Inject
    lateinit var googleFitAuthManager: com.cardio.fitbit.auth.GoogleFitAuthManager

    private val googleBaseUrl = "https://www.googleapis.com/fitness/v1/"

    private val googleAuthInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        
        // Get access token (synchronous)
        var accessToken = googleFitAuthManager.getAccessToken()
        
        // Add authorization header
        val request = if (accessToken != null) {
            originalRequest.newBuilder()
                .addHeader("Authorization", "Bearer $accessToken")
                .build()
        } else {
            originalRequest
        }
        
        val response = chain.proceed(request)
        
        // Handle 401 Unauthorized
        if (response.code == 401) {
            response.close()
            
            // Force refresh
            runBlocking {
                val result = googleFitAuthManager.refreshAccessToken()
                accessToken = result.getOrNull()
            }
            
            // Retry request with new token
            if (accessToken != null) {
                val retryRequest = originalRequest.newBuilder()
                    .addHeader("Authorization", "Bearer $accessToken")
                    .build()
                return@Interceptor chain.proceed(retryRequest)
            }
        }
        
        response
    }

    private val googleOkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(googleAuthInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val googleRetrofit by lazy {
        Retrofit.Builder()
            .baseUrl(googleBaseUrl)
            .client(googleOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    val googleFitApi: GoogleFitApiService by lazy { googleRetrofit.create(GoogleFitApiService::class.java) }

    // --- Google Drive Client ---
    private val driveBaseUrl = "https://www.googleapis.com/drive/v3/"

    private val driveRetrofit by lazy {
        Retrofit.Builder()
            .baseUrl(driveBaseUrl)
            .client(googleOkHttpClient) // Reuse same auth client (tokens are same)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    val googleDriveApi: GoogleDriveApiService by lazy { driveRetrofit.create(GoogleDriveApiService::class.java) }
}
