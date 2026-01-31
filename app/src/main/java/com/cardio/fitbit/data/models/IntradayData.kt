package com.cardio.fitbit.data.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Combined minute-by-minute data for heart rate and steps
 */
@Parcelize
data class MinuteData(
    val time: String,        // "HH:mm" or "HH:mm:ss"
    val heartRate: Int,      // BPM (0 if no data)
    val steps: Int,           // Number of steps in this minute (sparse)
    val displaySteps: Int = steps // Total steps for the minute (for display purposes)
) : Parcelable

@Parcelize
data class IntradayData(
    val date: java.util.Date,
    val minuteData: List<MinuteData>
) : Parcelable
