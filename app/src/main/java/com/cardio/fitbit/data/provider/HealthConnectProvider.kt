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
            
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        startOfDay.toInstant(),
                        endOfDay.toInstant()
                    )
                )
            )

            if (response.records.isEmpty()) {
                return Result.success(null)
            }

            // Calculate zones and resting HR
            val heartRates = response.records.flatMap { it.samples }.map { it.beatsPerMinute }
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

            return Result.success(
                HeartRateData(
                    date = date,
                    restingHeartRate = restingHr,
                    heartRateZones = zones,
                    intradayData = null
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
            
            val hrResponse = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        startOfDay.toInstant(),
                        endOfDay.toInstant()
                    )
                )
            )

            val stepsResponse = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        startOfDay.toInstant(),
                        endOfDay.toInstant()
                    )
                )
            )

            val minuteDataMap = mutableMapOf<String, MinuteData>()

            hrResponse.records.forEach { record ->
                record.samples.forEach { sample ->
                    val time = sample.time.atZone(ZoneId.systemDefault())
                    val timeKey = String.format("%02d:%02d", time.hour, time.minute)
                    val existing = minuteDataMap[timeKey] ?: MinuteData(timeKey, 0, 0)
                    minuteDataMap[timeKey] = existing.copy(heartRate = sample.beatsPerMinute.toInt())
                }
            }

            stepsResponse.records.forEach { record ->
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
            val startOfDay = DateUtils.getStartOfDay(date)
            val endOfDay = DateUtils.getEndOfDay(date)
            
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        startOfDay.toInstant(),
                        endOfDay.toInstant()
                    )
                )
            )

            return Result.success(
                response.records.map { record ->
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
