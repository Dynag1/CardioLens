package com.cardio.fitbit.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workout_intensity")
data class WorkoutIntensityEntity(
    @PrimaryKey
    val activityId: Long,
    val intensity: Int, // 1-5
    val timestamp: Long // When it was last updated
)
