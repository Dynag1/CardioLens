package com.cardio.fitbit.ui.components

import android.graphics.Color
import android.graphics.DashPathEffect
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.cardio.fitbit.ui.screens.TrendPoint
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun TrendsChart(
    data: List<TrendPoint>,
    selectedMetrics: Set<TrendMetric> = setOf(TrendMetric.NIGHT, TrendMetric.DAY),
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            LineChart(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                
                description.isEnabled = false
                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(false)
                setPinchZoom(false)
                
                // Legend
                legend.isEnabled = true
                legend.verticalAlignment = Legend.LegendVerticalAlignment.TOP
                legend.horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
                legend.orientation = Legend.LegendOrientation.HORIZONTAL
                legend.setDrawInside(false)
                legend.isWordWrapEnabled = true
                
                // Axis
                xAxis.position = XAxis.XAxisPosition.BOTTOM
                xAxis.setDrawGridLines(false)
                xAxis.granularity = 1f
                
                axisLeft.setDrawGridLines(true)
                axisRight.setDrawGridLines(false) // Right axis for Steps
            }
        },
        update = { chart ->
            if (data.isEmpty()) {
                chart.clear()
                return@AndroidView
            }

            val dateFormat = SimpleDateFormat("EEE", Locale.getDefault())
            val dateLabels = data.map { dateFormat.format(it.date) }
            
            // X-Axis Labels
            chart.xAxis.valueFormatter = IndexAxisValueFormatter(dateLabels)
            chart.xAxis.labelCount = data.size

            // Data Sets
            val lineData = LineData()
            var hasLeftAxisData = false
            var hasRightAxisData = false

            // 1. Night RHR (Blue)
            if (selectedMetrics.contains(TrendMetric.NIGHT)) {
                val nightEntries = data.mapIndexedNotNull { index, point ->
                    point.rhrNight?.let { Entry(index.toFloat(), it.toFloat()) }
                }
                if (nightEntries.isNotEmpty()) {
                    val nightSet = LineDataSet(nightEntries, "Nuit (Repos)").apply {
                        color = Color.parseColor("#1565C0") // Blue
                        setCircleColor(Color.parseColor("#1565C0"))
                        lineWidth = 2f
                        circleRadius = 4f
                        setDrawCircleHole(false)
                        setDrawValues(true)
                        valueTextSize = 10f
                        mode = LineDataSet.Mode.CUBIC_BEZIER
                        axisDependency = com.github.mikephil.charting.components.YAxis.AxisDependency.LEFT
                    }
                    lineData.addDataSet(nightSet)
                    hasLeftAxisData = true
                }
            }
            
            // 2. Day RHR (Orange)
            if (selectedMetrics.contains(TrendMetric.DAY)) {
                val dayEntries = data.mapIndexedNotNull { index, point ->
                    point.rhrDay?.let { Entry(index.toFloat(), it.toFloat()) }
                }
                if (dayEntries.isNotEmpty()) {
                    val daySet = LineDataSet(dayEntries, "Jour (Repos)").apply {
                        color = Color.parseColor("#FB8C00") // Orange
                        setCircleColor(Color.parseColor("#FB8C00"))
                        lineWidth = 2f
                        circleRadius = 4f
                        setDrawCircleHole(false)
                        setDrawValues(true)
                        valueTextSize = 10f
                        mode = LineDataSet.Mode.CUBIC_BEZIER
                        axisDependency = com.github.mikephil.charting.components.YAxis.AxisDependency.LEFT
                    }
                    lineData.addDataSet(daySet)
                    hasLeftAxisData = true
                }
            }
            
            // 3. Avg RHR (Purple dashed)
            if (selectedMetrics.contains(TrendMetric.AVG)) {
                val avgEntries = data.mapIndexedNotNull { index, point ->
                    point.rhrAvg?.let { Entry(index.toFloat(), it.toFloat()) }
                }
                if (avgEntries.isNotEmpty()) {
                    val avgSet = LineDataSet(avgEntries, "Moyenne").apply {
                        color = Color.parseColor("#7B1FA2") // Purple
                        setCircleColor(Color.parseColor("#7B1FA2"))
                        lineWidth = 2f
                        circleRadius = 3f
                        enableDashedLine(10f, 5f, 0f)
                        setDrawCircleHole(false)
                        setDrawValues(true)
                        valueTextSize = 10f
                        mode = LineDataSet.Mode.LINEAR
                        axisDependency = com.github.mikephil.charting.components.YAxis.AxisDependency.LEFT
                    }
                    lineData.addDataSet(avgSet)
                    hasLeftAxisData = true
                }
            }

            // 4. HRV (Red/Pink)
            if (selectedMetrics.contains(TrendMetric.HRV)) {
                val hrvEntries = data.mapIndexedNotNull { index, point ->
                    point.hrv?.let { Entry(index.toFloat(), it.toFloat()) }
                }
                if (hrvEntries.isNotEmpty()) {
                    val hrvSet = LineDataSet(hrvEntries, "HRV (RMSSD)").apply {
                        color = Color.parseColor("#E91E63") // Pink
                        setCircleColor(Color.parseColor("#E91E63"))
                        lineWidth = 2f
                        circleRadius = 4f
                        setDrawCircleHole(false)
                        setDrawValues(true)
                        valueTextSize = 10f
                        mode = LineDataSet.Mode.CUBIC_BEZIER
                        axisDependency = com.github.mikephil.charting.components.YAxis.AxisDependency.LEFT
                    }
                    lineData.addDataSet(hrvSet)
                    hasLeftAxisData = true
                }
            }

            // 5. Steps (Green)
            if (selectedMetrics.contains(TrendMetric.STEPS)) {
                val stepsEntries = data.mapIndexedNotNull { index, point ->
                    point.steps?.let { Entry(index.toFloat(), it.toFloat()) }
                }
                if (stepsEntries.isNotEmpty()) {
                    val stepsSet = LineDataSet(stepsEntries, "Pas").apply {
                        color = Color.parseColor("#43A047") // Green
                        setCircleColor(Color.parseColor("#43A047"))
                        lineWidth = 2f
                        circleRadius = 4f
                        setDrawCircleHole(false)
                        setDrawValues(false) // Too many numbers if shown, or maybe true? False for now.
                        mode = LineDataSet.Mode.LINEAR
                        // Use Right Axis if mixed with HR, otherwise Left is fine but for consistency keep Right?
                        // Actually if ONLY steps, display on left? No, simplifies logic to keep Right if we want consistency?
                        // But if Left is empty, line chart on Right looks weird without left labels.
                        // Let's say: If Right is used, enable it.
                        axisDependency = if (hasLeftAxisData) com.github.mikephil.charting.components.YAxis.AxisDependency.RIGHT else com.github.mikephil.charting.components.YAxis.AxisDependency.LEFT
                    }
                    if (stepsSet.axisDependency == com.github.mikephil.charting.components.YAxis.AxisDependency.RIGHT) {
                         hasRightAxisData = true
                    } else {
                         hasLeftAxisData = true
                    }
                    lineData.addDataSet(stepsSet)
                }
            }

            chart.data = lineData
            
            // Toggle Axes
            chart.axisLeft.isEnabled = hasLeftAxisData
            chart.axisRight.isEnabled = hasRightAxisData
            
            // Adjust Scales
            if (hasLeftAxisData) {
                // If only HR, nice pad. If mixed, MPChart handles auto-scale.
                // We add a little buffer
                val min = lineData.getDataSets().filter { it.axisDependency == com.github.mikephil.charting.components.YAxis.AxisDependency.LEFT }.minOfOrNull { it.yMin } ?: 0f
                val max = lineData.getDataSets().filter { it.axisDependency == com.github.mikephil.charting.components.YAxis.AxisDependency.LEFT }.maxOfOrNull { it.yMax } ?: 100f
                chart.axisLeft.axisMinimum = (min - 5f).coerceAtLeast(0f)
                chart.axisLeft.axisMaximum = max + 5f
            }
             if (hasRightAxisData) {
                 val min = lineData.getDataSets().filter { it.axisDependency == com.github.mikephil.charting.components.YAxis.AxisDependency.RIGHT }.minOfOrNull { it.yMin } ?: 0f
                 val max = lineData.getDataSets().filter { it.axisDependency == com.github.mikephil.charting.components.YAxis.AxisDependency.RIGHT }.maxOfOrNull { it.yMax } ?: 1000f
                 chart.axisRight.axisMinimum = 0f // Steps start at 0
                 chart.axisRight.axisMaximum = max * 1.1f // 10% buffer
            }

            chart.invalidate()
        }
    )
}

enum class TrendMetric {
    NIGHT, DAY, AVG, HRV, STEPS
}
