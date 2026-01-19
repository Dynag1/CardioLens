package com.cardio.fitbit.data.api

import com.cardio.fitbit.data.models.*
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Fitbit Web API Service
 */
interface FitbitApiService {

    /**
     * Get user profile
     */
    @GET("1/user/-/profile.json")
    suspend fun getUserProfile(): Response<UserProfileResponse>

    /**
     * Get heart rate data for a specific date
     */
    @GET("1/user/-/activities/heart/date/{date}/1d.json")
    suspend fun getHeartRate(
        @Path("date") date: String
    ): Response<HeartRateResponse>

    /**
     * Get intraday heart rate data (minute-by-minute)
     */
    @GET("1/user/-/activities/heart/date/{date}/1d/1min.json")
    suspend fun getIntradayHeartRate(@Path("date") date: String): Response<HeartRateResponse>

    /**
     * Get intraday heart rate data for specific time range
     * Format times as HH:mm
     */
    @GET("1/user/-/activities/heart/date/{date}/1d/1min/time/{startTime}/{endTime}.json")
    suspend fun getIntradayHeartRateRange(
        @Path("date") date: String,
        @Path("startTime") startTime: String,
        @Path("endTime") endTime: String
    ): Response<HeartRateResponse>

    /**
     * Get intraday steps data (minute-by-minute)
     */
    @GET("1/user/-/activities/steps/date/{date}/1d/1min.json")
    suspend fun getIntradaySteps(@Path("date") date: String): Response<StepsIntradayResponse>

    /**
     * Get heart rate intraday data (requires Personal app type)
     */
    @GET("1/user/-/activities/heart/date/{date}/1d/1min.json")
    suspend fun getHeartRateIntraday(
        @Path("date") date: String
    ): Response<HeartRateResponse>

    /**
     * Get heart rate time series
     */
    @GET("1/user/-/activities/heart/date/{startDate}/{endDate}.json")
    suspend fun getHeartRateRange(
        @Path("startDate") startDate: String,
        @Path("endDate") endDate: String
    ): Response<HeartRateResponse>

    /**
     * Get sleep logs for a specific date
     */
    @GET("1.2/user/-/sleep/date/{date}.json")
    suspend fun getSleep(
        @Path("date") date: String
    ): Response<SleepResponse>

    /**
     * Get sleep logs for a date range
     */
    @GET("1.2/user/-/sleep/date/{startDate}/{endDate}.json")
    suspend fun getSleepRange(
        @Path("startDate") startDate: String,
        @Path("endDate") endDate: String
    ): Response<SleepResponse>

    /**
     * Get steps time series
     */
    @GET("1/user/-/activities/steps/date/{startDate}/{endDate}.json")
    suspend fun getSteps(
        @Path("startDate") startDate: String,
        @Path("endDate") endDate: String
    ): Response<StepsResponse>

    /**
     * Get daily activity summary
     */
    @GET("1/user/-/activities/date/{date}.json")
    suspend fun getActivities(
        @Path("date") date: String
    ): Response<ActivityResponse>

    /**
     * Get activity logs list
     */
    @GET("1/user/-/activities/list.json")
    suspend fun getActivityLogs(
        @Query("beforeDate") beforeDate: String,
        @Query("sort") sort: String = "desc",
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 20
    ): Response<ActivityListResponse>

    /**
     * Get HRV data for a specific date
     */
    @GET("1/user/-/hrv/date/{date}.json")
    suspend fun getHrv(
        @Path("date") date: String
    ): Response<HrvResponse>

    /**
     * Get HRV data for a date range
     */
    @GET("1/user/-/hrv/date/{startDate}/{endDate}.json")
    suspend fun getHrvRange(
        @Path("startDate") startDate: String,
        @Path("endDate") endDate: String
    ): Response<HrvResponse>

    /**
     * Get SpO2 data for a specific date
     */
    @GET("1/user/-/spo2/date/{date}.json")
    suspend fun getSpO2(
        @Path("date") date: String
    ): Response<SpO2Response>

    /**
     * Get SpO2 data for a date range
     */
    @GET("1/user/-/spo2/date/{startDate}/{endDate}.json")
    suspend fun getSpO2Range(
        @Path("startDate") startDate: String,
        @Path("endDate") endDate: String
    ): Response<SpO2Response>
}
