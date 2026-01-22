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
        preMidnightHeartRates: List<Int> = emptyList(),
        nativeRhr: Int? = null
    ): RhrResult {
        if (intraday.isEmpty()) {
            return RhrResult(nativeRhr, null, nativeRhr)
        }

        // Helper to get timestamp from HH:mm or HH:mm:ss string for THIS date
        fun getTimestamp(timeStr: String): Long {
            val parts = timeStr.split(":")
            val c = Calendar.getInstance().apply { time = date }
            c.set(Calendar.HOUR_OF_DAY, parts[0].toInt())
            c.set(Calendar.MINUTE, parts[1].toInt())
            c.set(Calendar.SECOND, 0) // Always normalise to 00 seconds for aggregation
            c.set(Calendar.MILLISECOND, 0)
            return c.timeInMillis
        }

        // AGGREGATION: Normalize to 1-minute buckets to maintain algorithm consistency (window sizes etc)
        // This handles cases where we might have second-by-second data mixed in.
        val aggregatedMinutes = intraday
            .groupBy { 
                // key by HH:mm
                if (it.time.length >= 5) it.time.substring(0, 5) else it.time
            }
            .map { (timePrefix, samples) ->
                // Average valid heart rates
                val validHrs = samples.map { it.heartRate }.filter { it > 0 }
                val avgHr = if (validHrs.isNotEmpty()) validHrs.average().toInt() else 0
                
                // Sum steps (if any sample has steps, the minute has steps)
                val totalSteps = samples.sumOf { it.steps }
                
                MinuteData(timePrefix, avgHr, totalSteps)
            }

        // Parse and Sort Intraday Data
        val sortedMinutes = aggregatedMinutes.map { data -> 
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

        // 4. Calculate Day RHR (Sedentary Blocks > 5 mins + 10th Percentile)
        // New algorithm: 
        // 1. Identify contiguous blocks of valid sedentary minutes.
        // 2. Filter for blocks > 5 minutes.
        // 3. Take 10th percentile of each block.
        // 4. Take 10th percentile of those block values.

        val blockRepresentativeValues = mutableListOf<Int>()
        var currentBlock = mutableListOf<Int>()

        for (i in 0 until sortedMinutes.size) {
            if (validMinutesMask[i]) {
                currentBlock.add(sortedMinutes[i].third)
            } else {
                // End of a block
                if (currentBlock.size >= 5) {
                    // Calculate 20th percentile of this block
                    blockRepresentativeValues.add(calculatePercentile(currentBlock, 20.0))
                }
                currentBlock.clear()
            }
        }
        // Check final block
        if (currentBlock.size >= 5) {
             blockRepresentativeValues.add(calculatePercentile(currentBlock, 20.0))
        }

        // 5. Final RHR Calculation
        val rhrDayComputed = if (blockRepresentativeValues.isNotEmpty()) {
            calculatePercentile(blockRepresentativeValues, 20.0)
        } else null

        val rhrDay = rhrDayComputed ?: nativeRhr
        
        // 6. Calculate Average (Simple average of available metrics)
        val rhrAvg = when {
            rhrNight != null && rhrDay != null -> (rhrNight + rhrDay) / 2
            rhrNight != null -> rhrNight
            rhrDay != null -> rhrDay
            else -> null
        }

        return RhrResult(rhrDay, rhrNight, rhrAvg)
    }

    // Helper for Percentile Calculation (Nearest Rank method)
    private fun calculatePercentile(data: List<Int>, percentile: Double): Int {
        if (data.isEmpty()) return 0
        val sorted = data.sorted()
        val index = kotlin.math.ceil((percentile / 100.0) * sorted.size).toInt() - 1
        return sorted[index.coerceIn(0, sorted.lastIndex)]
    }
}
