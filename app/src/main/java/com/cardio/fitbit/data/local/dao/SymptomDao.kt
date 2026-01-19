package com.cardio.fitbit.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cardio.fitbit.data.local.entities.SymptomEntry

@Dao
interface SymptomDao {
    @Query("SELECT * FROM symptoms WHERE date = :date")
    suspend fun getSymptomsForDate(date: String): SymptomEntry?

    @Query("SELECT * FROM symptoms")
    suspend fun getAllSymptoms(): List<SymptomEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSymptom(entry: SymptomEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<SymptomEntry>)
}
