package com.cardio.fitbit.utils

import com.cardio.fitbit.data.models.MinuteData
import com.cardio.fitbit.data.models.SleepData
import com.cardio.fitbit.data.models.ActivityData
import java.util.Calendar
import java.util.Date

object HeartRateAnalysisUtils {

    data class RhrResult(
        val rhrDay: Int?,
        val rhrNight: Int?,
        val rhrAvg: Int?
    )

    fun calculateDailyRHR(
        date: Date,
        intraday: List<MinuteData>,
        sleep: List<SleepData>,
        activity: ActivityData?,
        preMidnightHeartRates: List<Int> = emptyList()
    ): RhrResult {
        if (intraday.isEmpty()) {
            return RhrResult(null, null, null)
        }

        // Helper to get timestamp from HH:mm or HH:mm:ss string for THIS date
        fun getTimestamp(timeStr: String): Long {
            val parts = timeStr.split(":")
            val c = Calendar.getInstance().apply { time = date }
            c.set(Calendar.HOUR_OF_DAY, parts[0].toInt())
            c.set(Calendar.MINUTE, parts[1].toInt())
            if (parts.size > 2) {
                c.set(Calendar.SECOND, parts[2].toInt())
            } else {
                c.set(Calendar.SECOND, 0)
            }
            c.set(Calendar.MILLISECOND, 0)
            return c.timeInMillis
        }

        // Parse and Sort Intraday Data
        val sortedMinutes = intraday.map { data -> 
            val ts = getTimestamp(data.time)
            Triple(data, ts, data.heartRate)
        }.sortedBy { it.second }

        // 1. Identify Activity & Sleep Ranges
        val sleepRanges = sleep.map { it.startTime.time..it.endTime.time }
        val activityRanges = activity?.activities?.map { 
            it.startTime.time..(it.startTime.time + it.duration) 
        } ?: emptyList()

        // 2. Mark Valid/Invalid Minutes
        val validMinutesMask = BooleanArray(sortedMinutes.size) { false }
        var cooldownUntil = 0L
        val COOLDOWN_MS = 15 * 60 * 1000L

        val nightHeartRates = preMidnightHeartRates.toMutableList()

        sortedMinutes.forEachIndexed { index, triple ->
            val data = triple.first
            val ts = triple.second
            val hr = triple.third
            
            // A. Sleep Check
            val isSleep = sleepRanges.any { range -> ts in range }
            if (isSleep) {
                if (hr > 0) nightHeartRates.add(hr) // Collect Night HR
                return@forEachIndexed // Exclude from Day RHR
            }

            // B. Activity Check (Logged Activity OR Steps > 0)
            val isLoggedActivity = activityRanges.any { range -> ts in range }
            val isStepActivity = data.steps > 0
            
            if (isLoggedActivity) {
                // Trigger Cooldown for Logged Activity
                cooldownUntil = ts + COOLDOWN_MS
                return@forEachIndexed
            }

            if (isStepActivity) {
                // Just skip this minute if moving, but don't trigger long cooldown unless high intensity?
                // For now, just strict skip.
                return@forEachIndexed
            }

            // C. Cooldown Check
            if (ts < cooldownUntil) {
                return@forEachIndexed
            }

            // D. Invalid Data Check (Noise/Device Off)
            // < 35 bpm considered physiological outlier/noise
            if (hr < 35) {
                return@forEachIndexed
            }

            // If passed all checks -> VALID Resting Minute
            validMinutesMask[index] = true
        }

        // 3. Calculate Night RHR (Average of Sleep HR - logic from Dashboard/Trends merge)
        // Note: Trends used average() on list, Dashboard used special logic for pre-midnight.
        // We will stick to the basic average of collected night heart rates for now as passed in 'sleep' data
        // For simple consistency:
        val rhrNight = if (nightHeartRates.isNotEmpty()) {
            nightHeartRates.average().toInt()
        } else null

        // 4. Calculate Day RHR (Sliding Window on Valid Minutes)
        // Scientific Standard: Lowest stable 10-minute average (matches Trends logic)
        val windowAverages = mutableListOf<Double>()
        val WINDOW_SIZE_MINUTES = 10
        
        // Iterate through minutes to find 10-min valid blocks
        for (i in 0..sortedMinutes.size - WINDOW_SIZE_MINUTES) {
            // Optimization: Skip if start is invalid
            if (!validMinutesMask[i]) continue

            val startTs = sortedMinutes[i].second
            val endTsTarget = startTs + (WINDOW_SIZE_MINUTES * 60 * 1000L)
            
            var sampleCount = 0
            var sumHr = 0.0
            var isValidWindow = true
            
            // Scan forward 
            for (j in i until sortedMinutes.size) {
                val currTs = sortedMinutes[j].second
                if (currTs >= endTsTarget) break // Window full
                
                // Strict Contiguity: Any invalid minute taints the window
                if (!validMinutesMask[j]) {
                    isValidWindow = false
                    break
                }
                
                sumHr += sortedMinutes[j].third
                sampleCount++
            }

            // Require density (at least 8 samples for a 10m window)
            if (isValidWindow && sampleCount >= 8) {
                windowAverages.add(sumHr / sampleCount)
            }
        }

        // 5. Select Resting Baseline (Median of Valid Windows) - Matches Trends Logic
        val rhrDay = if (windowAverages.isNotEmpty()) {
            val sorted = windowAverages.sorted()
            val mid = sorted.size / 2
            if (sorted.size % 2 == 0) ((sorted[mid-1] + sorted[mid]) / 2).toInt() else sorted[mid].toInt()
        } else null
        
        // 6. Calculate Average (Simple average of available metrics)
        val rhrAvg = when {
            rhrNight != null && rhrDay != null -> (rhrNight + rhrDay) / 2
            rhrNight != null -> rhrNight
            rhrDay != null -> rhrDay
            else -> null
        }

        return RhrResult(rhrDay, rhrNight, rhrAvg)
    }
}
