package com.cardio.fitbit.utils

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.view.View
import com.cardio.fitbit.data.models.Activity
import com.cardio.fitbit.data.models.MinuteData
import com.cardio.fitbit.ui.screens.WeeklySummary
import com.cardio.fitbit.ui.screens.MonthlySummary
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

object PdfGenerator {

    // Modern color palette
    private val COLOR_PRIMARY = Color.parseColor("#6366F1") // Indigo
    private val COLOR_SECONDARY = Color.parseColor("#10B981") // Emerald
    private val COLOR_STEPS = Color.parseColor("#3B82F6") // Blue
    private val COLOR_HEART = Color.parseColor("#EF4444") // Red
    private val COLOR_TEXT_MAIN = Color.parseColor("#1E293B")
    private val COLOR_TEXT_SUB = Color.parseColor("#64748B")
    private val COLOR_CARD_BG = Color.parseColor("#F8FAFC")
    private val COLOR_BORDER = Color.parseColor("#E2E8F0")

    // Fonts with explicit creation to ensure UTF-8 support
    private val TYPEFACE_NORMAL = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    private val TYPEFACE_BOLD = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

    fun generateWeeklyReport(
        context: Context,
        summary: WeeklySummary,
        activities: List<Pair<Activity, List<MinuteData>>>,
        dateOfBirth: Long? = null
    ): File? {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size

        // --- Page 1: Summary ---
        val page1 = document.startPage(pageInfo)
        val canvas1 = page1.canvas
        drawSummaryPage(canvas1, summary, pageInfo.pageWidth, pageInfo.pageHeight)
        document.finishPage(page1)

        // --- Subsequent Pages: 1 Activity per Page ---
        activities.sortedByDescending { it.first.startTime }.forEach { (activity, minuteData) ->
            val page = document.startPage(pageInfo)
            val canvas = page.canvas
            drawPageHeader(canvas, "Détails de l'entraînement", pageInfo.pageWidth)
            drawActivityDetailFullPage(context, canvas, activity, minuteData, pageInfo.pageWidth, pageInfo.pageHeight, dateOfBirth)
            drawFooter(canvas, pageInfo.pageWidth, pageInfo.pageHeight)
            document.finishPage(page)
        }

        // Save file
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        val fileName = "Rapport_CardioLens_${summary.year}_S${summary.week}_$timestamp.pdf"
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)
        
