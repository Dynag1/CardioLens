package com.cardio.fitbit.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cardio.fitbit.data.local.entities.ActivityDetailsEntity

@Dao
interface ActivityDetailsDao {
    @Query("SELECT * FROM activity_details WHERE activityId = :activityId")
    suspend fun getActivityDetails(activityId: String): ActivityDetailsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivityDetails(details: ActivityDetailsEntity)

    @Query("DELETE FROM activity_details WHERE timestamp < :threshold")
    suspend fun clearOldCache(threshold: Long)
}
