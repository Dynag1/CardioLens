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
        return Result.success(null)
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

    private fun Date.toInstant(): Instant = this.toInstant()
}
