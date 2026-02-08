package com.cardio.fitbit.data.provider

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.cardio.fitbit.data.models.*
import com.cardio.fitbit.utils.DateUtils
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthConnectProvider @Inject constructor(
    private val healthConnectClient: HealthConnectClient
) : HealthDataProvider {

    override val providerId: String = "health_connect"

    companion object {
        val PERMISSIONS = setOf(
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(DistanceRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
            HealthPermission.getReadPermission(OxygenSaturationRecord::class)
        )
    }

    override suspend fun getHeartRateData(date: Date): Result<HeartRateData?> {
        try {
            val startOfDay = DateUtils.getStartOfDay(date)
            val endOfDay = DateUtils.getEndOfDay(date)
            

            
            // Paginate to get all records
            val allRecords = mutableListOf<HeartRateRecord>()
            var pageToken: String? = null
            var pageCount = 0
            
            do {
                pageCount++
                val response = healthConnectClient.readRecords(
                    ReadRecordsRequest(
                        HeartRateRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(
                            startOfDay.toInstant(),
                            endOfDay.toInstant()
                        ),
                        pageSize = 5000,
                        pageToken = pageToken
                    )
                )
                
                allRecords.addAll(response.records)
                pageToken = response.pageToken
            } while (pageToken != null)
            


            if (allRecords.isEmpty()) {
                return Result.success(null)
            }

            // Collect all heart rate samples
            val allSamples = allRecords.flatMap { record ->
                record.samples.map { sample ->
                    Pair(sample.time, sample.beatsPerMinute.toLong())
                }
            }.sortedBy { it.first }

            // Calculate zones and resting HR
            val heartRates = allSamples.map { it.second }
            val restingHr = heartRates.minOrNull()?.toInt()

            // Simple zone calculation
            val zonesMap = mutableMapOf(
                "Out of Range" to 0,
                "Fat Burn" to 0,
                "Cardio" to 0,
                "Peak" to 0
            )

            heartRates.forEach { bpm ->
                when {
                    bpm < 100 -> zonesMap["Out of Range"] = zonesMap["Out of Range"]!! + 1
                    bpm < 140 -> zonesMap["Fat Burn"] = zonesMap["Fat Burn"]!! + 1
                    bpm < 170 -> zonesMap["Cardio"] = zonesMap["Cardio"]!! + 1
                    else -> zonesMap["Peak"] = zonesMap["Peak"]!! + 1
                }
            }

            val zones = listOf(
                HeartRateZone("Out of Range", 30, 100, zonesMap["Out of Range"] ?: 0, 0.0),
                HeartRateZone("Fat Burn", 100, 140, zonesMap["Fat Burn"] ?: 0, 0.0),
                HeartRateZone("Cardio", 140, 170, zonesMap["Cardio"] ?: 0, 0.0),
                HeartRateZone("Peak", 170, 220, zonesMap["Peak"] ?: 0, 0.0)
            )

            // Convert samples to IntradayHeartRate with HH:mm format
            val intradayData = allSamples.map { (instant, bpm) ->
                val zonedTime = instant.atZone(ZoneId.systemDefault())
                val timeStr = String.format("%02d:%02d", 
                    zonedTime.hour, 
                    zonedTime.minute
                )
                IntradayHeartRate(time = timeStr, value = bpm.toInt())
            }

            return Result.success(
                HeartRateData(
                    date = date,
                    restingHeartRate = restingHr,
                    heartRateZones = zones,
                    intradayData = intradayData
                )
            )
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    override suspend fun getIntradayData(date: Date, forceRefresh: Boolean): Result<IntradayData?> {
        try {
            val startOfDay = DateUtils.getStartOfDay(date)
            val endOfDay = DateUtils.getEndOfDay(date)
            
            // 1. Fetch Exercise Sessions to identify high-precision windows (Paginated)
            val exercises = mutableListOf<ExerciseSessionRecord>()
            var exPageToken: String? = null
            do {
                val exerciseResponse = healthConnectClient.readRecords(
                    ReadRecordsRequest(
                        ExerciseSessionRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(
                            startOfDay.toInstant(),
                            endOfDay.toInstant()
                        ),
                        pageToken = exPageToken
                    )
                )
                exercises.addAll(exerciseResponse.records)
                exPageToken = exerciseResponse.pageToken
            } while (exPageToken != null)
            

            // 2. Paginate Heart Rate Records
            val allHrRecords = mutableListOf<HeartRateRecord>()
            var hrPageToken: String? = null
            do {
                val response = healthConnectClient.readRecords(
                    ReadRecordsRequest(
                        HeartRateRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(
                            startOfDay.toInstant(),
                            endOfDay.toInstant()
                        ),
                        pageSize = 5000,
                        pageToken = hrPageToken
                    )
                )
                allHrRecords.addAll(response.records)
                hrPageToken = response.pageToken
            } while (hrPageToken != null)


            // 3. Paginate Steps Records
            val allStepsRecords = mutableListOf<StepsRecord>()
            var stepsPageToken: String? = null
            do {
                val response = healthConnectClient.readRecords(
                    ReadRecordsRequest(
                        StepsRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(
                            startOfDay.toInstant(),
                            endOfDay.toInstant()
                        ),
                        pageSize = 5000,
                        pageToken = stepsPageToken
                    )
                )
                allStepsRecords.addAll(response.records)
                stepsPageToken = response.pageToken
            } while (stepsPageToken != null)


            val minuteDataMap = mutableMapOf<String, MinuteData>()
            // Helper to check if a time is inside an exercise
            fun isDuringExercise(time: java.time.Instant): Boolean {
                return exercises.any { ex -> 
                    !time.isBefore(ex.startTime) && !time.isAfter(ex.endTime)
                }
            }

            // Buckets for NON-exercise minutes to average them
            val pendingAverages = mutableMapOf<String, MutableList<Int>>()

            allHrRecords.forEach { record ->
                record.samples.forEach { sample ->
                    if (isDuringExercise(sample.time)) {
                        // High Precision: Use exact time
                        val time = sample.time.atZone(ZoneId.systemDefault())
                        val timeKey = String.format("%02d:%02d:%02d", time.hour, time.minute, time.second)
                        
                        // If collision (multiple samples same second), overwrite/keep last
                        val existing = minuteDataMap[timeKey]
                        minuteDataMap[timeKey] = existing?.copy(heartRate = sample.beatsPerMinute.toInt()) 
                            ?: MinuteData(timeKey, sample.beatsPerMinute.toInt(), 0)
                    } else {
                        // Low Precision: Aggregate to Minute
                        val time = sample.time.atZone(ZoneId.systemDefault())
                        val timeKey = String.format("%02d:%02d:00", time.hour, time.minute) // Force 00s
                        
                        pendingAverages.getOrPut(timeKey) { mutableListOf() }.add(sample.beatsPerMinute.toInt())
                    }
                }
            }

            // Process Averages
            pendingAverages.forEach { (key, values) ->
                val avg = values.average().toInt()
                val existing = minuteDataMap[key]
                // Only write if not overwriting high precision (though keys differ by seconds usually)
                // If key is HH:mm:00, it might collide with a high precision sample at exactly 00s.
                // We prioritize High Precision if exists.
                if (existing == null) {
                    minuteDataMap[key] = MinuteData(key, avg, 0)
                }
            }

            // Calculate Total Steps per Minute for Display
            val stepsPerMinute = mutableMapOf<String, Int>()
            allStepsRecords.forEach { record ->
                val time = record.startTime.atZone(ZoneId.systemDefault())
                val minuteKey = String.format("%02d:%02d", time.hour, time.minute)
                stepsPerMinute[minuteKey] = (stepsPerMinute[minuteKey] ?: 0) + record.count.toInt()
            }

            allStepsRecords.forEach { record ->
                val time = record.startTime.atZone(ZoneId.systemDefault())
                val timeKey = String.format("%02d:%02d:00", time.hour, time.minute)
                val minuteKey = String.format("%02d:%02d", time.hour, time.minute)
                val minuteTotal = stepsPerMinute[minuteKey] ?: 0
                
                val existing = minuteDataMap[timeKey] ?: MinuteData(timeKey, 0, 0, minuteTotal)
                minuteDataMap[timeKey] = existing.copy(
                    steps = existing.steps + record.count.toInt(),
                    displaySteps = minuteTotal
                )
            }
            
            // Backfill displaySteps for HR entries that might have been created before Steps (or purely HR)
            // This ensures "0 steps" entries still get the "Minute Total" for display if steps exist for that minute
            minuteDataMap.keys.toList().forEach { key ->
                 val data = minuteDataMap[key] ?: return@forEach
                 if (data.displaySteps == 0) {
                     val minuteKey = key.substring(0, 5) // HH:mm
                     val total = stepsPerMinute[minuteKey] ?: 0
                     if (total > 0) {
                         minuteDataMap[key] = data.copy(displaySteps = total)
                     }
                 }
            }

            val minuteDataList = minuteDataMap.values.sortedBy { it.time }
            
            return Result.success(IntradayData(date, minuteDataList, null))
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
    override suspend fun getIntradayHistory(startDate: Date, endDate: Date): Result<List<IntradayData>> {
        try {
            // 1. Fetch ALL HR records for range
            // Expand search start to capture previous night's sleep heart rates (same as getSleepHistory)
            val searchStart = Date(startDate.time - (24 * 60 * 60 * 1000))
            
            val allHrRecords = mutableListOf<HeartRateRecord>()
            var hrPageToken: String? = null
            do {
                val response = healthConnectClient.readRecords(
                    ReadRecordsRequest(
                        HeartRateRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(searchStart.toInstant(), endDate.toInstant()),
                        pageSize = 5000,
                        pageToken = hrPageToken
                    )
                )
                allHrRecords.addAll(response.records)
                hrPageToken = response.pageToken
            } while (hrPageToken != null)

            // 2. Fetch ALL Steps records for range
            val allStepsRecords = mutableListOf<StepsRecord>()
            var stepsPageToken: String? = null
            do {
                val response = healthConnectClient.readRecords(
                    ReadRecordsRequest(
                        StepsRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(startDate.toInstant(), endDate.toInstant()),
                        pageSize = 5000,
                        pageToken = stepsPageToken
                    )
                )
                allStepsRecords.addAll(response.records)
                stepsPageToken = response.pageToken
            } while (stepsPageToken != null)

            // 3. Group by Day
            val hrByDay = allHrRecords.groupBy { DateUtils.getStartOfDay(Date.from(it.startTime)) }
            val stepsByDay = allStepsRecords.groupBy { DateUtils.getStartOfDay(Date.from(it.startTime)) }

            // 4. Construct Result List
            val resultList = mutableListOf<IntradayData>()
            val cal = java.util.Calendar.getInstance()
            cal.time = startDate
            while (!cal.time.after(endDate)) {
                val currentDay = DateUtils.getStartOfDay(cal.time)
                
                // Merge for this day (Logic same as getIntradayData)
                val dayHr = hrByDay[currentDay] ?: emptyList()
                val daySteps = stepsByDay[currentDay] ?: emptyList()
                
                // AGGREGATION FIX: Bucket by MINUTE to align Steps with HR
                val minuteDataMap = mutableMapOf<String, MinuteData>()
                
                // Temporary storage for averaging HR within a minute
                val hrBuckets = mutableMapOf<String, MutableList<Int>>()

                dayHr.forEach { record ->
                    record.samples.forEach { sample ->
                        val time = sample.time.atZone(ZoneId.systemDefault())
                        val timeKey = String.format("%02d:%02d:00", time.hour, time.minute) // Force 00 seconds
                        
                        hrBuckets.getOrPut(timeKey) { mutableListOf() }.add(sample.beatsPerMinute.toInt())
                    }
                }
                
                // Populate MinuteData from HR averages
                hrBuckets.forEach { (key, values) ->
                    val avgHr = kotlin.math.round(values.average()).toInt()
                    minuteDataMap[key] = MinuteData(key, avgHr, 0)
                }

                daySteps.forEach { record ->
                    val time = record.startTime.atZone(ZoneId.systemDefault())
                    val timeKey = String.format("%02d:%02d:00", time.hour, time.minute) // Force 00 seconds
                    
                    val existing = minuteDataMap[timeKey] ?: MinuteData(timeKey, 0, 0)
                    minuteDataMap[timeKey] = existing.copy(steps = existing.steps + record.count.toInt())
                }
                
                val minuteDataList = minuteDataMap.values.sortedBy { it.time }
                resultList.add(IntradayData(currentDay, minuteDataList, null))
                
                cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
            
            return Result.success(resultList)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    override suspend fun getStepsData(startDate: Date, endDate: Date): Result<List<StepsData>> {
        try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        startDate.toInstant(),
                        endDate.toInstant()
                    )
                )
            )

            val dailySteps = mutableMapOf<Date, Long>()
            response.records.forEach { record ->
                val day = Date.from(record.startTime.atZone(ZoneId.systemDefault()).toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant())
                dailySteps[day] = (dailySteps[day] ?: 0) + record.count
            }

            return Result.success(
                dailySteps.map { (date, steps) ->
                    StepsData(date, steps.toInt(), 0.0, 0, 0)
                }
            )
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    override suspend fun getSleepData(date: Date): Result<List<SleepData>> {
        try {
            // Sleep sessions often span midnight (e.g., 23:00 -> 07:00)
            // So we need to search from previous day evening to current day end
            val startOfDay = DateUtils.getStartOfDay(date)
            val endOfDay = DateUtils.getEndOfDay(date)
            
            // Search from 12 hours before the day starts (to catch evening sleep)
            val searchStart = Date(startOfDay.time - (12 * 60 * 60 * 1000))
            

            
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        searchStart.toInstant(),
                        endOfDay.toInstant()
                    )
                )
            )
            
            // Filter to only include sessions that overlap with the target date
            val filteredRecords = response.records.filter { record ->
                val sessionStart = record.startTime.toEpochMilli()
                val sessionEnd = record.endTime.toEpochMilli()
                val dayStart = startOfDay.time
                val dayEnd = endOfDay.time
                
                // Include if session overlaps with target day
                sessionEnd > dayStart && sessionStart < dayEnd
            }

            return Result.success(
                filteredRecords.map { record ->
                    val durationMs = record.endTime.toEpochMilli() - record.startTime.toEpochMilli()
                    val stages = record.stages
                    val levelsList = stages.map { stage ->
                        SleepLevel(
                            dateTime = Date.from(stage.startTime),
                            level = when (stage.stage) {
                                SleepSessionRecord.STAGE_TYPE_AWAKE -> "wake"
                                SleepSessionRecord.STAGE_TYPE_LIGHT -> "light"
                                SleepSessionRecord.STAGE_TYPE_DEEP -> "deep"
                                SleepSessionRecord.STAGE_TYPE_REM -> "rem"
                                SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> "wake" // Treat out of bed as wake
                                else -> "unknown"
                            },
                            seconds = (stage.endTime.toEpochMilli() - stage.startTime.toEpochMilli()).toInt() / 1000
                        )
                    }

                    SleepData(
                        date = date,
                        duration = durationMs,
                        efficiency = 90,
                        startTime = Date.from(record.startTime),
                        endTime = Date.from(record.endTime),
                        minutesAsleep = (durationMs / 60000).toInt(),
                        minutesAwake = levelsList.filter { it.level == "wake" }.sumOf { it.seconds } / 60,
                        stages = SleepStages(
                            deep = levelsList.filter { it.level == "deep" }.sumOf { it.seconds } / 60,
                            light = levelsList.filter { it.level == "light" }.sumOf { it.seconds } / 60,
                            rem = levelsList.filter { it.level == "rem" }.sumOf { it.seconds } / 60,
                            wake = levelsList.filter { it.level == "wake" }.sumOf { it.seconds } / 60
                        ),
                        levels = levelsList
                    )
                }
            )
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    override suspend fun getActivityData(date: Date): Result<ActivityData?> {
        try {
            val startOfDay = DateUtils.getStartOfDay(date)
            val endOfDay = DateUtils.getEndOfDay(date)
            
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        startOfDay.toInstant(),
                        endOfDay.toInstant()
                    )
                )
            )

            // Maps records to Activity objects
            val rawActivities = response.records.map { record ->
                val durationMs = record.endTime.toEpochMilli() - record.startTime.toEpochMilli()
                
                // Aggregate metrics for this specific session
                var calories = 0.0
                var steps = 0
                var distance = 0.0

                try {
                     val aggregateResponse = healthConnectClient.aggregate(
                        androidx.health.connect.client.request.AggregateRequest(
                            metrics = setOf(
                                ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                                TotalCaloriesBurnedRecord.ENERGY_TOTAL,
                                StepsRecord.COUNT_TOTAL,
                                DistanceRecord.DISTANCE_TOTAL
                            ),
                            timeRangeFilter = TimeRangeFilter.between(
                                record.startTime,
                                record.endTime
                            )
                        )
                    )
                    val active = aggregateResponse[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0
                    val total = aggregateResponse[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories ?: 0.0
                    calories = if (active > 0.1) active else total

                    steps = aggregateResponse[StepsRecord.COUNT_TOTAL]?.toInt() ?: 0
                    distance = aggregateResponse[DistanceRecord.DISTANCE_TOTAL]?.inKilometers ?: 0.0
                } catch (e: Exception) {
                    // Ignore aggregation errors
                }

                val title = record.title ?: when(record.exerciseType) {
                    ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Marche" 
                    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "Course"
                    ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "Vélo"
                    else -> "Exercice"
                }

                Activity(
                    activityId = record.startTime.toEpochMilli(),
                    activityName = title,
                    startTime = Date.from(record.startTime),
                    duration = durationMs,
                    calories = calories.toInt(),
                    distance = if (distance > 0.001) distance else null,
                    steps = if (steps > 0) steps else null,
                    averageHeartRate = null 
                )
            }

            // --- Deduplication Logic ---
            // Group by approximate start time or handle overlaps
            val uniqueActivities = mutableListOf<Activity>()
            val sortedActivities = rawActivities.sortedBy { it.startTime }

            for (activity in sortedActivities) {
                // Check if this activity significantly overlaps with an existing one
                val duplicateIndex = uniqueActivities.indexOfFirst { existing ->
                    val overlap = calculateOverlap(existing, activity)
                    val minDuration = kotlin.math.min(existing.duration, activity.duration)
                    // If overlap is > 70% of the shorter duration, allow it to replace or ignore
                    overlap > (minDuration * 0.7)
                }

                if (duplicateIndex != -1) {
                    val existing = uniqueActivities[duplicateIndex]
                    // Keep the "better" one (e.g. has steps/distance or manually titled)
                    if (isBetterActivity(activity, existing)) {
                        uniqueActivities[duplicateIndex] = activity
                    }
                    // Else ignore 'activity'
                } else {
                    uniqueActivities.add(activity)
                }
            }


            // Calculate Summary from Sessions
            val totalCaloriesFromSessions = uniqueActivities.sumOf { it.calories }
            val totalActiveMinutes = uniqueActivities.sumOf { (it.duration / 60000).toInt() }

            // Fetch Total Daily Stats
            var totalDailySteps = 0L
            var totalDailyCalories = 0.0
            
            try {
                val aggregateResponse = healthConnectClient.aggregate(
                    androidx.health.connect.client.request.AggregateRequest(
                        metrics = setOf(
                            StepsRecord.COUNT_TOTAL,
                            TotalCaloriesBurnedRecord.ENERGY_TOTAL,
                            ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL
                        ),
                        timeRangeFilter = TimeRangeFilter.between(
                            startOfDay.toInstant(),
                            endOfDay.toInstant()
                        )
                    )
                )
                totalDailySteps = aggregateResponse[StepsRecord.COUNT_TOTAL] ?: 0L
                val totalCals = aggregateResponse[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories ?: 0.0
                val activeCals = aggregateResponse[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0
                totalDailyCalories = if (totalCals > 0) totalCals else activeCals
                
                if (totalDailySteps == 0L) {
                     val fallbackResponse = healthConnectClient.readRecords(
                        ReadRecordsRequest(
                            StepsRecord::class,
                            timeRangeFilter = TimeRangeFilter.between(
                                startOfDay.toInstant(),
                                endOfDay.toInstant()
                            )
                        )
                    )
                    totalDailySteps = fallbackResponse.records.sumOf { it.count }
                }
            } catch (e: Exception) {
               // Ignore
            }
            
            if (totalDailyCalories <= 0.1) totalDailyCalories = totalCaloriesFromSessions.toDouble()

            return Result.success(
                ActivityData(
                    date = date,
                    activities = uniqueActivities,
                    summary = ActivitySummary(
                        steps = totalDailySteps.toInt(),
                        distance = 0.0,
                        floors = 0,
                        caloriesOut = totalDailyCalories.toInt(),
                        activeMinutes = totalActiveMinutes,
                        sedentaryMinutes = 0
                    )
                )
            )
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    private fun calculateOverlap(a1: Activity, a2: Activity): Long {
        val start1 = a1.startTime.time
        val end1 = start1 + a1.duration
        val start2 = a2.startTime.time
        val end2 = start2 + a2.duration

        val overlapStart = kotlin.math.max(start1, start2)
        val overlapEnd = kotlin.math.min(end1, end2)

        return kotlin.math.max(0, overlapEnd - overlapStart)
    }

    private fun isBetterActivity(new: Activity, existing: Activity): Boolean {
        // Preference: Has Distance > Has Steps > Has Calories > Longer Duration
        val newScore = (if (new.distance != null) 4 else 0) + 
                       (if (new.steps != null) 2 else 0) + 
                       (if (new.calories > 0) 1 else 0)
        
        val existingScore = (if (existing.distance != null) 4 else 0) + 
                            (if (existing.steps != null) 2 else 0) + 
                            (if (existing.calories > 0) 1 else 0)

        if (newScore > existingScore) return true
        if (newScore < existingScore) return false
        
        // Tie-breaker: Name (if not generic)
        if (new.activityName != "Exercice" && existing.activityName == "Exercice") return true
        
        // Tie-breaker: Duration
        return new.duration > existing.duration
    }

    override suspend fun getActivityHistory(startDate: Date, endDate: Date): Result<List<ActivityData>> {
        return Result.success(emptyList())
    }

    override suspend fun getUserProfile(): Result<UserProfile> {
        return Result.success(
            UserProfile(
                userId = "health_connect_user",
                displayName = "Health Connect User",
                avatar = null,
                age = null,
                gender = null,
                height = null,
                weight = null
            )
        )
    }

    override suspend fun isAuthorized(): Boolean {
        val granted = healthConnectClient.permissionController.getGrantedPermissions()
        return PERMISSIONS.all { it in granted }
    }

    override suspend fun requestpermissions() {
        // Permissions are requested via HealthConnectClient from UI
        // This is typically done from Activity context
        // No implementation needed here
    }

    override suspend fun getHeartRateHistory(startDate: Date, endDate: Date): Result<List<HeartRateData>> {
        // For efficiency, we can query range and group by day.
        try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        startDate.toInstant(),
                        endDate.toInstant()
                    )
                )
            )
            
            val grouped = response.records.groupBy { 
                DateUtils.getStartOfDay(Date.from(it.startTime))
            }
            
            val history = grouped.map { (day, records) ->
                val samples = records.flatMap { it.samples }.map { it.beatsPerMinute.toInt() }
                val resting = samples.minOrNull() // Proxy for RHR
                
                // Minimal HeartRateData
                HeartRateData(
                    date = day,
                    restingHeartRate = resting,
                    heartRateZones = emptyList(), // TODO: calculate if needed
                    intradayData = null
                )
            }.sortedBy { it.date }
            
            return Result.success(history)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    override suspend fun getSleepHistory(startDate: Date, endDate: Date): Result<List<SleepData>> {
         try {
             // Similar to single day but range
             // Search from 24 hours before the start date to catch sleep sessions starting the previous evening
             // This ensures we capture the sleep session that contributes to the RHR of 'startDate'
             val searchStart = Date(startDate.time - (24 * 60 * 60 * 1000))

             val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        searchStart.toInstant(),
                        endDate.toInstant()
                    )
                )
            )
            
            val list = response.records.map { record ->
                 val durationMs = record.endTime.toEpochMilli() - record.startTime.toEpochMilli()
                 SleepData(
                     date = DateUtils.getStartOfDay(Date.from(record.endTime)), // Use End Time (Morning) as the date anchor
                     duration = durationMs,
                     efficiency = 90,
                     startTime = Date.from(record.startTime),
                     endTime = Date.from(record.endTime),
                     minutesAsleep = (durationMs / 60000).toInt(),
                     minutesAwake = 0,
                     stages = null,
                     levels = emptyList()
                 )
            }.sortedBy { it.startTime }
            return Result.success(list)
         } catch (e: Exception) {
             return Result.failure(e)
         }
    }

    override suspend fun getHeartRateSeries(startTime: Date, endTime: Date): Result<List<MinuteData>> {
        try {
            val allRecords = mutableListOf<HeartRateRecord>()
            var pageToken: String? = null
            
            do {
                val response = healthConnectClient.readRecords(
                    ReadRecordsRequest(
                        HeartRateRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(
                            startTime.toInstant(),
                            endTime.toInstant()
                        ),
                        pageSize = 5000,
                        pageToken = pageToken
                    )
                )
                allRecords.addAll(response.records)
                pageToken = response.pageToken
            } while (pageToken != null)

            val minuteDataMap = mutableMapOf<String, MinuteData>()
            val pendingAverages = mutableMapOf<String, MutableList<Int>>()

            allRecords.forEach { record ->
                record.samples.forEach { sample ->
                    val time = sample.time.atZone(ZoneId.systemDefault())
                    // Aggregating to Minute (00 seconds) for consistency with RHR calculation
                    val timeKey = String.format("%02d:%02d:00", time.hour, time.minute)
                    pendingAverages.getOrPut(timeKey) { mutableListOf() }.add(sample.beatsPerMinute.toInt())
                }
            }
            
            pendingAverages.forEach { (key, values) ->
                val avg = values.average().toInt()
                minuteDataMap[key] = MinuteData(key, avg, 0)
            }
            
            return Result.success(minuteDataMap.values.sortedBy { it.time })

        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    override suspend fun getHrvData(date: Date): Result<List<HrvRecord>> {
        try {
            val startOfDay = DateUtils.getStartOfDay(date)
            val endOfDay = DateUtils.getEndOfDay(date)

            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    HeartRateVariabilityRmssdRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        startOfDay.toInstant(),
                        endOfDay.toInstant()
                    )
                )
            )
            
            val hrvRecords = response.records.map { record ->
                HrvRecord(
                    time = Date.from(record.time),
                    rmssd = record.heartRateVariabilityMillis
                )
            }.sortedBy { it.time }
            
            return Result.success(hrvRecords)

        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    private fun Date.toInstant(): Instant = this.toInstant()

    override suspend fun getHrvHistory(startDate: Date, endDate: Date): Result<List<HrvRecord>> {
        // Health Connect simply queries by range
        try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    HeartRateVariabilityRmssdRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        startDate.toInstant(),
                        endDate.toInstant()
                    )
                )
            )
            
            val hrvRecords = response.records.map { record ->
                HrvRecord(
                    time = Date.from(record.time),
                    rmssd = record.heartRateVariabilityMillis
                )
            }.sortedBy { it.time }
            
            return Result.success(hrvRecords)

        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    override suspend fun getSpO2Data(date: Date): Result<SpO2Data?> {
        try {
            val startOfDay = DateUtils.getStartOfDay(date)
            val endOfDay = DateUtils.getEndOfDay(date)

            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    OxygenSaturationRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        startOfDay.toInstant(),
                        endOfDay.toInstant()
                    )
                )
            )

            if (response.records.isEmpty()) return Result.success(null)

            // Calculate daily stats from all samples
            val values = response.records.map { it.percentage.value }
            if (values.isEmpty()) return Result.success(null)

            return Result.success(
                SpO2Data(
                    date = date,
                    avg = values.average(),
                    min = values.minOrNull() ?: 0.0,
                    max = values.maxOrNull() ?: 0.0
                )
            )
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    override suspend fun getSpO2History(startDate: Date, endDate: Date): Result<List<SpO2Data>> {
         try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    OxygenSaturationRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        startDate.toInstant(),
                        endDate.toInstant()
                    )
                )
            )
            
            // Group by day
            val grouped = response.records.groupBy { 
                DateUtils.getStartOfDay(Date.from(it.time))
            }
            
            val history = grouped.map { (day, records) ->
                val values = records.map { it.percentage.value }
                SpO2Data(
                    date = day,
                    avg = values.average(),
                    min = values.minOrNull() ?: 0.0,
                    max = values.maxOrNull() ?: 0.0
                )
            }.sortedBy { it.date }

            return Result.success(history)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
}
