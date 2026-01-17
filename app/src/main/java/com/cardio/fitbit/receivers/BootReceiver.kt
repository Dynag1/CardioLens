package com.cardio.fitbit.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.cardio.fitbit.workers.SyncWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import com.cardio.fitbit.data.repository.UserPreferencesRepository

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {

            
            // GoAsync if needed, but for WorkManager enqueue it's usually fast enough.
            // However, we need to read DataStore which is suspend.
            val pendingResult = goAsync()
            
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val intervalMinutes = userPreferencesRepository.syncIntervalMinutes.first()
                    scheduleSync(context, intervalMinutes)
                } catch (e: Exception) {

                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private fun scheduleSync(context: Context, intervalMinutes: Int) {
        // If interval is 0, don't schedule sync
        if (intervalMinutes == 0) {

            return
        }
        
        // WorkManager requires minimum 15 minutes
        val actualInterval = kotlin.math.max(intervalMinutes, 15)
        
        if (intervalMinutes < 15) {

        }
        
        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(false)
            .build()
        
        val workRequest = PeriodicWorkRequest.Builder(
            SyncWorker::class.java,
            actualInterval.toLong(),
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "CardioSyncWork",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
        

    }
}
