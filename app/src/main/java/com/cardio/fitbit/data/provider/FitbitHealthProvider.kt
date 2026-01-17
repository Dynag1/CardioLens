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
    private val authManager: FitbitAuthManager
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

    override suspend fun getIntradayData(date: Date): Result<IntradayData?> {
         try {
            val dateString = DateUtils.formatForApi(date)
            val hrResponse = apiClient.fitbitApi.getIntradayHeartRate(dateString)
            val stepsResponse = apiClient.fitbitApi.getIntradaySteps(dateString)

            if (!hrResponse.isSuccessful) {
                if (hrResponse.code() == 429) {
                     return Result.failure(com.cardio.fitbit.data.api.RateLimitException("Limite d'API Fitbit atteinte.", 3600))
                }
                return Result.failure(Exception("Fitbit Intraday HR Error"))
            }

            // Steps parsing is less critical but check success
            
            val hrData = hrResponse.body()?.intradayData?.dataset ?: emptyList()
            val stepsData = stepsResponse.body()?.intradayData?.dataset ?: emptyList()

            val allTimes = (hrData.map { it.time } + stepsData.map { it.time }).distinct()
            val hrMap = hrData.associateBy { it.time }
            val stepsMap = stepsData.associateBy { it.time }

            val minuteData = allTimes.map { timeRaw ->
                // Normalize time to HH:mm (Fitbit returns HH:mm:ss)
                val time = if (timeRaw.length >= 5) timeRaw.substring(0, 5) else timeRaw
                
                MinuteData(
                    time = time,
                    heartRate = hrMap[timeRaw]?.value ?: 0,
                    steps = stepsMap[timeRaw]?.value ?: 0
                )
            }.sortedBy { it.time }

            return Result.success(IntradayData(date, minuteData))
        } catch (e: Exception) {
            return Result.failure(e)
        }
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
            // Endpoint: /1/user/-/hrv/date/[date]/[date].json or /1/user/-/hrv/date/[start]/[end].json
            // We need to add this to ApiService.
            
            // Wait, does ApiService have range support? 
            // Previous edit showed: getHrv(date) -> /1/user/-/hrv/date/{date}/all.json
            // We need: /1/user/-/hrv/date/{base-date}/{end-date}.json
            
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
