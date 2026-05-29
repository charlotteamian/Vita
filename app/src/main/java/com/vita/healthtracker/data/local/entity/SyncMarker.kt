package com.vita.healthtracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** 记录每个数据类型的上次同步终点,避免每次全量拉取 */
@Serializable
@Entity(tableName = "sync_marker")
data class SyncMarker(
    @PrimaryKey val dataType: String,       // steps / sleep / heart_rate / ...
    val lastSyncedThroughEpochMs: Long,
    val lastSyncedAtEpochMs: Long,
)
