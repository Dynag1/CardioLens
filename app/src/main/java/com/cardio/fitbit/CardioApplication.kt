package com.cardio.fitbit

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import com.cardio.fitbit.utils.NotificationHelper

@HiltAndroidApp
class CardioApplication : Application(), Configuration.Provider {
    
    @Inject
    lateinit var workerFactory: HiltWorkerFactory
    
    @Inject
    lateinit var notificationHelper: NotificationHelper

    companion object {
        var instance: CardioApplication? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        notificationHelper.createNotificationChannel()
        setupRecurringBackup()
    }

    private fun setupRecurringBackup() {
        val constraints = androidx.work.Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiresDeviceIdle(true) // Run when user is not using device (e.g. at night)
            .build()

        val backupRequest = androidx.work.PeriodicWorkRequestBuilder<com.cardio.fitbit.workers.AutoBackupWorker>(
            1, java.util.concurrent.TimeUnit.DAYS
        )
            .setConstraints(constraints)
            .setInitialDelay(2, java.util.concurrent.TimeUnit.HOURS) // Delay first run a bit
            .build()

        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "DailyBackup",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            backupRequest
        )
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
