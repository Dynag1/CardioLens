package com.cardio.fitbit.data.repository

import com.cardio.fitbit.data.models.*
import com.cardio.fitbit.data.provider.FitbitHealthProvider
import com.cardio.fitbit.data.provider.HealthDataProvider
import com.cardio.fitbit.utils.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for health data
 * Handles data fetching from selected Provider and local caching
 */
@Singleton
class HealthRepository @Inject constructor(
    private val fitbitProvider: FitbitHealthProvider,
    private val googleFitProvider: com.cardio.fitbit.data.provider.GoogleFitHealthProvider,
    private val healthConnectProvider: com.cardio.fitbit.data.provider.HealthConnectProvider,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val intradayDataDao: com.cardio.fitbit.data.local.dao.IntradayDataDao,
    private val sleepDataDao: com.cardio.fitbit.data.local.dao.SleepDataDao,
    private val activityDataDao: com.cardio.fitbit.data.local.dao.ActivityDataDao
) {
    companion object {
        private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L // 24 hours
    }

    private suspend fun getProvider(): HealthDataProvider {
        val useHealthConnect = userPreferencesRepository.useHealthConnect.first() 
        
        // Priority: Health Connect > Google Fit Cloud > Fitbit
        return when {
            useHealthConnect -> healthConnectProvider
            googleFitProvider.isAuthorized() -> googleFitProvider
            else -> fitbitProvider
        }
    }

    /**
     * Get current provider ID (FITBIT, GOOGLE_FIT, or health_connect)
     */
    suspend fun getCurrentProviderId(): String {
        return getProvider().providerId
    }

    /**
     * Get heart rate data for a specific date
     */
    suspend fun getHeartRateData(date: java.util.Date): Result<HeartRateData?> {
        return getProvider().getHeartRateData(date)
    }

    /**
     * Get heart rate data with intraday details
     */
    suspend fun getHeartRateIntraday(date: java.util.Date): Result<HeartRateData?> {
        // Mapped to getHeartRateData in new interface which includes intraday if available
        return getProvider().getHeartRateData(date)
    }

    suspend fun getHeartRateSeries(startTime: java.util.Date, endTime: java.util.Date): Result<List<MinuteData>> {
        return getProvider().getHeartRateSeries(startTime, endTime)
    }

    /**
     * Get sleep data for a specific date
     * Uses cache-first strategy with 24h TTL
     */
    suspend fun getSleepData(date: java.util.Date, forceRefresh: Boolean = false): Result<List<SleepData>> = withContext(Dispatchers.IO) {
        try {
            val dateString = DateUtils.formatForApi(date)
            
            // Check cache first (unless forceRefresh)
            if (!forceRefresh) {
                val cached = sleepDataDao.getByDate(dateString)
                if (cached != null) {
                    val type = object : com.google.gson.reflect.TypeToken<List<SleepData>>() {}.type
                    val data = com.google.gson.Gson().fromJson<List<SleepData>>(cached.data, type)
                    return@withContext Result.success(data)
                }
            }
            
            // Fetch from Provider
            val result = getProvider().getSleepData(date)
            
            if (result.isSuccess) {
                val data = result.getOrNull() ?: emptyList()
                // Cache
                if (data.isNotEmpty()) {
                    val json = com.google.gson.Gson().toJson(data)
                    sleepDataDao.insert(
                        com.cardio.fitbit.data.local.entities.SleepDataEntity(
                            date = dateString,
                            data = json,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
                Result.success(data)
            } else {
                result
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get steps data for a date range
     */
    suspend fun getStepsData(startDate: java.util.Date, endDate: java.util.Date): Result<List<StepsData>> {
        return getProvider().getStepsData(startDate, endDate)
    }

    /**
     * Get activity data for a specific date
     * Uses cache-first strategy with 24h TTL
     */
    suspend fun getActivityData(date: java.util.Date, forceRefresh: Boolean = false): Result<ActivityData?> = withContext(Dispatchers.IO) {
        try {
            val dateString = DateUtils.formatForApi(date)
            
            // Check cache first (unless forceRefresh)
            if (!forceRefresh) {
                val cached = activityDataDao.getByDate(dateString)
                if (cached != null) {
                    val data = com.google.gson.Gson().fromJson(cached.data, ActivityData::class.java)
                    return@withContext Result.success(data)
                }
            }
            
            // Fetch from Provider
            val result = getProvider().getActivityData(date)
            
            if (result.isSuccess) {
                val data = result.getOrNull()
                // Cache
                if (data != null) {
                    val json = com.google.gson.Gson().toJson(data)
                    activityDataDao.insert(
                        com.cardio.fitbit.data.local.entities.ActivityDataEntity(
                            date = dateString,
                            data = json,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
                Result.success(data)
            } else {
                result
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get user profile
     */
    suspend fun getUserProfile(): Result<UserProfile?> {
        return getProvider().getUserProfile()
    }

    /**
     * Get combined intraday data (heart rate + steps minute-by-minute)
     * Uses cache-first strategy with 24h TTL
     */
    suspend fun getIntradayData(date: java.util.Date, forceRefresh: Boolean = false): Result<IntradayData?> = withContext(Dispatchers.IO) {
        try {
            val dateString = DateUtils.formatForApi(date)
            
            // Check cache first (unless forceRefresh)
            if (!forceRefresh) {
                val cached = intradayDataDao.getByDate(dateString)
                if (cached != null) {
                    val data = com.google.gson.Gson().fromJson(cached.data, IntradayData::class.java)
                    return@withContext Result.success(data)
                }
            }
            
            // Fetch from Provider
            val result = getProvider().getIntradayData(date)
            
            if (result.isSuccess) {
                val data = result.getOrNull()
                // Cache
                if (data != null) {
                    val json = com.google.gson.Gson().toJson(data)
                    intradayDataDao.insert(
                        com.cardio.fitbit.data.local.entities.IntradayDataEntity(
                            date = dateString,
                            data = json,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
                Result.success(data)
            } else {
                result
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}


