package com.cardio.fitbit.data.models

import com.google.gson.annotations.SerializedName
import java.util.Date

/**
 * Fitbit API Response Models for JSON deserialization
 */

// Heart Rate API Response
data class HeartRateResponse(
    @SerializedName("activities-heart")
    val activitiesHeart: List<HeartRateDay>,
    @SerializedName("activities-heart-intraday")
    val intradayData: IntradayHeartRateData?
)

data class HeartRateDay(
    @SerializedName("dateTime")
    val dateTime: String,
    @SerializedName("value")
    val value: HeartRateValue
)

data class HeartRateValue(
    @SerializedName("restingHeartRate")
    val restingHeartRate: Int?,
    @SerializedName("heartRateZones")
    val heartRateZones: List<HeartRateZoneResponse>
)

data class HeartRateZoneResponse(
    @SerializedName("name")
    val name: String,
    @SerializedName("min")
    val min: Int,
    @SerializedName("max")
    val max: Int,
    @SerializedName("minutes")
    val minutes: Int,
    @SerializedName("caloriesOut")
    val caloriesOut: Double
)

data class IntradayHeartRateData(
    @SerializedName("dataset")
    val dataset: List<IntradayHeartRatePoint>,
    @SerializedName("datasetInterval")
    val datasetInterval: Int,
    @SerializedName("datasetType")
    val datasetType: String
)

data class IntradayHeartRatePoint(
    @SerializedName("time")
    val time: String,
    @SerializedName("value")
    val value: Int
)

// Sleep API Response
data class SleepResponse(
    @SerializedName("sleep")
    val sleep: List<SleepLog>,
    @SerializedName("summary")
    val summary: SleepSummary?
)

data class SleepLog(
    @SerializedName("dateOfSleep")
    val dateOfSleep: String,
    @SerializedName("duration")
    val duration: Long,
    @SerializedName("efficiency")
    val efficiency: Int,
    @SerializedName("startTime")
    val startTime: String,
    @SerializedName("endTime")
    val endTime: String,
    @SerializedName("minutesAsleep")
    val minutesAsleep: Int,
    @SerializedName("minutesAwake")
    val minutesAwake: Int,
    @SerializedName("levels")
    val levels: SleepLevels
)

data class SleepLevels(
    @SerializedName("summary")
    val summary: SleepStagesSummary?,
    @SerializedName("data")
    val data: List<SleepLevelData>
)

data class SleepStagesSummary(
    @SerializedName("deep")
    val deep: SleepStageMinutes?,
    @SerializedName("light")
    val light: SleepStageMinutes?,
    @SerializedName("rem")
    val rem: SleepStageMinutes?,
    @SerializedName("wake")
    val wake: SleepStageMinutes?
)

data class SleepStageMinutes(
    @SerializedName("minutes")
    val minutes: Int
)

data class SleepLevelData(
    @SerializedName("dateTime")
    val dateTime: String,
    @SerializedName("level")
    val level: String,
    @SerializedName("seconds")
    val seconds: Int
)

data class SleepSummary(
    @SerializedName("totalMinutesAsleep")
    val totalMinutesAsleep: Int,
    @SerializedName("totalTimeInBed")
    val totalTimeInBed: Int
)

// Steps API Response
data class StepsResponse(
    @SerializedName("activities-steps")
    val activitiesSteps: List<StepsDay>
)

data class StepsDay(
    @SerializedName("dateTime")
    val dateTime: String,
    @SerializedName("value")
    val value: String
)

// Steps Intraday Response
data class StepsIntradayResponse(
    @SerializedName("activities-steps")
    val activitiesSteps: List<StepsDay>,
    @SerializedName("activities-steps-intraday")
    val intradayData: StepsIntradayData?
)

data class StepsIntradayData(
    @SerializedName("dataset")
    val dataset: List<StepsIntradayPoint>,
    @SerializedName("datasetInterval")
    val datasetInterval: Int,
    @SerializedName("datasetType")
    val datasetType: String
)

data class StepsIntradayPoint(
    @SerializedName("time")
    val time: String,
    @SerializedName("value")
    val value: Int
)

// Activity API Response
data class ActivityResponse(
    @SerializedName("activities")
    val activities: List<ActivityLog>,
    @SerializedName("summary")
    val summary: ActivitySummaryResponse
)

data class ActivityLog(
    @SerializedName("activityId")
    val activityId: Long,
    @SerializedName("activityName")
    val activityName: String,
    @SerializedName("startTime")
    val startTime: String,
    @SerializedName("duration")
    val duration: Long,
    @SerializedName("calories")
    val calories: Int,
    @SerializedName("distance")
    val distance: Double?,
    @SerializedName("steps")
    val steps: Int?,
    @SerializedName("averageHeartRate")
    val averageHeartRate: Int?
)

data class ActivitySummaryResponse(
    @SerializedName("steps")
    val steps: Int,
    @SerializedName("distances")
    val distances: List<Distance>,
    @SerializedName("floors")
    val floors: Int,
    @SerializedName("caloriesOut")
    val caloriesOut: Int,
    @SerializedName("fairlyActiveMinutes")
    val fairlyActiveMinutes: Int,
    @SerializedName("lightlyActiveMinutes")
    val lightlyActiveMinutes: Int,
    @SerializedName("sedentaryMinutes")
    val sedentaryMinutes: Int,
    @SerializedName("veryActiveMinutes")
    val veryActiveMinutes: Int
)

data class Distance(
    @SerializedName("activity")
    val activity: String,
    @SerializedName("distance")
    val distance: Double
)

// User Profile Response
data class UserProfileResponse(
    @SerializedName("user")
    val user: UserData
)

data class UserData(
    @SerializedName("encodedId")
    val encodedId: String,
    @SerializedName("displayName")
    val displayName: String,
    @SerializedName("avatar")
    val avatar: String?,
    @SerializedName("age")
    val age: Int?,
    @SerializedName("gender")
    val gender: String?,
    @SerializedName("height")
    val height: Double?,
    @SerializedName("weight")
    val weight: Double?
)

// OAuth Token Response
data class TokenResponse(
    @SerializedName("access_token")
    val accessToken: String,
    @SerializedName("refresh_token")
    val refreshToken: String,
    @SerializedName("expires_in")
    val expiresIn: Long,
    @SerializedName("token_type")
    val tokenType: String,
    @SerializedName("user_id")
    val userId: String
)
