package com.cardio.fitbit.data.repository

import com.cardio.fitbit.data.api.ApiClient
import com.cardio.fitbit.data.models.*
import com.cardio.fitbit.utils.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for health data
 * Handles data fetching from API and local caching
 */
@Singleton
class HealthRepository @Inject constructor(
    private val apiClient: ApiClient,
    private val intradayDataDao: com.cardio.fitbit.data.local.dao.IntradayDataDao,
    private val sleepDataDao: com.cardio.fitbit.data.local.dao.SleepDataDao,
    private val activityDataDao: com.cardio.fitbit.data.local.dao.ActivityDataDao
) {
    companion object {
        private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L // 24 hours
    }
    /**
     * Get heart rate data for a specific date
     */
    suspend fun getHeartRateData(date: java.util.Date): Result<HeartRateData?> = withContext(Dispatchers.IO) {
        try {
            val dateString = DateUtils.formatForApi(date)
            val response = apiClient.fitbitApi.getHeartRate(dateString)
            
            if (response.isSuccessful && response.body() != null) {
                val apiResponse = response.body()!!
                val heartRateData = mapHeartRateResponse(apiResponse)
                Result.success(heartRateData)
            } else {
                Result.failure(Exception("Failed to fetch heart rate data: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get heart rate data with intraday details
     */
    suspend fun getHeartRateIntraday(date: java.util.Date): Result<HeartRateData?> = withContext(Dispatchers.IO) {
        try {
            val dateString = DateUtils.formatForApi(date)
            val response = apiClient.fitbitApi.getHeartRateIntraday(dateString)
            
            if (response.isSuccessful && response.body() != null) {
                val apiResponse = response.body()!!
                val heartRateData = mapHeartRateResponse(apiResponse)
                Result.success(heartRateData)
            } else {
                Result.failure(Exception("Failed to fetch intraday heart rate: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get sleep data for a specific date
     * Uses cache-first strategy with 24h TTL
     */
    suspend fun getSleepData(date: java.util.Date, forceRefresh: Boolean = false): Result<SleepData?> = withContext(Dispatchers.IO) {
        try {
            val dateString = DateUtils.formatForApi(date)
            android.util.Log.d("HealthRepo", "Loading sleep data for: $dateString (forceRefresh=$forceRefresh)")
            
            // Check cache first (unless forceRefresh)
            if (!forceRefresh) {
                val cached = sleepDataDao.getByDate(dateString)
                if (cached != null && System.currentTimeMillis() - cached.timestamp < CACHE_TTL_MS) {
                    android.util.Log.d("HealthRepo", "Using cached sleep data")
                    val data = com.google.gson.Gson().fromJson(cached.data, SleepData::class.java)
                    return@withContext Result.success(data)
                }
            }
            
            // Fetch from API
            android.util.Log.d("HealthRepo", "Fetching sleep data from API")
            val response = apiClient.fitbitApi.getSleep(dateString)
            
            if (response.isSuccessful && response.body() != null) {
                val apiResponse = response.body()!!
                val sleepData = mapSleepResponse(apiResponse)
                
                // Cache the result
                if (sleepData != null) {
                    val json = com.google.gson.Gson().toJson(sleepData)
                    sleepDataDao.insert(
                        com.cardio.fitbit.data.local.entities.SleepDataEntity(
                            date = dateString,
                            data = json,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
                
                android.util.Log.d("HealthRepo", "Sleep data loaded: ${sleepData?.startTime} - ${sleepData?.endTime}")
                Result.success(sleepData)
            } else {
                Result.failure(Exception("Failed to fetch sleep data: ${response.code()}"))
            }
        } catch (e: Exception) {
            android.util.Log.e("HealthRepo", "Failed to load sleep data", e)
            Result.failure(e)
        }
    }

    /**
     * Get steps data for a date range
     */
    suspend fun getStepsData(startDate: java.util.Date, endDate: java.util.Date): Result<List<StepsData>> = withContext(Dispatchers.IO) {
        try {
            val startDateString = DateUtils.formatForApi(startDate)
            val endDateString = DateUtils.formatForApi(endDate)
            val response = apiClient.fitbitApi.getSteps(startDateString, endDateString)
            
            if (response.isSuccessful && response.body() != null) {
                val apiResponse = response.body()!!
                val stepsDataList = mapStepsResponse(apiResponse)
                Result.success(stepsDataList)
            } else {
                Result.failure(Exception("Failed to fetch steps data: ${response.code()}"))
            }
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
            android.util.Log.d("HealthRepo", "Loading activity data for: $dateString (forceRefresh=$forceRefresh)")
            
            // Check cache first (unless forceRefresh)
            if (!forceRefresh) {
                val cached = activityDataDao.getByDate(dateString)
                if (cached != null && System.currentTimeMillis() - cached.timestamp < CACHE_TTL_MS) {
                    android.util.Log.d("HealthRepo", "Using cached activity data")
                    val data = com.google.gson.Gson().fromJson(cached.data, ActivityData::class.java)
                    return@withContext Result.success(data)
                }
            }
            
            // Fetch from API
            android.util.Log.d("HealthRepo", "Fetching activity data from API")
            val response = apiClient.fitbitApi.getActivities(dateString)
            
            if (response.isSuccessful && response.body() != null) {
                val apiResponse = response.body()!!
                val activityData = mapActivityResponse(apiResponse, date) // Keep original signature for mapActivityResponse
                
                // Cache the result
                if (activityData != null) {
                    val json = com.google.gson.Gson().toJson(activityData)
                    activityDataDao.insert(
                        com.cardio.fitbit.data.local.entities.ActivityDataEntity(
                            date = dateString,
                            data = json,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
                
                Result.success(activityData)
            } else {
                Result.failure(Exception("Failed to fetch activity data: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get user profile
     */
    suspend fun getUserProfile(): Result<UserProfile?> = withContext(Dispatchers.IO) {
        try {
            val response = apiClient.fitbitApi.getUserProfile()
            
            if (response.isSuccessful && response.body() != null) {
                val apiResponse = response.body()!!
                val userProfile = mapUserProfileResponse(apiResponse)
                Result.success(userProfile)
            } else {
                Result.failure(Exception("Failed to fetch user profile: ${response.code()}"))
            }
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
            android.util.Log.d("HealthRepo", "Fetching intraday data for: $dateString (forceRefresh=$forceRefresh)")
            
            // Check cache first (unless forceRefresh)
            if (!forceRefresh) {
                val cached = intradayDataDao.getByDate(dateString)
                if (cached != null && System.currentTimeMillis() - cached.timestamp < CACHE_TTL_MS) {
                    android.util.Log.d("HealthRepo", "Using cached intraday data")
                    val data = com.google.gson.Gson().fromJson(cached.data, IntradayData::class.java)
                    return@withContext Result.success(data)
                }
            }
            
            // Fetch from API
            android.util.Log.d("HealthRepo", "Fetching intraday data from API")
            val hrResponse = apiClient.fitbitApi.getIntradayHeartRate(dateString)
            val stepsResponse = apiClient.fitbitApi.getIntradaySteps(dateString)
            
            android.util.Log.d("HealthRepo", "HR Response: ${hrResponse.code()}, Steps Response: ${stepsResponse.code()}")
            
            if (!hrResponse.isSuccessful || !stepsResponse.isSuccessful) {
                val hrError = hrResponse.errorBody()?.string() ?: "Unknown error"
                val stepsError = stepsResponse.errorBody()?.string() ?: "Unknown error"
                android.util.Log.e("HealthRepo", "Intraday fetch failed - HR: $hrError, Steps: $stepsError")
                return@withContext Result.failure(Exception("Failed to fetch intraday data"))
            }
            
            val hrData = hrResponse.body()?.intradayData?.dataset ?: emptyList()
            val stepsData = stepsResponse.body()?.intradayData?.dataset ?: emptyList()
            
            android.util.Log.d("HealthRepo", "HR data points: ${hrData.size}, Steps data points: ${stepsData.size}")
            
            // Create a map of time -> steps for quick lookup
            val stepsMap = stepsData.associateBy { it.time }
            
            // Combine data
            val minuteData = hrData.map { hrPoint ->
                MinuteData(
                    time = hrPoint.time,
                    heartRate = hrPoint.value,
                    steps = stepsMap[hrPoint.time]?.value ?: 0
                )
            }
            
            val intradayData = IntradayData(date, minuteData)
            
            // Cache the result
            val json = com.google.gson.Gson().toJson(intradayData)
            intradayDataDao.insert(
                com.cardio.fitbit.data.local.entities.IntradayDataEntity(
                    date = dateString,
                    data = json,
                    timestamp = System.currentTimeMillis()
                )
            )
            
            android.util.Log.d("HealthRepo", "Combined minute data points: ${minuteData.size}")
            Result.success(intradayData)
        } catch (e: Exception) {
            android.util.Log.e("HealthRepo", "Exception fetching intraday data", e)
            Result.failure(e)
        }
    }

    // Mapping functions
    private fun mapHeartRateResponse(response: HeartRateResponse): HeartRateData? {
        if (response.activitiesHeart.isEmpty()) return null
        
        val heartRateDay = response.activitiesHeart[0]
        val date = DateUtils.parseApiDate(heartRateDay.dateTime) ?: return null
        
        val zones = heartRateDay.value.heartRateZones.map { zone ->
            HeartRateZone(
                name = zone.name,
                min = zone.min,
                max = zone.max,
                minutes = zone.minutes,
                caloriesOut = zone.caloriesOut
            )
        }
        
        val intradayData = response.intradayData?.dataset?.map { point ->
            IntradayHeartRate(
                time = point.time,
                value = point.value
            )
        }
        
        return HeartRateData(
            date = date,
            restingHeartRate = heartRateDay.value.restingHeartRate,
            heartRateZones = zones,
            intradayData = intradayData
        )
    }

    private fun mapSleepResponse(response: SleepResponse): SleepData? {
        if (response.sleep.isEmpty()) return null
        
        val sleepLog = response.sleep[0]
        val date = DateUtils.parseApiDate(sleepLog.dateOfSleep) ?: return null
        val startTime = DateUtils.parseApiDateTime(sleepLog.startTime) ?: date
        val endTime = DateUtils.parseApiDateTime(sleepLog.endTime) ?: date
        
        val stages = sleepLog.levels.summary?.let { summary ->
            SleepStages(
                deep = summary.deep?.minutes ?: 0,
                light = summary.light?.minutes ?: 0,
                rem = summary.rem?.minutes ?: 0,
                wake = summary.wake?.minutes ?: 0
            )
        }
        
        val levels = sleepLog.levels.data.mapNotNull { levelData ->
            val levelDate = DateUtils.parseApiDateTime(levelData.dateTime)
            if (levelDate != null) {
                SleepLevel(
                    dateTime = levelDate,
                    level = levelData.level,
                    seconds = levelData.seconds
                )
            } else null
        }
        
        return SleepData(
            date = date,
            duration = sleepLog.duration,
            efficiency = sleepLog.efficiency,
            startTime = startTime,
            endTime = endTime,
            minutesAsleep = sleepLog.minutesAsleep,
            minutesAwake = sleepLog.minutesAwake,
            stages = stages,
            levels = levels
        )
    }

    private fun mapStepsResponse(response: StepsResponse): List<StepsData> {
        return response.activitiesSteps.mapNotNull { stepsDay ->
            val date = DateUtils.parseApiDate(stepsDay.dateTime)
            if (date != null) {
                StepsData(
                    date = date,
                    steps = stepsDay.value.toIntOrNull() ?: 0,
                    distance = 0.0,
                    floors = 0,
                    caloriesOut = 0
                )
            } else null
        }
    }

    private fun mapActivityResponse(response: ActivityResponse, date: java.util.Date): ActivityData {
        val activities = response.activities.mapNotNull { activityLog ->
            val startTime = DateUtils.parseApiDateTime(activityLog.startTime)
            if (startTime != null) {
                Activity(
                    activityId = activityLog.activityId,
                    activityName = activityLog.activityName,
                    startTime = startTime,
                    duration = activityLog.duration,
                    calories = activityLog.calories,
                    distance = activityLog.distance,
                    steps = activityLog.steps,
                    averageHeartRate = activityLog.averageHeartRate
                )
            } else null
        }
        
        val totalDistance = response.summary.distances.find { it.activity == "total" }?.distance ?: 0.0
        val activeMinutes = response.summary.fairlyActiveMinutes + 
                           response.summary.lightlyActiveMinutes + 
                           response.summary.veryActiveMinutes
        
        val summary = ActivitySummary(
            steps = response.summary.steps,
            distance = totalDistance,
            floors = response.summary.floors,
            caloriesOut = response.summary.caloriesOut,
            activeMinutes = activeMinutes,
            sedentaryMinutes = response.summary.sedentaryMinutes
        )
        
        return ActivityData(
            date = date,
            activities = activities,
            summary = summary
        )
    }

    private fun mapUserProfileResponse(response: UserProfileResponse): UserProfile {
        return UserProfile(
            userId = response.user.encodedId,
            displayName = response.user.displayName,
            avatar = response.user.avatar,
            age = response.user.age,
            gender = response.user.gender,
            height = response.user.height,
            weight = response.user.weight
        )
    }
}
