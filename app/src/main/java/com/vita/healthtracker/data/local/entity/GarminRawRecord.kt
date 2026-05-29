package com.vita.healthtracker.data.local.entity

import androidx.room.Entity
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "garmin_raw_record",
    primaryKeys = ["date", "domain", "categoryKey"],
)
data class GarminRawRecord(
    val date: String,
    val domain: String,
    val categoryKey: String,
    val categoryLabel: String,
    val endpointPath: String,
    val payloadJson: String,
    val fetchedAtEpochMs: Long = System.currentTimeMillis(),
)
