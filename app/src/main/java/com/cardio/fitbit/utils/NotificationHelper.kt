package com.cardio.fitbit.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.cardio.fitbit.R
import com.cardio.fitbit.ui.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_ID = "cardio_alerts_v3"
        const val SYNC_CHANNEL_ID = "cardio_sync_v2"
        const val NOTIFICATION_ID = 1001
        const val SYNC_NOTIFICATION_ID = 1002
    }

    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Alert Channel
            val name = context.getString(R.string.channel_alerts_name)
            val descriptionText = context.getString(R.string.channel_alerts_desc)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            
            // Sync Channel (High Importance for Demo)
            val syncName = context.getString(R.string.channel_sync_name)
            val syncDesc = context.getString(R.string.channel_sync_desc)
            val syncImportance = NotificationManager.IMPORTANCE_HIGH
            val syncChannel = NotificationChannel(SYNC_CHANNEL_ID, syncName, syncImportance).apply {
                description = syncDesc
            }
            
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            notificationManager.createNotificationChannel(syncChannel)
        }
    }

    fun getSyncNotification(): android.app.Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(context, SYNC_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(context.getString(R.string.notif_sync_title))
            .setContentText(context.getString(R.string.notif_sync_text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    fun showHeartRateAlert(bpm: Int, isHigh: Boolean, threshold: Int, time: String) {
        val title = if (isHigh) context.getString(R.string.notif_high_hr_title) else context.getString(R.string.notif_low_hr_title)
        val message = context.getString(R.string.notif_hr_message, bpm, threshold, time)
        
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.star_on) // TODO: Use a real heart icon
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
             // Check permission for Android 13+ inside Activity/Fragment usually, 
             // but here we assume permission is granted or we just try.
             val notificationManager = NotificationManagerCompat.from(context)
             // SecurityException can happen if POST_NOTIFICATIONS is not granted
             notificationManager.notify(NOTIFICATION_ID, builder.build())

        } catch (e: SecurityException) {
            // Permission not granted

        }
    }

    fun showWorkoutSummary(activityName: String, duration: String, distance: String, avgHr: Int, calories: Int) {
        val title = context.getString(R.string.notif_workout_title, activityName)
        val message = "$duration | $distance | $avgHr bpm | $calories kcal"

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message)) // Expandable for details
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
        } catch (e: SecurityException) {
            // Permission check handled by caller or ignored if missing
        }
    }
}
