package com.cardio.fitbit.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "symptoms")
data class SymptomEntry(
    @PrimaryKey val date: String, // Format: YYYY-MM-DD
    val symptoms: String,         // Comma separated tags
    val timestamp: Long
)
