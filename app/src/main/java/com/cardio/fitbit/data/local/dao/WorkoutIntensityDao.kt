package com.cardio.fitbit.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cardio.fitbit.data.local.entities.WorkoutIntensityEntity

@Dao
interface WorkoutIntensityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIntensity(intensity: WorkoutIntensityEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(intensities: List<WorkoutIntensityEntity>)
    
    @Query("SELECT * FROM workout_intensity WHERE activityId = :activityId")
    suspend fun getIntensity(activityId: Long): WorkoutIntensityEntity?
    
    @Query("SELECT * FROM workout_intensity")
    suspend fun getAllIntensities(): List<WorkoutIntensityEntity>
    
    @Query("DELETE FROM workout_intensity WHERE activityId = :activityId")
    suspend fun deleteIntensity(activityId: Long)
}
