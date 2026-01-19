package com.cardio.fitbit.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.cardio.fitbit.data.models.SpO2Data
import java.util.Date

@Entity(tableName = "spo2_data")
data class SpO2DataEntity(
    @PrimaryKey
    val date: Long,
    val avg: Double,
    val min: Double,
    val max: Double
) {
    fun toDomain(): SpO2Data {
        return SpO2Data(
            date = Date(date),
            avg = avg,
            min = min,
            max = max
        )
    }

    companion object {
        fun fromDomain(data: SpO2Data): SpO2DataEntity {
            return SpO2DataEntity(
                date = data.date.time,
                avg = data.avg,
                min = data.min,
                max = data.max
            )
        }
    }
}
