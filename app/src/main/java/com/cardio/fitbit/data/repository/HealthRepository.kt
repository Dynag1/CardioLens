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
    private val stepsDao: com.cardio.fitbit.data.local.dao.StepsDao,
    private val moodDao: com.cardio.fitbit.data.local.dao.MoodDao,
    private val spo2Dao: com.cardio.fitbit.data.local.dao.SpO2Dao,
    private val symptomDao: com.cardio.fitbit.data.local.dao.SymptomDao,
    private val gson: com.google.gson.Gson
) {
    companion object {
        private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L // 24 hours
    }

    suspend fun getMood(date: java.util.Date): Int? = withContext(Dispatchers.IO) {
        val dateString = DateUtils.formatForApi(date)
        moodDao.getByDate(dateString)?.rating
    }

    suspend fun saveMood(date: java.util.Date, rating: Int) = withContext(Dispatchers.IO) {
        val dateString = DateUtils.formatForApi(date)
        moodDao.insert(
            com.cardio.fitbit.data.local.entities.MoodEntry(
                date = dateString,
                rating = rating,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun getSymptoms(date: java.util.Date): String? = withContext(Dispatchers.IO) {
        val dateString = DateUtils.formatForApi(date)
        symptomDao.getSymptomsForDate(dateString)?.symptoms
    }

    suspend fun saveSymptoms(date: java.util.Date, symptoms: String) = withContext(Dispatchers.IO) {
        val dateString = DateUtils.formatForApi(date)
        symptomDao.insertSymptom(
            com.cardio.fitbit.data.local.entities.SymptomEntry(
                date = dateString,
                symptoms = symptoms,
                timestamp = System.currentTimeMillis()
            )
        )
    }
    
    suspend fun getSymptomsHistory(startDate: java.util.Date, endDate: java.util.Date): List<com.cardio.fitbit.data.local.entities.SymptomEntry> = withContext(Dispatchers.IO) {
        val startStr = DateUtils.formatForApi(startDate)
        val endStr = DateUtils.formatForApi(endDate)
        symptomDao.getBetweenDates(startStr, endStr)
    }

    suspend fun getMoodHistory(startDate: java.util.Date, endDate: java.util.Date): List<com.cardio.fitbit.data.local.entities.MoodEntry> = withContext(Dispatchers.IO) {
        val startStr = DateUtils.formatForApi(startDate)
        val endStr = DateUtils.formatForApi(endDate)
        moodDao.getBetweenDates(startStr, endStr)
    }

    private suspend fun getProvider(): HealthDataProvider {
        val useHealthConnect = userPreferencesRepository.useHealthConnect.first() 
        
        // Priority: Health Connect > Google Fit Cloud > Fitbit
        return when {
            useHealthConnect -> healthConnectProvider
            fitbitProvider.isAuthorized() -> fitbitProvider
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
    suspend fun getHeartRateData(date: java.util.Date, forceRefresh: Boolean = false): Result<HeartRateData> = withContext(Dispatchers.IO) {
        val dateString = DateUtils.formatForApi(date)
        
        // Hold onto summary cache if we find it
        var cachedSummary: HeartRateData? = null

        if (!forceRefresh) {
            val cached = heartRateDao.getByDate(dateString)
            if (cached != null) {
                try {
                    val data = gson.fromJson(cached.data, HeartRateData::class.java)
                    // If we have details, return immediately
                    if (data.intradayData != null && data.intradayData.isNotEmpty()) {
                        return@withContext Result.success(data)
                    }
                    // Determine if this is a summary
                    cachedSummary = data
                } catch (e: Exception) {
                    android.util.Log.e("HealthRepository", "Failed to parse cached heart rate data: ${e.message}")
                    // Ignore corrupt cache
                }
            }
        }

        // Try to fetch fresh detailed data
        val result = getProvider().getHeartRateData(date)
        
        if (result.isSuccess) {
            val data = result.getOrNull()
            if (data != null) {
                // Save to cache
                val json = gson.toJson(data)
                heartRateDao.insert(
                    com.cardio.fitbit.data.local.entities.HeartRateDataEntity(
                        date = dateString,
                        data = json,
                        timestamp = System.currentTimeMillis()
                    )
                )
                return@withContext Result.success(data)
            }
        }
        
        // If fetch failed or came back empty, but we have a cached summary (from history), use it!
        // This is crucial for "Server" apps that might not have Intraday permission but have Daily Summaries.
        if (cachedSummary != null) {
            return@withContext Result.success(cachedSummary)
        }

        // If all else fails, return the original fetch result (which might be failure or success with null data)
        // If the original result was a success but with null data, we convert it to a failure for consistency
        if (result.isSuccess && result.getOrNull() == null) {
            Result.failure(NoSuchElementException("No heart rate data found for $dateString"))
        } else {
            result.map { it ?: throw NoSuchElementException("No heart rate data found for $dateString") }
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
     * Get heart rate history (daily summaries) for a date range
     * Note: This usually returns simplified data (no full intraday) to save bandwidth/quota.
     * We do NOT cache this into the main HeartRate entity to avoid overwriting detailed intraday data with summaries.
     */
    suspend fun getHeartRateHistory(startDate: java.util.Date, endDate: java.util.Date): Result<List<HeartRateData>> = withContext(Dispatchers.IO) {
        try {
            val startStr = DateUtils.formatForApi(startDate)
            val endStr = DateUtils.formatForApi(endDate)

            // 1. Check cache
            val cachedList = heartRateDao.getBetweenDates(startStr, endStr)
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

            val fetchedData = mutableListOf<HeartRateData>()

            if (missingDates.isNotEmpty()) {
                val totalDays = ((endDate.time - startDate.time) / (1000 * 60 * 60 * 24)).toInt() + 1
                
                 val fetchStart: java.util.Date
                 val fetchEnd: java.util.Date

                 if (missingDates.size > totalDays / 2) {
                     fetchStart = startDate
                     fetchEnd = endDate
                 } else {
                     fetchStart = missingDates.minOrNull() ?: startDate
                     fetchEnd = missingDates.maxOrNull() ?: endDate
                 }

                // Ensure we cover the full range of the days (00:00 -> 23:59)
                val actualStart = DateUtils.getStartOfDay(fetchStart)
                val actualEnd = DateUtils.getEndOfDay(fetchEnd)
                
                val result = getProvider().getHeartRateHistory(actualStart, actualEnd)
                
                if (result.isSuccess) {
                    val data = result.getOrNull() ?: emptyList()
                    fetchedData.addAll(data)

                    // Cache (overwrite if exists, to ensure we have at least this summary)
                    // Logic: If we already have a record for this date, should we overwrite?
                    // If existing record has intraday (!= null), DO NOT overwrite with summary (null).
                    // If existing record is just summary, we can overwrite (update).
                    // Since we filtered `missingDates` based on `cachedMap[dateStr] == null`, 
                    // we are mostly dealing with new data here.
                    // BUT, if we fetched a Range (e.g. startDate to endDate) because >50% missing, 
                    // the `data` result includes dates we ALREADY have.
                    // We must be careful not to downgrade detailed data to summary.
                    
                    val existingMap = cachedMap.mapValues { 
                         try { gson.fromJson(it.value.data, HeartRateData::class.java) } catch(e: Exception) { null }
                    }.filterValues { it != null } as Map<String, HeartRateData>
                    
                    val entitiesToInsert = mutableListOf<com.cardio.fitbit.data.local.entities.HeartRateDataEntity>()
                    
                    data.forEach { newItem ->
                        val dateStr = DateUtils.formatForApi(newItem.date)
                        val existingItem = existingMap[dateStr]
                        
                        // Only insert/update if:
                        // 1. No existing item
                        // 2. Existing item is just a summary (intraday == null) AND new item is better? (History fetch is always summary, so never better than detail)
                        // So: rely on "History Fetch never beats Detailed Cache".
                        // Basic rule: If exists and has details, keep existing. If not exists, insert new. 
                        // If exists but summary, simple update (same level).
                        
                        val shouldSave = existingItem == null || existingItem.intradayData == null
                        
                        if (shouldSave) {
                             val json = gson.toJson(newItem)
                             entitiesToInsert.add(
                                 com.cardio.fitbit.data.local.entities.HeartRateDataEntity(
                                     date = dateStr,
                                     data = json,
                                     timestamp = System.currentTimeMillis()
                                 )
                             )
                        }
                    }
                    
                    if (entitiesToInsert.isNotEmpty()) {
                        heartRateDao.insertAll(entitiesToInsert)
                    }
                }
            }

            // 3. Reconstruct Result
            val finalData = mutableListOf<HeartRateData>()
            finalData.addAll(fetchedData)
            
            // Add cached items that weren't in fetchedData (or were skipped during save)
            // But we need to return the BEST data we have for the whole range.
            // If we fetched the whole range, `fetchedData` has everything (Summaries).
            // But `cachedList` might have DETAILS for some of those days.
            // We should prioritize DETAILS over fetched SUMMARIES for the return value too.
            
            val fetchedMap = fetchedData.associateBy { DateUtils.formatForApi(it.date) }
            val combinedResult = mutableListOf<HeartRateData>()
            
            // Iterate day by day for the requested range to build consistent list
            val cal = java.util.Calendar.getInstance()
            cal.time = startDate
            
            while (!cal.time.after(endDate)) {
                val dStr = DateUtils.formatForApi(cal.time)
                
                // Get from cache (parse it)
                val cachedEntity = cachedMap[dStr]
                val cachedObj = cachedEntity?.let { 
                    try { gson.fromJson(it.data, HeartRateData::class.java) } catch(e:Exception){null} 
                }
                
                val fetchedObj = fetchedMap[dStr]
                
                // Pick best: Cached Details > Fetched Summary > Cached Summary
                var bestObj: HeartRateData? = null
                
                if (cachedObj != null && cachedObj.intradayData != null) {
                    bestObj = cachedObj
                } else {
                    // No detailed cache. Use fetched if available, else cached summary
                    bestObj = fetchedObj ?: cachedObj
                }
                
                if (bestObj != null) combinedResult.add(bestObj)
                
                cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
            
            Result.success(combinedResult)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get sleep history for a date range
     */
    suspend fun getSleepHistory(startDate: java.util.Date, endDate: java.util.Date, forceRefresh: Boolean = false): Result<List<SleepData>> = withContext(Dispatchers.IO) {
        try {
            val startStr = DateUtils.formatForApi(startDate)
            val endStr = DateUtils.formatForApi(endDate)
            
            // 1. Check cache
            val cachedList = if (!forceRefresh) sleepDataDao.getBetweenDates(startStr, endStr) else emptyList()
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
            
            val fetchedData = mutableListOf<SleepData>()
            
            if (missingDates.isNotEmpty()) {
                val totalDays = ((endDate.time - startDate.time) / (1000 * 60 * 60 * 24)).toInt() + 1
                 // Range fetch strategy
                 val fetchStart: java.util.Date
                 val fetchEnd: java.util.Date
                 
                 if (missingDates.size > totalDays / 2) {
                     fetchStart = startDate
                     fetchEnd = endDate
                 } else {
                     fetchStart = missingDates.minOrNull() ?: startDate
                     fetchEnd = missingDates.maxOrNull() ?: endDate
                 }
                
                // Ensure we cover the full range of the days (00:00 -> 23:59)
                val actualStart = DateUtils.getStartOfDay(fetchStart)
                val actualEnd = DateUtils.getEndOfDay(fetchEnd)
                
                val result = getProvider().getSleepHistory(actualStart, actualEnd)
                
                if (result.isSuccess) {
                    val data = result.getOrNull() ?: emptyList()
                    fetchedData.addAll(data)
                    
                    // Cache
                    // SleepData structure is complex (list of SleepData per day? No, getSleepHistory returns List<SleepData> flat?)
                    // Fitbit API returns list of sleep logs. Multiple logs can exist for same day.
                    // SleepDataDao expects One Entity per Date?
                    // Let's check SleepDataDao...
                    // SleepDataEntity: date (String), data (JSON List<SleepData>)
                    // getSleepHistory returns List<SleepData>.
                    // We need to group by Date to insert into DB.
                    
                    val grouped = data.groupBy { DateUtils.formatForApi(it.date) }
                    
                    val entities = grouped.map { (dateStr, sleepList) ->
                        com.cardio.fitbit.data.local.entities.SleepDataEntity(
                            date = dateStr,
                            data = gson.toJson(sleepList),
                            timestamp = System.currentTimeMillis()
                        )
                    }
                    if (entities.isNotEmpty()) {
                        sleepDataDao.insertAll(entities)
                    }
                }
            }
            
            // Reconstruct logic mostly same as HrvHistory
            // ... (Simplified: just returning what we have combined)
             val finalRecords = mutableListOf<SleepData>()
             finalRecords.addAll(fetchedData)
             
             // Simple distinct union
             val fetchedDates = fetchedData.map { DateUtils.formatForApi(it.date) }.toSet()
             
             cachedList.forEach { entity ->
                  if (!fetchedDates.contains(entity.date)) {
                      val type = object : com.google.gson.reflect.TypeToken<List<SleepData>>() {}.type
                      try {
                          val list = gson.fromJson<List<SleepData>>(entity.data, type)
                          finalRecords.addAll(list)
                      } catch (e: Exception) { /* ignore */ }
                  }
             }
             
             Result.success(finalRecords.sortedBy { it.startTime })
            
        } catch (e: Exception) {
            Result.failure(e)
        }
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
                    try {
                        val type = object : com.google.gson.reflect.TypeToken<List<SleepData>>() {}.type
                        val data = gson.fromJson<List<SleepData>>(cached.data, type)
                        return@withContext Result.success(data)
                    } catch (e: Exception) { /* ignore */ }
                }
            }
            
            // Fetch from Provider
            val result = getProvider().getSleepData(date)
            
            if (result.isSuccess) {
                val rawData = result.getOrNull() ?: emptyList()
                // Strict Filter: Only keep sleep logs that match the requested date
                val data = rawData.filter { DateUtils.formatForApi(it.date) == dateString }
                
                // Cache
                if (data.isNotEmpty()) {
                    val json = gson.toJson(data)
                    sleepDataDao.insert(
                        com.cardio.fitbit.data.local.entities.SleepDataEntity(
                            date = dateString,
                            data = json,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                } 
                // If data is empty after filter, it means no sleep found for THIS day 
                // even if API returned something (e.g. yesterday's sleep).
                // Returning empty list is correct.
                
                Result.success(data)
            } else {
                // Fallback to cache if API fails (e.g. 429)
                val fallbackCache = sleepDataDao.getByDate(dateString)
                if (fallbackCache != null) {
                    try {
                        val type = object : com.google.gson.reflect.TypeToken<List<SleepData>>() {}.type
                        val data = gson.fromJson<List<SleepData>>(fallbackCache.data, type)
                        Result.success(data)
                    } catch (e: Exception) { result }
                } else {
                    result
                }
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
            android.util.Log.d("TrendsDebug", "HR Steps: Cached: ${cachedList.size}, Missing: ${missingDates.size}")

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
                
                // Ensure we cover the full range of the days (00:00 -> 23:59)
                // This prevents "start == end" crashes in Health Connect and ensures full day data.
                val actualStart = DateUtils.getStartOfDay(fetchStart)
                val actualEnd = DateUtils.getEndOfDay(fetchEnd)
                
                val result = getProvider().getStepsData(actualStart, actualEnd)
                android.util.Log.d("TrendsDebug", "HR Steps: Fetched Result Success: ${result.isSuccess}, Data Size: ${result.getOrNull()?.size}")
                
                if (result.isSuccess) {
                     val data = result.getOrNull() ?: emptyList()
                     fetchedData.addAll(data)
                     
                     // Cache new data
                     val entities = data.map { stepsData ->
                         com.cardio.fitbit.data.local.entities.StepsDataEntity(
                             date = DateUtils.formatForApi(stepsData.date),
                             data = gson.toJson(stepsData),
                             timestamp = System.currentTimeMillis()
                         )
                     }
                     stepsDao.insertAll(entities)
                } else {
                    android.util.Log.e("TrendsDebug", "HR Steps: Fetch Failed: ${result.exceptionOrNull()}")
                    return@withContext result // Fail if network fails and we needed data
                }
            }
            
            // 4. Reconstruct full list from DB to ensure consistency (and merged results)
            
            val finalMap = mutableMapOf<String, StepsData>()
            
            // Add cached
            cachedList.forEach { entity ->
                 try {
                     try {
                         val obj = gson.fromJson(entity.data, StepsData::class.java)
                         finalMap[entity.date] = obj
                     } catch (e: Exception) { /* ignore corrupt */ }
                 } catch (e: Exception) { /* ignore corrupt */ }
            }
            
            // Add fetched (overrides cached if overlap)
            fetchedData.forEach { data ->
                finalMap[DateUtils.formatForApi(data.date)] = data
            }
            
            // Sort by date
            val finalList = finalMap.values.sortedBy { it.date }
            android.util.Log.d("TrendsDebug", "HR Steps: Final List Size: ${finalList.size}")
            
            Result.success(finalList)
            
        } catch (e: Exception) {
            android.util.Log.e("TrendsDebug", "HR Steps: Exception: ${e.message}")
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
                    val data = gson.fromJson(cached.data, ActivityData::class.java)
                    return@withContext Result.success(data)
                }
            }
            
            // Fetch from Provider
            val result = getProvider().getActivityData(date)
            
            if (result.isSuccess) {
                val data = result.getOrNull()
                // Cache
                if (data != null) {
                    val json = gson.toJson(data)
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
     * Get all cached activities
     */
    suspend fun getAllActivities(): Result<List<ActivityData>> = withContext(Dispatchers.IO) {
        try {
            val cachedList = activityDataDao.getAll()
            val result = cachedList.mapNotNull { entity ->
                try {
                    gson.fromJson(entity.data, ActivityData::class.java)
                } catch (e: Exception) {
                    null
                }
            }.sortedByDescending { it.date } // Most recent first
            
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }



    /**
     * Get activity history for a date range
     */
    suspend fun getActivityHistory(startDate: java.util.Date, endDate: java.util.Date, forceRefresh: Boolean = false): Result<List<ActivityData>> = withContext(Dispatchers.IO) {
        try {
            val startStr = DateUtils.formatForApi(startDate)
            val endStr = DateUtils.formatForApi(endDate)
            
            // 1. Check cache
            val cachedList = if (!forceRefresh) activityDataDao.getBetweenDates(startStr, endStr) else emptyList()
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
            
            val fetchedData = mutableListOf<ActivityData>()
            
            if (missingDates.isNotEmpty()) {
                val totalDays = ((endDate.time - startDate.time) / (1000 * 60 * 60 * 24)).toInt() + 1
                val fetchStart: java.util.Date
                val fetchEnd: java.util.Date
                  
                if (missingDates.size > totalDays / 2) {
                    fetchStart = startDate
                    fetchEnd = endDate
                } else {
                    fetchStart = missingDates.minOrNull() ?: startDate
                    fetchEnd = missingDates.maxOrNull() ?: endDate
                }
                
                // Ensure we cover the full range of the days (00:00 -> 23:59)
                val actualStart = DateUtils.getStartOfDay(fetchStart)
                val actualEnd = DateUtils.getEndOfDay(fetchEnd)
                
                val result = getProvider().getActivityHistory(actualStart, actualEnd)
                if (result.isSuccess) {
                    val data = result.getOrNull() ?: emptyList()
                    fetchedData.addAll(data)
                    
                    // Cache
                    val entities = data.map { item ->
                        com.cardio.fitbit.data.local.entities.ActivityDataEntity(
                            date = DateUtils.formatForApi(item.date),
                            data = gson.toJson(item),
                            timestamp = System.currentTimeMillis()
                        )
                    }
                    if (entities.isNotEmpty()) {
                        activityDataDao.insertAll(entities)
                    }
                }
            }
            
            // 3. Merge
            val finalRecords = mutableListOf<ActivityData>()
            finalRecords.addAll(fetchedData)
            val fetchedDates = fetchedData.map { DateUtils.formatForApi(it.date) }.toSet()
            
            cachedList.forEach { entity ->
                 if (!fetchedDates.contains(entity.date)) {
                      try {
                          try {
                              val item = gson.fromJson(entity.data, ActivityData::class.java)
                              finalRecords.add(item)
                          } catch (e: Exception) {}
                      } catch (e: Exception) {}
                 }
            }
            
            Result.success(finalRecords.sortedBy { it.date })
            
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

    suspend fun getIntradayHistory(startDate: java.util.Date, endDate: java.util.Date, forceRefresh: Boolean = false): Result<List<IntradayData>> = withContext(Dispatchers.IO) {
        try {
            val startStr = DateUtils.formatForApi(startDate)
            val endStr = DateUtils.formatForApi(endDate)
            
            // 1. Check cache
            val cachedList = if (!forceRefresh) intradayDataDao.getBetweenDates(startStr, endStr) else emptyList()
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
            
            val fetchedData = mutableListOf<IntradayData>()
            
            if (missingDates.isNotEmpty()) {
                // If missing > 50% or scattered, fetch range
                val totalDays = ((endDate.time - startDate.time) / (1000 * 60 * 60 * 24)).toInt() + 1
                  val fetchStart: java.util.Date
                  val fetchEnd: java.util.Date
                  
                  if (missingDates.size > totalDays / 2) {
                      fetchStart = startDate
                      fetchEnd = endDate
                  } else {
                      fetchStart = missingDates.minOrNull() ?: startDate
                      fetchEnd = missingDates.maxOrNull() ?: endDate
                  }
                
                // Ensure we cover the full range of the days (00:00 -> 23:59)
                val actualStart = DateUtils.getStartOfDay(fetchStart)
                val actualEnd = DateUtils.getEndOfDay(fetchEnd)
                
                val result = getProvider().getIntradayHistory(actualStart, actualEnd)
                
                if (result.isSuccess) {
                    val data = result.getOrNull() ?: emptyList()
                    fetchedData.addAll(data)
                    
                    // Cache
                    val entities = data.map { item ->
                        com.cardio.fitbit.data.local.entities.IntradayDataEntity(
                            date = DateUtils.formatForApi(item.date),
                            data = gson.toJson(item),
                            timestamp = System.currentTimeMillis()
                        )
                    }
                    if (entities.isNotEmpty()) {
                        intradayDataDao.insertAll(entities)
                    }
                }
            }
            
            // 3. Merged Result
            val finalRecords = mutableListOf<IntradayData>()
            finalRecords.addAll(fetchedData)
            val fetchedDates = fetchedData.map { DateUtils.formatForApi(it.date) }.toSet()
            
            cachedList.forEach { entity ->
                 if (!fetchedDates.contains(entity.date)) {
                      try {
                          try {
                              val item = gson.fromJson(entity.data, IntradayData::class.java)
                              finalRecords.add(item)
                          } catch (e: Exception) {}
                      } catch (e: Exception) {}
                 }
            }
            Result.success(finalRecords.sortedBy { it.date })
        } catch (e: Exception) {
            Result.failure(e)
        }
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
                    try {
                        val data = gson.fromJson(cached.data, IntradayData::class.java)
                        return@withContext Result.success(data)
                    } catch (e: Exception) { /* ignore */ }
                }
            }
            
            // Fetch from Provider
            val result = getProvider().getIntradayData(date, forceRefresh)
            
            if (result.isSuccess) {
                val data = result.getOrNull()
                // Cache
                if (data != null) {
                    val json = gson.toJson(data)
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
                    try {
                        val type = object : com.google.gson.reflect.TypeToken<List<com.cardio.fitbit.data.models.HrvRecord>>() {}.type
                        val data = gson.fromJson<List<com.cardio.fitbit.data.models.HrvRecord>>(cached.data, type)
                        return@withContext Result.success(data)
                    } catch (e: Exception) { /* ignore */ }
                }
            }

            // Fetch from Provider
            val result = getProvider().getHrvData(date)
            
            if (result.isSuccess) {
                val data = result.getOrNull() ?: emptyList()
                // Cache
                if (data.isNotEmpty()) {
                    val json = gson.toJson(data)
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
                             data = gson.toJson(records),
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
                     val records = gson.fromJson<List<com.cardio.fitbit.data.models.HrvRecord>>(entity.data, type)
                     
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

    suspend fun getSpO2Data(date: java.util.Date, forceRefresh: Boolean = false): Result<SpO2Data?> = withContext(Dispatchers.IO) {
        try {
            // Check cache
            if (!forceRefresh) {
                val cached = spo2Dao.getSpO2ByDate(date.time)
                if (cached != null) {
                    return@withContext Result.success(cached.toDomain())
                }
            }

            // Fetch
            val result = getProvider().getSpO2Data(date)
            if (result.isSuccess) {
                val data = result.getOrNull()
                if (data != null) {
                    spo2Dao.insertSpO2(com.cardio.fitbit.data.local.entities.SpO2DataEntity.fromDomain(data))
                }
                Result.success(data)
            } else {
                result
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSpO2History(startDate: java.util.Date, endDate: java.util.Date, forceRefresh: Boolean = false): Result<List<SpO2Data>> = withContext(Dispatchers.IO) {
        try {
             // 1. Check cache
            val cachedEntities = if (!forceRefresh) spo2Dao.getSpO2Range(startDate.time, endDate.time) else emptyList()
            val cachedMap = cachedEntities.associateBy { it.date } // date is Long here (clean midnight timestamp expected)
            
            // 2. Identify missing dates
            val calendar = java.util.Calendar.getInstance()
            calendar.time = startDate
            DateUtils.toStartOfDay(calendar) // Ensure we step through midnights
            
            val missingDates = mutableListOf<java.util.Date>()
            
            while (!calendar.time.after(endDate)) {
                if (cachedMap[calendar.timeInMillis] == null) {
                    missingDates.add(calendar.time)
                }
                calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
            }

            // 3. Fetch Strategy
            val fetchedData = mutableListOf<SpO2Data>()
            
            if (missingDates.isNotEmpty()) {
                val fetchStart = missingDates.minOrNull() ?: startDate
                val fetchEnd = missingDates.maxOrNull() ?: endDate
                
                val result = getProvider().getSpO2History(fetchStart, fetchEnd)
                if (result.isSuccess) {
                    val data = result.getOrNull() ?: emptyList()
                    fetchedData.addAll(data)
                    
                    val entities = data.map { com.cardio.fitbit.data.local.entities.SpO2DataEntity.fromDomain(it) }
                    spo2Dao.insertAll(entities)
                }
            }

            // 4. Reconstruct
            val finalData = mutableListOf<SpO2Data>()
            finalData.addAll(fetchedData)
            
            val fetchedDates = fetchedData.map { it.date.time }.toSet()
            
            cachedEntities.forEach { entity ->
                if (!fetchedDates.contains(entity.date)) {
                    finalData.add(entity.toDomain())
                }
            }
            
            Result.success(finalData.sortedBy { it.date })

        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}


