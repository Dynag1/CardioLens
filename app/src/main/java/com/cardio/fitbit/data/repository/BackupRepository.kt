package com.cardio.fitbit.data.repository

import com.cardio.fitbit.data.local.AppDatabase
import com.cardio.fitbit.data.models.BackupData
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.firstOrNull
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import javax.inject.Inject
import javax.inject.Singleton

import androidx.room.withTransaction

@Singleton
class BackupRepository @Inject constructor(
    private val database: AppDatabase,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    private val gson: Gson = GsonBuilder()
        .setDateFormat("yyyy-MM-dd'T'HH:mm:ss")
        .create()

    suspend fun exportData(outputStream: OutputStream): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val backupData = BackupData(
                intradayData = database.intradayDataDao().getAll(),
                sleepData = database.sleepDataDao().getAll(),
                activityData = database.activityDataDao().getAll(),
                hrvData = database.hrvDataDao().getAll(),
                heartRateData = database.heartRateDao().getAll(),
                stepsData = database.stepsDao().getAll(),
                moodEntries = database.moodDao().getAll(),
                spo2Data = database.spo2Dao().getAll(),
                symptomEntries = database.symptomDao().getAllSymptoms(),
                dateOfBirth = kotlinx.coroutines.flow.firstOrNull(userPreferencesRepository.dateOfBirth)
            )

            OutputStreamWriter(outputStream).use { writer ->
                gson.toJson(backupData, writer)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importData(inputStream: InputStream): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val backupData = InputStreamReader(inputStream).use { reader ->
                gson.fromJson(reader, BackupData::class.java)
            }

            database.withTransaction {
                // Upsert strategy (Insert with REPLACE)
                if (backupData.intradayData.isNotEmpty()) database.intradayDataDao().insertAll(backupData.intradayData)
                if (backupData.sleepData.isNotEmpty()) database.sleepDataDao().insertAll(backupData.sleepData)
                if (backupData.activityData.isNotEmpty()) database.activityDataDao().insertAll(backupData.activityData)
                if (backupData.hrvData.isNotEmpty()) database.hrvDataDao().insertAll(backupData.hrvData)
                if (backupData.heartRateData.isNotEmpty()) database.heartRateDao().insertAll(backupData.heartRateData)
                if (backupData.stepsData.isNotEmpty()) database.stepsDao().insertAll(backupData.stepsData)
                if (backupData.moodEntries.isNotEmpty()) database.moodDao().insertAll(backupData.moodEntries)
                if (backupData.spo2Data.isNotEmpty()) database.spo2Dao().insertAll(backupData.spo2Data)
                if (backupData.symptomEntries.isNotEmpty()) database.symptomDao().insertAll(backupData.symptomEntries)
            }
            
            // Restore Preferences
            backupData.dateOfBirth?.let { dob ->
                userPreferencesRepository.setDateOfBirth(dob)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
