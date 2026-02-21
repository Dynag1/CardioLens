package com.cardio.fitbit.wear

import android.util.Log
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

class HealthComplicationService : ComplicationDataSourceService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.SHORT_TEXT) return null
        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder("72").build(),
            contentDescription = PlainComplicationText.Builder("RHR").build()
        ).setTitle(PlainComplicationText.Builder("RHR").build())
            .build()
    }

    override fun onComplicationRequest(
        request: ComplicationRequest,
        listener: ComplicationRequestListener
    ) {
        scope.launch {
            val data = fetchLatestData()
            val complicationData = createComplicationData(data, request.complicationType)
            listener.onComplicationData(complicationData)
        }
    }

    private suspend fun fetchLatestData(): Map<String, Int> {
        val stats = mutableMapOf<String, Int>()
        try {
            val dataClient = Wearable.getDataClient(this)
            // Use URI for the specific path
            val dataItems = dataClient.dataItems.await()
            for (item in dataItems) {
                if (item.uri.path == "/health_stats") {
                    val dataMap = com.google.android.gms.wearable.DataMapItem.fromDataItem(item).dataMap
                    stats["rhr"] = dataMap.getInt("rhr")
                    stats["hrv"] = dataMap.getInt("hrv")
                    stats["readiness"] = dataMap.getInt("readiness")
                    stats["steps"] = dataMap.getInt("steps")
                }
            }
        } catch (e: Exception) {
            Log.e("HealthComplication", "Error fetching data", e)
        }
        return stats
    }

    private fun createComplicationData(stats: Map<String, Int>, type: ComplicationType): ComplicationData {
        val rhr = stats["rhr"]
        val hrv = stats["hrv"]
        val readiness = stats["readiness"]

        // For now, let's prioritize RHR or Readiness depending on availability
        val displayValue = if (readiness != null && readiness > 0) readiness.toString() else (rhr?.toString() ?: "--")
        val label = if (readiness != null && readiness > 0) "REC" else "RHR"

        return when (type) {
            ComplicationType.SHORT_TEXT -> {
                ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder(displayValue).build(),
                    contentDescription = PlainComplicationText.Builder("CardioLens Stats").build()
                ).setTitle(PlainComplicationText.Builder(label).build())
                    .build()
            }
            ComplicationType.RANGED_VALUE -> {
                // Ranged value for Readiness (0-100)
                RangedValueComplicationData.Builder(
                    value = (readiness ?: 0).toFloat(),
                    min = 0f,
                    max = 100f,
                    contentDescription = PlainComplicationText.Builder("Score de Récupération").build()
                ).setText(PlainComplicationText.Builder(displayValue).build())
                    .setTitle(PlainComplicationText.Builder(label).build())
                    .build()
            }
            else -> throw IllegalArgumentException("Unsupported complication type")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
