package com.cardio.fitbit.wear

import android.content.ComponentName
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService

class WearDataListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == "/health_stats") {
                // Data updated, request complication refresh
                val requester = ComplicationDataSourceUpdateRequester.create(
                    this,
                    ComponentName(this, HealthComplicationService::class.java)
                )
                requester.requestUpdateAll()
            }
        }
    }
}
