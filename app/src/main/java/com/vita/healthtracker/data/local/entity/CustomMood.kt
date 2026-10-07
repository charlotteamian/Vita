package com.vita.healthtracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 用户自定义的情绪 (与内置 8 种并列出现在选择器里)。
 *
 * - [id]       稳定主键 (仓库生成 UUID), 写进 mood_entry.moodId。
 * - [shape]    [com.vita.healthtracker.domain.MoodShape] 的 name; 未知值绘制时退化为圆形。
 * - [valence]  情绪「价」1..5 (越高越正向), 与内置情绪同一口径, 供聚合/关联分析用。
 * - [isArchived] 删除 = 归档: 选择器里不再出现, 但历史记录仍能解析出名字/颜色/价。
 */
@Serializable
@Entity(tableName = "custom_mood")
data class CustomMood(
    @PrimaryKey val id: String,
    val label: String,
    val colorHex: Long,
    val shape: String,
    val valence: Int,
    val isArchived: Boolean = false,
    val createdAtEpochMs: Long = 0L,
)
