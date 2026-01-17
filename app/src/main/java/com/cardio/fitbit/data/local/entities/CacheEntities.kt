package com.cardio.fitbit.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "intraday_data")
data class IntradayDataEntity(
    @PrimaryKey
    val date: String, // Format: yyyy-MM-dd
    val data: String, // JSON serialized IntradayData
    val timestamp: Long // Cache timestamp
)

@Entity(tableName = "sleep_data")
data class SleepDataEntity(
    @PrimaryKey
    val date: String,
    val data: String, // JSON serialized SleepData
    val timestamp: Long
)

@Entity(tableName = "activity_data")
data class ActivityDataEntity(
    @PrimaryKey
    val date: String,
    val data: String, // JSON serialized ActivityData
    val timestamp: Long
)

@Entity(tableName = "hrv_data")
data class HrvDataEntity(
    @PrimaryKey
    val date: String,
    val data: String, // JSON serialized List<HrvRecord>
    val timestamp: Long
)

@Entity(tableName = "heart_rate_data")
data class HeartRateDataEntity(
    @PrimaryKey
    val date: String,
    val data: String, // JSON serialized HeartRateData
    val timestamp: Long
)

@Entity(tableName = "steps_data")
data class StepsDataEntity(
    @PrimaryKey
    val date: String,
    val data: String, // JSON serialized StepsData (Daily summary/list wrapper)
    val timestamp: Long
)
