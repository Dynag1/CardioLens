package com.cardio.fitbit.ui.components

import android.content.Context
import android.widget.TextView
import com.cardio.fitbit.R
import com.cardio.fitbit.ui.screens.TrendPoint
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import java.text.SimpleDateFormat
import java.util.Locale

class TrendMarkerView(
    context: Context, 
    layoutResource: Int,
    private var selectedMetrics: Set<TrendMetric>
) : MarkerView(context, layoutResource) {

    private val tvContent: TextView = findViewById(R.id.tvContent)
    private val dateFormat = SimpleDateFormat("EEE dd MMM", Locale.getDefault())

    fun updateMetrics(metrics: Set<TrendMetric>) {
        this.selectedMetrics = metrics
    }

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        if (e == null) return

        val point = e.data as? TrendPoint
        if (point != null) {
            val sb = StringBuilder()
            sb.append(dateFormat.format(point.date)).append("\n")

            if (selectedMetrics.contains(TrendMetric.NIGHT) && point.rhrNight != null) {
                sb.append("Nuit: ${point.rhrNight} bpm\n")
            }
            if (selectedMetrics.contains(TrendMetric.DAY) && point.rhrDay != null) {
                sb.append("Jour: ${point.rhrDay} bpm\n")
            }
            if (selectedMetrics.contains(TrendMetric.AVG) && point.rhrAvg != null) {
                sb.append("Moy: ${point.rhrAvg} bpm\n")
            }
            if (selectedMetrics.contains(TrendMetric.HRV) && point.hrv != null) {
                sb.append("HRV: ${point.hrv} ms\n")
            }
            if (selectedMetrics.contains(TrendMetric.STEPS) && point.steps != null) {
                sb.append("Pas: ${point.steps}\n")
            }
            if (selectedMetrics.contains(TrendMetric.WORKOUTS) && point.workoutDurationMinutes != null && point.workoutDurationMinutes > 0) {
                sb.append("Sport: ${point.workoutDurationMinutes} min\n")
            }
            
            // Remove last newline
            if (sb.isNotEmpty() && sb.last() == '\n') {
                sb.setLength(sb.length - 1)
            }
            
            tvContent.text = sb.toString()
        } else {
            tvContent.text = ""
        }

        super.refreshContent(e, highlight)
    }

    override fun getOffset(): MPPointF {
        return MPPointF(-(width / 2).toFloat(), -(height + 50).toFloat())
    }
}
