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

    fun generateReport(context: Context, data: List<TrendPoint>, days: Int): File {
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
        
        y += 30f

        // Table Header
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

        document.finishPage(page)

        val file = File(context.cacheDir, "Rapport_Health_${System.currentTimeMillis()}.pdf")
        document.writeTo(FileOutputStream(file))
        document.close()
        
        return file
    }
}
