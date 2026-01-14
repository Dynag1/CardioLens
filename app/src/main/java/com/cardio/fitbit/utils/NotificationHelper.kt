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
        const val CHANNEL_ID = "cardio_alerts"
        const val NOTIFICATION_ID = 1001
    }

    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Alertes Cardio"
            val descriptionText = "Notifications pour fréquence cardiaque élevée ou basse"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showHeartRateAlert(bpm: Int, isHigh: Boolean, threshold: Int) {
        val title = if (isHigh) "Fréquence Cardiaque Élevée !" else "Fréquence Cardiaque Basse !"
        val message = "Votre rythme cardiaque est de $bpm BPM (Seuil: $threshold)"
        
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_icon) // Use vector icon
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
             // Check permission for Android 13+ inside Activity/Fragment usually, 
             // but here we assume permission is granted or we just try.
             val notificationManager = NotificationManagerCompat.from(context)
             // SecurityException can happen if POST_NOTIFICATIONS is not granted
             notificationManager.notify(NOTIFICATION_ID, builder.build())
             android.util.Log.d("NotificationHelper", "Notification sent successfully: $title")
        } catch (e: SecurityException) {
            // Permission not granted
            android.util.Log.e("NotificationHelper", "Permission denied for notifications", e)
        }
    }
}
