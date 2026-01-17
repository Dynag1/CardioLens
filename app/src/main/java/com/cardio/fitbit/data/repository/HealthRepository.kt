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
    private val activityDataDao: com.cardio.fitbit.data.local.dao.ActivityDataDao,
    private val hrvDataDao: com.cardio.fitbit.data.local.dao.HrvDataDao,
    private val heartRateDao: com.cardio.fitbit.data.local.dao.HeartRateDao,
    private val stepsDao: com.cardio.fitbit.data.local.dao.StepsDao
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

    suspend fun isAuthorized(): Boolean {
        return getProvider().isAuthorized()
    }

    /**
     * Get heart rate data for a specific date
     */
    suspend fun getHeartRateData(date: java.util.Date, forceRefresh: Boolean = false): Result<HeartRateData?> = withContext(Dispatchers.IO) {
        try {
            val dateString = DateUtils.formatForApi(date)
            
            // Check cache
            if (!forceRefresh) {
                val cached = heartRateDao.getByDate(dateString)
                if (cached != null) {
                    val data = com.google.gson.Gson().fromJson(cached.data, HeartRateData::class.java)
                    return@withContext Result.success(data)
                }
            }
            
            // Fetch
            val result = getProvider().getHeartRateData(date)
            
            if (result.isSuccess) {
                val data = result.getOrNull()
                // Cache
                if (data != null) {
                    val json = com.google.gson.Gson().toJson(data)
                    heartRateDao.insert(
                        com.cardio.fitbit.data.local.entities.HeartRateDataEntity(
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
     * Get heart rate data with intraday details
     */
    suspend fun getHeartRateIntraday(date: java.util.Date): Result<HeartRateData?> {
        // Mapped to getHeartRateData in new interface which includes intraday if available
        return getProvider().getHeartRateData(date) // Intraday usually fresh, but let's respect underlying call
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
    suspend fun getStepsData(startDate: java.util.Date, endDate: java.util.Date, forceRefresh: Boolean = false): Result<List<StepsData>> = withContext(Dispatchers.IO) {
        try {
            val startStr = DateUtils.formatForApi(startDate)
            val endStr = DateUtils.formatForApi(endDate)
            
            // 1. Check what we have in cache
            val cachedList = if (!forceRefresh) stepsDao.getBetweenDates(startStr, endStr) else emptyList()
            val cachedMap = cachedList.associateBy { it.date }
            
            // 2. Identify missing dates
            val calendar = java.util.Calendar.getInstance()
            calendar.time = startDate
            val missingDates = mutableListOf<java.util.Date>()
            
            while (!calendar.time.after(endDate)) {
                val dateStr = DateUtils.formatForApi(calendar.time)
                if (cachedMap[dateStr] == null) {
                    missingDates.add(calendar.time)
                }
                calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
            
            // 3. Decide Fetch Strategy
            val fetchedData = mutableListOf<StepsData>()
            
            if (missingDates.isNotEmpty()) {
                val totalDays = ((endDate.time - startDate.time) / (1000 * 60 * 60 * 24)).toInt() + 1
                
                // If missing > 50% or missing dates are scattered, fetch full range to be safe/efficient api-wise
                // Otherwise fetch just the missing range (simplified to min..max of missing)
                val fetchStart: java.util.Date
                val fetchEnd: java.util.Date
                
                if (missingDates.size > totalDays / 2) {
                     fetchStart = startDate
                     fetchEnd = endDate
                } else {
                     fetchStart = missingDates.minOrNull() ?: startDate
                     fetchEnd = missingDates.maxOrNull() ?: endDate
                }
                
                val result = getProvider().getStepsData(fetchStart, fetchEnd)
                if (result.isSuccess) {
                     val data = result.getOrNull() ?: emptyList()
                     fetchedData.addAll(data)
                     
                     // Cache new data
                     val entities = data.map { stepsData ->
                         com.cardio.fitbit.data.local.entities.StepsDataEntity(
                             date = DateUtils.formatForApi(stepsData.date),
                             data = com.google.gson.Gson().toJson(stepsData),
                             timestamp = System.currentTimeMillis()
                         )
                     }
                     stepsDao.insertAll(entities)
                } else {
                    return@withContext result // Fail if network fails and we needed data
                }
            }
            
            // 4. Reconstruct full list from DB to ensure consistency (and merged results)
            
            val finalMap = mutableMapOf<String, StepsData>()
            
            // Add cached
            cachedList.forEach { entity ->
                 try {
                     val obj = com.google.gson.Gson().fromJson(entity.data, StepsData::class.java)
                     finalMap[entity.date] = obj
                 } catch (e: Exception) { /* ignore corrupt */ }
            }
            
            // Add fetched (overrides cached if overlap)
            fetchedData.forEach { data ->
                finalMap[DateUtils.formatForApi(data.date)] = data
            }
            
            // Sort by date
            val finalList = finalMap.values.sortedBy { it.date }
            
            Result.success(finalList)
            
        } catch (e: Exception) {
            Result.failure(e)
        }
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
    suspend fun getHrvData(date: java.util.Date, forceRefresh: Boolean = false): Result<List<com.cardio.fitbit.data.models.HrvRecord>> = withContext(Dispatchers.IO) {
        try {
            val dateString = DateUtils.formatForApi(date)
            
            // Check cache first
            if (!forceRefresh) {
                val cached = hrvDataDao.getByDate(dateString)
                if (cached != null) {
                    val type = object : com.google.gson.reflect.TypeToken<List<com.cardio.fitbit.data.models.HrvRecord>>() {}.type
                    val data = com.google.gson.Gson().fromJson<List<com.cardio.fitbit.data.models.HrvRecord>>(cached.data, type)
                    return@withContext Result.success(data)
                }
            }

            // Fetch from Provider
            val result = getProvider().getHrvData(date)
            
            if (result.isSuccess) {
                val data = result.getOrNull() ?: emptyList()
                // Cache
                if (data.isNotEmpty()) {
                    val json = com.google.gson.Gson().toJson(data)
                    hrvDataDao.insert(
                        com.cardio.fitbit.data.local.entities.HrvDataEntity(
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
     * Get HRV history for a date range
     */
    suspend fun getHrvHistory(startDate: java.util.Date, endDate: java.util.Date, forceRefresh: Boolean = false): Result<List<com.cardio.fitbit.data.models.HrvRecord>> = withContext(Dispatchers.IO) {
        try {
            val startStr = DateUtils.formatForApi(startDate)
            val endStr = DateUtils.formatForApi(endDate)
            
            // 1. Check cache
            val cachedList = if (!forceRefresh) hrvDataDao.getBetweenDates(startStr, endStr) else emptyList()
            val cachedMap = cachedList.associateBy { it.date }
            
            // 2. Identify missing dates
            val calendar = java.util.Calendar.getInstance()
            calendar.time = startDate
            val missingDates = mutableListOf<java.util.Date>()
            
            while (!calendar.time.after(endDate)) {
                val dateStr = DateUtils.formatForApi(calendar.time)
                if (cachedMap[dateStr] == null) {
                    missingDates.add(calendar.time)
                }
                calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
            
            // 3. Fetch Strategy
            val fetchedData = mutableListOf<com.cardio.fitbit.data.models.HrvRecord>()
            
            if (missingDates.isNotEmpty()) {
                val totalDays = ((endDate.time - startDate.time) / (1000 * 60 * 60 * 24)).toInt() + 1
                
                // Fetch full range if significant data missing, otherwise just missing range
                val fetchStart: java.util.Date
                val fetchEnd: java.util.Date
                
                if (missingDates.size > totalDays / 2) {
                     fetchStart = startDate
                     fetchEnd = endDate
                } else {
                     fetchStart = missingDates.minOrNull() ?: startDate
                     fetchEnd = missingDates.maxOrNull() ?: endDate
                }
                
                val result = getProvider().getHrvHistory(fetchStart, fetchEnd)
                if (result.isSuccess) {
                     val allRecords = result.getOrNull() ?: emptyList()
                     fetchedData.addAll(allRecords)
                     
                     // Group by Date to Cache
                     val recordsByDate = allRecords.groupBy { DateUtils.formatForApi(it.time) }
                     
                     // Also handle empty days (explicitly cache empty list if provider success but no data for a day?)
                     // For now, caching what we got.
                     
                     val entities = recordsByDate.map { (dateStr, records) ->
                         com.cardio.fitbit.data.local.entities.HrvDataEntity(
                             date = dateStr,
                             data = com.google.gson.Gson().toJson(records),
                             timestamp = System.currentTimeMillis()
                         )
                     }
                     if (entities.isNotEmpty()) {
                        hrvDataDao.insertAll(entities)
                     }
                } else {
                    // Soft fail: return partial
                }
            }
            
            // 4. Reconstruct: Prioritize FETCHED data over CACHED data
            val finalRecords = mutableListOf<com.cardio.fitbit.data.models.HrvRecord>()
            
            // Add all fetched records first (Fresh data)
            finalRecords.addAll(fetchedData)
            
            // Create a set of fetched dates for easy lookup
            val fetchedDates = fetchedData.map { DateUtils.formatForApi(it.time) }.toSet()
            
            // Add cached records ONLY if their date was NOT fetched
            cachedList.forEach { entity ->
                 try {
                     val type = object : com.google.gson.reflect.TypeToken<List<com.cardio.fitbit.data.models.HrvRecord>>() {}.type
                     val records = com.google.gson.Gson().fromJson<List<com.cardio.fitbit.data.models.HrvRecord>>(entity.data, type)
                     
                     records.forEach { record ->
                         if (!fetchedDates.contains(DateUtils.formatForApi(record.time))) {
                             finalRecords.add(record)
                         }
                     }
                 } catch (e: Exception) { /* ignore */ }
            }
            
            Result.success(finalRecords.sortedBy { it.time })
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Keep old signature for compatibility/migration if needed, or remove it.
    // Let's rename the interface method or overload it.
}


