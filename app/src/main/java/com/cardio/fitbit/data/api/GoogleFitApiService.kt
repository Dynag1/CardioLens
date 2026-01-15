package com.cardio.fitbit.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface GoogleFitApiService {
    @POST("users/me/dataset:aggregate")
    suspend fun aggregate(@Body request: GoogleFitAggregateRequest): Response<GoogleFitAggregateResponse>
}

// --- Request Models ---

data class GoogleFitAggregateRequest(
    val aggregateBy: List<AggregateBy>,
    val bucketByTime: BucketByTime? = null,
    val startTimeMillis: Long,
    val endTimeMillis: Long
)

data class AggregateBy(
    val dataTypeName: String,
    val dataSourceId: String? = null
)

data class BucketByTime(
    val durationMillis: Long
)

// --- Response Models ---

data class GoogleFitAggregateResponse(
    val bucket: List<AggregateBucket>
)

data class AggregateBucket(
    val startTimeMillis: String,
    val endTimeMillis: String,
    val dataset: List<AggregateDataset>
)

data class AggregateDataset(
    val dataSourceId: String,
    val point: List<DataPoint>
)

data class DataPoint(
    val startTimeNanos: String,
    val endTimeNanos: String,
    val value: List<Value>
)

data class Value(
    val intVal: Int? = null,
    val fpVal: Float? = null,
    val stringVal: String? = null,
    val mapVal: List<MapValue>? = null
)

data class MapValue(
    val key: String,
    val value: MapValueInner
)

data class MapValueInner(
    val fpVal: Float
)
