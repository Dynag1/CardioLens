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
    type: TrendsChartType = TrendsChartType.COMBINED,
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
                
                // Axis
                xAxis.position = XAxis.XAxisPosition.BOTTOM
                xAxis.setDrawGridLines(false)
                xAxis.granularity = 1f
                
                axisRight.isEnabled = false
                axisLeft.setDrawGridLines(true)
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

            // 1. Night RHR (Blue)
            if (type == TrendsChartType.NIGHT || type == TrendsChartType.COMBINED) {
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
                        setDrawValues(true) // Visible on separate chart
                        valueTextSize = 10f
                        mode = LineDataSet.Mode.CUBIC_BEZIER
                    }
                    lineData.addDataSet(nightSet)
                }
            }
            
            // 2. Day RHR (Orange)
            if (type == TrendsChartType.DAY || type == TrendsChartType.COMBINED) {
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
                    }
                    lineData.addDataSet(daySet)
                }
            }
            
            // 3. Avg RHR (Purple dashed)
            if (type == TrendsChartType.AVERAGE || type == TrendsChartType.COMBINED) {
                val avgEntries = data.mapIndexedNotNull { index, point ->
                    point.rhrAvg?.let { Entry(index.toFloat(), it.toFloat()) }
                }
                if (avgEntries.isNotEmpty()) {
                    val avgSet = LineDataSet(avgEntries, "Moyenne").apply {
                        color = Color.parseColor("#7B1FA2") // Purple
                        setCircleColor(Color.parseColor("#7B1FA2"))
                        lineWidth = 2f
                        circleRadius = 3f
                        enableDashedLine(10f, 5f, 0f) // Dashed line for average
                        setDrawCircleHole(false)
                        setDrawValues(true)
                        valueTextSize = 10f
                        mode = LineDataSet.Mode.LINEAR
                    }
                    lineData.addDataSet(avgSet)
                }
            }

            chart.data = lineData
            
            // Adjust Y Axis to fit data comfortably
            if (lineData.dataSetCount > 0) {
                val min = lineData.yMin
                val max = lineData.yMax
                chart.axisLeft.axisMinimum = (min - 5f).coerceAtLeast(0f)
                chart.axisLeft.axisMaximum = max + 5f
            }

            chart.invalidate()
        }
    )
}

enum class TrendsChartType {
    NIGHT, DAY, AVERAGE, COMBINED
}
