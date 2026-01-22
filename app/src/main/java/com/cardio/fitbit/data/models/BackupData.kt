package com.cardio.fitbit.data.models

import com.cardio.fitbit.data.local.entities.*

data class BackupData(
    val version: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val intradayData: List<IntradayDataEntity> = emptyList(),
    val sleepData: List<SleepDataEntity> = emptyList(),
    val activityData: List<ActivityDataEntity> = emptyList(),
    val hrvData: List<HrvDataEntity> = emptyList(),
    val heartRateData: List<HeartRateDataEntity> = emptyList(),
    val stepsData: List<StepsDataEntity> = emptyList(),
    val moodEntries: List<MoodEntry> = emptyList(),
    val spo2Data: List<SpO2DataEntity> = emptyList(),
    val symptomEntries: List<SymptomEntry> = emptyList(),
    val dateOfBirth: Long? = null
)
