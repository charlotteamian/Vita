package com.vita.healthtracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** 本地天气日记。首版由用户选择，避免在未同意时上传位置。 */
@Serializable
@Entity(tableName = "weather_entry")
data class WeatherEntry(
    @PrimaryKey val date: String,
    val weatherId: String,
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
)
