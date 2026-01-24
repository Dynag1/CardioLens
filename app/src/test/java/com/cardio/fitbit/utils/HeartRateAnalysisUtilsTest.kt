package com.cardio.fitbit.utils

import com.cardio.fitbit.data.models.ActivityData
import com.cardio.fitbit.data.models.MinuteData
import com.cardio.fitbit.data.models.SleepData
import com.cardio.fitbit.data.models.SleepStages
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.Date

class HeartRateAnalysisUtilsTest {

    @Test
    fun calculateDailyRHR_excludesSameDayNightSleep() {
        // Setup Date: Jan 24, 2026
        val cal = Calendar.getInstance()
        cal.set(2026, Calendar.JANUARY, 24, 12, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val date = cal.time

        // 1. Setup Intraday Data
        // - "Yesterday Night" Sleep (should be INCLUDED if passed via preMidnight, logic is weird, let's stick to what we see)
        // Actually, HeartRateAnalysisUtils logic for "preMidnightHeartRates" is just "add these to night HR".
        // The issue is about "Sleep ranges" excluding data from Day RHR and contributing to Night RHR.
        // We want to simulate a sleep session starting at 22:00 on Jan 24 (SAME DAY).
        // This sleep session should NOT contribute to Jan 24th's "Night RHR" (which should be "Last Night").
        // It SHOULD be excluded from "Day RHR" calculation though.

        val intraday = mutableListOf<MinuteData>()
        
        // Add some day data (12:00 - 13:00) - Resting
        for (m in 0 until 60) {
            val time = String.format("12:%02d:00", m)
            intraday.add(MinuteData(time, 60, 0))
        }

        // Add some night data (22:00 - 23:00) - Sleeping (Future night)
        for (m in 0 until 60) {
            val time = String.format("22:%02d:00", m)
            intraday.add(MinuteData(time, 50, 0)) // Lower HR, should NOT pull down the daily RHR by being counted as "Night RHR"
        }

        // 2. Setup Sleep Data
        // Sleep 1: "Last Night" (Ends Jan 24 morning) - Represented by pre-calculated average or just implicit?
        // Usage says "calculateDailyRHR" computes "rhrAvg".
        // "rhrNight" comes from "nightHeartRates" which is "preMidnightHeartRates" list PLUS any HR found in sleep ranges.
        
        // Scenario: User has a sleep session tonight (22:00 - 23:00... continues next day)
        val sleepStart = Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 22)
            set(Calendar.MINUTE, 0)
        }.time
        
        val sleepEnd = Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 23) // Ends same day for simplicity of test data, or next day. 
            // If it ends next day, it's definitely "Tonight".
            // Even if it ends same day late, it's "Tonight".
        }.time

        val sleepSession = SleepData(
            date = date, // Fitbit assigns sleep to the day it ENDS usually, but let's say it's logged.
            // If it starts at 22h Jan 24 and ends Jan 25, it's a Jan 25 sleep log.
            // BUT DashboardVM loads "Tomorrow's sleep" and filters for overlap with TODAY.
            // validHeartRates includes TODAY's intraday.
            duration = 3600000,
            efficiency = 90,
            startTime = sleepStart,
            endTime = sleepEnd,
            minutesAsleep = 60,
            minutesAwake = 0,
            stages = null,
            levels = emptyList()
        )

        // 3. Activity (None)
        val activity: ActivityData? = null

        // 4. Pre-midnight HR (from Yesterday's sleep, i.e. Jan 23 night)
        // Let's say prev night HR was 55.
        val preMidnightHeartRates = listOf(55, 55, 55)

        // EXECUTE
        val result = HeartRateAnalysisUtils.calculateDailyRHR(
            date = date,
            intraday = intraday,
            sleep = listOf(sleepSession),
            activity = activity,
            preMidnightHeartRates = preMidnightHeartRates
        )

        // ASSERT
        
        // Expectation:
        // Night RHR should be average of preMidnightHeartRates (55).
        // It should NOT include the 50 bpm from 22:00-23:00 today.
        
        // Current Buggy Behavior (Predicted):
        // It checks if timestamp is in sleepRanges.
        // 22:00 is in sleepSession range.
        // It adds 50 to nightHeartRates.
        // Result Night RHR = Average(55, 55, 55, 50, 50...) ~= 52.5
        
        // Desired Behavior:
        // Night RHR = 55.
        
        assertEquals("Night RHR should be 55 (excluding tonight's sleep)", 55, result.rhrNight)
    }
}
