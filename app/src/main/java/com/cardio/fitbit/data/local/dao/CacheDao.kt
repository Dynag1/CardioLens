package com.cardio.fitbit.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cardio.fitbit.data.local.entities.IntradayDataEntity
import com.cardio.fitbit.data.local.entities.SleepDataEntity
import com.cardio.fitbit.data.local.entities.ActivityDataEntity

@Dao
interface IntradayDataDao {
    @Query("SELECT * FROM intraday_data WHERE date = :date")
    suspend fun getByDate(date: String): IntradayDataEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(data: IntradayDataEntity)
    
    @Query("DELETE FROM intraday_data WHERE timestamp < :expiryTime")
    suspend fun deleteExpired(expiryTime: Long)
}

@Dao
interface SleepDataDao {
    @Query("SELECT * FROM sleep_data WHERE date = :date")
    suspend fun getByDate(date: String): SleepDataEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(data: SleepDataEntity)
    
    @Query("DELETE FROM sleep_data WHERE timestamp < :expiryTime")
    suspend fun deleteExpired(expiryTime: Long)
}

@Dao
interface ActivityDataDao {
    @Query("SELECT * FROM activity_data WHERE date = :date")
    suspend fun getByDate(date: String): ActivityDataEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(data: ActivityDataEntity)
    
    @Query("DELETE FROM activity_data WHERE timestamp < :expiryTime")
    suspend fun deleteExpired(expiryTime: Long)
}
