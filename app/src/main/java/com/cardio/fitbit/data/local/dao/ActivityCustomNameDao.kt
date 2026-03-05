package com.cardio.fitbit.data.local.dao

import androidx.room.*
import com.cardio.fitbit.data.local.entities.ActivityCustomNameEntity
import com.cardio.fitbit.data.local.entities.WorkoutTagEntity

@Dao
interface ActivityCustomNameDao {
    @Query("SELECT * FROM activity_custom_names")
    suspend fun getAllCustomNames(): List<ActivityCustomNameEntity>

    @Query("SELECT * FROM activity_custom_names WHERE activityId = :activityId")
    suspend fun getCustomName(activityId: Long): ActivityCustomNameEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomName(entity: ActivityCustomNameEntity)
}

@Dao
interface WorkoutTagDao {
    @Query("SELECT * FROM workout_tags")
    suspend fun getAllTags(): List<WorkoutTagEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(entity: WorkoutTagEntity)
}
