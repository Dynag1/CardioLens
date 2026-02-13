package com.cardio.fitbit.di

import android.content.Context
import androidx.room.Room
import com.cardio.fitbit.data.local.AppDatabase
import com.cardio.fitbit.data.local.dao.IntradayDataDao
import com.cardio.fitbit.data.local.dao.SleepDataDao
import com.cardio.fitbit.data.local.dao.ActivityDataDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "cardio_database"
        )
        .fallbackToDestructiveMigration()
        .build()
    }
    
    @Provides
    fun provideIntradayDataDao(database: AppDatabase): IntradayDataDao {
        return database.intradayDataDao()
    }
    
    @Provides
    fun provideSleepDataDao(database: AppDatabase): SleepDataDao {
        return database.sleepDataDao()
    }
    
    @Provides
    fun provideActivityDataDao(database: AppDatabase): ActivityDataDao {
        return database.activityDataDao()
    }

    @Provides
    fun provideHrvDataDao(database: AppDatabase): com.cardio.fitbit.data.local.dao.HrvDataDao {
        return database.hrvDataDao()
    }

    @Provides
    fun provideHeartRateDao(database: AppDatabase): com.cardio.fitbit.data.local.dao.HeartRateDao {
        return database.heartRateDao()
    }

    @Provides
    fun provideStepsDao(database: AppDatabase): com.cardio.fitbit.data.local.dao.StepsDao {
        return database.stepsDao()
    }

    @Provides
    fun provideMoodDao(database: AppDatabase): com.cardio.fitbit.data.local.dao.MoodDao {
        return database.moodDao()
    }

    @Provides
    fun provideSpO2Dao(database: AppDatabase): com.cardio.fitbit.data.local.dao.SpO2Dao {
        return database.spo2Dao()
    }

    @Provides
    fun provideSymptomDao(database: AppDatabase): com.cardio.fitbit.data.local.dao.SymptomDao {
        return database.symptomDao()
    }

    @Provides
    fun provideActivityDetailsDao(database: AppDatabase): com.cardio.fitbit.data.local.dao.ActivityDetailsDao {
        return database.activityDetailsDao()
    }

    @Provides
    fun provideWorkoutIntensityDao(database: AppDatabase): com.cardio.fitbit.data.local.dao.WorkoutIntensityDao {
        return database.workoutIntensityDao()
    }
}
