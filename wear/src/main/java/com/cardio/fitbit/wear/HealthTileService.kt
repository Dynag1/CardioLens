package com.cardio.fitbit.wear

import android.util.Log
import com.google.common.util.concurrent.ListenableFuture
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.material.CircularProgressIndicator
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.LayoutDefaults
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import androidx.wear.tiles.TimelineBuilders as TileTimelineBuilders
import androidx.wear.tiles.ResourceBuilders as TileResourceBuilders
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

class HealthTileService : TileService() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        return kotlinx.coroutines.guava.Futures.toListenableFuture(serviceScope.async {
            val stats = fetchLatestData()
            TileBuilders.Tile.Builder()
                .setResourcesVersion("1")
                .setTileTimeline(
                    TileTimelineBuilders.Timeline.Builder()
                        .addTimelineEntry(
                            TileTimelineBuilders.TimelineEntry.Builder()
                                .setLayout(
                                    TileTimelineBuilders.Layout.Builder()
                                        .setRoot(layout(stats))
                                        .build()
                                )
                                .build()
                        )
                        .build()
                ).build()
        })
    }

    override fun onResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<TileResourceBuilders.Resources> {
        return kotlinx.coroutines.guava.Futures.toListenableFuture(serviceScope.async {
            TileResourceBuilders.Resources.Builder()
                .setVersion("1")
                .build()
        })
    }

    data class TileData(
        val rhr: Int = 0,
        val rhrDay: Int = 0,
        val rhrNight: Int = 0,
        val hrv: Int = 0,
        val readiness: Int = 0,
        val steps: Int = 0,
        val hrSeries: IntArray = intArrayOf(),
        val maxHr: Int = 0
    )

    private suspend fun fetchLatestData(): TileData {
        var data = TileData()
        try {
            val dataClient = Wearable.getDataClient(this)
            val dataItems = dataClient.dataItems.await()
            for (item in dataItems) {
                if (item.uri.path == "/health_stats") {
                    val dataMap = DataMapItem.fromDataItem(item).dataMap
                    data = TileData(
                        rhr = dataMap.getInt("rhr"),
                        rhrDay = dataMap.getInt("rhr_day"),
                        rhrNight = dataMap.getInt("rhr_night"),
                        hrv = dataMap.getInt("hrv"),
                        readiness = dataMap.getInt("readiness"),
                        steps = dataMap.getInt("steps"),
                        hrSeries = dataMap.getIntArray("hr_series") ?: intArrayOf(),
                        maxHr = dataMap.getInt("max_hr").let { if (it > 0) it else 220 }
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("HealthTile", "Error fetching data", e)
        }
        return data
    }

    private fun layout(data: TileData): LayoutElementBuilders.LayoutElement {
        return LayoutElementBuilders.Box.Builder()
            .addContent(
                LayoutElementBuilders.Column.Builder()
                    .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
                    // 1. RHR Nuit / Jour
                    .addContent(
                        LayoutElementBuilders.Row.Builder()
                            .addContent(statMiniItem("NUIT", data.rhrNight.toString()))
                            .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(DimensionBuilders.dp(16f)).build())
                            .addContent(
                                Text.Builder(this, "PU L S")
                                    .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                                    .setColor(ColorBuilders.argb(0xFF9E9E9E.toInt()))
                                    .build()
                            )
                            .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(DimensionBuilders.dp(16f)).build())
                            .addContent(statMiniItem("JOUR", data.rhrDay.toString()))
                            .build()
                    )
                    .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(4f)).build())
                    
                    // 2. HR Graph
                    .addContent(createHrGraph(data.hrSeries, data.maxHr))
                    .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(4f)).build())

                    // 3. Stats Row
                    .addContent(
                        LayoutElementBuilders.Row.Builder()
                            .addContent(statMiniItem("REC", "${data.readiness}%"))
                            .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(DimensionBuilders.dp(12f)).build())
                            .addContent(statMiniItem("HRV", data.hrv.toString()))
                            .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(DimensionBuilders.dp(12f)).build())
                            .addContent(statMiniItem("PAS", data.steps.toString()))
                            .build()
                    )
                    .build()
            )
            .build()
    }

    private fun createHrGraph(series: IntArray, maxHr: Int): LayoutElementBuilders.LayoutElement {
        if (series.isEmpty()) {
            return LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(40f)).build()
        }
        
        val graphHeight = 40f
        val barWidth = 1.5f
        val gap = 0.5f
        
        val rowBuilder = LayoutElementBuilders.Row.Builder()
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_BOTTOM)

        // Limiter le nombre de points pour l'affichage (max ~60 pour éviter de surcharger)
        val displaySeries = if (series.size > 60) {
            val step = series.size / 60
            series.filterIndexed { index, _ -> index % step == 0 }.toIntArray()
        } else series

        displaySeries.forEach { hr ->
            if (hr > 0) {
                // Normaliser la hauteur (min 40bpm, maxhr)
                val normalizedHr = (hr - 40).coerceAtLeast(0).toFloat()
                val heightPercent = normalizedHr / (maxHr - 40).toFloat().coerceAtLeast(1f)
                val barHeight = (heightPercent * graphHeight).coerceAtLeast(2f).coerceAtMost(graphHeight)
                
                rowBuilder.addContent(
                    LayoutElementBuilders.Box.Builder()
                        .setWidth(DimensionBuilders.dp(barWidth))
                        .setHeight(DimensionBuilders.dp(barHeight))
                        .setModifiers(
                            ModifiersBuilders.Modifiers.Builder()
                                .setBackground(
                                    ModifiersBuilders.Background.Builder()
                                        .setColor(ColorBuilders.argb(getHeartRateColor(hr.toFloat(), maxHr)))
                                        .setCorner(ModifiersBuilders.Corner.Builder().setRadius(DimensionBuilders.dp(1f)).build())
                                        .build()
                                )
                                .build()
                        )
                        .build()
                )
                rowBuilder.addContent(LayoutElementBuilders.Spacer.Builder().setWidth(DimensionBuilders.dp(gap)).build())
            }
        }

        return LayoutElementBuilders.Box.Builder()
            .setHeight(DimensionBuilders.dp(graphHeight))
            .addContent(rowBuilder.build())
            .build()
    }

    private fun getHeartRateColor(bpm: Float, maxHr: Int): Int {
        val zoneStart = maxHr * 0.35f
        val zone1End = maxHr * 0.45f
        val zone2End = maxHr * 0.55f
        val zone3End = maxHr * 0.70f
        val zone4End = maxHr * 0.85f

        val colorBlue = 0xFF42A5F5.toInt()
        val colorCyan = 0xFF06B6D4.toInt()
        val colorGreen = 0xFF10B981.toInt()
        val colorYellow = 0xFFFFD600.toInt()
        val colorOrange = 0xFFF59E0B.toInt()
        val colorRed = 0xFFEF4444.toInt()

        return when {
            bpm < zoneStart -> colorBlue
            bpm < zone1End -> colorCyan
            bpm < zone2End -> colorGreen
            bpm < zone3End -> colorYellow
            bpm < zone4End -> colorOrange
            else -> colorRed
        }
    }

    private fun statMiniItem(label: String, value: String): LayoutElementBuilders.LayoutElement {
        return LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .addContent(
                Text.Builder(this, value)
                    .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                    .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                    .build()
            )
            .addContent(
                Text.Builder(this, label)
                    .setTypography(Typography.TYPOGRAPHY_CAPTION3)
                    .setColor(ColorBuilders.argb(0xFF9E9E9E.toInt()))
                    .build()
            )
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
