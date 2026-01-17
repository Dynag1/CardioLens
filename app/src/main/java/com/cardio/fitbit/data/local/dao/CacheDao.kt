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

@Dao
interface HrvDataDao {
    @Query("SELECT * FROM hrv_data WHERE date = :date")
    suspend fun getByDate(date: String): com.cardio.fitbit.data.local.entities.HrvDataEntity?
    
    @Query("SELECT * FROM hrv_data WHERE date BETWEEN :startDate AND :endDate")
    suspend fun getBetweenDates(startDate: String, endDate: String): List<com.cardio.fitbit.data.local.entities.HrvDataEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(data: com.cardio.fitbit.data.local.entities.HrvDataEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(data: List<com.cardio.fitbit.data.local.entities.HrvDataEntity>)
    
    @Query("DELETE FROM hrv_data WHERE timestamp < :expiryTime")
    suspend fun deleteExpired(expiryTime: Long)
}

@Dao
interface HeartRateDao {
    @Query("SELECT * FROM heart_rate_data WHERE date = :date")
    suspend fun getByDate(date: String): com.cardio.fitbit.data.local.entities.HeartRateDataEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(data: com.cardio.fitbit.data.local.entities.HeartRateDataEntity)
    
    @Query("DELETE FROM heart_rate_data WHERE timestamp < :expiryTime")
    suspend fun deleteExpired(expiryTime: Long)
}

@Dao
interface StepsDao {
    @Query("SELECT * FROM steps_data WHERE date = :date")
    suspend fun getByDate(date: String): com.cardio.fitbit.data.local.entities.StepsDataEntity?
    
    @Query("SELECT * FROM steps_data WHERE date BETWEEN :startDate AND :endDate")
    suspend fun getBetweenDates(startDate: String, endDate: String): List<com.cardio.fitbit.data.local.entities.StepsDataEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(data: com.cardio.fitbit.data.local.entities.StepsDataEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(data: List<com.cardio.fitbit.data.local.entities.StepsDataEntity>)
    
    @Query("DELETE FROM steps_data WHERE timestamp < :expiryTime")
    suspend fun deleteExpired(expiryTime: Long)
}
