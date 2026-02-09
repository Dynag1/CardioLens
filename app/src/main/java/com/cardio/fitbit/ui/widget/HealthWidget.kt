package com.cardio.fitbit.ui.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.cardio.fitbit.ui.MainActivity
import com.cardio.fitbit.R

class HealthWidget : GlanceAppWidget() {

    companion object {
        val KEY_RHR = intPreferencesKey("rhr")
        val KEY_LAST_HR = intPreferencesKey("last_hr")
        val KEY_STEPS = intPreferencesKey("steps")
        val KEY_LAST_TIME = stringPreferencesKey("last_time")
        val KEY_LAST_SYNC_STATUS = stringPreferencesKey("last_sync_status") // For "...", "Err", etc.
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                WidgetContent(context)
            }
        }
    }

    @Composable
    private fun WidgetContent(context: Context) {
        val prefs = currentState<Preferences>()
        val rhr = prefs[KEY_RHR]
        val lastHr = prefs[KEY_LAST_HR]
        val steps = prefs[KEY_STEPS]
        val lastTime = prefs[KEY_LAST_TIME]
        // Default to "..." if null, or error text if status indicates error
        val status = prefs[KEY_LAST_SYNC_STATUS]

        val displayRhr = rhr?.toString() ?: "--"
        val displayLastHr = lastHr?.toString() ?: "--"
        val displaySteps = steps?.toString() ?: "--"
        val displayTime = if (lastTime != null) "$lastTime" else (status ?: "...")

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .padding(8.dp)
                .clickable(
                    actionStartActivity(
                        android.content.Intent(
                            context,
                            MainActivity::class.java
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically
            ) {

                // Top Row: RHR and Last HR
                Row(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // RHR
                    MetricItem(context.getString(R.string.widget_rhr), displayRhr, "")

                    Spacer(GlanceModifier.width(24.dp))

                    // Last HR
                    MetricItem(context.getString(R.string.widget_last), displayLastHr, "")
                }

                Spacer(GlanceModifier.size(8.dp))

                // Bottom Row: Steps
                MetricItem(context.getString(R.string.widget_steps), displaySteps, "")
            }
        }
    }

    @Composable
    private fun MetricItem(label: String, value: String, unit: String) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant)
            )
            Text(
                text = value,
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    color = GlanceTheme.colors.onSurface
                )
            )
            if (unit.isNotEmpty()) {
                Text(
                    text = unit,
                    style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant)
                )
            }
        }
    }
}
