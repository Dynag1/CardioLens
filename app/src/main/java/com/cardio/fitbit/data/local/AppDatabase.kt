package com.cardio.fitbit.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.cardio.fitbit.data.local.dao.IntradayDataDao
import com.cardio.fitbit.data.local.dao.SleepDataDao
import com.cardio.fitbit.data.local.dao.ActivityDataDao
import com.cardio.fitbit.data.local.dao.HrvDataDao
import com.cardio.fitbit.data.local.dao.HeartRateDao
import com.cardio.fitbit.data.local.dao.StepsDao
import com.cardio.fitbit.data.local.dao.MoodDao
import com.cardio.fitbit.data.local.entities.IntradayDataEntity
import com.cardio.fitbit.data.local.entities.SleepDataEntity
import com.cardio.fitbit.data.local.entities.ActivityDataEntity
import com.cardio.fitbit.data.local.entities.HrvDataEntity
import com.cardio.fitbit.data.local.entities.HeartRateDataEntity
import com.cardio.fitbit.data.local.entities.StepsDataEntity
import com.cardio.fitbit.data.local.entities.MoodEntry

@Database(
    entities = [
        IntradayDataEntity::class,
        SleepDataEntity::class,
        ActivityDataEntity::class,
        HrvDataEntity::class,
        HeartRateDataEntity::class,
        StepsDataEntity::class,
        MoodEntry::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun intradayDataDao(): IntradayDataDao
    abstract fun sleepDataDao(): SleepDataDao
    abstract fun activityDataDao(): ActivityDataDao
    abstract fun hrvDataDao(): HrvDataDao
    abstract fun heartRateDao(): HeartRateDao
    abstract fun stepsDao(): StepsDao
    abstract fun moodDao(): MoodDao
}
