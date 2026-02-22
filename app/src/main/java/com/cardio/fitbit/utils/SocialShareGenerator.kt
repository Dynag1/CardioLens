package com.cardio.fitbit.utils

import android.content.Context
import android.graphics.*
import com.cardio.fitbit.ui.screens.WeeklySummary
import com.cardio.fitbit.ui.screens.MonthlySummary
import com.cardio.fitbit.utils.DateUtils
import java.io.File
import java.io.FileOutputStream

object SocialShareGenerator {

    fun generateWeeklyVibrantCard(context: Context, summary: WeeklySummary): File {
        val width = 1080
        val height = 1350
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Background Gradient
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                Color.parseColor("#1A237E"), // Deep Indigo
                Color.parseColor("#4A148C"), // Deep Purple
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Add some subtle accent circles
        val accentPaint = Paint().apply {
            color = Color.WHITE
            alpha = 20
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawCircle(width.toFloat(), 0f, 400f, accentPaint)
        canvas.drawCircle(0f, height.toFloat(), 300f, accentPaint)

        val textPaint = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        // 2. App Branding
        textPaint.textSize = 48f
        textPaint.alpha = 200
        canvas.drawText("CardioLens", width / 2f, 100f, textPaint)

        // 3. Title & Date
        textPaint.alpha = 255
        textPaint.textSize = 80f
        canvas.drawText("RÉSUMÉ DE LA SEMAINE", width / 2f, 250f, textPaint)
        
        textPaint.textSize = 42f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        val dateRange = "Semaine ${summary.week} | ${DateUtils.formatForDisplay(summary.startDate)} - ${DateUtils.formatForDisplay(summary.endDate)}"
        canvas.drawText(dateRange, width / 2f, 320f, textPaint)

        // 4. Main Stats Cards (2x2 Grid)
        val cardMargin = 60f
        val cardWidth = (width - 3 * cardMargin) / 2f
        val cardHeight = 350f
        val topStart = 450f
        
        drawStatCard(canvas, cardMargin, topStart, cardWidth, cardHeight, "Entraînements", summary.count.toString(), "#FF4081")
        drawStatCard(canvas, width / 2f + cardMargin / 2f, topStart, cardWidth, cardHeight, "Durée Totale", DateUtils.formatMinutes((summary.avgDuration * summary.count / 60000).toInt()), "#00E5FF")
        drawStatCard(canvas, cardMargin, topStart + cardHeight + cardMargin, cardWidth, cardHeight, "Intensité Moy.", String.format("%.1f", summary.avgIntensity), "#FFD600")
        
        val calories = (summary.avgIntensity * (summary.avgDuration / 60000) * summary.count).toInt()
        drawStatCard(canvas, width / 2f + cardMargin / 2f, topStart + cardHeight + cardMargin, cardWidth, cardHeight, "Calories Brûlées", "$calories kcal", "#76FF03")

        // 5. Secondary Stats (Bottom bar)
        textPaint.textSize = 38f
        textPaint.textAlign = Paint.Align.LEFT
        var bottomY = 1150f
        val startX = 100f
        
        if (summary.avgHeartRate > 0) {
            canvas.drawText("❤️ Pouls Moyen: ${summary.avgHeartRate} bpm", startX, bottomY, textPaint)
            bottomY += 60f
        }
        if (summary.avgSpeed > 0) {
            canvas.drawText("⚡ Vitesse Moyenne: ${String.format("%.1f", summary.avgSpeed)} km/h", startX, bottomY, textPaint)
        }

        // 6. Footer
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 32f
        textPaint.alpha = 150
        canvas.drawText("Généré par CardioLens - Votre santé au cœur de vos données", width / 2f, height - 80f, textPaint)

        // Save
        val file = File(context.cacheDir, "Vibrant_Weekly_${System.currentTimeMillis()}.png")
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, FileOutputStream(file))
        return file
    }

    fun generateMonthlyVibrantCard(context: Context, summary: MonthlySummary): File {
        val width = 1080
        val height = 1350
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Background Gradient - Different from weekly (maybe more towards Emerald/Teal)
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                Color.parseColor("#004D40"), // Deep Teal
                Color.parseColor("#1A237E"), // Deep Indigo
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Subtle accent patterns
        val accentPaint = Paint().apply {
            color = Color.WHITE
            alpha = 15
            style = Paint.Style.STROKE
            strokeWidth = 2f
            isAntiAlias = true
        }
        for (i in 0..10) {
            canvas.drawCircle(width / 2f, height / 2f, 200f + i * 100f, accentPaint)
        }

        val textPaint = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        // 2. Branding
        textPaint.textSize = 48f
        textPaint.alpha = 200
        canvas.drawText("CardioLens", width / 2f, 100f, textPaint)

        // 3. Title & Month
        textPaint.alpha = 255
        textPaint.textSize = 85f
        canvas.drawText("BILAN MENSUEL", width / 2f, 250f, textPaint)
        
        textPaint.textSize = 54f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("${summary.monthName} ${summary.year}", width / 2f, 330f, textPaint)

        // 4. Stats Grid
        val margin = 60f
        val cardW = (width - 3 * margin) / 2f
        val cardH = 350f
        val top = 450f
        
        drawStatCard(canvas, margin, top, cardW, cardH, "Activités", summary.count.toString(), "#00BFA5")
        drawStatCard(canvas, width / 2f + margin / 2f, top, cardW, cardH, "Durée Totale", DateUtils.formatDuration(summary.totalDuration), "#2979FF")
        drawStatCard(canvas, margin, top + cardH + margin, cardW, cardH, "Pouls Moyen", "${summary.avgHeartRate} bpm", "#F50057")
        drawStatCard(canvas, width / 2f + margin / 2f, top + cardH + margin, cardW, cardH, "Pas Moyens", summary.avgSteps.toString(), "#FFEA00")

        // 5. Bonus Stats
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 40f
        var bonusY = 1160f
        val startX = 100f
        
        if (summary.avgSpeed > 0) {
            canvas.drawText("⚡ Vitesse Moyenne: ${String.format("%.1f", summary.avgSpeed)} km/h", startX, bonusY, textPaint)
            bonusY += 60f
        }
        canvas.drawText("🔥 Intensité: ${String.format("%.1f", summary.avgIntensity)} cal/min", startX, bonusY, textPaint)

        // 6. Footer
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 32f
        textPaint.alpha = 150
        canvas.drawText("Votre santé, vos données - CardioLens", width / 2f, height - 80f, textPaint)

        // Save
        val file = File(context.cacheDir, "Vibrant_Monthly_${System.currentTimeMillis()}.png")
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, FileOutputStream(file))
        return file
    }

    private fun drawStatCard(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, label: String, value: String, accentColor: String) {
        val rect = RectF(x, y, x + w, y + h)
        
        // Card Background (Semi-transparent white)
        val bgPaint = Paint().apply {
            color = Color.WHITE
            alpha = 30
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(rect, 40f, 40f, bgPaint)
        
        // Accent Bar
        val accentPaint = Paint().apply {
            color = Color.parseColor(accentColor)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(x, y, x + 15f, y + h, 20f, 20f, accentPaint)

        val textPaint = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
        }

        // Value
        textPaint.textSize = 72f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(value, x + w / 2f, y + h / 2f + 20f, textPaint)

        // Label
        textPaint.textSize = 34f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textPaint.alpha = 180
        canvas.drawText(label, x + w / 2f, y + h / 2f + 80f, textPaint)
    }
}
