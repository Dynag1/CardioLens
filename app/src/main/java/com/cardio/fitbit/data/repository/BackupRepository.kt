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
import com.cardio.fitbit.data.local.entities.*
import com.google.gson.stream.JsonReader
import java.io.InputStreamReader

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
                dateOfBirth = userPreferencesRepository.dateOfBirth.firstOrNull()
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
            // Use Room transaction for atomicity (optional, but good for restore)
            // Note: For very large files, a single transaction might be too heavy?
            // SQLite is usually fine with large transactions if we just insert.
            database.withTransaction {
                val reader = com.google.gson.stream.JsonReader(InputStreamReader(inputStream, "UTF-8"))
                reader.beginObject() // Start of BackupData object

                while (reader.hasNext()) {
                    val name = reader.nextName()
                    when (name) {
                        "dateOfBirth" -> {
                             val dob = reader.nextLong()
                             userPreferencesRepository.setDateOfBirth(dob)
                        }
                        "intradayData" -> parseAndInsertBatch(reader, IntradayDataEntity::class.java) { database.intradayDataDao().insertAll(it) }
                        "sleepData" -> parseAndInsertBatch(reader, SleepDataEntity::class.java) { database.sleepDataDao().insertAll(it) }
                        "activityData" -> parseAndInsertBatch(reader, ActivityDataEntity::class.java) { database.activityDataDao().insertAll(it) }
                        "hrvData" -> parseAndInsertBatch(reader, HrvDataEntity::class.java) { database.hrvDataDao().insertAll(it) }
                        "heartRateData" -> parseAndInsertBatch(reader, HeartRateDataEntity::class.java) { database.heartRateDao().insertAll(it) }
                        "stepsData" -> parseAndInsertBatch(reader, StepsDataEntity::class.java) { database.stepsDao().insertAll(it) }
                        "moodEntries" -> parseAndInsertBatch(reader, MoodEntry::class.java) { database.moodDao().insertAll(it) }
                        "spo2Data" -> parseAndInsertBatch(reader, SpO2DataEntity::class.java) { database.spo2Dao().insertAll(it) }
                        "symptomEntries" -> parseAndInsertBatch(reader, SymptomEntry::class.java) { database.symptomDao().insertAll(it) }
                        else -> reader.skipValue() // Skip version, timestamp, etc.
                    }
                }
                reader.endObject()
                reader.close()
            }

            Result.success(Unit)
        } catch (e: Throwable) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private suspend fun <T> parseAndInsertBatch(
        reader: com.google.gson.stream.JsonReader,
        clazz: Class<T>,
        insertFn: suspend (List<T>) -> Unit
    ) {
        val batchSize = 500
        val batch = ArrayList<T>(batchSize)
        
        reader.beginArray()
        while (reader.hasNext()) {
            val item = gson.fromJson<T>(reader, clazz)
            if (item != null) {
                batch.add(item)
            }
            
            if (batch.size >= batchSize) {
                insertFn(batch)
                batch.clear()
            }
        }
        // Insert remaining
        if (batch.isNotEmpty()) {
            insertFn(batch)
        }
        reader.endArray()
    }
}
