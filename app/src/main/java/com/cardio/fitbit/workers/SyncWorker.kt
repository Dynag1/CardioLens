package com.cardio.fitbit.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cardio.fitbit.data.repository.HealthRepository
import com.cardio.fitbit.data.repository.UserPreferencesRepository
import com.cardio.fitbit.utils.DateUtils
import com.cardio.fitbit.utils.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.ForegroundInfo

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val healthRepository: HealthRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val notificationHelper: NotificationHelper
) : CoroutineWorker(appContext, workerParams) {


// ...

    override suspend fun doWork(): Result {
        return try {
            // Set Foreground Service to show "Syncing" notification
            // This satisfies the FOREGROUND_SERVICE_DATA_SYNC requirement
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val notification = notificationHelper.getSyncNotification()
                    val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    } else {
                        0 
                    }
                    // Use a unique ID for the sync notification
                    setForeground(ForegroundInfo(NotificationHelper.SYNC_NOTIFICATION_ID, notification, type))
                }
            } catch (e: Exception) {

            }


            

            
            // 1. Fetch latest data (today) - FORCE REFRESH to bypass 24h cache in background
            val today = DateUtils.getToday()

            val result = healthRepository.getIntradayData(today, forceRefresh = true)
            
            if (result.isSuccess) {
                val data = result.getOrNull()
                
                if (data != null && data.minuteData.isNotEmpty()) {
                    val highThreshold = userPreferencesRepository.highHrThreshold.first()
                    val lowThreshold = userPreferencesRepository.lowHrThreshold.first()
                    val notificationsEnabled = userPreferencesRepository.notificationsEnabled.first()
                    

                    
                    if (notificationsEnabled) {
                        // Check last 20 minutes for any violation (covers 15min sync + buffer)
                        val recentMeasurements = data.minuteData.takeLast(20)
                        

                        
                        // Get last alert timestamps to avoid duplicate notifications
                        val prefs = applicationContext.getSharedPreferences("cardio_alerts", Context.MODE_PRIVATE)
                        val lastHighAlertTime = prefs.getLong("last_high_alert", 0L)
                        val lastLowAlertTime = prefs.getLong("last_low_alert", 0L)
                        val currentTime = System.currentTimeMillis()
                        val alertCooldown = 60 * 60 * 1000L // 60 minutes cooldown between alerts
                        
                        // Check for high HR
                        val highHrViolation = recentMeasurements.find { it.heartRate > highThreshold }
                        if (highHrViolation != null && (currentTime - lastHighAlertTime) > alertCooldown) {
                            notificationHelper.showHeartRateAlert(
                                highHrViolation.heartRate, 
                                true, 
                                highThreshold,
                                highHrViolation.time
                            )
                            prefs.edit().putLong("last_high_alert", currentTime).apply()

                        } else if (highHrViolation != null) {

                        }
                        
                        // Check for low HR (only if HR > 0, to avoid false alarms with missing data)
                        val lowHrViolation = recentMeasurements.find { 
                            it.heartRate > 0 && it.heartRate < lowThreshold 
                        }
                        if (lowHrViolation != null && (currentTime - lastLowAlertTime) > alertCooldown) {
                            notificationHelper.showHeartRateAlert(
                                lowHrViolation.heartRate, 
                                false, 
                                lowThreshold,
                                lowHrViolation.time
                            )
                            prefs.edit().putLong("last_low_alert", currentTime).apply()

                        } else if (lowHrViolation != null) {

                        }
                        
                        if (highHrViolation == null && lowHrViolation == null) {

                        }
                    } else {

                    }
                } else {

                }
            } else {

                return Result.retry()
            }
            

            Result.success()
        } catch (e: Exception) {

            Result.failure()
        }
    }
    override suspend fun getForegroundInfo(): ForegroundInfo {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notification = notificationHelper.getSyncNotification()
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }
            return ForegroundInfo(NotificationHelper.SYNC_NOTIFICATION_ID, notification, type)
        } else {
             throw IllegalStateException("Foreground Service not supported on this API level")
        }
    }
}
