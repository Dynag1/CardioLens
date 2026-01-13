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
    suspend fun getSleepData(date: java.util.Date, forceRefresh: Boolean = false): Result<List<SleepData>> = withContext(Dispatchers.IO) {
        try {
            val dateString = DateUtils.formatForApi(date)
            android.util.Log.d("HealthRepo", "Loading sleep data for: $dateString (forceRefresh=$forceRefresh)")
            
            // Check cache first (unless forceRefresh)
            if (!forceRefresh) {
                val cached = sleepDataDao.getByDate(dateString)
                if (cached != null && System.currentTimeMillis() - cached.timestamp < CACHE_TTL_MS) {
                    android.util.Log.d("HealthRepo", "Using cached sleep data")
                    val type = object : com.google.gson.reflect.TypeToken<List<SleepData>>() {}.type
                    val data = com.google.gson.Gson().fromJson<List<SleepData>>(cached.data, type)
                    return@withContext Result.success(data)
                }
            }
            
            // Fetch from API
            android.util.Log.d("HealthRepo", "Fetching sleep data from API")
            val response = apiClient.fitbitApi.getSleep(dateString)
            
            if (response.isSuccessful && response.body() != null) {
                val apiResponse = response.body()!!
                val sleepDataList = mapSleepResponse(apiResponse)
                
                // Cache the result
                if (sleepDataList.isNotEmpty()) {
                    val json = com.google.gson.Gson().toJson(sleepDataList)
                    sleepDataDao.insert(
                        com.cardio.fitbit.data.local.entities.SleepDataEntity(
                            date = dateString,
                            data = json,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
                
                android.util.Log.d("HealthRepo", "Sleep data loaded: ${sleepDataList.size} sessions")
                Result.success(sleepDataList)
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
            android.util.Log.d("HealthRepo", "Fetching activity data from API (Double Fetch)")
            
            // 1. Get Summary (Daily)
            val summaryRes = apiClient.fitbitApi.getActivities(dateString)
            
            if (summaryRes.isSuccessful && summaryRes.body() != null) {
                val summaryBody = summaryRes.body()!!
                var finalActivities = summaryBody.activities
                
                // 2. Get Logs (Detailed List to catch SmartTrack activities)
                try {
                    val cal = java.util.Calendar.getInstance()
                    val parsedDate = DateUtils.parseApiDate(dateString)
                    if (parsedDate != null) {
                        cal.time = parsedDate
                        cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
                        val nextDayString = DateUtils.formatForApi(cal.time)
                        
                        android.util.Log.d("HealthRepo", "Fetching activity logs for $dateString (beforeDate=$nextDayString)")
                        val logsRes = apiClient.fitbitApi.getActivityLogs(beforeDate = nextDayString, limit = 50)
                        
                        if (logsRes.isSuccessful && logsRes.body() != null) {
                            val logs = logsRes.body()!!.activities
                            android.util.Log.d("HealthRepo", "Fetched ${logs.size} detailed logs")
                            android.util.Log.d("HealthRepo", "Summary activities before merge: ${finalActivities.size}")
                            // Merge and deduplicate
                            finalActivities = (finalActivities + logs).distinctBy { it.activityId }
                            android.util.Log.d("HealthRepo", "Total activities after merge: ${finalActivities.size}")
                        } else {
                            android.util.Log.w("HealthRepo", "Failed to fetch activity logs: ${logsRes.code()}")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("HealthRepo", "Error fetching activity logs", e)
                }

                // Create synthetic response
                val syntheticResponse = ActivityResponse(
                    activities = finalActivities,
                    summary = summaryBody.summary
                )

                val activityData = mapActivityResponse(syntheticResponse, date) 
                
                // Cache the result
                val json = com.google.gson.Gson().toJson(activityData)
                activityDataDao.insert(
                    com.cardio.fitbit.data.local.entities.ActivityDataEntity(
                        date = dateString,
                        data = json,
                        timestamp = System.currentTimeMillis()
                    )
                )
                
                Result.success(activityData)
            } else {
                Result.failure(Exception("Failed to fetch activity data: ${summaryRes.code()}"))
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
            
            // Combine data: Ensure we include all minutes from both HR and Steps
            val allTimes = (hrData.map { it.time } + stepsData.map { it.time }).distinct()
            val hrMap = hrData.associateBy { it.time }
            val stepsMap = stepsData.associateBy { it.time }
            
            val minuteData = allTimes.map { time ->
                MinuteData(
                    time = time,
                    heartRate = hrMap[time]?.value ?: 0,
                    steps = stepsMap[time]?.value ?: 0
                )
            }.sortedBy { it.time }
            
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

    private fun mapSleepResponse(response: SleepResponse): List<SleepData> {
        if (response.sleep.isEmpty()) return emptyList()
        
        return response.sleep.mapNotNull { sleepLog ->
            val date = DateUtils.parseApiDate(sleepLog.dateOfSleep) ?: return@mapNotNull null
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
            
            SleepData(
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
        val rawList = response.activities
        val targetDateStr = DateUtils.formatForApi(date)
        
        android.util.Log.d("ActivityMap", "Mapping ${rawList.size} raw activities for $targetDateStr")

        val activities = rawList.mapNotNull { activityLog ->
            val startTime = DateUtils.parseFitbitTimeOrDateTime(activityLog.startTime, date)
            if (startTime == null) {
                android.util.Log.e("ActivityMap", "Failed to parse startTime: ${activityLog.startTime}")
                return@mapNotNull null
            }

            // Filter to ensure it's for the requested day
            val activityDateStr = DateUtils.formatForApi(startTime)
            val isSameDay = activityDateStr == targetDateStr
            
            if (isSameDay) {
                Activity(
                    activityId = activityLog.logId ?: activityLog.activityId, 
                    activityName = activityLog.activityName ?: activityLog.name ?: "Activité",
                    startTime = startTime,
                    duration = activityLog.duration,
                    calories = activityLog.calories,
                    distance = activityLog.distance ?: 0.0,
                    steps = activityLog.steps ?: 0,
                    averageHeartRate = activityLog.averageHeartRate ?: 0
                )
            } else {
                null
            }
        }.sortedBy { it.startTime }

        // Fuzzy Deduplication
        // Merge activities if they start within 2 minutes of each other and have similar names/types
        val distinctActivities = activities.fold(mutableListOf<Activity>()) { acc, activity ->
            if (acc.isEmpty()) {
                acc.add(activity)
            } else {
                val last = acc.last()
                val timeDiff = kotlin.math.abs(activity.startTime.time - last.startTime.time) // in ms
                val isSameTime = timeDiff < 2 * 60 * 1000 // < 2 minutes tolerance

                // Basic name check (can be improved)
                val sameName = activity.activityName == last.activityName || 
                               (activity.activityName.contains(last.activityName, ignoreCase = true)) ||
                               (last.activityName.contains(activity.activityName, ignoreCase = true))

                if (isSameTime && sameName) {
                    android.util.Log.d("ActivityMap", "Merging duplicate: ${activity.activityName} ($timeDiff ms diff)")
                    // Keep the one with the most data or "best" ID (Log ID > Activity ID)
                    // Here we assume the latter one in the list (sorted by time or source) might be better, 
                    // or strictly prefer the one that came from Logs (which we can't easily tell here without extra flags).
                    // Logic: Retain the one with the longer duration or just the first one found?
                    // Actually, let's keep the one that seems 'richer'. For now: keep the first ONE.
                } else {
                    acc.add(activity)
                }
            }
            acc
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
            activities = distinctActivities,
            summary = summary,
            debugInfo = ""
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
