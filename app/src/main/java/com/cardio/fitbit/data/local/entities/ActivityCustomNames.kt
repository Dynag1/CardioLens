package com.cardio.fitbit.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "activity_custom_names")
data class ActivityCustomNameEntity(
    @PrimaryKey
    val activityId: Long,
    val customName: String,
    val timestamp: Long
)

@Entity(tableName = "workout_tags")
data class WorkoutTagEntity(
    @PrimaryKey
    val tag: String,
    val timestamp: Long
)
