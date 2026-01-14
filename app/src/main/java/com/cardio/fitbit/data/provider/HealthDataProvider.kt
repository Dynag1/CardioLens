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
    
    suspend fun getStepsData(startDate: Date, endDate: Date): Result<List<StepsData>>
    
    suspend fun getActivityData(date: Date): Result<ActivityData?>
    
    suspend fun getUserProfile(): Result<UserProfile?>
}
