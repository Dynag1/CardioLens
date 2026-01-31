
import java.text.SimpleDateFormat
import java.util.*

fun main() {
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    
    // 1. Check Date Formatting
    val now = Date()
    val apiFmt = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE)
    println("Now: ${fmt.format(now)}")
    println("Api: ${apiFmt.format(now)}")
    
    // 2. Check GetDaysAgo
    val cal = Calendar.getInstance()
    cal.time = now
    
    val prev = Calendar.getInstance()
    prev.time = now
    prev.add(Calendar.DAY_OF_YEAR, -1)
    
    println("Prev (-1): ${fmt.format(prev.time)}")
    println("Prev Api: ${apiFmt.format(prev.time)}")
    
    // 3. Check Parse/Format Loop
    val dateStr = "2026-01-30"
    val parsed = apiFmt.parse(dateStr)
    println("Parsed '$dateStr': ${parsed}")
    println("Re-formatted: ${apiFmt.format(parsed)}")
    
    // 4. Check boundaries (Midnight)
    val midnight = Calendar.getInstance()
    midnight.set(2026, 0, 31, 0, 0, 0) // Jan 31 00:00:00
    println("Midnight Jan 31: ${fmt.format(midnight.time)}")
    println("Api Format: ${apiFmt.format(midnight.time)}")
    
    // 5. Check "Noon" boundary?
    // If I formatted "Jan 31 00:00" -> "2026-01-31".
    // If I go back 1 day -> "Jan 30 00:00" -> "2026-01-30".
    
    // 6. Check if formatForApi is affected by Locale (FRANCE)
    // "yyyy-MM-dd" should be stable.
}

main()
