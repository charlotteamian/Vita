package com.vita.healthtracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "sleep_session")
data class SleepSession(
    @PrimaryKey val id: String,             // Health Connect record uid
    val startEpochMs: Long,
    val endEpochMs: Long,
    val totalMinutes: Long,
    val deepMinutes: Long,
    val lightMinutes: Long,
    val remMinutes: Long,
    val awakeMinutes: Long,
    val sleepScore: Int? = null,            // 佳明睡眠评分 (0-100), HC 来源为空
    val source: String,                     // 数据来源 app (Garmin Connect / Samsung Health / ...)
)
