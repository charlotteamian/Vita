package com.vita.healthtracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "body_battery_sample")
data class BodyBatterySample(
    @PrimaryKey val timeEpochMs: Long,
    val level: Int,
    val source: String,
)
