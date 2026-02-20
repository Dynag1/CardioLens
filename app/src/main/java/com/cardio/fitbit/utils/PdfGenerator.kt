package com.cardio.fitbit.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import com.cardio.fitbit.data.models.Activity
import com.cardio.fitbit.data.models.MinuteData
import com.cardio.fitbit.ui.screens.WeeklySummary
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

object PdfGenerator {

    fun generateWeeklyReport(
        context: Context,
        summary: WeeklySummary,
        activities: List<Pair<Activity, List<MinuteData>>>
    ): File? {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size: 595x842

        // --- Page 1: Summary ---
        val page1 = document.startPage(pageInfo)
        val canvas1 = page1.canvas
        drawWeeklySummaryPage(canvas1, summary, pageInfo.pageWidth, pageInfo.pageHeight)
        document.finishPage(page1)

        // --- Subsequent Pages: Activities ---
        // Let's put 2 activities per page to save space? Or 1 per page for clarity?
        // User asked for "Recap, Graph / Next Workout".
        // Let's try 2 per page.
        
        var currentY = 0f
        var currentPage: PdfDocument.Page? = null
        var currentCanvas: Canvas? = null

        activities.sortedByDescending { it.first.startTime }.forEachIndexed { index, (activity, minuteData) ->
            if (currentPage == null) {
                currentPage = document.startPage(pageInfo)
                currentCanvas = currentPage!!.canvas
                currentY = 40f // Top margin
            }

            // Check if we have enough space for this activity (approx 350 height needed)
            if (currentY + 400 > pageInfo.pageHeight) {
                document.finishPage(currentPage!!)
                currentPage = document.startPage(pageInfo)
                currentCanvas = currentPage!!.canvas
                currentY = 40f
            }

            drawActivitySection(context, currentCanvas!!, activity, minuteData, currentY, pageInfo.pageWidth)
            currentY += 400f // Move down for next activity
        }

        if (currentPage != null) {
            document.finishPage(currentPage!!)
        }

        // Save file
        val timestamp = System.currentTimeMillis()
        val fileName = "Cardio_Report_Week_${summary.year}_${summary.week}_$timestamp.pdf"
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)
        
