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
        val KEY_READINESS = intPreferencesKey("readiness")
        val KEY_STEP_GOAL = intPreferencesKey("step_goal")
        val KEY_WORKOUT_COUNT = intPreferencesKey("workout_count")
        val KEY_WORKOUT_GOAL = intPreferencesKey("workout_goal")
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
        val stepGoal = prefs[KEY_STEP_GOAL] ?: 10000
        val workoutCount = prefs[KEY_WORKOUT_COUNT] ?: 0
        val workoutGoal = prefs[KEY_WORKOUT_GOAL] ?: 3
        val readiness = prefs[KEY_READINESS]
        val lastTime = prefs[KEY_LAST_TIME]
        val status = prefs[KEY_LAST_SYNC_STATUS]

        val displayRhr = rhr?.toString() ?: "--"
        val displayLastHr = lastHr?.toString() ?: "--"
        val displaySteps = steps?.toString() ?: "--"
        val displayReadiness = readiness?.toString() ?: "--"
        val displayTime = if (lastTime != null) "$lastTime" else (status ?: "...")

        // Progress calculations
        val stepProgress = if (stepGoal > 0) (steps ?: 0).toFloat() / stepGoal else 0f
        val workoutProgress = if (workoutGoal > 0) workoutCount.toFloat() / workoutGoal else 0f

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
                // Top Row: Readiness & RHR
                Row(
                    modifier = GlanceModifier.padding(bottom = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MetricItem("REC", displayReadiness, "")
                    Spacer(GlanceModifier.width(12.dp))
                    MetricItem("RHR", displayRhr, "")
                    Spacer(GlanceModifier.width(12.dp))
                    MetricItem("DER", displayLastHr, "")
                }

                // Middle: Large Steps
                Text(
                    text = displaySteps,
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = GlanceTheme.colors.onSurface
                    )
                )

                // Bottom: Goals Progress Mini-Bars (Simple indicators)
                Row(
                    modifier = GlanceModifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GoalIndicator("Pas", stepProgress)
                    Spacer(GlanceModifier.width(8.dp))
                    GoalIndicator("Entr", workoutProgress)
                }
            }
        }
    }

    @Composable
    private fun GoalIndicator(label: String, progress: Float) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = GlanceModifier
                    .size(width = 32.dp, height = 4.dp)
                    .background(androidx.glance.unit.ColorProvider(androidx.compose.ui.graphics.Color.Gray.copy(alpha = 0.2f)))
            ) {
                Box(
                    modifier = GlanceModifier
                        .size(width = (32 * progress.coerceIn(0f, 1f)).dp, height = 4.dp)
                        .background(if (progress >= 1f) androidx.glance.unit.ColorProvider(androidx.compose.ui.graphics.Color(0xFF4CAF50)) else GlanceTheme.colors.primary)
                ) {}
            }
            Text(label, style = TextStyle(fontSize = 8.sp, color = GlanceTheme.colors.onSurfaceVariant))
        }
    }

    @Composable
    private fun MetricItem(label: String, value: String, unit: String, isLarge: Boolean = false) {
        val fontSize = if (isLarge) {
            when {
                value.length > 6 -> 11.sp
                value.length > 5 -> 12.sp
                value.length > 4 -> 13.sp
                else -> 15.sp
            }
        } else {
            when {
                value.length > 5 -> 11.sp
                value.length > 4 -> 12.sp
                else -> 13.sp
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                style = TextStyle(
                    fontSize = 10.sp, 
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontWeight = FontWeight.Normal
                )
            )
            Text(
                text = value,
                maxLines = 1,
                style = TextStyle(
                    fontWeight = FontWeight.Medium,
                    fontSize = fontSize,
                    color = GlanceTheme.colors.onSurface
                )
            )
            if (unit.isNotEmpty()) {
                Text(
                    text = unit,
                    style = TextStyle(fontSize = 10.sp, color = GlanceTheme.colors.onSurfaceVariant)
                )
            }
        }
    }
}
