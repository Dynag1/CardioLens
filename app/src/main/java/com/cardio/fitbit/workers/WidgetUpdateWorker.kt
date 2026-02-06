package com.cardio.fitbit.workers

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cardio.fitbit.data.repository.HealthRepository
import com.cardio.fitbit.ui.widget.HealthWidget
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class WidgetUpdateWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val healthRepository: HealthRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            // Trigger Glance update
            // Since HealthWidget uses LaunchedEffect to fetch data inside provideGlance,
            // calling updateAll() should invalidate the widget and re-run provideGlance.
            HealthWidget(healthRepository).updateAll(applicationContext)
            
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }
}
