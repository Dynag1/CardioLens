package com.cardio.fitbit.ui.components

import android.graphics.Color
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.cardio.fitbit.ui.screens.TrendPoint
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.CombinedData
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.toArgb

@Composable
fun TrendsChart(
    data: List<TrendPoint>,
    selectedMetrics: Set<TrendMetric> = setOf(TrendMetric.NIGHT, TrendMetric.DAY),
    modifier: Modifier = Modifier
) {
    val labelColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f).toArgb()

    AndroidView(
        modifier = modifier,
        factory = { context ->
            CombinedChart(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                
                description.isEnabled = false
                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(true) 
                setScaleYEnabled(false) // Only zoom X
                setPinchZoom(true)
                
                drawOrder = arrayOf(
                    CombinedChart.DrawOrder.BAR,
                    CombinedChart.DrawOrder.LINE
                )
                
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
                axisRight.setDrawGridLines(false) // Right axis
                
                // Marker
                val markerView = TrendMarkerView(context, com.cardio.fitbit.R.layout.marker_view, selectedMetrics)
                markerView.chartView = this
                marker = markerView
            }
        },
        update = { chart ->
            if (data.isEmpty()) {
                chart.clear()
                return@AndroidView
            }

            // Update Marker Metrics
            (chart.marker as? TrendMarkerView)?.updateMetrics(selectedMetrics)

            val dateFormat = SimpleDateFormat("dd", Locale.getDefault())
            val dateLabels = data.map { dateFormat.format(it.date) }
            
            // X-Axis Labels
            chart.xAxis.valueFormatter = IndexAxisValueFormatter(dateLabels)
            chart.xAxis.labelCount = data.size.coerceAtMost(8) // Avoid overcrowding if zoomed out

            chart.xAxis.textColor = labelColor
            chart.axisLeft.textColor = labelColor
            chart.axisRight.textColor = labelColor
            chart.legend.textColor = labelColor
            
            chart.axisLeft.gridColor = gridColor
            chart.xAxis.gridColor = gridColor

            // Data Sets
            val combinedData = CombinedData()
            
            // --- Line Data (Heart Rate & HRV) ---
            val lineData = LineData()
            var hasLeftAxisData = false

            // Helper to attach data payload
            fun createEntry(index: Int, value: Float, point: TrendPoint): Entry {
                return Entry(index.toFloat(), value, point)
            }

            // 1. Night RHR (Blue)
            if (selectedMetrics.contains(TrendMetric.NIGHT)) {
                val nightEntries = data.mapIndexedNotNull { index, point ->
                    point.rhrNight?.let { createEntry(index, it.toFloat(), point) }
                }
                if (nightEntries.isNotEmpty()) {
                    val nightSet = LineDataSet(nightEntries, "Nuit (Repos)").apply {
                        color = Color.parseColor("#1565C0") // Blue
                        setCircleColor(Color.parseColor("#1565C0"))
                        lineWidth = 2f
                        circleRadius = 4f
                        setDrawCircleHole(false)
                        setDrawValues(false) // Hide values on chart itself, rely on marker
                        mode = LineDataSet.Mode.CUBIC_BEZIER
                        axisDependency = YAxis.AxisDependency.LEFT
                        highLightColor = Color.YELLOW
                    }
                    lineData.addDataSet(nightSet)
                    hasLeftAxisData = true
                }
            }
            
            // 2. Day RHR (Orange)
            if (selectedMetrics.contains(TrendMetric.DAY)) {
                val dayEntries = data.mapIndexedNotNull { index, point ->
                    point.rhrDay?.let { createEntry(index, it.toFloat(), point) }
                }
                if (dayEntries.isNotEmpty()) {
                    val daySet = LineDataSet(dayEntries, "Jour (Repos)").apply {
                        color = Color.parseColor("#FB8C00") // Orange
                        setCircleColor(Color.parseColor("#FB8C00"))
                        lineWidth = 2f
                        circleRadius = 4f
                        setDrawCircleHole(false)
                        setDrawValues(false)
                        mode = LineDataSet.Mode.CUBIC_BEZIER
                        axisDependency = YAxis.AxisDependency.LEFT
                        highLightColor = Color.YELLOW
                    }
                    lineData.addDataSet(daySet)
                    hasLeftAxisData = true
                }
            }
            
            // 3. Avg RHR (Purple dashed)
            if (selectedMetrics.contains(TrendMetric.AVG)) {
                val avgEntries = data.mapIndexedNotNull { index, point ->
                    point.rhrAvg?.let { createEntry(index, it.toFloat(), point) }
                }
                if (avgEntries.isNotEmpty()) {
                    val avgSet = LineDataSet(avgEntries, "Moyenne").apply {
                        color = Color.parseColor("#7B1FA2") // Purple
                        setCircleColor(Color.parseColor("#7B1FA2"))
                        lineWidth = 2f
                        circleRadius = 3f
                        enableDashedLine(10f, 5f, 0f)
                        setDrawCircleHole(false)
                        setDrawValues(false)
                        mode = LineDataSet.Mode.LINEAR
                        axisDependency = YAxis.AxisDependency.LEFT
                        highLightColor = Color.YELLOW
                    }
                    lineData.addDataSet(avgSet)
                    hasLeftAxisData = true
                }
            }

            // 4. HRV (Pink)
            if (selectedMetrics.contains(TrendMetric.HRV)) {
                val hrvEntries = data.mapIndexedNotNull { index, point ->
                    point.hrv?.let { createEntry(index, it.toFloat(), point) }
                }
                if (hrvEntries.isNotEmpty()) {
                    val hrvSet = LineDataSet(hrvEntries, "HRV (RMSSD)").apply {
                        color = Color.parseColor("#E91E63") // Pink
                        setCircleColor(Color.parseColor("#E91E63"))
                        lineWidth = 2f
                        circleRadius = 4f
                        setDrawCircleHole(false)
                        setDrawValues(false)
                        mode = LineDataSet.Mode.CUBIC_BEZIER
                        axisDependency = YAxis.AxisDependency.LEFT
                        highLightColor = Color.YELLOW
                    }
                    lineData.addDataSet(hrvSet)
                    hasLeftAxisData = true
                }
            }

            if (lineData.dataSetCount > 0) {
                combinedData.setData(lineData)
            } else {
                 combinedData.setData(LineData())
            }

            // --- Bar Data (Steps & Workouts) ---
            val barData = BarData()
            var hasRightAxisData = false
            // 5. Steps (Green Bars)
            if (selectedMetrics.contains(TrendMetric.STEPS)) {
                val stepsEntries = data.mapIndexedNotNull { index, point ->
                    point.steps?.let { BarEntry(index.toFloat(), it.toFloat(), point) }
                }
                if (stepsEntries.isNotEmpty()) {
                    val stepsSet = BarDataSet(stepsEntries, "Pas").apply {
                        color = Color.parseColor("#6643A047") // Green with transparency
                        setDrawValues(false)
                        axisDependency = YAxis.AxisDependency.RIGHT
                        highLightColor = Color.YELLOW
                    }
                    barData.addDataSet(stepsSet)
                    hasRightAxisData = true
                }
            }

            // 6. Workouts (Red/Orange Bars)
            if (selectedMetrics.contains(TrendMetric.WORKOUTS)) {
                val workoutEntries = data.mapIndexedNotNull { index, point ->
                     point.workoutDurationMinutes?.let { 
                         if (it > 0) BarEntry(index.toFloat(), it.toFloat() * 100f, point) else null // Scale x100
                     }
                }
                if (workoutEntries.isNotEmpty()) {
                    val workoutSet = BarDataSet(workoutEntries, "Entraînement (min)").apply {
                        color = Color.parseColor("#66D32F2F") // Transparent Red
                        setDrawValues(false) // Hide internal values, use marker
                        axisDependency = YAxis.AxisDependency.RIGHT 
                        highLightColor = Color.YELLOW
                    }
                    barData.addDataSet(workoutSet)
                    hasRightAxisData = true
                }
            } 

            if (barData.dataSetCount > 0) {
                 barData.barWidth = 0.5f 
                 combinedData.setData(barData)
            } else {
                 combinedData.setData(BarData())
            }

            chart.data = combinedData
            
            // Toggle Axes
            chart.axisLeft.isEnabled = hasLeftAxisData
            chart.axisRight.isEnabled = hasRightAxisData
            
            // Adjust Scales
            
            // Left Axis: Purely for Lines (HR / HRV) -> Auto-scale based on Line Min/Max
            if (hasLeftAxisData) {
                val minLine = lineData.getDataSets()?.minOfOrNull { it.yMin } ?: 0f
                val maxLine = lineData.getDataSets()?.maxOfOrNull { it.yMax } ?: 100f
                
                chart.axisLeft.axisMinimum = (minLine - 5f).coerceAtLeast(0f)
                chart.axisLeft.axisMaximum = (maxLine + 5f).coerceAtLeast(chart.axisLeft.axisMinimum + 10f)
            }
            
            // Right Axis: For Bars (Steps / Workouts) -> Start at 0
             if (hasRightAxisData) {
                 val max = barData.getDataSets()?.maxOfOrNull { it.yMax } ?: 0f
                 
                 chart.axisRight.axisMinimum = 0f
                 // Ensure max is at least slightly above 0 to avoid range 0..0
                 chart.axisRight.axisMaximum = (max * 1.1f).coerceAtLeast(100f) 
            }
            
            // Reset zoom if logical change? Or keep it?
            // chart.fitScreen() -- prefer letting user keep context if possible, but fitScreen ensures data visible if range changed significantly.
            // chart.setVisibleXRangeMaximum(8f) // Optional: limit viewport to 8 days initially?
            // Let's stick to simple invalidate for now.
            chart.invalidate()
        }
    )
}

enum class TrendMetric {
    NIGHT, DAY, AVG, HRV, STEPS, WORKOUTS
}
