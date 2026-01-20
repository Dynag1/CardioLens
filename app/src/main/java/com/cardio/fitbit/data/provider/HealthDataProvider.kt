package com.cardio.fitbit.data.provider

import com.cardio.fitbit.data.models.*
import java.util.Date

/**
 * Common interface for Health Data Providers (Fitbit, Health Connect)
 */
interface HealthDataProvider {
    
    /**
     * Unique identifier for the provider (e.g. "FITBIT", "HEALTH_CONNECT")
     */
    val providerId: String

    /**
     * Check if the provider is authorized/connected
     */
    suspend fun isAuthorized(): Boolean

    /**
     * Request authorization/permissions.
     * Note: This usually requires an Activity context, handled via specific UI flows.
     */
    suspend fun requestpermissions() // Generalized, implementation might differ

    suspend fun getHeartRateData(date: Date): Result<HeartRateData?>
    
    suspend fun getIntradayData(date: Date): Result<IntradayData?>
    
    suspend fun getSleepData(date: Date): Result<List<SleepData>>
    suspend fun getSleepHistory(startDate: Date, endDate: Date): Result<List<SleepData>>
    
    suspend fun getStepsData(startDate: Date, endDate: Date): Result<List<StepsData>>
    
    suspend fun getActivityData(date: Date): Result<ActivityData?>
    
    suspend fun getUserProfile(): Result<UserProfile?>
    
    /**
     * Get raw heart rate series for a specific time range.
     * Useful for fetching data across midnight (e.g. sleep logic).
     */
    suspend fun getHeartRateSeries(startTime: Date, endTime: Date): Result<List<MinuteData>>
    
    suspend fun getHeartRateHistory(startDate: Date, endDate: Date): Result<List<HeartRateData>> // History of daily summaries
    
    suspend fun getHrvData(date: Date): Result<List<HrvRecord>> // Single day details (intraday if available)
    suspend fun getHrvHistory(startDate: Date, endDate: Date): Result<List<HrvRecord>> // Daily summaries over range

    suspend fun getSpO2Data(date: Date): Result<SpO2Data?> // Daily summary
    suspend fun getSpO2History(startDate: Date, endDate: Date): Result<List<SpO2Data>> // History over range
}
