package com.cardio.fitbit.utils

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.cardio.fitbit.ui.screens.TrendPoint
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReportGenerator {

    fun generateReport(context: Context, data: List<TrendPoint>, days: Int, correlations: List<com.cardio.fitbit.ui.screens.CorrelationResult>): File {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size in points
        var page = document.startPage(pageInfo)
        var canvas = page.canvas
        val paint = Paint()

        var y = 50f

        // Title
        paint.textSize = 24f
        paint.isFakeBoldText = true
        canvas.drawText("Rapport de Santé CardioLens", 50f, y, paint)
        y += 20f

        // Date
        paint.textSize = 12f
        paint.isFakeBoldText = false
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val dateStr = dateFormat.format(Date())
        canvas.drawText("Généré le: $dateStr", 50f, y, paint)
        y += 15f
        canvas.drawText("Période: $days derniers jours", 50f, y, paint)
        y += 35f

        // Summary
        paint.textSize = 16f
        paint.isFakeBoldText = true
        canvas.drawText("Résumé", 50f, y, paint)
        y += 20f

        paint.textSize = 12f
        paint.isFakeBoldText = false
        
        val avgHrv = data.mapNotNull { it.hrv }.average()
        val avgRhr = data.mapNotNull { it.rhrAvg }.average()
        val avgSleep = data.mapNotNull { it.sleepMinutes }.average()

        if (!avgHrv.isNaN()) canvas.drawText("- VRC Moyenne: ${avgHrv.toInt()} ms", 50f, y, paint)
        y += 15f
        if (!avgRhr.isNaN()) canvas.drawText("- RHR Moyenne: ${avgRhr.toInt()} bpm", 50f, y, paint)
        y += 15f
        if (!avgSleep.isNaN()) canvas.drawText("- Sommeil Moyen: ${(avgSleep/60).toInt()}h ${(avgSleep%60).toInt()}m", 50f, y, paint)
        
        y += 40f

        // --- CHARTS ---
        val chartHeight = 120f // Slightly smaller to fit 4
        val chartWidth = 500f
        val chartLeft = 50f
        
        // 1. RHR Chart
        if (y + chartHeight > 800) { document.finishPage(page); page = document.startPage(pageInfo); canvas = page.canvas; y = 50f }
        paint.textSize = 14f
        paint.isFakeBoldText = true
        canvas.drawText("Evolution RHR (bpm)", 50f, y, paint)
        y += 20f
        drawRhrChart(canvas, data, chartLeft, y, chartWidth, chartHeight, paint)
        y += chartHeight + 30f
        
        // 2. HRV Chart
        if (y + chartHeight > 800) { document.finishPage(page); page = document.startPage(pageInfo); canvas = page.canvas; y = 50f }
        paint.textSize = 14f
        paint.isFakeBoldText = true
        canvas.drawText("Variabilité Cardiaque (HRV - ms)", 50f, y, paint)
        y += 20f
        drawHrvChart(canvas, data, chartLeft, y, chartWidth, chartHeight, paint)
        y += chartHeight + 30f

        // 3. Sleep Chart
        if (y + chartHeight > 800) { document.finishPage(page); page = document.startPage(pageInfo); canvas = page.canvas; y = 50f }
        paint.textSize = 14f
        paint.isFakeBoldText = true
        canvas.drawText("Sommeil (heures)", 50f, y, paint)
        y += 20f
        drawSleepChart(canvas, data, chartLeft, y, chartWidth, chartHeight, paint)
        y += chartHeight + 30f

        // 4. Steps Chart
        if (y + chartHeight > 800) { document.finishPage(page); page = document.startPage(pageInfo); canvas = page.canvas; y = 50f }
        paint.textSize = 14f
        paint.isFakeBoldText = true
        canvas.drawText("Activité Sédentaire vs Active (Pas)", 50f, y, paint)
        y += 20f
        drawStepsChart(canvas, data, chartLeft, y, chartWidth, chartHeight, paint)
        y += chartHeight + 40f



        // Table Header
        if (y > 750) {
             document.finishPage(page)
             page = document.startPage(pageInfo)
             canvas = page.canvas
             y = 50f
        }

        paint.textSize = 12f
        paint.isFakeBoldText = true
        canvas.drawText("Date", 50f, y, paint)
        canvas.drawText("RHR", 130f, y, paint)
        canvas.drawText("VRC", 180f, y, paint)
        canvas.drawText("Sommeil", 230f, y, paint)
        canvas.drawText("Humeur", 300f, y, paint)
        canvas.drawText("Symptômes", 360f, y, paint)
        y += 20f
        
        paint.isFakeBoldText = false
        
        // Items
        data.sortedByDescending { it.date }.forEach { point ->
            if (y > 800) { // New Page needed
                document.finishPage(page)
                page = document.startPage(pageInfo)
                canvas = page.canvas
                y = 50f
                
                // Redraw Header
                paint.isFakeBoldText = true
                canvas.drawText("Date", 50f, y, paint)
                canvas.drawText("RHR", 130f, y, paint)
                canvas.drawText("VRC", 180f, y, paint)
                canvas.drawText("Sommeil", 230f, y, paint)
                canvas.drawText("Humeur", 300f, y, paint)
                canvas.drawText("Symptômes", 360f, y, paint)
                y += 20f
                paint.isFakeBoldText = false
            }
            
            canvas.drawText(dateFormat.format(point.date), 50f, y, paint)
            canvas.drawText("${point.rhrAvg ?: "-"}", 130f, y, paint)
            canvas.drawText("${point.hrv ?: "-"}", 180f, y, paint)
            
            val sleepStr = point.sleepMinutes?.let { "${it/60}h${it%60}" } ?: "-"
            canvas.drawText(sleepStr, 230f, y, paint)
            
            canvas.drawText("${point.moodRating ?: "-"}/5", 300f, y, paint)
            
            val sym = point.symptoms?.take(20)?.let { if (it.length < point.symptoms.length) "$it..." else it } ?: "-" // Truncate
            canvas.drawText(sym, 360f, y, paint)
            
            y += 15f
        }

        y += 30f

        // --- INSIGHTS / CORRELATIONS (CARDS AT THE BOTTOM) ---
        if (correlations.isNotEmpty()) {
            if (y + 100 > 800) { document.finishPage(page); page = document.startPage(pageInfo); canvas = page.canvas; y = 50f }
            
            paint.textSize = 16f
            paint.isFakeBoldText = true
            canvas.drawText("Corrélations & Insights", 50f, y, paint)
            y += 25f
            
            paint.textSize = 12f
            paint.isFakeBoldText = false
            
            correlations.forEach { correlation ->
                val cardHeight = 70f
                if (y + cardHeight > 800) { document.finishPage(page); page = document.startPage(pageInfo); canvas = page.canvas; y = 50f }

                // Card Background
                val bgPaint = Paint().apply {
                    color = if (correlation.isPositive) android.graphics.Color.parseColor("#E8F5E9") else android.graphics.Color.parseColor("#FFEBEE")
                    style = Paint.Style.FILL
                }
                canvas.drawRect(50f, y, 545f, y + cardHeight, bgPaint)
                
                // Icon Placeholder
                val iconPaint = Paint().apply {
                    color = if (correlation.isPositive) android.graphics.Color.parseColor("#4CAF50") else android.graphics.Color.parseColor("#E57373")
                    style = Paint.Style.FILL
                }
                canvas.drawCircle(80f, y + 35f, 15f, iconPaint)
                
                // Text
                paint.isFakeBoldText = true
                paint.textSize = 12f
                canvas.drawText(correlation.title, 110f, y + 25f, paint)
                
                paint.isFakeBoldText = false
                canvas.drawText(correlation.description, 110f, y + 45f, paint)
                
                // Impact
                paint.isFakeBoldText = true
                paint.color = if (correlation.isPositive) android.graphics.Color.parseColor("#2E7D32") else android.graphics.Color.parseColor("#C62828")
                canvas.drawText("Impact: ${correlation.impact}", 110f, y + 60f, paint)
                paint.color = android.graphics.Color.BLACK // Reset

                y += cardHeight + 15f
            }
            y += 20f
        }

        document.finishPage(page)

        val file = File(context.cacheDir, "Rapport_Health_${System.currentTimeMillis()}.pdf")
        document.writeTo(FileOutputStream(file))
        document.close()
        
        return file
    }
    
    private fun drawRhrChart(canvas: android.graphics.Canvas, data: List<TrendPoint>, left: Float, top: Float, width: Float, height: Float, textPaint: Paint) {
        val sorted = data.sortedBy { it.date }
        val rhrValues = sorted.mapNotNull { it.rhrAvg }
        if (rhrValues.isEmpty()) return
        
        val min = rhrValues.minOrNull() ?: 50
        val max = rhrValues.maxOrNull() ?: 100
        val range = (max - min).toFloat().coerceAtLeast(10f)
        
        val linePaint = Paint().apply {
            color = android.graphics.Color.BLUE
            strokeWidth = 3f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        val pointPaint = Paint().apply {
             color = android.graphics.Color.BLUE
             style = Paint.Style.FILL
             isAntiAlias = true
        }
        
        // Draw Axis Box
        val axisPaint = Paint().apply {
            color = android.graphics.Color.LTGRAY
            style = Paint.Style.STROKE
        }
        canvas.drawRect(left, top, left + width, top + height, axisPaint)
        
        val stepX = width / (sorted.size - 1).coerceAtLeast(1)
        
        val path = android.graphics.Path()
        
        sorted.forEachIndexed { index, point ->
             val rhr = point.rhrAvg ?: return@forEachIndexed
             val x = left + index * stepX
             val normalizedY = (rhr - min) / range // 0..1
             val y = top + height - (normalizedY * height)
             
             if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
             canvas.drawCircle(x, y, 4f, pointPaint)
             
             // Draw Value
             textPaint.textSize = 8f
             textPaint.textAlign = Paint.Align.CENTER
             canvas.drawText(rhr.toString(), x, y - 8f, textPaint)
             textPaint.textAlign = Paint.Align.LEFT
        }
        canvas.drawPath(path, linePaint)
        
        // Labels
        textPaint.textSize = 10f
        canvas.drawText("${max}bpm", left - 35f, top + 10f, textPaint)
        canvas.drawText("${min}bpm", left - 35f, top + height, textPaint)
    }

    private fun drawHrvChart(canvas: android.graphics.Canvas, data: List<TrendPoint>, left: Float, top: Float, width: Float, height: Float, textPaint: Paint) {
        val sorted = data.sortedBy { it.date }
        val values = sorted.mapNotNull { it.hrv }
        if (values.isEmpty()) return
        
        val min = values.minOrNull() ?: 0
        val max = values.maxOrNull() ?: 100
        val range = (max - min).toFloat().coerceAtLeast(10f)
        
        val linePaint = Paint().apply {
            color = android.graphics.Color.parseColor("#FF9800") // Orange
            strokeWidth = 3f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        val pointPaint = Paint().apply {
             color = android.graphics.Color.parseColor("#FF9800")
             style = Paint.Style.FILL
             isAntiAlias = true
        }
        
        val axisPaint = Paint().apply {
            color = android.graphics.Color.LTGRAY
            style = Paint.Style.STROKE
        }
        canvas.drawRect(left, top, left + width, top + height, axisPaint)
        
        val stepX = width / (sorted.size - 1).coerceAtLeast(1)
        val path = android.graphics.Path()
        
        sorted.forEachIndexed { index, point ->
             val value = point.hrv ?: return@forEachIndexed
             val x = left + index * stepX
             val normalizedY = (value - min) / range 
             val y = top + height - (normalizedY * height)
             
             if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
             canvas.drawCircle(x, y, 4f, pointPaint)
             
             // Draw Value
             textPaint.textSize = 8f
             textPaint.textAlign = Paint.Align.CENTER
             canvas.drawText(value.toString(), x, y - 8f, textPaint)
             textPaint.textAlign = Paint.Align.LEFT
        }
        canvas.drawPath(path, linePaint)
        
        textPaint.textSize = 10f
        canvas.drawText("${max}ms", left - 35f, top + 10f, textPaint)
        canvas.drawText("${min}ms", left - 35f, top + height, textPaint)
    }

    private fun drawStepsChart(canvas: android.graphics.Canvas, data: List<TrendPoint>, left: Float, top: Float, width: Float, height: Float, textPaint: Paint) {
        val sorted = data.sortedBy { it.date }
        
        val barPaint = Paint().apply {
            color = android.graphics.Color.parseColor("#009688") // Teal
            style = Paint.Style.FILL
        }
        
        val axisPaint = Paint().apply {
            color = android.graphics.Color.LTGRAY
            style = Paint.Style.STROKE
        }
        canvas.drawRect(left, top, left + width, top + height, axisPaint)
        
        val barWidth = (width / sorted.size) * 0.7f
        val stepX = width / sorted.size
        
        val maxSteps = sorted.mapNotNull { it.steps }.maxOrNull() ?: 10000
        val range = maxSteps.toFloat().coerceAtLeast(1000f)
        
        sorted.forEachIndexed { index, point ->
            val steps = point.steps ?: 0
            val normalizedHeight = (steps / range).coerceAtMost(1f) * height
            
            val x = left + index * stepX + (stepX - barWidth)/2
            val y = top + height - normalizedHeight
            
            canvas.drawRect(x, y, x + barWidth, top + height, barPaint)
            
            // Draw Value (Vertical if small space?)
            // Just simple number above bar
            if (steps > 0) {
                textPaint.textSize = 7f
                textPaint.textAlign = Paint.Align.CENTER
                val stepsStr = if (steps > 1000) "${steps/1000}k" else steps.toString()
                canvas.drawText(stepsStr, x + barWidth/2, y - 5f, textPaint)
                textPaint.textAlign = Paint.Align.LEFT
            }
        }
        
        textPaint.textSize = 10f
        canvas.drawText("${maxSteps}", left - 40f, top + 10f, textPaint)
        canvas.drawText("0", left - 25f, top + height, textPaint)
    }

    private fun drawSleepChart(canvas: android.graphics.Canvas, data: List<TrendPoint>, left: Float, top: Float, width: Float, height: Float, textPaint: Paint) {
        val sorted = data.sortedBy { it.date }
        
        val barPaint = Paint().apply {
            color = android.graphics.Color.parseColor("#9575CD") // Purple
            style = Paint.Style.FILL
        }
        
        val axisPaint = Paint().apply {
            color = android.graphics.Color.LTGRAY
            style = Paint.Style.STROKE
        }
        canvas.drawRect(left, top, left + width, top + height, axisPaint)
        
        val barWidth = (width / sorted.size) * 0.7f
        val stepX = width / sorted.size
        
        sorted.forEachIndexed { index, point ->
            val sleepMins = point.sleepMinutes ?: 0
            val hours = sleepMins / 60f
            
            // Max 10 hours for scale
            val normalizedHeight = (hours / 10f).coerceAtMost(1f) * height
            
            val x = left + index * stepX + (stepX - barWidth)/2
            val y = top + height - normalizedHeight
            
            canvas.drawRect(x, y, x + barWidth, top + height, barPaint)
            
            // Draw Value
            if (sleepMins > 0) {
                textPaint.textSize = 8f
                textPaint.textAlign = Paint.Align.CENTER
                val valStr = "${sleepMins/60}:${String.format("%02d", sleepMins%60)}"
                canvas.drawText(valStr, x + barWidth/2, y - 5f, textPaint)
                textPaint.textAlign = Paint.Align.LEFT
            }
        }
        
        textPaint.textSize = 10f
        canvas.drawText("10h", left - 25f, top + 10f, textPaint)
        canvas.drawText("0h", left - 25f, top + height, textPaint)
    }
}
