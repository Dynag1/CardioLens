package com.cardio.fitbit.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cardio.fitbit.data.local.entities.SpO2DataEntity

@Dao
interface SpO2Dao {
    @Query("SELECT * FROM spo2_data WHERE date = :date LIMIT 1")
    suspend fun getSpO2ByDate(date: Long): SpO2DataEntity?

    @Query("SELECT * FROM spo2_data WHERE date BETWEEN :start AND :end ORDER BY date ASC")
    suspend fun getSpO2Range(start: Long, end: Long): List<SpO2DataEntity>

    @Query("SELECT * FROM spo2_data")
    suspend fun getAll(): List<SpO2DataEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpO2(data: SpO2DataEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(data: List<SpO2DataEntity>)
}
