package com.cardio.fitbit.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.cardio.fitbit.data.local.dao.IntradayDataDao
import com.cardio.fitbit.data.local.dao.SleepDataDao
import com.cardio.fitbit.data.local.dao.ActivityDataDao
import com.cardio.fitbit.data.local.entities.IntradayDataEntity
import com.cardio.fitbit.data.local.entities.SleepDataEntity
import com.cardio.fitbit.data.local.entities.ActivityDataEntity

@Database(
    entities = [
        IntradayDataEntity::class,
        SleepDataEntity::class,
        ActivityDataEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun intradayDataDao(): IntradayDataDao
    abstract fun sleepDataDao(): SleepDataDao
    abstract fun activityDataDao(): ActivityDataDao
}
