package com.cardio.fitbit.utils

import android.content.Context
import android.util.Log
import android.content.Intent
import android.net.Uri
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

object WearIntegrationManager {
    private const val TAG = "WearIntegrationManager"
    private const val DATA_PATH = "/health_stats"
    
    // Keys for the data map
    private const val KEY_RHR = "rhr"
    private const val KEY_RHR_DAY = "rhr_day"
    private const val KEY_RHR_NIGHT = "rhr_night"
    private const val KEY_HR_SERIES = "hr_series"
    private const val KEY_MAX_HR = "max_hr"
    private const val KEY_HRV = "hrv"
    private const val KEY_READINESS = "readiness"
    private const val KEY_STEPS = "steps"
    private const val KEY_TIMESTAMP = "timestamp"

    private const val CAPABILITY_NAME = "cardio_lens_wear_app"
    
    /**
     * Checks if the Wear OS app is installed on any connected watch.
     */
    suspend fun isWearAppInstalled(context: Context): Boolean {
        try {
            val capabilityClient = Wearable.getCapabilityClient(context)
            val capabilityInfo = capabilityClient.getCapability(
                CAPABILITY_NAME,
                com.google.android.gms.wearable.CapabilityClient.FILTER_REACHABLE
            ).await()
            
            val nodes = capabilityInfo.nodes
            Log.d(TAG, "Wear nodes with capability $CAPABILITY_NAME: ${nodes.size}")
            return nodes.isNotEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking wear app installation", e)
            return false
        }
    }

    /**
     * Opens the Play Store page for this app on the connected watch(es).
     */
    suspend fun openPlayStoreOnWear(context: Context) {
        try {
            val nodeClient = Wearable.getNodeClient(context)
            val nodes = nodeClient.connectedNodes.await()
            
            if (nodes.isEmpty()) {
                Log.w(TAG, "No connected Wear OS nodes found to open Play Store.")
                return
            }

            val remoteActivityHelper = RemoteActivityHelper(context)
            val intent = Intent(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setData(Uri.parse("market://details?id=${context.packageName}"))
            
            nodes.forEach { node ->
                Log.d(TAG, "Attempting to open Play Store on node: ${node.displayName}")
                remoteActivityHelper.startRemoteActivity(intent, node.id)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening Play Store on Wear", e)
        }
    }

    /**
     * Pushes latest health stats to the Wear OS Data Layer.
     */
    suspend fun pushStatsToWear(
        context: Context,
        rhr: Int?,
        rhrDay: Int?,
        rhrNight: Int?,
        hrSeries: IntArray?,
        maxHr: Int?,
        hrv: Int?,
        readiness: Int?,
        steps: Int?
    ) {
        try {
            val dataClient = Wearable.getDataClient(context)
            val putDataMapReq = PutDataMapRequest.create(DATA_PATH)
            
            rhr?.let { putDataMapReq.dataMap.putInt(KEY_RHR, it) }
            rhrDay?.let { putDataMapReq.dataMap.putInt(KEY_RHR_DAY, it) }
            rhrNight?.let { putDataMapReq.dataMap.putInt(KEY_RHR_NIGHT, it) }
            hrSeries?.let { putDataMapReq.dataMap.putIntArray(KEY_HR_SERIES, it) }
            maxHr?.let { putDataMapReq.dataMap.putInt(KEY_MAX_HR, it) }
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
