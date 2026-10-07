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
    // ─── 手动修正 (跨时区旅行时手表按出发地时区记录, 用户可自己改对) ───
    val isEdited: Boolean = false,          // 手动改过时间, 之后外部同步不再覆盖这条
    val isDeleted: Boolean = false,         // 软删墓碑: 不显示不统计, 且挡住重新同步带回
    val originalStartEpochMs: Long? = null, // 修正前的原始时间窗, 用来识别外部再次发来的同一条记录
    val originalEndEpochMs: Long? = null,
)
