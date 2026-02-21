package com.cardio.fitbit.utils

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

object WearIntegrationManager {
    private const val TAG = "WearIntegrationManager"
    private const val DATA_PATH = "/health_stats"
    
    // Keys for the data map
    private const val KEY_RHR = "rhr"
    private const val KEY_HRV = "hrv"
    private const val KEY_READINESS = "readiness"
    private const val KEY_STEPS = "steps"
    private const val KEY_TIMESTAMP = "timestamp"

    /**
     * Pushes latest health stats to the Wear OS Data Layer.
     */
    suspend fun pushStatsToWear(
        context: Context,
        rhr: Int?,
        hrv: Int?,
        readiness: Int?,
        steps: Int?
    ) {
        try {
            val dataClient = Wearable.getDataClient(context)
            val putDataMapReq = PutDataMapRequest.create(DATA_PATH)
            
            rhr?.let { putDataMapReq.dataMap.putInt(KEY_RHR, it) }
            hrv?.let { putDataMapReq.dataMap.putInt(KEY_HRV, it) }
            readiness?.let { putDataMapReq.dataMap.putInt(KEY_READINESS, it) }
            steps?.let { putDataMapReq.dataMap.putInt(KEY_STEPS, it) }
            
            putDataMapReq.dataMap.putLong(KEY_TIMESTAMP, System.currentTimeMillis())
            
            // Set urgent to ensure quick delivery to the watch
            putDataMapReq.setUrgent()
            
            val putDataReq = putDataMapReq.asPutDataRequest()
            dataClient.putDataItem(putDataReq).await()
            Log.d(TAG, "Successfully pushed stats to Wear: RHR=$rhr, HRV=$hrv, Readiness=$readiness")
        } catch (e: Exception) {
            Log.e(TAG, "Error pushing stats to Wear", e)
        }
    }
}
