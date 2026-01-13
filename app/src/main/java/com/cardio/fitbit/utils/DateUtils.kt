package com.cardio.fitbit.utils

import java.text.SimpleDateFormat
import java.util.*

object DateUtils {
    private const val FITBIT_DATE_FORMAT = "yyyy-MM-dd"
    private const val FITBIT_DATETIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS"
    private const val DISPLAY_DATE_FORMAT = "dd MMM yyyy"
    private const val DISPLAY_TIME_FORMAT = "HH:mm"
    
    private val fitbitDateFormatter = SimpleDateFormat(FITBIT_DATE_FORMAT, Locale.FRANCE)
    private val fitbitDateTimeFormatter = SimpleDateFormat(FITBIT_DATETIME_FORMAT, Locale.FRANCE)
    private val displayDateFormatter = SimpleDateFormat(DISPLAY_DATE_FORMAT, Locale.FRANCE)
    private val displayTimeFormatter = SimpleDateFormat(DISPLAY_TIME_FORMAT, Locale.FRANCE)

    /**
     * Format date for Fitbit API (yyyy-MM-dd)
     */
    fun formatForApi(date: Date): String {
        return fitbitDateFormatter.format(date)
    }

    /**
     * Parse Fitbit API date string
     */
    fun parseApiDate(dateString: String): Date? {
        return try {
            fitbitDateFormatter.parse(dateString)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parse Fitbit API datetime string
     */
    fun parseApiDateTime(dateTimeString: String): Date? {
        return try {
            fitbitDateTimeFormatter.parse(dateTimeString)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Format date for display (dd MMM yyyy)
     */
    fun formatForDisplay(date: Date): String {
        return displayDateFormatter.format(date)
    }

    /**
     * Format time for display (HH:mm)
     */
    fun formatTimeForDisplay(date: Date): String {
        return displayTimeFormatter.format(date)
    }

    /**
     * Get today's date
     */
    fun getToday(): Date {
        return Calendar.getInstance().time
    }

    /**
     * Get date N days ago
     */
    fun getDaysAgo(days: Int): Date {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -days)
        return calendar.time
    }

    /**
     * Get start of day
     */
    fun getStartOfDay(date: Date): Date {
        val calendar = Calendar.getInstance()
        calendar.time = date
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.time
    }

    /**
     * Format duration in milliseconds to readable string
     */
    fun formatDuration(durationMs: Long): String {
        val hours = durationMs / (1000 * 60 * 60)
        val minutes = (durationMs % (1000 * 60 * 60)) / (1000 * 60)
        return "${hours}h ${minutes}min"
    }

    /**
     * Format minutes to hours and minutes
     */
    fun formatMinutes(minutes: Int): String {
        val hours = minutes / 60
        val mins = minutes % 60
        return if (hours > 0) {
            "${hours}h ${mins}min"
        } else {
            "${mins}min"
        }
    }
}
