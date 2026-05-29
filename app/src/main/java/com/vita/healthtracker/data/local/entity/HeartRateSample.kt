package com.vita.healthtracker.data.local.entity

import androidx.room.Entity
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "heart_rate_sample", primaryKeys = ["timeEpochMs", "source"])
data class HeartRateSample(
    val timeEpochMs: Long,
    val bpm: Int,
    val source: String,
)
