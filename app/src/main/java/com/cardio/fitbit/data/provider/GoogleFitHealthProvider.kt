package com.cardio.fitbit.data.provider

import com.cardio.fitbit.auth.GoogleFitAuthManager
import com.cardio.fitbit.data.api.ApiClient
import com.cardio.fitbit.data.api.AggregateBy
import com.cardio.fitbit.data.api.BucketByTime
import com.cardio.fitbit.data.api.GoogleFitAggregateRequest
import com.cardio.fitbit.data.api.GoogleFitAggregateResponse
import com.cardio.fitbit.data.models.*
import com.cardio.fitbit.utils.DateUtils
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleFitHealthProvider @Inject constructor(
    private val apiClient: ApiClient,
    private val authManager: GoogleFitAuthManager
) : HealthDataProvider {

    override val providerId = "GOOGLE_FIT"

    override suspend fun isAuthorized(): Boolean {
        return authManager.isAuthenticated()
    }

    override suspend fun requestpermissions() {
        // Handled by Setup Screen
    }

    override suspend fun getHeartRateData(date: Date): Result<HeartRateData?> {
        try {
            val startOfDay = DateUtils.getStartOfDay(date).time
            val endOfDay = DateUtils.getEndOfDay(date).time
            
            val request = GoogleFitAggregateRequest(
                aggregateBy = listOf(
                    AggregateBy(dataTypeName = "com.google.heart_rate.bpm")
                ),
                bucketByTime = BucketByTime(durationMillis = 60000), // 1 minute buckets
                startTimeMillis = startOfDay,
                endTimeMillis = endOfDay
            )
            
            val response = apiClient.googleFitApi.aggregate(request)
            
            if (response.isSuccessful && response.body() != null) {
                return Result.success(mapHeartRateResponse(response.body()!!, date))
            }
            return Result.failure(Exception("Google Fit Error: ${response.code()} ${response.message()}"))
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    private fun mapHeartRateResponse(response: GoogleFitAggregateResponse, date: Date): HeartRateData? {
        val buckets = response.bucket
        if (buckets.isEmpty()) return null

        val intradayPoints = mutableListOf<IntradayHeartRate>()
        var totalHr = 0L
        var count = 0
        var minHr = Int.MAX_VALUE
        var maxHr = Int.MIN_VALUE

        buckets.forEach { bucket ->
            val bucketStartTime = bucket.startTimeMillis.toLongOrNull() ?: 0L
            // Iterate datasets in bucket
            bucket.dataset.forEach { dataset ->
                 dataset.point.forEach { point ->
                     // bpm is usually fpVal in first value
                     val bpm = point.value.firstOrNull()?.fpVal?.toInt() ?: point.value.firstOrNull()?.intVal ?: 0
                     if (bpm > 0) {
                         // Format time HH:mm
                         val timeStr = DateUtils.formatTimeForDisplay(Date(bucketStartTime))
                         intradayPoints.add(IntradayHeartRate(time = timeStr, value = bpm))
                         
                         totalHr += bpm
                         count++
                         if (bpm < minHr) minHr = bpm
                         if (bpm > maxHr) maxHr = bpm
                     }
                 }
            }
        }
        
        if (intradayPoints.isEmpty()) return null

        // Zones (Synthetic since Google Fit doesn't summarize them directly in this view easily)
        // We could calculate them based on age/max HR like Fitbit does, 
        // or just mock them for now based on distribution.
        val zones = calculateZones(intradayPoints)

        // Resting HR: Google Fit has a type for it, but for now we can approximate with min or average of sleep ... 
        // actually simplest is just use 'min' observed or average. 
        // Let's use average for now or 0 if we want to be strict.
        val restingHr = if (count > 0) (totalHr / count).toInt() else 0 // This is AVG, not RHR.
        
        // Better: Request com.google.heart_rate.resting in a separate call or same call?
        // Using minHr as poor man's RHR for now to avoid multiple calls complexity initially.
        val estimatedRhr = minHr 

        return HeartRateData(
            date = date,
            restingHeartRate = estimatedRhr,
            heartRateZones = zones,
            intradayData = intradayPoints
        )
    }

    private fun calculateZones(points: List<IntradayHeartRate>): List<HeartRateZone> {
        // Simple fixed zones for demo
        val values = points.map { it.value }
        val fatBurn = values.count { it in 100..130 }
        val cardio = values.count { it in 130..160 }
        val peak = values.count { it > 160 }
        val outOfRange = values.count { it < 100 }
        
        return listOf(
            HeartRateZone("Out of Range", 30, 100, outOfRange, 0.0),
            HeartRateZone("Fat Burn", 100, 130, fatBurn, 0.0),
            HeartRateZone("Cardio", 130, 160, cardio, 0.0),
            HeartRateZone("Peak", 160, 220, peak, 0.0)
        )
    }

    override suspend fun getIntradayData(date: Date): Result<IntradayData?> {
        try {
            val startOfDay = DateUtils.getStartOfDay(date).time
            val endOfDay = DateUtils.getEndOfDay(date).time
            
            val request = GoogleFitAggregateRequest(
                aggregateBy = listOf(
                    AggregateBy(dataTypeName = "com.google.heart_rate.bpm"),
                    AggregateBy(dataTypeName = "com.google.step_count.delta")
                ),
                bucketByTime = BucketByTime(durationMillis = 60000), // 1 minute buckets
                startTimeMillis = startOfDay,
                endTimeMillis = endOfDay
            )
            
            val response = apiClient.googleFitApi.aggregate(request)
            
            if (response.isSuccessful && response.body() != null) {
                return Result.success(mapIntradayResponse(response.body()!!, date))
            }
            return Result.failure(Exception("Google Fit Intraday Error: ${response.code()}"))
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    private fun mapIntradayResponse(response: GoogleFitAggregateResponse, date: Date): IntradayData? {
        val buckets = response.bucket
        if (buckets.isEmpty()) return null

        val minuteDataList = mutableListOf<MinuteData>()

        buckets.forEach { bucket ->
            val bucketStartTime = bucket.startTimeMillis.toLongOrNull() ?: 0L
            val timeStr = DateUtils.formatTimeForDisplay(Date(bucketStartTime)) // HH:mm
            
            var hr = 0
            var steps = 0

            bucket.dataset.forEach { dataset ->
                val type = dataset.dataSourceId
                // Check based on known types or inference
                // dataset.dataSourceId is often full unique ID, not just type name
                // But we know we asked for 2 types.
                // We can check point values data types if we had them or just try to parse.
                
                // Typically:
                // derived:com.google.heart_rate.bpm:... -> HR
                // derived:com.google.step_count.delta:... -> Steps
                
                dataset.point.forEach { point ->
                    // Try to guess or check
                    val valInt = point.value.firstOrNull()?.intVal
                    val valFp = point.value.firstOrNull()?.fpVal
                    
                    if (dataset.dataSourceId.contains("heart_rate")) {
                        if (valFp != null) hr = valFp.toInt()
                        else if (valInt != null) hr = valInt
                    } else if (dataset.dataSourceId.contains("step_count")) {
                         if (valInt != null) steps += valInt
                         else if (valFp != null) steps += valFp.toInt()
                    }
                }
            }
            
            if (hr > 0 || steps > 0) {
                minuteDataList.add(MinuteData(time = timeStr, heartRate = hr, steps = steps))
            }
        }
        
        return IntradayData(date = date, minuteData = minuteDataList)
    }

    override suspend fun getSleepData(date: Date): Result<List<SleepData>> {
         // TODO: Implement
        return Result.success(emptyList())
    }

    override suspend fun getStepsData(startDate: Date, endDate: Date): Result<List<StepsData>> {
        try {
            val startSearch = DateUtils.getStartOfDay(startDate).time
            val endSearch = DateUtils.getEndOfDay(endDate).time
            
            val request = GoogleFitAggregateRequest(
                aggregateBy = listOf(
                    AggregateBy(dataTypeName = "com.google.step_count.delta")
                ),
                bucketByTime = BucketByTime(durationMillis = 86400000), // 24 hours
                startTimeMillis = startSearch,
                endTimeMillis = endSearch
            )
            
            val response = apiClient.googleFitApi.aggregate(request)
            
            if (response.isSuccessful && response.body() != null) {
                return Result.success(mapStepsResponse(response.body()!!))
            }
            return Result.failure(Exception("Google Fit Steps Error: ${response.code()}"))
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    private fun mapStepsResponse(response: GoogleFitAggregateResponse): List<StepsData> {
        val buckets = response.bucket
        val result = mutableListOf<StepsData>()
        
        buckets.forEach { bucket ->
            val startTime = bucket.startTimeMillis.toLongOrNull() ?: 0L
            var totalSteps = 0
            
            bucket.dataset.forEach { dataset ->
                dataset.point.forEach { point ->
                    // For steps, value is intVal
                    totalSteps += point.value.firstOrNull()?.intVal ?: 0
                }
            }
            
            if (totalSteps > 0 || startTime > 0) {
                 result.add(
                     StepsData(
                         date = Date(startTime),
                         steps = totalSteps,
                         distance = 0.0, // Need separate aggregation for distance (com.google.distance.delta)
                         floors = 0,
                         caloriesOut = 0 // Need separate aggregation for calories
                     )
                 )
            }
        }
        return result
    }

    override suspend fun getActivityData(date: Date): Result<ActivityData?> {
         // TODO: Implement
        return Result.success(null)
    }

    override suspend fun getUserProfile(): Result<UserProfile?> {
         // TODO: Implement
        return Result.success(null)
    }

    override suspend fun getHeartRateSeries(startTime: Date, endTime: Date): Result<List<MinuteData>> {
        return Result.success(emptyList())
    }

    override suspend fun getHrvData(date: Date): Result<List<com.cardio.fitbit.data.models.HrvRecord>> {
        return Result.success(emptyList())
    }

    override suspend fun getHrvHistory(startDate: Date, endDate: Date): Result<List<com.cardio.fitbit.data.models.HrvRecord>> {
        return Result.success(emptyList())
    }

    override suspend fun getSpO2Data(date: Date): Result<SpO2Data?> {
        // Not implemented for Google Fit yet
        return Result.success(null)
    }

    override suspend fun getSpO2History(startDate: Date, endDate: Date): Result<List<SpO2Data>> {
        // Not implemented for Google Fit yet
        return Result.success(emptyList())
    }
}
