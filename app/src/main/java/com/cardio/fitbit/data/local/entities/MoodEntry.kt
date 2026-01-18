package com.cardio.fitbit.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mood_entries")
data class MoodEntry(
    @PrimaryKey
    val date: String, // Format: YYYY-MM-DD
    val rating: Int,  // 1 to 5
    val timestamp: Long
)
