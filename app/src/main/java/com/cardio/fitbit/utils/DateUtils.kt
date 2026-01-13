package com.cardio.fitbit.utils

import java.text.SimpleDateFormat
import java.util.*

object DateUtils {
    private const val FITBIT_DATE_FORMAT = "yyyy-MM-dd"
    private const val DISPLAY_DATE_FORMAT = "EEEE d MMMM yyyy"
    private const val DISPLAY_TIME_FORMAT = "HH:mm"

    // Removed static SimpleDateFormat instances as they are not thread-safe.
    // We will create them locally in functions or use ThreadLocal.

    /**
     * Format date for Fitbit API (yyyy-MM-dd)
     */
    fun formatForApi(date: Date): String {
        return SimpleDateFormat(FITBIT_DATE_FORMAT, Locale.FRANCE).format(date)
    }

    /**
     * Parse Fitbit API date string
     */
    fun parseApiDate(dateString: String): Date? {
        return try {
            SimpleDateFormat(FITBIT_DATE_FORMAT, Locale.FRANCE).parse(dateString)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parse Fitbit API datetime string using multiple patterns for robustness
     */
    fun parseApiDateTime(dateTimeString: String): Date? {
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", // ISO 8601 with timezone (e.g. -08:00)
            "yyyy-MM-dd'T'HH:mm:ss.SSSZ",   // RFC 822 timezone (e.g. -0800)
            "yyyy-MM-dd'T'HH:mm:ss.SSS",    // No timezone
            "yyyy-MM-dd'T'HH:mm:ss"         // No millis, no timezone
        )
        
        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                return sdf.parse(dateTimeString)
            } catch (e: Exception) {
                // Continue
            }
        }
        return null
    }

    /**
     * Format date for display (dd MMM yyyy)
     */
    fun formatForDisplay(date: Date): String {
        return SimpleDateFormat(DISPLAY_DATE_FORMAT, Locale.FRANCE).format(date)
    }

    /**
     * Format time for display (HH:mm)
     */
    fun formatTimeForDisplay(date: Date): String {
        return SimpleDateFormat("HH:mm", Locale.FRANCE).format(date)
    }

    /**
     * Get start of today
     */
    fun getToday(): Date {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.time
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
    /**
     * Parse timestamp from Fitbit API (Try DateTime ISO, then Time HH:mm)
     * @param dateTimeString The string to parse (e.g. "2023-01-01T12:00:00" or "12:00")
     * @param refDate The reference date to use if string is only time
     */
    fun parseFitbitTimeOrDateTime(dateTimeString: String, refDate: Date): Date? {
        // 1. Try full DateTime using robust parser
        val fullDate = parseApiDateTime(dateTimeString)
        if (fullDate != null) return fullDate
        
        // 2. Try Time processing with Reference Date
        try {
            val cal = Calendar.getInstance()
            cal.time = refDate
            
            // Clean string (sometimes T12:00:00)
            val cleanTime = dateTimeString.substringAfter("T")
            val parts = cleanTime.split(":")
            
            if (parts.size >= 2) {
                cal.set(Calendar.HOUR_OF_DAY, parts[0].toInt())
                cal.set(Calendar.MINUTE, parts[1].toInt())
                // Optional seconds
                val seconds = if (parts.size > 2) {
                    // Handle "00.000" or just "00"
                    parts[2].take(2).toIntOrNull() ?: 0
                } else 0
                cal.set(Calendar.SECOND, seconds)
                cal.set(Calendar.MILLISECOND, 0)
                
                return cal.time
            }
        } catch (e: Exception) {
            // Fail
        }
        
        return null
    }
}
