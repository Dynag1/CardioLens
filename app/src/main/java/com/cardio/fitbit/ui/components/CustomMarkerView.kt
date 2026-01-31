package com.cardio.fitbit.ui.components

import android.content.Context
import android.widget.TextView
import com.cardio.fitbit.R
import com.cardio.fitbit.data.models.MinuteData
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF

class CustomMarkerView(context: Context, layoutResource: Int) : MarkerView(context, layoutResource) {

    private val tvContent: TextView = findViewById(R.id.tvContent)

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        if (e == null) return

        if (e.data is MinuteData) {
            val data = e.data as MinuteData
            tvContent.text = "${data.time}\n${context.getString(R.string.marker_hr)}: ${data.heartRate} BPM\n${context.getString(R.string.marker_steps)}: ${data.displaySteps}"
        } else if (e.data is Int) {
            // Handle Steps Bar Entry (Data is Total Steps Int)
            val steps = e.data as Int
            val totalMinutes = e.x.toInt()
            val hour = totalMinutes / 60
            val minute = totalMinutes % 60
            val timeStr = String.format("%02d:%02d", hour, minute)
            
            tvContent.text = "$timeStr\n${context.getString(R.string.marker_steps)}: $steps"
        } else {
             // Fallback if data is not attached (e.g. sleep bubble)
             val yVal = e.y.toInt()
             tvContent.text = "Val: $yVal"
        }

        super.refreshContent(e, highlight)
    }

    override fun getOffset(): MPPointF {
        // Center the marker horizontally
        // Place it strictly ABOVE the entry with LARGE extra padding (-100px)
        // This ensures it is never covered by the finger, even with large touch areas.
        return MPPointF(-(width / 2).toFloat(), -(height + 100).toFloat())
    }
}
