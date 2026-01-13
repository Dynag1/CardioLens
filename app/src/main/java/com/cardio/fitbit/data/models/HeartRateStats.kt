package com.cardio.fitbit.data.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Heart rate statistics calculated from intraday data
 */
@Parcelize
data class HeartRateStats(
    val sleepHeartRate: Int?,      // Average HR during sleep
    val restingHeartRate: Int?,    // Average HR during rest (awake, no activity)
    val averageHeartRate: Int?,    // Overall average
    val minHeartRate: Int?,        // Minimum HR
    val maxHeartRate: Int?         // Maximum HR
) : Parcelable

/**
 * Calculate heart rate statistics from minute data and sleep info
 */
fun calculateHeartRateStats(
    minuteData: List<MinuteData>,
    sleepStartHour: Int?,
    sleepEndHour: Int?
): HeartRateStats {
    if (minuteData.isEmpty()) {
        return HeartRateStats(null, null, null, null, null)
    }

    val allHeartRates = minuteData.mapNotNull { if (it.heartRate > 0) it.heartRate else null }
    
    // Calculate sleep heart rate (during sleep hours)
    val sleepHeartRates = if (sleepStartHour != null && sleepEndHour != null) {
        minuteData.filter { data ->
            val hour = data.time.substring(0, 2).toIntOrNull() ?: 0
            val isDuringSleep = if (sleepStartHour > sleepEndHour) {
                hour >= sleepStartHour || hour < sleepEndHour
            } else {
                hour >= sleepStartHour && hour < sleepEndHour
            }
            isDuringSleep && data.heartRate > 0
        }.map { it.heartRate }
    } else emptyList()
    
    // Calculate resting heart rate (awake, no steps)
    val restingHeartRates = minuteData.filter { data ->
        val hour = data.time.substring(0, 2).toIntOrNull() ?: 0
        val isDuringSleep = if (sleepStartHour != null && sleepEndHour != null) {
            if (sleepStartHour > sleepEndHour) {
                hour >= sleepStartHour || hour < sleepEndHour
            } else {
                hour >= sleepStartHour && hour < sleepEndHour
            }
        } else false
        
        // Exclude sleep AND exclude moments with steps
        !isDuringSleep && data.steps == 0 && data.heartRate > 0
    }.map { it.heartRate }

    return HeartRateStats(
        sleepHeartRate = if (sleepHeartRates.isNotEmpty()) sleepHeartRates.average().toInt() else null,
        restingHeartRate = if (restingHeartRates.isNotEmpty()) restingHeartRates.average().toInt() else null,
        averageHeartRate = if (allHeartRates.isNotEmpty()) allHeartRates.average().toInt() else null,
        minHeartRate = allHeartRates.minOrNull(),
        maxHeartRate = allHeartRates.maxOrNull()
    )
}
