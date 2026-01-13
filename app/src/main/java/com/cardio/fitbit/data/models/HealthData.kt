package com.cardio.fitbit.data.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date

/**
 * Heart Rate Data Models
 */
@Parcelize
data class HeartRateData(
    val date: Date,
    val restingHeartRate: Int?,
    val heartRateZones: List<HeartRateZone>,
    val intradayData: List<IntradayHeartRate>?
) : Parcelable

@Parcelize
data class HeartRateZone(
    val name: String,
    val min: Int,
    val max: Int,
    val minutes: Int,
    val caloriesOut: Double
) : Parcelable

@Parcelize
data class IntradayHeartRate(
    val time: String,
    val value: Int
) : Parcelable

/**
 * Sleep Data Models
 */
@Parcelize
data class SleepData(
    val date: Date,
    val duration: Long, // in milliseconds
    val efficiency: Int,
    val startTime: Date,
    val endTime: Date,
    val minutesAsleep: Int,
    val minutesAwake: Int,
    val stages: SleepStages?,
    val levels: List<SleepLevel>
) : Parcelable

@Parcelize
data class SleepStages(
    val deep: Int,
    val light: Int,
    val rem: Int,
    val wake: Int
) : Parcelable

@Parcelize
data class SleepLevel(
    val dateTime: Date,
    val level: String, // "deep", "light", "rem", "wake"
    val seconds: Int
) : Parcelable

/**
 * Steps Data Models
 */
@Parcelize
data class StepsData(
    val date: Date,
    val steps: Int,
    val distance: Double, // in km
    val floors: Int,
    val caloriesOut: Int
) : Parcelable

/**
 * Activity Data Models
 */
@Parcelize
data class ActivityData(
    val date: Date,
    val activities: List<Activity>,
    val summary: ActivitySummary,
    val debugInfo: String? = null
) : Parcelable

@Parcelize
data class Activity(
    val activityId: Long,
    val activityName: String,
    val startTime: Date,
    val duration: Long, // in milliseconds
    val calories: Int,
    val distance: Double?,
    val steps: Int?,
    val averageHeartRate: Int?
) : Parcelable

@Parcelize
data class ActivitySummary(
    val steps: Int,
    val distance: Double,
    val floors: Int,
    val caloriesOut: Int,
    val activeMinutes: Int,
    val sedentaryMinutes: Int
) : Parcelable

/**
 * User Profile
 */
@Parcelize
data class UserProfile(
    val userId: String,
    val displayName: String,
    val avatar: String?,
    val age: Int?,
    val gender: String?,
    val height: Double?,
    val weight: Double?
) : Parcelable
