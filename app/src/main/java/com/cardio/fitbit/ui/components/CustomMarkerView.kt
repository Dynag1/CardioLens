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
            tvContent.text = "${data.time}\nHR: ${data.heartRate} BPM\nSteps: ${data.steps}"
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
