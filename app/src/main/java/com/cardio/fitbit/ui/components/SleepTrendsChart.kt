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
fun SleepTrendsChart(
    data: List<TrendPoint>,
    selectedMetrics: Set<TrendMetric> = setOf(TrendMetric.SLEEP_TOTAL, TrendMetric.SLEEP_DEEP, TrendMetric.SLEEP_LIGHT, TrendMetric.SLEEP_REM),
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
                axisRight.setDrawGridLines(false) 
                axisRight.isEnabled = false // Not needed
                
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
            chart.xAxis.labelCount = data.size.coerceAtMost(8)

            chart.xAxis.textColor = labelColor
            chart.axisLeft.textColor = labelColor
            chart.legend.textColor = labelColor
            
            chart.axisLeft.gridColor = gridColor
            chart.xAxis.gridColor = gridColor

            val combinedData = CombinedData()
            val lineData = LineData()
            var hasLeftAxisData = false

            fun createEntry(index: Int, value: Float, point: TrendPoint): Entry {
                // Convert minutes to hours for Y-axis readability (or keep minutes)
                // Let's keep minutes for accuracy on Y axis, or fraction of hours.
                // It's clearer in hours (e.g. 7.5 hours) for humans
                return Entry(index.toFloat(), value / 60f, point)
            }

            // 1. Total Sleep
            if (selectedMetrics.contains(TrendMetric.SLEEP_TOTAL)) {
                val entries = data.mapIndexedNotNull { index, point ->
                    point.sleepMinutes?.let { createEntry(index, it.toFloat(), point) }
                }
                if (entries.isNotEmpty()) {
                    val set = LineDataSet(entries, "Total").apply {
                        color = Color.parseColor("#424242") // Dark Grey
                        setCircleColor(Color.parseColor("#424242"))
                        lineWidth = 2f
                        circleRadius = 4f
                        setDrawCircleHole(false)
                        setDrawValues(false)
                        mode = LineDataSet.Mode.CUBIC_BEZIER
                        axisDependency = YAxis.AxisDependency.LEFT
                        highLightColor = Color.YELLOW
                    }
                    lineData.addDataSet(set)
                    hasLeftAxisData = true
                }
            }
            
            // 2. Deep Sleep
            if (selectedMetrics.contains(TrendMetric.SLEEP_DEEP)) {
                val entries = data.mapIndexedNotNull { index, point ->
                    point.sleepDeep?.let { createEntry(index, it.toFloat(), point) }
                }
                if (entries.isNotEmpty()) {
                    val set = LineDataSet(entries, "Profond").apply {
                        color = Color.parseColor("#283593") // Dark Blue
                        setCircleColor(Color.parseColor("#283593"))
                        lineWidth = 2f
                        circleRadius = 3f
                        setDrawCircleHole(false)
                        setDrawValues(false)
                        mode = LineDataSet.Mode.CUBIC_BEZIER
                        axisDependency = YAxis.AxisDependency.LEFT
                        highLightColor = Color.YELLOW
                    }
                    lineData.addDataSet(set)
                    hasLeftAxisData = true
                }
            }
            
            // 3. Light Sleep
            if (selectedMetrics.contains(TrendMetric.SLEEP_LIGHT)) {
                val entries = data.mapIndexedNotNull { index, point ->
                    point.sleepLight?.let { createEntry(index, it.toFloat(), point) }
                }
                if (entries.isNotEmpty()) {
                    val set = LineDataSet(entries, "Léger").apply {
                        color = Color.parseColor("#4FC3F7") // Light Blue
                        setCircleColor(Color.parseColor("#4FC3F7"))
                        lineWidth = 2f
                        circleRadius = 3f
                        setDrawCircleHole(false)
                        setDrawValues(false)
                        mode = LineDataSet.Mode.CUBIC_BEZIER
                        axisDependency = YAxis.AxisDependency.LEFT
                        highLightColor = Color.YELLOW
                    }
                    lineData.addDataSet(set)
                    hasLeftAxisData = true
                }
            }

            // 4. REM Sleep
            if (selectedMetrics.contains(TrendMetric.SLEEP_REM)) {
                val entries = data.mapIndexedNotNull { index, point ->
                    point.sleepRem?.let { createEntry(index, it.toFloat(), point) }
                }
                if (entries.isNotEmpty()) {
                    val set = LineDataSet(entries, "Paradoxal").apply {
                        color = Color.parseColor("#AB47BC") // Purple
                        setCircleColor(Color.parseColor("#AB47BC"))
                        lineWidth = 2f
                        circleRadius = 3f
                        setDrawCircleHole(false)
                        setDrawValues(false)
                        mode = LineDataSet.Mode.CUBIC_BEZIER
                        axisDependency = YAxis.AxisDependency.LEFT
                        highLightColor = Color.YELLOW
                    }
                    lineData.addDataSet(set)
                    hasLeftAxisData = true
                }
            }

            // 5. Awake Mode
            if (selectedMetrics.contains(TrendMetric.SLEEP_WAKE)) {
                val entries = data.mapIndexedNotNull { index, point ->
                    point.sleepWake?.let { createEntry(index, it.toFloat(), point) }
                }
                if (entries.isNotEmpty()) {
                    val set = LineDataSet(entries, "Éveillé").apply {
                        color = Color.parseColor("#EF5350") // Red
                        setCircleColor(Color.parseColor("#EF5350"))
                        lineWidth = 2f
                        circleRadius = 3f
                        setDrawCircleHole(false)
                        setDrawValues(false)
                        mode = LineDataSet.Mode.CUBIC_BEZIER
                        axisDependency = YAxis.AxisDependency.LEFT
                        highLightColor = Color.YELLOW
                    }
                    lineData.addDataSet(set)
                    hasLeftAxisData = true
                }
            }

            if (lineData.dataSetCount > 0) {
                combinedData.setData(lineData)
            } else {
                 combinedData.setData(LineData())
            }

            chart.data = combinedData
            
            // Toggle Axes
            chart.axisLeft.isEnabled = hasLeftAxisData
            
            // Adjust Scales
            if (hasLeftAxisData) {
                // Minimum goes to 0 hours
                chart.axisLeft.axisMinimum = 0f
                
                val maxLine = lineData.dataSets?.maxOfOrNull { it.yMax } ?: 4f
                // Add some margin at the top
                chart.axisLeft.axisMaximum = (maxLine * 1.2f).coerceAtLeast(1f)
            }
            
            chart.invalidate()
        }
    )
}