        try {
            val fos = FileOutputStream(file)
            document.writeTo(fos)
            document.close()
            fos.close()
            return file
        } catch (e: IOException) {
            e.printStackTrace()
            document.close()
            return null
        }
    }

    private fun drawWeeklySummaryPage(canvas: Canvas, summary: WeeklySummary, width: Int, height: Int) {
        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 24f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        
        val textPaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 14f
            textAlign = Paint.Align.LEFT
        }

        val centerX = width / 2f
        var y = 60f

        canvas.drawText("Résumé Hebdomadaire", centerX, y, titlePaint)
        y += 40f
        
        titlePaint.textSize = 18f
        canvas.drawText("Semaine ${summary.week} - ${summary.year}", centerX, y, titlePaint)
        y += 30f
        
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("(${DateUtils.formatForDisplay(summary.startDate)} - ${DateUtils.formatForDisplay(summary.endDate)})", centerX, y, textPaint)
        y += 60f

        // Stats Table
        drawStatRow(canvas, "Entraînements", "${summary.count}", centerX, y)
        y += 30f
        drawStatRow(canvas, "Durée Totale", DateUtils.formatMinutes((summary.totalDuration / 60000).toInt()), centerX, y)
        y += 30f
        drawStatRow(canvas, "Intensité Moyenne", String.format("%.1f cal/min", summary.avgIntensity), centerX, y)
        y += 30f
        drawStatRow(canvas, "Fréquence Cardiaque Moy.", "${summary.avgHeartRate} bpm", centerX, y)
        y += 30f
        if (summary.avgSpeed > 0) {
            drawStatRow(canvas, "Vitesse Moyenne", String.format("%.1f km/h", summary.avgSpeed), centerX, y)
            y += 30f
        }
        if (summary.avgSteps > 0) {
            drawStatRow(canvas, "Pas Moyens", "${summary.avgSteps}", centerX, y)
            y += 30f
        }
    }

    private fun drawStatRow(canvas: Canvas, label: String, value: String, centerX: Float, y: Float) {
        val labelPaint = Paint().apply {
            color = Color.BLACK
            textSize = 16f
            textAlign = Paint.Align.RIGHT
            isFakeBoldText = true
        }
        val valuePaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 16f
            textAlign = Paint.Align.LEFT
        }
        
        canvas.drawText("$label :  ", centerX, y, labelPaint)
        canvas.drawText(value, centerX, y, valuePaint)
    }

    private fun drawActivitySection(
        context: Context,
        canvas: Canvas,
        activity: Activity,
        minuteData: List<MinuteData>,
        currentY: Float,
        pageWidth: Int
    ) {
        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 16f
            isFakeBoldText = true
        }
        
        val textPaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 12f
        }

        var y = currentY
        val margin = 40f

        // Title
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val startTimeStr = timeFormat.format(activity.startTime)
        val endTimeStr = timeFormat.format(Date(activity.startTime.time + activity.duration))
        canvas.drawText("${activity.activityName} - ${DateUtils.formatDate(activity.startTime)} ($startTimeStr - $endTimeStr)", margin, y + 20, titlePaint)
        
        // Details
        val avgHrStr = if (activity.averageHeartRate != null && activity.averageHeartRate > 0) "${activity.averageHeartRate} bpm avg" else "N/A"
        val details = "${DateUtils.formatDuration(activity.duration)} | ${activity.calories} cal | $avgHrStr"
        canvas.drawText(details, margin, y + 45, textPaint)

        // --- Heart Rate Zones Bar ---
        if (minuteData.isNotEmpty()) {
            val userMaxHr = 190 // Default or estimated
            val maxHr = userMaxHr.toFloat()
            val zoneStart = maxHr * 0.30f
            val zone1End = maxHr * 0.40f
            val zone2End = maxHr * 0.50f
            val zone3End = maxHr * 0.65f
            val zone4End = maxHr * 0.80f

            var z0 = 0; var z1 = 0; var z2 = 0; var z3 = 0; var z4 = 0; var z5 = 0
            
            minuteData.forEach { 
                val hr = it.heartRate
                if (hr > 0) {
                    when {
                        hr < zoneStart -> z0++
                        hr < zone1End -> z1++
                        hr < zone2End -> z2++
                        hr < zone3End -> z3++
                        hr < zone4End -> z4++
                        else -> z5++
                    }
                }
            }
            
            val total = (z0+z1+z2+z3+z4+z5).toFloat()
            if (total > 0) {
                val barWidth = pageWidth - (margin * 2)
                val barHeight = 15f
                val barY = y + 70 // Position below details
                
                var currentX = margin
                val paint = Paint()
                
                val counts = listOf(z0, z1, z2, z3, z4, z5)
                val colors = listOf(
                    Color.LTGRAY, 
                    Color.parseColor("#42A5F5"), // Blue
                    Color.parseColor("#10B981"), // Green
                    Color.parseColor("#FFD600"), // Yellow
                    Color.parseColor("#F59E0B"), // Orange
                    Color.parseColor("#EF4444")  // Red
                )
                
                counts.forEachIndexed { index, count ->
                    if (count > 0) {
                        val segmentWidth = (count / total) * barWidth
                        paint.color = colors[index]
                        canvas.drawRect(currentX, barY, currentX + segmentWidth, barY + barHeight, paint)
                        currentX += segmentWidth
                    }
                }
                
                // Labels below bar (Optional but nice)
                val cardioSum = z3+z4+z5
                val fatBurnSum = z1+z2
                val labelY = barY + barHeight + 12
                val labelPaint = Paint(textPaint).apply { textSize = 8f }

                if (fatBurnSum > 0) {
                     labelPaint.color = Color.parseColor("#10B981")
                     canvas.drawText("FatBurn ${(fatBurnSum*100/total).toInt()}%", margin, labelY, labelPaint)
                }
                if (cardioSum > 0) {
                     labelPaint.color = Color.parseColor("#F59E0B")
                     // Align right
                     val label = "Cardio ${(cardioSum*100/total).toInt()}%"
                     val w = labelPaint.measureText(label)
                     canvas.drawText(label, margin + barWidth - w, labelY, labelPaint)
                }

                // Shift Chart Down
                y += 40 
            }
        }

        // Chart
        if (minuteData.isNotEmpty()) {
            val chartHeight = 300
            val chartWidth = pageWidth - (margin * 2).toInt()
            
            // Generate Bitmap of the chart
            val chart = com.github.mikephil.charting.charts.CombinedChart(context)
            
            val startTimeMs = activity.startTime.time
             // Calculate Age for Max HR (Default 30 if unknown)
            val userMaxHr = 190 // 220 - 30
            
            // --- DOWNSAMPLING LOGIC ---
            // Group 1-second data into 1-minute buckets to ensure visible bars on PDF
            // Only downsample if we have significantly more points than minutes (e.g. > 2x)
            val processedData = if (minuteData.size > (activity.duration / 60000) * 2) {
                 val grouped = minuteData.groupBy { data ->
                     val dataTime = DateUtils.parseTimeToday(data.time)?.time ?: 0L
                     val fullDataTime = DateUtils.combineDateAndTime(activity.startTime, dataTime)
                     ((fullDataTime - startTimeMs) / 60000).toLong()
                 }
                 
                 grouped.map { (min, dataList) ->
                     val avgHr = dataList.map { it.heartRate }.average().toInt()
                     val sumSteps = dataList.sumOf { it.steps }
                     // Construct valid MinuteData
                     // MinuteData(time: String, heartRate: Int, steps: Int, displaySteps: Int = steps)
                     val timeMs = startTimeMs + (min * 60000)
                     val timeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(timeMs))
                     MinuteData(timeStr, avgHr, sumSteps, sumSteps)
                 }.sortedBy { it.time }
            } else {
                minuteData
            }

            val combinedData = com.github.mikephil.charting.data.CombinedData()

            // 1. Bar Data (HR)
            val hrEntries = mutableListOf<com.github.mikephil.charting.data.BarEntry>()
            
            processedData.forEach { data ->
                if (data.heartRate > 0) {
                     val dataTime = DateUtils.parseTimeToday(data.time)?.time ?: 0L
                     val fullDataTime = DateUtils.combineDateAndTime(activity.startTime, dataTime)
                     val diffMin = (fullDataTime - startTimeMs) / 60000f 
                    
                    if (diffMin >= 0) {
                        hrEntries.add(com.github.mikephil.charting.data.BarEntry(diffMin, data.heartRate.toFloat()))
                    }
                }
            }

            if (hrEntries.isNotEmpty()) {
                val hrDataSet = com.github.mikephil.charting.data.BarDataSet(hrEntries, "Heart Rate").apply {
                    val colors = hrEntries.map { entry -> getHeartRateColor(entry.y, userMaxHr) }
                    setColors(colors)
                    setDrawValues(false)
                    axisDependency = com.github.mikephil.charting.components.YAxis.AxisDependency.LEFT
                    isHighlightEnabled = false
                }
                val barData = com.github.mikephil.charting.data.BarData(hrDataSet)
                // Standard width for 1-minute bars
                barData.barWidth = 0.8f
                combinedData.setData(barData)
            }

            // 2. Steps Line (Purple)
            val stepEntries = mutableListOf<Entry>()
             processedData.forEach { data ->
                if (data.steps > 0) {
                     val dataTime = DateUtils.parseTimeToday(data.time)?.time ?: 0L
                    val fullDataTime = DateUtils.combineDateAndTime(activity.startTime, dataTime)
                    val diffMin = (fullDataTime - startTimeMs) / 60000f
                    
                    if (diffMin >= 0) {
                        stepEntries.add(Entry(diffMin, data.steps.toFloat()))
                    }
                }
            }

            if (stepEntries.isNotEmpty()) {
                val stepDataSet = LineDataSet(stepEntries, "Steps").apply {
                    color = Color.parseColor("#9C27B0")
                    setCircleColor(Color.parseColor("#9C27B0"))
                    circleRadius = 1.5f
                    setDrawCircles(false)
                    setDrawValues(false)
                    lineWidth = 1f 
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                    axisDependency = com.github.mikephil.charting.components.YAxis.AxisDependency.RIGHT
                    isHighlightEnabled = false
                }
                combinedData.setData(LineData(stepDataSet))
            }
            
            chart.data = combinedData
            
            // Styling
            chart.description.isEnabled = false
            chart.legend.isEnabled = false 
            chart.setDrawGridBackground(false)
            chart.setBackgroundColor(Color.WHITE)
            
            chart.drawOrder = arrayOf(
                com.github.mikephil.charting.charts.CombinedChart.DrawOrder.BAR,
                com.github.mikephil.charting.charts.CombinedChart.DrawOrder.LINE
            )
            
            chart.axisLeft.apply {
                setDrawGridLines(true)
                gridColor = Color.LTGRAY
                textColor = Color.GRAY
                textSize = 10f
                axisMinimum = 40f
                 val maxHrInGraph = processedData.maxOfOrNull { it.heartRate } ?: 150
                axisMaximum = (maxHrInGraph + 10).toFloat().coerceAtLeast(100f)
            }
            
            chart.axisRight.apply {
                isEnabled = true
                setDrawLabels(false)
                setDrawGridLines(false)
                axisMinimum = 0f
                val maxSteps = processedData.maxOfOrNull { it.steps } ?: 50
                axisMaximum = (maxSteps * 1.5f).coerceAtLeast(10f)
            }

            chart.xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                textColor = Color.GRAY
                textSize = 10f
                // Show elapsed minutes (0, 10, 20...)
                setLabelCount(6, false) 
                axisMinimum = 0f
                axisMaximum = (activity.duration / 60000f)

                valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return "${value.toInt()}"
                    }
                }
            }
            
            // Measure and Layout
            val widthSpec = android.view.View.MeasureSpec.makeMeasureSpec(chartWidth, android.view.View.MeasureSpec.EXACTLY)
            val heightSpec = android.view.View.MeasureSpec.makeMeasureSpec(chartHeight, android.view.View.MeasureSpec.EXACTLY)
            chart.measure(widthSpec, heightSpec)
            chart.layout(0, 0, chartWidth, chartHeight)
            
            // Translate canvas to draw chart
            canvas.save()
            canvas.translate(margin, y + 60)
            chart.draw(canvas)
            canvas.restore()
        } else {
             canvas.drawText("(Pas de données cardiaques détaillées)", margin, y + 100, textPaint)
        }
    }
    
    // Color Utils
    private fun interpolateColor(color1: Int, color2: Int, fraction: Float): Int {
        val a = (Color.alpha(color1) + (Color.alpha(color2) - Color.alpha(color1)) * fraction).toInt()
        val r = (Color.red(color1) + (Color.red(color2) - Color.red(color1)) * fraction).toInt()
        val g = (Color.green(color1) + (Color.green(color2) - Color.green(color1)) * fraction).toInt()
        val b = (Color.blue(color1) + (Color.blue(color2) - Color.blue(color1)) * fraction).toInt()
        return Color.argb(a, r, g, b)
    }

    private fun getHeartRateColor(bpm: Float, userMaxHr: Int = 190): Int {
        val maxHr = userMaxHr.toFloat()
        
        val zoneStart = maxHr * 0.30f
        val zone1End = maxHr * 0.40f
        val zone2End = maxHr * 0.50f
        val zone3End = maxHr * 0.65f
        val zone4End = maxHr * 0.80f

        val colorBlue = Color.parseColor("#42A5F5")
        val colorCyan = Color.parseColor("#06B6D4")
        val colorGreen = Color.parseColor("#10B981")
        val colorYellow = Color.parseColor("#FFD600")
        val colorOrange = Color.parseColor("#F59E0B")
        val colorRed = Color.parseColor("#EF4444")

        return when {
            bpm < zoneStart -> colorBlue
            bpm < zone1End -> interpolateColor(colorCyan, colorGreen, (bpm - zoneStart) / (zone1End - zoneStart))
            bpm < zone2End -> interpolateColor(colorGreen, colorYellow, (bpm - zone1End) / (zone2End - zone1End))
            bpm < zone3End -> interpolateColor(colorYellow, colorOrange, (bpm - zone2End) / (zone3End - zone2End))
            bpm < zone4End -> interpolateColor(colorOrange, colorRed, (bpm - zone3End) / (zone4End - zone3End))
            else -> colorRed
        }
    }
}