        return try {
            val fos = FileOutputStream(file)
            document.writeTo(fos)
            document.close()
            fos.close()
            file
        } catch (e: IOException) {
            e.printStackTrace()
            document.close()
            null
        }
    }

    fun generateMonthlyReport(
        context: Context,
        summary: MonthlySummary,
        activities: List<Pair<Activity, List<MinuteData>>>,
        dateOfBirth: Long? = null
    ): File? {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()

        // --- Page 1: Monthly Summary ---
        val page1 = document.startPage(pageInfo)
        val canvas1 = page1.canvas
        drawMonthlySummaryPage(canvas1, summary, pageInfo.pageWidth, pageInfo.pageHeight)
        document.finishPage(page1)

        // --- Subsequent Pages: 1 Activity per Page ---
        activities.sortedByDescending { it.first.startTime }.forEach { (activity, minuteData) ->
            val page = document.startPage(pageInfo)
            val canvas = page.canvas
            drawPageHeader(canvas, "Détails de l'activité", pageInfo.pageWidth)
            drawActivityDetailFullPage(context, canvas, activity, minuteData, pageInfo.pageWidth, pageInfo.pageHeight, dateOfBirth)
            drawFooter(canvas, pageInfo.pageWidth, pageInfo.pageHeight)
            document.finishPage(page)
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        val fileName = "Rapport_Mensuel_CardioLens_${summary.year}_M${summary.month}_$timestamp.pdf"
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)
        
        return try {
            val fos = FileOutputStream(file)
            document.writeTo(fos)
            document.close()
            fos.close()
            file
        } catch (e: IOException) {
            e.printStackTrace()
            document.close()
            null
        }
    }

    private fun drawPageHeader(canvas: Canvas, pageTitle: String, width: Int) {
        val paint = Paint()
        // Header bar
        paint.color = COLOR_PRIMARY
        canvas.drawRect(0f, 0f, width.toFloat(), 70f, paint)

        // App Logo/Name
        paint.color = Color.WHITE
        paint.typeface = TYPEFACE_BOLD
        paint.textSize = 22f
        canvas.drawText("CardioLens", 35f, 42f, paint)

        // Title
        paint.typeface = TYPEFACE_NORMAL
        paint.textSize = 14f
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(pageTitle, width - 35f, 42f, paint)
    }

    private fun drawFooter(canvas: Canvas, width: Int, height: Int) {
        val paint = Paint().apply {
            color = COLOR_TEXT_SUB
            textSize = 9f
            typeface = TYPEFACE_NORMAL
            textAlign = Paint.Align.CENTER
        }
        val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRENCH).format(Date())
        canvas.drawText("Document généré par l'application CardioLens le $dateStr", width / 2f, height - 30f, paint)
    }

    private fun drawSummaryPage(canvas: Canvas, summary: WeeklySummary, width: Int, height: Int) {
        drawPageHeader(canvas, "Résumé Hebdomadaire", width)
        
        val centerX = width / 2f
        var y = 140f

        // Title Section
        val paint = Paint().apply {
            color = COLOR_PRIMARY
            textSize = 28f
            typeface = TYPEFACE_BOLD
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Vos Progrès de la Semaine", centerX, y, paint)
        y += 30f
        
        paint.color = COLOR_TEXT_SUB
        paint.textSize = 16f
        paint.typeface = TYPEFACE_NORMAL
        canvas.drawText("Semaine ${summary.week} | Année ${summary.year}", centerX, y, paint)
        y += 20f
        
        val dateRange = "${DateUtils.formatForDisplay(summary.startDate)} au ${DateUtils.formatForDisplay(summary.endDate)}"
        paint.textSize = 13f
        canvas.drawText(dateRange, centerX, y, paint)
        y += 70f

        // Stats Grid
        val margin = 50f
        val gap = 20f
        val cardW = (width - margin * 2 - gap) / 2f
        val cardH = 85f
        
        val stats = listOf(
            "Entraînements" to "${summary.count}",
            "Durée totale" to DateUtils.formatMinutes((summary.totalDuration / 60000).toInt()),
            "Calories / min" to String.format("%.1f", summary.avgIntensity),
            "Pulsations moyennes" to "${summary.avgHeartRate} bpm",
            "Vitesse moyenne" to if (summary.avgSpeed > 0) String.format("%.1f km/h", summary.avgSpeed) else "--",
            "Pas moyens" to if (summary.avgSteps > 0) "${summary.avgSteps}" else "--"
        )

        val cardPaint = Paint()
        val valPaint = Paint().apply { color = COLOR_PRIMARY; textSize = 20f; typeface = TYPEFACE_BOLD }
        val labPaint = Paint().apply { color = COLOR_TEXT_SUB; textSize = 11f; typeface = TYPEFACE_NORMAL }

        stats.forEachIndexed { i, (label, value) ->
            val row = i / 2
            val col = i % 2
            val left = margin + col * (cardW + gap)
            val top = y + row * (cardH + gap)
            
            // Card
            cardPaint.color = COLOR_CARD_BG
            canvas.drawRoundRect(left, top, left + cardW, top + cardH, 12f, 12f, cardPaint)
            cardPaint.style = Paint.Style.STROKE
            cardPaint.color = COLOR_BORDER
            cardPaint.strokeWidth = 1f
            canvas.drawRoundRect(left, top, left + cardW, top + cardH, 12f, 12f, cardPaint)
            cardPaint.style = Paint.Style.FILL
            
            // Content
            canvas.drawText(label, left + 20f, top + 32f, labPaint)
            canvas.drawText(value, left + 20f, top + 64f, valPaint)
        }
        
        drawFooter(canvas, width, height)
    }

    private fun drawMonthlySummaryPage(canvas: Canvas, summary: MonthlySummary, width: Int, height: Int) {
        drawPageHeader(canvas, "Résumé Mensuel", width)
        
        val centerX = width / 2f
        var y = 140f

        val paint = Paint().apply {
            color = COLOR_PRIMARY
            textSize = 28f
            typeface = TYPEFACE_BOLD
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Bilan Mensuel Santé", centerX, y, paint)
        y += 35f
        
        paint.color = COLOR_TEXT_MAIN
        paint.textSize = 20f
        canvas.drawText("${summary.monthName} ${summary.year}", centerX, y, paint)
        y += 80f

        // Stats Grid
        val margin = 50f
        val gap = 20f
        val cardW = (width - margin * 2 - gap) / 2f
        val cardH = 85f
        
        val stats = listOf(
            "Entraînements" to "${summary.count}",
            "Durée totale" to DateUtils.formatDuration(summary.totalDuration),
            "Intensité Moy." to String.format("%.1f cal/min", summary.avgIntensity),
            "Pouls Moyen" to "${summary.avgHeartRate} bpm",
            "Moy. Vitesse" to if (summary.avgSpeed > 0) String.format("%.1f km/h", summary.avgSpeed) else "--",
            "Moy. Pas" to if (summary.avgSteps > 0) "${summary.avgSteps}" else "--"
        )

        val cardPaint = Paint()
        val valPaint = Paint().apply { color = COLOR_PRIMARY; textSize = 20f; typeface = TYPEFACE_BOLD }
        val labPaint = Paint().apply { color = COLOR_TEXT_SUB; textSize = 11f; typeface = TYPEFACE_NORMAL }

        stats.forEachIndexed { i, (label, value) ->
            val row = i / 2
            val col = i % 2
            val left = margin + col * (cardW + gap)
            val top = y + row * (cardH + gap)
            
            cardPaint.color = COLOR_CARD_BG
            canvas.drawRoundRect(left, top, left + cardW, top + cardH, 12f, 12f, cardPaint)
            cardPaint.style = Paint.Style.STROKE
            cardPaint.color = COLOR_BORDER
            cardPaint.strokeWidth = 1f
            canvas.drawRoundRect(left, top, left + cardW, top + cardH, 12f, 12f, cardPaint)
            cardPaint.style = Paint.Style.FILL
            
            canvas.drawText(label, left + 20f, top + 32f, labPaint)
            canvas.drawText(value, left + 20f, top + 64f, valPaint)
        }
        
        drawFooter(canvas, width, height)
    }

    private fun drawActivityDetailFullPage(
        context: Context,
        canvas: Canvas,
        activity: Activity,
        minuteData: List<MinuteData>,
        width: Int,
        height: Int,
        dateOfBirth: Long? = null
    ) {
        val margin = 50f
        var y = 110f
        
        // Calculate Max HR for zones
        val age = if (dateOfBirth != null && dateOfBirth > 0) {
            val dob = Calendar.getInstance().apply { timeInMillis = dateOfBirth }
            val now = Calendar.getInstance()
            var a = now.get(Calendar.YEAR) - dob.get(Calendar.YEAR)
            if (now.get(Calendar.DAY_OF_YEAR) < dob.get(Calendar.DAY_OF_YEAR)) a--
            a
        } else 30
        val maxHr = 220 - age
        
        // 1. Title and Date with Icon placeholder style
        val titlePaint = Paint().apply {
            color = COLOR_TEXT_MAIN
            textSize = 24f
            typeface = TYPEFACE_BOLD
        }
        val subPaint = Paint().apply {
            color = COLOR_TEXT_SUB
            textSize = 12f
            typeface = TYPEFACE_NORMAL
        }

        canvas.drawText(activity.activityName, margin, y, titlePaint)
        y += 25f
        
        val sdf = SimpleDateFormat("EEEE d MMMM yyyy  •  HH:mm", Locale.FRENCH)
        val timeStr = "${sdf.format(activity.startTime)} - ${SimpleDateFormat("HH:mm", Locale.FRENCH).format(Date(activity.startTime.time + activity.duration))}"
        canvas.drawText(timeStr, margin, y, subPaint)
        y += 50f

        // 2. Main Stats Grid (Big Cards)
        val effectiveDur = calculateEffectiveDurationMs(activity, minuteData)
        val hrs = (effectiveDur / 3600000).toInt()
        val mins = ((effectiveDur % 3600000) / 60000).toInt()
        val durationStr = if (hrs > 0) "${hrs}h ${mins}min" else "${mins}min"

        val stats = mutableListOf(
            "Durée" to durationStr,
            "Calories" to "${activity.calories} kcal",
            "Pouls Moy." to "${activity.averageHeartRate ?: "--"} bpm"
        )
        
        if ((activity.steps ?: 0) > 0) {
            stats.add("Pas" to "${activity.steps}")
        }
        
        val cardW = (width - 2 * margin - 30f) / 2f
        val cardH = 70f
        
        val cardPaint = Paint().apply { color = COLOR_CARD_BG; style = Paint.Style.FILL }
        val borderPaint = Paint().apply { color = COLOR_BORDER; style = Paint.Style.STROKE; strokeWidth = 1f }
        val valPaint = Paint().apply { color = COLOR_PRIMARY; textSize = 18f; typeface = TYPEFACE_BOLD }
        val lblPaint = Paint().apply { color = COLOR_TEXT_SUB; textSize = 10f; typeface = TYPEFACE_NORMAL }

        stats.forEachIndexed { i, (label, value) ->
            val row = i / 2
            val col = i % 2
            val left = margin + col * (cardW + 30f)
            val top = y + row * (cardH + 20f)
            
            canvas.drawRoundRect(left, top, left + cardW, top + cardH, 12f, 12f, cardPaint)
            canvas.drawRoundRect(left, top, left + cardW, top + cardH, 12f, 12f, borderPaint)
            
            canvas.drawText(label, left + 15f, top + 25f, lblPaint)
            canvas.drawText(value, left + 15f, top + 55f, valPaint)
        }
        
        y += (stats.size + 1) / 2 * (cardH + 20f) + 20f

        // 3. Speed / Pace if applicable
        if ((activity.distance ?: 0.0) > 0.0) {
            val speed = activity.distance!! / (effectiveDur / 3600000.0)
            val pace = 60.0 / speed
            val paceStr = String.format("%d'%02d\" /km", pace.toInt(), ((pace - pace.toInt()) * 60).toInt())
            
            canvas.drawRoundRect(margin, y, width - margin, y + 60f, 12f, 12f, cardPaint)
            canvas.drawRoundRect(margin, y, width - margin, y + 60f, 12f, 12f, borderPaint)
            
            canvas.drawText("Vitesse & Allure", margin + 15f, y + 22f, lblPaint)
            canvas.drawText(String.format("%.2f km", activity.distance), margin + 15f, y + 48f, valPaint)
            val speedText = String.format("%.1f km/h  •  %s", speed, paceStr)
            val speedW = valPaint.measureText(speedText)
            canvas.drawText(speedText, width - margin - 15f - speedW, y + 48f, valPaint)
            
            y += 90f
        }

        // 4. Intensity Bar
        if (minuteData.isNotEmpty()) {
            canvas.drawText("ZONES D'INTENSITÉ CARDIAQUE", margin, y, lblPaint.apply { typeface = TYPEFACE_BOLD; textSize = 9f })
            y += 15f
            drawHrZoneBar(canvas, margin, y, width - 2 * margin, minuteData)
            y += 80f
        }

        // 5. Large Heart Rate Chart
        if (minuteData.isNotEmpty()) {
            canvas.drawText("ÉVOLUTION DU POULS ET DES PAS", margin, y, lblPaint.apply { typeface = TYPEFACE_BOLD; textSize = 9f })
            y += 15f
            drawActivityChart(context, canvas, margin, y, width - 2 * margin, 250f, activity, minuteData, maxHr)
        } else {
            canvas.drawText("(Graphique indisponible - données cardiaques manquantes)", margin, y + 40f, subPaint)
        }
    }

    private fun drawHrZoneBar(canvas: Canvas, x: Float, y: Float, width: Float, data: List<MinuteData>) {
        val maxHr = 190f
        val zones = IntArray(6) // 0 to 5
        data.forEach {
            val hr = it.heartRate
            if (hr > 0) {
                when {
                    hr < maxHr * 0.40f -> zones[0]++
                    hr < maxHr * 0.50f -> zones[1]++
                    hr < maxHr * 0.65f -> zones[2]++
                    hr < maxHr * 0.80f -> zones[3]++
                    hr < maxHr * 0.90f -> zones[4]++
                    else -> zones[5]++
                }
            }
        }
        
        val total = zones.sum().toFloat()
        if (total == 0f) return

        var currentX = x
        val barH = 10f
        val colors = intArrayOf(
            Color.parseColor("#94A3B8"), // Zone 0 - Slate
            Color.parseColor("#3B82F6"), // Zone 1 - Blue
            Color.parseColor("#10B981"), // Zone 2 - Green
            Color.parseColor("#F59E0B"), // Zone 3 - Amber
            Color.parseColor("#F97316"), // Zone 4 - Orange
            Color.parseColor("#EF4444")  // Zone 5 - Red
        )
        
        val paint = Paint()
        zones.forEachIndexed { i, count ->
            if (count > 0) {
                val sw = (count / total) * width
                paint.color = colors[i]
                canvas.drawRect(currentX, y, currentX + sw, y + barH, paint)
                currentX += sw
            }
        }
        
        // Legends
        val labPaint = Paint().apply { textSize = 8f; typeface = TYPEFACE_BOLD }
        val cardioCount = zones[3] + zones[4] + zones[5]
        val fatCount = zones[1] + zones[2]
        
        if (fatCount > 0) {
            labPaint.color = COLOR_SECONDARY
            canvas.drawText("Brûle-graisse: ${(fatCount * 100 / total).toInt()}%", x, y + barH + 12f, labPaint)
        }
        if (cardioCount > 0) {
            labPaint.color = COLOR_HEART
            val txt = "Cardio: ${(cardioCount * 100 / total).toInt()}%"
            val tw = labPaint.measureText(txt)
            canvas.drawText(txt, x + width - tw, y + barH + 12f, labPaint)
        }
    }

    private fun drawActivityChart(
        context: Context,
        canvas: Canvas,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        activity: Activity,
        minuteData: List<MinuteData>,
        userMaxHr: Int
    ) {
        val chart = CombinedChart(context)
        chart.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        
        val referenceStartMs = activity.startTime.time
        val durationMin = (activity.duration / 60000f).coerceAtLeast(1f)
        
        val entriesHr = mutableListOf<BarEntry>()
        val entriesSteps = mutableListOf<Entry>()
        
        // High density sampling like in the app
        // If we have many points, we use thin bars
        val sortedMinutes = minuteData.sortedBy { it.time }
        val isHighDensity = sortedMinutes.size > (durationMin * 2)
        val barWidth = if (isHighDensity) 0.02f else 0.8f

        for (i in sortedMinutes.indices) {
            val data = sortedMinutes[i]
            if (data.heartRate > 0) {
                val dataTimeDate = DateUtils.parseTimeToday(data.time)
                if (dataTimeDate != null) {
                    val fullTime = DateUtils.combineDateAndTime(activity.startTime, dataTimeDate.time)
                    val offsetMin = (fullTime - referenceStartMs) / 60000f
                    
                    if (offsetMin >= -0.5f && offsetMin <= durationMin + 0.5f) {
                        val finalX = offsetMin.coerceAtLeast(0f)
                        
                        // Adaptive widening for gaps in high density
                        var gapToNext = 1.0f
                        if (i < sortedMinutes.size - 1) {
                            val nextTime = DateUtils.combineDateAndTime(activity.startTime, DateUtils.parseTimeToday(sortedMinutes[i+1].time)?.time ?: 0L)
                            gapToNext = (nextTime - fullTime) / 60000f
                        }

                        if (isHighDensity && gapToNext > 0.5f) {
                            val fillCount = ((gapToNext / barWidth) * 0.9f).toInt().coerceIn(1, 60)
                            for (j in 0 until fillCount) {
                                entriesHr.add(BarEntry(finalX + (j * barWidth), data.heartRate.toFloat()))
                            }
                        } else {
                            entriesHr.add(BarEntry(finalX, data.heartRate.toFloat()))
                        }
                    }
                }
            }
            if (data.steps > 0) {
                val dataTimeDate = DateUtils.parseTimeToday(data.time)
                if (dataTimeDate != null) {
                    val fullTime = DateUtils.combineDateAndTime(activity.startTime, dataTimeDate.time)
                    val offsetMin = (fullTime - referenceStartMs) / 60000f
                    if (offsetMin >= 0) {
                        entriesSteps.add(Entry(offsetMin, data.steps.toFloat()))
                    }
                }
            }
        }

        val data = CombinedData()
        
        // HR as multi-colored Bars (Primary)
        if (entriesHr.isNotEmpty()) {
            val ds = BarDataSet(entriesHr, "Pouls").apply {
                colors = entriesHr.map { getHeartRateColor(it.y, userMaxHr.toFloat()) }
                setDrawValues(false)
                axisDependency = YAxis.AxisDependency.LEFT
                isHighlightEnabled = false
            }
            val barData = BarData(ds)
            barData.barWidth = barWidth
            data.setData(barData)
        }
        
        // Steps as Line (Secondary)
        if (entriesSteps.isNotEmpty()) {
            val ds = LineDataSet(entriesSteps.sortedBy { it.x }, "Pas").apply {
                color = Color.argb(120, 156, 39, 176) // Purple with transparency (#9C27B0)
                lineWidth = 1f
                setDrawCircles(false)
                setDrawValues(false)
                mode = LineDataSet.Mode.CUBIC_BEZIER
                axisDependency = YAxis.AxisDependency.RIGHT
                isHighlightEnabled = false
            }
            data.setData(LineData(ds))
        }

        chart.data = data
        chart.description.isEnabled = false
        chart.legend.isEnabled = false
        chart.setDrawGridBackground(false)
        chart.setBackgroundColor(Color.WHITE)
        chart.drawOrder = arrayOf(CombinedChart.DrawOrder.BAR, CombinedChart.DrawOrder.LINE)
        
        chart.axisLeft.apply {
            setDrawGridLines(true)
            gridColor = Color.parseColor("#F1F5F9")
            textColor = COLOR_TEXT_SUB
            textSize = 7f
            axisMinimum = 40f
            axisMaximum = (entriesHr.maxOfOrNull { it.y } ?: 180f) + 15f
            setLabelCount(5, true)
        }
        
        chart.axisRight.apply {
            isEnabled = true
            setDrawLabels(false)
            setDrawGridLines(false)
            axisMinimum = 0f
            axisMaximum = (entriesSteps.maxOfOrNull { it.y } ?: 10f) * 4f // Compress steps to bottom
        }

        chart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(false)
            textColor = COLOR_TEXT_SUB
            textSize = 7f
            axisMinimum = 0f
            axisMaximum = durationMin
            setLabelCount(5, false)
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float) = "${value.toInt()}'"
            }
        }

        chart.measure(
            View.MeasureSpec.makeMeasureSpec(width.toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height.toInt(), View.MeasureSpec.EXACTLY)
        )
        chart.layout(0, 0, width.toInt(), height.toInt())
        
        canvas.save()
        canvas.translate(x, y)
        chart.draw(canvas)
        canvas.restore()
    }

    private fun getHeartRateColor(bpm: Float, maxHr: Float): Int {
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

    private fun interpolateColor(color1: Int, color2: Int, fraction: Float): Int {
        val a = (Color.alpha(color1) + (Color.alpha(color2) - Color.alpha(color1)) * fraction).toInt()
        val r = (Color.red(color1) + (Color.red(color2) - Color.red(color1)) * fraction).toInt()
        val g = (Color.green(color1) + (Color.green(color2) - Color.green(color1)) * fraction).toInt()
        val b = (Color.blue(color1) + (Color.blue(color2) - Color.blue(color1)) * fraction).toInt()
        return Color.argb(a, r, g, b)
    }

    private fun getHeartRateColor(bpm: Float): Int {
        val max = 190f
        return when {
            bpm < max * 0.40f -> Color.parseColor("#94A3B8")
            bpm < max * 0.50f -> Color.parseColor("#3B82F6")
            bpm < max * 0.65f -> Color.parseColor("#10B981")
            bpm < max * 0.80f -> Color.parseColor("#F59E0B")
            bpm < max * 0.90f -> Color.parseColor("#F97316")
            else -> Color.parseColor("#EF4444")
        }
    }

    private fun calculateEffectiveDurationMs(activity: Activity, minuteData: List<MinuteData>?): Long {
        if (minuteData.isNullOrEmpty()) return activity.duration
        val start = activity.startTime.time
        val end = start + activity.duration
        val activeMins = minuteData.filter { 
            val dt = DateUtils.parseTimeToday(it.time)?.time ?: 0L
            val ft = DateUtils.combineDateAndTime(activity.startTime, dt)
            ft in start..end
        }
        .groupBy { it.time.substring(0, 5) }
        .values
        .count { it.sumOf { m -> m.steps } > 50 }
        return if (activeMins > 0) activeMins.toLong() * 60000L else activity.duration
    }
}
