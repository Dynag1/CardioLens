package com.cardio.fitbit.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cardio.fitbit.data.local.entities.MoodEntry

@Dao
interface MoodDao {
    @Query("SELECT * FROM mood_entries WHERE date = :date")
    suspend fun getByDate(date: String): MoodEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: MoodEntry)
}
