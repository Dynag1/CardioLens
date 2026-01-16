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
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class)
        )
    }

    override suspend fun getHeartRateData(date: Date): Result<HeartRateData?> {
        try {
            val startOfDay = DateUtils.getStartOfDay(date)
            val endOfDay = DateUtils.getEndOfDay(date)
            
            android.util.Log.d("HealthConnectProvider", "=== Loading Heart Rate for ${DateUtils.formatForApi(date)} ===")
            
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
            
            android.util.Log.d("HealthConnectProvider", "Total pages: $pageCount, Total records: ${allRecords.size}")

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

    override suspend fun getIntradayData(date: Date): Result<IntradayData?> {
        try {
            val startOfDay = DateUtils.getStartOfDay(date)
            val endOfDay = DateUtils.getEndOfDay(date)
            
            // Paginate Heart Rate Records
            val allHrRecords = mutableListOf<HeartRateRecord>()
            var hrPageToken: String? = null
            var pgCount = 0
            do {
                pgCount++
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
                android.util.Log.d("HealthConnectProvider", "Intraday HR Page $pgCount: ${response.records.size} records")
                hrPageToken = response.pageToken
            } while (hrPageToken != null)
            android.util.Log.d("HealthConnectProvider", "Intraday HR Total: ${allHrRecords.size} records")

            // Paginate Steps Records
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

            allHrRecords.forEach { record ->
                record.samples.forEach { sample ->
                    val time = sample.time.atZone(ZoneId.systemDefault())
                    val timeKey = String.format("%02d:%02d", time.hour, time.minute)
                    val existing = minuteDataMap[timeKey] ?: MinuteData(timeKey, 0, 0)
                    minuteDataMap[timeKey] = existing.copy(heartRate = sample.beatsPerMinute.toInt())
                }
            }

            allStepsRecords.forEach { record ->
                val time = record.startTime.atZone(ZoneId.systemDefault())
                val timeKey = String.format("%02d:%02d", time.hour, time.minute)
                val existing = minuteDataMap[timeKey] ?: MinuteData(timeKey, 0, 0)
                minuteDataMap[timeKey] = existing.copy(steps = existing.steps + record.count.toInt())
            }

            val minuteDataList = minuteDataMap.values.sortedBy { it.time }
            
            return Result.success(IntradayData(date, minuteDataList))
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
            
            android.util.Log.d("HealthConnectProvider", "Searching sleep from ${DateUtils.formatForApi(searchStart)} to ${DateUtils.formatForApi(endOfDay)}")
            
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
                    SleepData(
                        date = date,
                        duration = durationMs,
                        efficiency = 90,
                        startTime = Date.from(record.startTime),
                        endTime = Date.from(record.endTime),
                        minutesAsleep = (durationMs / 60000).toInt(),
                        minutesAwake = 0,
                        stages = null,
                        levels = emptyList()
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

            // Removed early return if records.isEmpty()
            // We want to calculate daily summary (Steps, Calories) even if no specific "Exercise Session" exists.

            val activities = response.records.map { record ->
                val durationMs = record.endTime.toEpochMilli() - record.startTime.toEpochMilli()
                
                // Aggregate calories for this specific session time range
                var calories = 0.0
                try {
                     val aggregateResponse = healthConnectClient.aggregate(
                        androidx.health.connect.client.request.AggregateRequest(
                            metrics = setOf(
                                ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                                TotalCaloriesBurnedRecord.ENERGY_TOTAL
                            ),
                            timeRangeFilter = TimeRangeFilter.between(
                                record.startTime,
                                record.endTime
                            )
                        )
                    )
                    val active = aggregateResponse[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0
                    val total = aggregateResponse[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories ?: 0.0
                    
                    android.util.Log.d("HealthConnectProvider", "Activity: ${record.title} (${record.exerciseType}), Active: $active, Total: $total")
                    
                    // Prefer Active, fallback to Total if Active is 0 (some devices only write Total)
                    calories = if (active > 0.1) active else total
                    
                } catch (e: Exception) {
                    android.util.Log.e("HealthConnectProvider", "Failed to aggregate calories: ${e.message}")
                }

                // Get title or default
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
                    distance = null, // Could fetch if linked, but complex for now
                    steps = null,
                    averageHeartRate = null // Could fetch derived data later
                )
            }

            // Calculate Summary from Sessions
            val totalCaloriesFromSessions = activities.sumOf { it.calories }
            val totalActiveMinutes = activities.sumOf { (it.duration / 60000).toInt() }

            // NEW: Fetch Total Steps & Calories for the ENTIRE DAY to populate summary
            // This is independent of having specific Exercise Sessions
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
                
                android.util.Log.d("HealthConnectProvider", "Total Daily: Steps=$totalDailySteps, Cals=$totalDailyCalories")
                
                // Fallback for Steps if aggregation returns 0
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
                    android.util.Log.d("HealthConnectProvider", "Total Daily Steps (Fallback): $totalDailySteps")
                }
            } catch (e: Exception) {
                android.util.Log.e("HealthConnectProvider", "Failed to aggregate daily totals", e)
            }
            
            // If totalDailyCalories is still 0 (agg failed), fallback to sum of sessions or 0
            if (totalDailyCalories <= 0.1) totalDailyCalories = totalCaloriesFromSessions.toDouble()

            return Result.success(
                ActivityData(
                    date = date,
                    activities = activities,
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

            allRecords.forEach { record ->
                record.samples.forEach { sample ->
                    val time = sample.time.atZone(ZoneId.systemDefault())
                    // IMPORTANT: Include Date in key to handle multi-day fetching correctness if needed, 
                    // but MinuteData usually only holds HH:mm.
                    // For the purpose of RHR calculation in DashboardVM, we likely process raw samples or need 
                    // a way to distinguish days if spanning midnight.
                    // However, standard MinuteData allows HH:mm. 
                    // Let's stick to standard map but be aware of overlaps effectively merging same times on different days?
                    // "getHeartRateSeries" is generic.
                    // For Night RHR logic, we specifically append PRE-midnight data to POST-midnight data.
                    // So distinct HH:mm keys are fine as long as we know the context.
                    // BUT: 23:59 from Day 1 and 00:01 from Day 2 are distinct.
                    
                    val timeKey = String.format("%02d:%02d", time.hour, time.minute)
                    val existing = minuteDataMap[timeKey] ?: MinuteData(timeKey, 0, 0)
                    // If multiple samples in same minute, average or max? Health Connect usually has one record per series but samples can definitely be sub-minute.
                    // Last-wins or simple average? Let's take the first or last for simplicity or max?
                    // Let's use the last one encountered for now, or better: average if multiple?
                    // Simply overwriting is the current behavior in getIntradayData.
                    minuteDataMap[timeKey] = existing.copy(heartRate = sample.beatsPerMinute.toInt())
                }
            }
            
            return Result.success(minuteDataMap.values.sortedBy { it.time })

        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    private fun Date.toInstant(): Instant = this.toInstant()
}
