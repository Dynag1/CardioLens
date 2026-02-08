package com.cardio.fitbit.ui.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import android.content.Context
import com.cardio.fitbit.data.repository.HealthRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class HealthWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget
        get() = HealthWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        scheduleUpdate(context)
    }
    
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        androidx.work.WorkManager.getInstance(context).cancelUniqueWork("widget_update")
    }
    
    private fun scheduleUpdate(context: Context) {
        val request = androidx.work.PeriodicWorkRequestBuilder<com.cardio.fitbit.workers.WidgetUpdateWorker>(
            30, java.util.concurrent.TimeUnit.MINUTES // Min is 15
        ).build()
        
        androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "widget_update",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
