package com.vita.healthtracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 每天一条情绪记录。主键 = 日期 (yyyy-MM-dd)，一天只保留一条 (覆盖)。
 * moodId 对应 [com.vita.healthtracker.domain.MoodCatalog] 里的情绪 id。
 */
@Serializable
@Entity(tableName = "mood_entry")
data class MoodEntry(
    @PrimaryKey val date: String,
    val moodId: String,
    val note: String = "",
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
)
