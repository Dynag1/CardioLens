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
                        // Check last 30 minutes for any violation
                        val recentMeasurements = data.minuteData.takeLast(30)
                        val maxHr = recentMeasurements.maxOfOrNull { it.heartRate } ?: 0
                        val minHr = recentMeasurements.filter { it.heartRate > 0 }.minOfOrNull { it.heartRate } ?: 0
                        
                        android.util.Log.d("SyncWorker", "Analyzing ${recentMeasurements.size} measurements. Window stats: Min=$minHr, Max=$maxHr BPM")
                        
                        // Check for high HR
                        val highHrViolation = recentMeasurements.find { it.heartRate > highThreshold }
                        if (highHrViolation != null) {
                            notificationHelper.showHeartRateAlert(
                                highHrViolation.heartRate, 
                                true, 
                                highThreshold
                            )
                            android.util.Log.w("SyncWorker", "High HR alert: ${highHrViolation.heartRate} BPM > $highThreshold BPM at ${highHrViolation.time}")
                        }
                        
                        // Check for low HR (only if HR > 0, to avoid false alarms with missing data)
                        val lowHrViolation = recentMeasurements.find { 
                            it.heartRate > 0 && it.heartRate < lowThreshold 
                        }
                        if (lowHrViolation != null) {
                            notificationHelper.showHeartRateAlert(
                                lowHrViolation.heartRate, 
                                false, 
                                lowThreshold
                            )
                            android.util.Log.w("SyncWorker", "Low HR alert: ${lowHrViolation.heartRate} BPM < $lowThreshold BPM at ${lowHrViolation.time}")
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
