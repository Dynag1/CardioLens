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

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val healthRepository: HealthRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val notificationHelper: NotificationHelper
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            android.util.Log.d("SyncWorker", "Starting background sync...")
            
            // 1. Fetch latest data (today) - FORCE REFRESH to bypass 24h cache in background
            val today = DateUtils.getToday()
            android.util.Log.d("SyncWorker", "Fetching fresh intraday data for $today")
            val result = healthRepository.getIntradayData(today, forceRefresh = true)
            
            if (result.isSuccess) {
                val data = result.getOrNull()
                
                if (data != null && data.minuteData.isNotEmpty()) {
                    val highThreshold = userPreferencesRepository.highHrThreshold.first()
                    val lowThreshold = userPreferencesRepository.lowHrThreshold.first()
                    val notificationsEnabled = userPreferencesRepository.notificationsEnabled.first()
                    
                    android.util.Log.d("SyncWorker", "Checking ${data.minuteData.size} measurements. Thresholds: low=$lowThreshold, high=$highThreshold")
                    
                    if (notificationsEnabled) {
                        // Check last 20 minutes for any violation (covers 15min sync + buffer)
                        val recentMeasurements = data.minuteData.takeLast(20)
                        
                        android.util.Log.d("SyncWorker", "Analyzing ${recentMeasurements.size} recent measurements from last 20 minutes")
                        
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
                                highThreshold
                            )
                            prefs.edit().putLong("last_high_alert", currentTime).apply()
                            android.util.Log.w("SyncWorker", "High HR alert: ${highHrViolation.heartRate} BPM > $highThreshold BPM at ${highHrViolation.time}")
                        } else if (highHrViolation != null) {
                            android.util.Log.d("SyncWorker", "High HR detected but alert recently sent (cooldown active)")
                        }
                        
                        // Check for low HR (only if HR > 0, to avoid false alarms with missing data)
                        val lowHrViolation = recentMeasurements.find { 
                            it.heartRate > 0 && it.heartRate < lowThreshold 
                        }
                        if (lowHrViolation != null && (currentTime - lastLowAlertTime) > alertCooldown) {
                            notificationHelper.showHeartRateAlert(
                                lowHrViolation.heartRate, 
                                false, 
                                lowThreshold
                            )
                            prefs.edit().putLong("last_low_alert", currentTime).apply()
                            android.util.Log.w("SyncWorker", "Low HR alert: ${lowHrViolation.heartRate} BPM < $lowThreshold BPM at ${lowHrViolation.time}")
                        } else if (lowHrViolation != null) {
                            android.util.Log.d("SyncWorker", "Low HR detected but alert recently sent (cooldown active)")
                        }
                        
                        if (highHrViolation == null && lowHrViolation == null) {
                            android.util.Log.d("SyncWorker", "All measurements within normal range ($lowThreshold - $highThreshold BPM)")
                        }
                    } else {
                        android.util.Log.d("SyncWorker", "Notifications disabled, skipping alert checks")
                    }
                } else {
                    android.util.Log.w("SyncWorker", "No intraday data available")
                }
            } else {
                android.util.Log.e("SyncWorker", "Sync failed: ${result.exceptionOrNull()?.message}")
                return Result.retry()
            }
            
            android.util.Log.d("SyncWorker", "Sync completed successfully")
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("SyncWorker", "Sync exception", e)
            Result.failure()
        }
    }
}
