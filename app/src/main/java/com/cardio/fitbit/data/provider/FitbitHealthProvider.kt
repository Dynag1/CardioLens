package com.cardio.fitbit.data.provider

import com.cardio.fitbit.auth.FitbitAuthManager
import com.cardio.fitbit.data.api.ApiClient
import com.cardio.fitbit.data.models.*
import com.cardio.fitbit.utils.DateUtils
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FitbitHealthProvider @Inject constructor(
    private val apiClient: ApiClient,
    private val authManager: FitbitAuthManager,
    private val activityDetailsDao: com.cardio.fitbit.data.local.dao.ActivityDetailsDao,
    private val gson: com.google.gson.Gson
) : HealthDataProvider {

    override val providerId = "FITBIT"

    override suspend fun isAuthorized(): Boolean {
        return authManager.isAuthenticated()
    }

    override suspend fun requestpermissions() {
        // Handled by FitbitAuthManager startAuthorization
        // This method might be unused for Fitbit as it uses a web flow
    }

    override suspend fun getHeartRateData(date: Date): Result<HeartRateData?> {
        try {
            val dateString = DateUtils.formatForApi(date)
            // Fitbit API logic moved from Repository
            val response = apiClient.fitbitApi.getHeartRate(dateString)
            if (response.isSuccessful && response.body() != null) {
                return Result.success(mapHeartRateResponse(response.body()!!))
            }
            if (response.code() == 429) {
                val retryAfter = response.headers()["Start-Retry-After"]?.toIntOrNull() ?: 3600
                return Result.failure(com.cardio.fitbit.data.api.RateLimitException("Limite d'API Fitbit atteinte.", retryAfter))
            }
            return Result.failure(Exception("Fitbit HR Error: ${response.code()}"))
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    override suspend fun getIntradayData(date: Date, forceRefresh: Boolean): Result<IntradayData?> {
         try {
            val dateString = DateUtils.formatForApi(date)
            
            // 1. Fetch Activities to identify high-precision windows
            var activities: List<Activity> = emptyList()
            val activityResult = getActivityData(date)
            if (activityResult.isSuccess) {
                activities = activityResult.getOrNull()?.activities ?: emptyList()
            }

            // 2. Fetch Base 1-minute Heart Rate (Whole Day)
            // Use standard 1min endpoint
            val baseHrResponse = apiClient.fitbitApi.getIntradayHeartRate(dateString)
            
            if (!baseHrResponse.isSuccessful) {
                if (baseHrResponse.code() == 429) {
                     return Result.failure(com.cardio.fitbit.data.api.RateLimitException("Limite d'API Fitbit atteinte.", 3600))
                }
                return Result.failure(Exception("Fitbit Intraday HR Error: ${baseHrResponse.code()}"))
            }

            val baseHrData = baseHrResponse.body()?.intradayData?.dataset ?: emptyList()
            
            // 3. Fetch High Precision (1sec) for Activity Periods
            val highPrecisionData = mutableListOf<IntradayHeartRate>()
            
            // Limit to avoid API Rate limits if too many activities? 
            // Usually user has 1-2 workouts.
            activities.forEach { activity ->
                 // Logic to confirm it's a "workout" -> maybe based on type or duration?
                 // For now, take all recorded activities as workouts worth zooming in.
                 
                 // Fitbit API requires HH:mm format for start and end time.
                 // To ensure we capture the entire activity (which might start at :30s),
                 // we round the Start Time DOWN to the nearest minute,
                 // and the End Time UP to the nearest minute.
                 
                 // 1. Calculate Start Time (Rounded Down)
                 val startCal = java.util.Calendar.getInstance()
                 startCal.time = activity.startTime
                 startCal.set(java.util.Calendar.SECOND, 0)
                 startCal.set(java.util.Calendar.MILLISECOND, 0)
                 val startTimeStr = DateUtils.formatTimeForDisplay(startCal.time) // HH:mm

                 // 2. Calculate End Time (Rounded Up)
                 // Start with exact end time
                 val maxDuration = 4 * 60 * 60 * 1000L
                 val effectiveDuration = if (activity.duration > maxDuration) maxDuration else activity.duration
                 val activityEnd = Date(activity.startTime.time + effectiveDuration)
                 
                 val endCal = java.util.Calendar.getInstance()
                 endCal.time = activityEnd
                 
                 // If we have any seconds/millis, bump to next minute to cover them
                 if (endCal.get(java.util.Calendar.SECOND) > 0 || endCal.get(java.util.Calendar.MILLISECOND) > 0) {
                     endCal.add(java.util.Calendar.MINUTE, 1)
                 }
                 endCal.set(java.util.Calendar.SECOND, 0)
                 endCal.set(java.util.Calendar.MILLISECOND, 0)

                 // 3. Handle End of Day Boundary
                 val endOfDay = DateUtils.getEndOfDay(date)
                 
                 // If the rounded-up time goes into next day (or is after end of today), clamp to 23:59
                 // Fitbit API usually accepts 23:59 as the last minute.
                 val endTimeStr = if (endCal.time.after(endOfDay) || (endCal.get(java.util.Calendar.HOUR_OF_DAY) == 0 && endCal.get(java.util.Calendar.MINUTE) == 0)) {
                     "23:59"
                 } else {
                     DateUtils.formatTimeForDisplay(endCal.time)
                 }
                 
                 android.util.Log.d("FitbitHealthProvider", "Processing Activity: ${activity.activityName} ($startTimeStr - $endTimeStr) ID: ${activity.activityId}")

                 // Avoid tiny activities or 0 duration
                 // Also avoid invalid range (start >= end) which causes 400
                 // Use 1 min minimum to be safe
                 if (activity.duration > 60000 && startTimeStr != endTimeStr) { 
                     try {
                         // CACHE CHECK
                         val activityId = activity.activityId.toString()
                         // Check cache first (Suspend function)
                         var cached: com.cardio.fitbit.data.local.entities.ActivityDetailsEntity? = null
                         
                         if (!forceRefresh) {
                             cached = activityDetailsDao.getActivityDetails(activityId)
                         }
                         
                         if (cached != null) {
                              val type = object : com.google.gson.reflect.TypeToken<List<IntradayHeartRate>>() {}.type
                              val cachedList: List<IntradayHeartRate> = gson.fromJson(cached.data, type)
                              highPrecisionData.addAll(cachedList)
                              android.util.Log.d("FitbitHealthProvider", "Precision HR loaded from CACHE: ${cachedList.size} points for $activityId")
                         } else {
                              android.util.Log.d("FitbitHealthProvider", "Fetching Precision HR API for $activityId (Force: $forceRefresh)")
                              
                              // Add explicit delay to be gentle with API and ensure sequence
                              if (activities.indexOf(activity) > 0) {
                                  kotlinx.coroutines.delay(500)
                              }

                              // FETCH FROM API
                              val activityHrResponse = apiClient.fitbitApi.getIntradayHeartRatePrecisionRange(
                                  date = dateString,
                                  startTime = startTimeStr,
                                  endTime = endTimeStr
                              )
                              if (activityHrResponse.isSuccessful) {
                                  val hpData = activityHrResponse.body()?.intradayData?.dataset?.map { IntradayHeartRate(it.time, it.value) } ?: emptyList()
                                  highPrecisionData.addAll(hpData)
                                  android.util.Log.d("FitbitHealthProvider", "Precision HR fetched: ${hpData.size} points for $startTimeStr-$endTimeStr")
                                  
                                  // CACHE WRITE
                                  if (hpData.isNotEmpty()) {
                                      val json = gson.toJson(hpData)
                                      val entity = com.cardio.fitbit.data.local.entities.ActivityDetailsEntity(
                                          activityId = activityId,
                                          data = json,
                                          timestamp = System.currentTimeMillis()
                                      )
                                      activityDetailsDao.insertActivityDetails(entity)
                                  }
                              } else {
                                  // Log error but continue
                                  if (activityHrResponse.code() == 403) {
                                      android.util.Log.w("FitbitHealthProvider", "Precision HR Forbidden (403) - App requires 'Personal' type.")
                                  } else {
                                      android.util.Log.e("FitbitHealthProvider", "Failed to fetch precision HR: ${activityHrResponse.code()} - $startTimeStr to $endTimeStr")
                                  }
                              }
                         }
                     } catch (e: Exception) {
                         android.util.Log.e("FitbitHealthProvider", "Error fetching precision HR", e)
                     }
                 } else {
                     android.util.Log.d("FitbitHealthProvider", "Skipping Activity: Too short or invalid range")
                 }
            }

            // 4. Fetch Steps (1min)
            val stepsResponse = apiClient.fitbitApi.getIntradaySteps(dateString)
            val stepsData = stepsResponse.body()?.intradayData?.dataset ?: emptyList()

            // 5. Merge Data Logic Split
            
            // Helper map to store Total Steps per Minute (HH:mm -> Steps)
            // We usethis to populate 'displaySteps' for high-precision seconds
            val stepsPerMinute = mutableMapOf<String, Int>()
            stepsData.forEach { pt ->
                // pt.time is "HH:mm:00" usually, strip to "HH:mm"
                val minuteKey = pt.time.substring(0, 5) 
                stepsPerMinute[minuteKey] = (stepsPerMinute[minuteKey] ?: 0) + pt.value.toInt()
            }

            // List A: Standard Minute Data (Pure)
            val standardMinuteList = mutableListOf<MinuteData>()
            baseHrData.forEach { pt ->
                // pt.time is "HH:mm:00" usually, strip to "HH:mm"
                val minuteKey = pt.time.substring(0, 5) 
                val displaySteps = stepsPerMinute[minuteKey] ?: 0
                
                standardMinuteList.add(MinuteData(pt.time, pt.value, 0, displaySteps))
            }
            
            // Inject Steps into Standard List
            // Note: baseHrData might miss some minutes where only steps exist
            val standardMap = standardMinuteList.associateBy { it.time }.toMutableMap()
            stepsData.forEach { pt ->
                val existing = standardMap[pt.time]
                val minuteKey = pt.time.substring(0, 5)
                val totalSteps = stepsPerMinute[minuteKey] ?: pt.value.toInt()
                
                if (existing != null) {
                    standardMap[pt.time] = existing.copy(steps = pt.value.toInt(), displaySteps = totalSteps)
                } else {
                    standardMap[pt.time] = MinuteData(pt.time, 0, pt.value.toInt(), totalSteps)
                }
            }
            val finalStandardList = standardMap.values.sortedBy { it.time }

            // List B: High Precision Data (Merged) - Used for preciseData
            // Use standard list as base, then overwrite with 1sec data
            val mergedMap = mutableMapOf<String, MinuteData>()
            
            // Populate with Standard first (providing the backdrop)
            finalStandardList.forEach { mergedMap[it.time] = it }
            
            // Overwrite/Add with High Precision Data
            highPrecisionData.forEach { pt ->
                // pt.time is HH:mm:ss
                val minuteKey = pt.time.substring(0, 5)
                val totalStepsForMinute = stepsPerMinute[minuteKey] ?: 0
                val existingSteps = mergedMap[pt.time]?.steps ?: 0
                
                // We don't have 1-sec steps, so we use 0 for specific second unless it aligns with a minute we already had info for
                // (though usually minute data steps are for the whole minute, attributing to :00 is standard approx)
                mergedMap[pt.time] = MinuteData(
                    time = pt.time, 
                    heartRate = pt.value, 
                    steps = existingSteps, 
                    displaySteps = totalStepsForMinute
                )
            }
            
            // Ensure we didn't lose step-only entries (already in standardList added to mergedMap)
            val finalPreciseList = mergedMap.values.sortedBy { it.time }

            return Result.success(IntradayData(date, finalStandardList, finalPreciseList))
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    override suspend fun getIntradayHistory(startDate: Date, endDate: Date): Result<List<IntradayData>> {
        val list = mutableListOf<IntradayData>()
        val cal = java.util.Calendar.getInstance()
        cal.time = startDate
        while (!cal.time.after(endDate)) {
            val result = getIntradayData(cal.time)
            if (result.isSuccess) {
                result.getOrNull()?.let { list.add(it) }
            }
            // Use try-catch or failure check? If one fails, do we fail all? Best effort is better for trends.
            cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        return Result.success(list)
    }

    override suspend fun getSleepData(date: Date): Result<List<SleepData>> {
         try {
            val dateString = DateUtils.formatForApi(date)
            val response = apiClient.fitbitApi.getSleep(dateString)
            if (response.isSuccessful && response.body() != null) {
                return Result.success(mapSleepResponse(response.body()!!))
            }
            return Result.failure(Exception("Fitbit Sleep Error: ${response.code()}"))
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    override suspend fun getStepsData(startDate: Date, endDate: Date): Result<List<StepsData>> {
        try {
            val startStr = DateUtils.formatForApi(startDate)
            val endStr = DateUtils.formatForApi(endDate)
            val response = apiClient.fitbitApi.getSteps(startStr, endStr)
            if (response.isSuccessful && response.body() != null) {
                return Result.success(mapStepsResponse(response.body()!!))
            }
             return Result.failure(Exception("Fitbit Steps Error"))
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    override suspend fun getActivityData(date: Date): Result<ActivityData?> {
         try {
            val dateString = DateUtils.formatForApi(date)
            val summaryRes = apiClient.fitbitApi.getActivities(dateString)
            
            if (summaryRes.isSuccessful && summaryRes.body() != null) {
                var finalActivities = summaryRes.body()!!.activities
                
                // Fetch detailed logs (optional but good for consistency with old repo)
                try {
                     val cal = java.util.Calendar.getInstance()
                     cal.time = date
                     cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
                     val nextDayString = DateUtils.formatForApi(cal.time)
                     
                     val logsRes = apiClient.fitbitApi.getActivityLogs(beforeDate = nextDayString, limit = 50)
                     if (logsRes.isSuccessful && logsRes.body() != null) {
                         finalActivities = (finalActivities + logsRes.body()!!.activities).distinctBy { it.activityId }
                     }
                } catch (e: Exception) {
                    // Ignore log fetch error
                }

                val syntheticResponse = ActivityResponse(
                    activities = finalActivities,
                    summary = summaryRes.body()!!.summary
                )
                return Result.success(mapActivityResponse(syntheticResponse, date))
            }
            return Result.failure(Exception("Fitbit Activity Error"))
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }



    override suspend fun getActivityHistory(startDate: Date, endDate: Date): Result<List<ActivityData>> {
        val list = mutableListOf<ActivityData>()
        val cal = java.util.Calendar.getInstance()
        cal.time = startDate
        while (!cal.time.after(endDate)) {
            val result = getActivityData(cal.time)
            if (result.isSuccess) {
                result.getOrNull()?.let { list.add(it) }
            }
            cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        return Result.success(list)
    }

    override suspend fun getUserProfile(): Result<UserProfile?> {
        try {
            val response = apiClient.fitbitApi.getUserProfile()
            if (response.isSuccessful && response.body() != null) {
                return Result.success(mapUserProfileResponse(response.body()!!))
            }
            return Result.failure(Exception("Fitbit Profile Error"))
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    override suspend fun getHeartRateHistory(startDate: Date, endDate: Date): Result<List<HeartRateData>> {
        try {
            val startStr = DateUtils.formatForApi(startDate)
            val endStr = DateUtils.formatForApi(endDate)
            val response = apiClient.fitbitApi.getHeartRateRange(startStr, endStr)
            
            if (response.isSuccessful && response.body() != null) {
                // Fitbit returns activities-heart list
                val list = response.body()!!.activitiesHeart.mapNotNull { daily ->
                    val date = DateUtils.parseApiDate(daily.dateTime) ?: return@mapNotNull null
                    
                    // Construct minimal HeartRateData (no intraday, just daily summary)
                    val zones = daily.value.heartRateZones.map { zone ->
                        HeartRateZone(zone.name, zone.min, zone.max, zone.minutes, zone.caloriesOut)
                    }
                    
                    HeartRateData(
                        date = date,
                        restingHeartRate = daily.value.restingHeartRate,
                        heartRateZones = zones,
                        intradayData = null
                    )
                }
                return Result.success(list)
            }
            return Result.failure(Exception("Fitbit HR History Error: ${response.code()}"))
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    override suspend fun getSleepHistory(startDate: Date, endDate: Date): Result<List<SleepData>> {
        try {
            val startStr = DateUtils.formatForApi(startDate)
            val endStr = DateUtils.formatForApi(endDate)
            val response = apiClient.fitbitApi.getSleepRange(startStr, endStr)
            
            if (response.isSuccessful && response.body() != null) {
                return Result.success(mapSleepResponse(response.body()!!))
            }
            return Result.failure(Exception("Fitbit Sleep History Error"))
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    override suspend fun getHeartRateSeries(startTime: Date, endTime: Date): Result<List<MinuteData>> {
        try {
            // Assume single day for now as per use case (22:00 to 23:59 on same day)
            // If spans across midnight, logic in Repository handles calling it twice or caller handles it.
            // DashboardVM calls it from 'earliestStart' to 'startOfDay' (midnight) -> Same day (Yesterday).
            
            val dateStr = DateUtils.formatForApi(startTime)
            val startTimeStr = DateUtils.formatTimeForDisplay(startTime) // HH:mm
            var endTimeStr = DateUtils.formatTimeForDisplay(endTime)     // HH:mm
            
            // Fix: If endTime is 00:00 (midnight of next day) and startTime is NOT 00:00,
            // Fitbit interprets 00:00 as start of the SAME day, causing "start > end" error.
            // We clamp it to 23:59 of the requested day.
            if (endTimeStr == "00:00" && startTimeStr != "00:00") {
                endTimeStr = "23:59"
            }
            
            val response = apiClient.fitbitApi.getIntradayHeartRateRange(dateStr, startTimeStr, endTimeStr)
            
            if (response.isSuccessful && response.body() != null) {
                // Map result
                val intraday = response.body()!!.intradayData?.dataset ?: emptyList()
                val mapped = intraday.map { 
                    MinuteData(it.time, it.value, 0)
                }
                return Result.success(mapped)
            }
            return Result.failure(Exception("Fitbit HR Series Error: ${response.code()}"))
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    override suspend fun getHrvData(date: Date): Result<List<HrvRecord>> {
        try {
            val dateString = DateUtils.formatForApi(date)
            // Fitbit Web API usually only returns *Daily* HRV summary via this endpoint -> One point per day.
            val response = apiClient.fitbitApi.getHrv(dateString)

            if (response.isSuccessful && response.body() != null) {
                val hrvData = response.body()!!.hrv
                val records = hrvData.map { log ->
                    // Set time to noon to represent the day, or start of day?
                    // Users want a graph. If we only have ONE point, the graph will be a flat line or single dot.
                    // But for "24h every 10 mins" request, Health Connect might provide it.
                    // Fitbit will just provide 1 point here.
                    val logDate = DateUtils.parseApiDate(log.dateTime) ?: date
                    HrvRecord(logDate, log.value.dailyRmssd)
                }
                return Result.success(records)
            }
            // 404 or empty often means no HRV device
            return Result.success(emptyList())

        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    override suspend fun getHrvHistory(startDate: Date, endDate: Date): Result<List<HrvRecord>> {
        try {
            val startStr = DateUtils.formatForApi(startDate)
            val endStr = DateUtils.formatForApi(endDate)
            
            val response = apiClient.fitbitApi.getHrvRange(startStr, endStr) 
            
            if (response.isSuccessful && response.body() != null) {
                val hrvData = response.body()!!.hrv
                val records = hrvData.map { log ->
                    val logDate = DateUtils.parseApiDate(log.dateTime) ?: startDate
                    HrvRecord(logDate, log.value.dailyRmssd)
                }.sortedBy { it.time }
                return Result.success(records)
            }
             return Result.success(emptyList()) // Fail soft
        } catch (e: Exception) {
             return Result.failure(e)
        }
    }

    override suspend fun getSpO2Data(date: Date): Result<SpO2Data?> {
        try {
            val dateString = DateUtils.formatForApi(date)
            // Fitbit SpO2 daily summary
            val response = apiClient.fitbitApi.getSpO2(dateString)

            if (response.isSuccessful && response.body() != null) {
                // Usually returns a list, effectively one item per day if requesting single day
                val logs = response.body()!!.spo2
                val log = logs.find { it.dateTime == dateString } ?: logs.firstOrNull() ?: return Result.success(null)
                
                val spO2Data = SpO2Data(
                    date = date,
                    avg = log.value.avg,
                    min = log.value.min,
                    max = log.value.max
                )
                return Result.success(spO2Data)
            }
            if (response.code() == 429) {
                 return Result.failure(com.cardio.fitbit.data.api.RateLimitException("Limite d'API Fitbit atteinte.", 3600))
            }
            return Result.success(null) // Soft fail if not found (e.g. device doesn't support it)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    override suspend fun getSpO2History(startDate: Date, endDate: Date): Result<List<SpO2Data>> {
         try {
            val startStr = DateUtils.formatForApi(startDate)
            val endStr = DateUtils.formatForApi(endDate)
            
            val response = apiClient.fitbitApi.getSpO2Range(startStr, endStr)
            
            if (response.isSuccessful && response.body() != null) {
                val logs = response.body()!!.spo2
                val data = logs.mapNotNull { log ->
                     DateUtils.parseApiDate(log.dateTime)?.let { date ->
                         SpO2Data(
                             date = date,
                             avg = log.value.avg,
                             min = log.value.min,
                             max = log.value.max
                         )
                     }
                }.sortedBy { it.date }
                return Result.success(data)
            }
            return Result.success(emptyList())
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    // ================= MAPPING HELPER FUNCTIONS (Copied from HealthRepository) =================
    
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
            IntradayHeartRate(time = point.time, value = point.value)
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
                DateUtils.parseApiDateTime(levelData.dateTime)?.let { levelDate ->
                    SleepLevel(dateTime = levelDate, level = levelData.level, seconds = levelData.seconds)
                }
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
            DateUtils.parseApiDate(stepsDay.dateTime)?.let { date ->
                StepsData(
                    date = date,
                    steps = stepsDay.value.toIntOrNull() ?: 0,
                    distance = 0.0, floors = 0, caloriesOut = 0
                )
            }
        }
    }

     private fun mapActivityResponse(response: ActivityResponse, date: java.util.Date): ActivityData {
        val rawList = response.activities
        val targetDateStr = DateUtils.formatForApi(date)
        
        val activities = rawList.mapNotNull { activityLog ->
            val startTime = DateUtils.parseFitbitTimeOrDateTime(activityLog.startTime, date) ?: return@mapNotNull null
            val activityDateStr = DateUtils.formatForApi(startTime)
            
            if (activityDateStr == targetDateStr) {
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
            } else null
        }.sortedBy { it.startTime }

        val distinctActivities = activities.fold(mutableListOf<Activity>()) { acc, activity ->
            if (acc.isEmpty()) acc.add(activity)
            else {
                val last = acc.last()
                val timeDiff = kotlin.math.abs(activity.startTime.time - last.startTime.time)
                val isSameTime = timeDiff < 2 * 60 * 1000
                val sameName = activity.activityName.contains(last.activityName, true) || last.activityName.contains(activity.activityName, true)
                if (!isSameTime || !sameName) acc.add(activity)
            }
            acc
        }
        
        val totalDistance = response.summary.distances.find { it.activity == "total" }?.distance ?: 0.0
        val activeMinutes = response.summary.fairlyActiveMinutes + response.summary.lightlyActiveMinutes + response.summary.veryActiveMinutes
        
        val summary = ActivitySummary(
            steps = response.summary.steps,
            distance = totalDistance,
            floors = response.summary.floors,
            caloriesOut = response.summary.caloriesOut,
            activeMinutes = activeMinutes,
            sedentaryMinutes = response.summary.sedentaryMinutes
        )

        return ActivityData(date = date, activities = distinctActivities, summary = summary, debugInfo = "")
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
