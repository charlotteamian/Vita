package com.vita.healthtracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 一条「情绪时刻」。一天可以有多条 (此前是每天一条覆盖)。
 *
 * - [id]            稳定主键 (仿 HabitDefinition 用 String, 由仓库生成 UUID)。
 * - [date]          记录时刻所在本地日期 (yyyy-MM-dd), 仅作分组/范围查询。
 * - [recordedAtEpochMs] 记录发生的时刻, 排序与早晚分析用。
 * - [moodId]        对应 [com.vita.healthtracker.domain.MoodCatalog] 的情绪 id。
 * - [weatherId]     记这一刻时顺手记的天气 (对应 [com.vita.healthtracker.domain.WeatherCatalog]);
 *                   一天内天气多变, 故随情绪时刻记, 可空。
 *
 * 旧的每日记录在迁移 (MIGRATION_11_12) 中各转成一条时刻; 备份里的旧记录在
 * BackupManager 导入时补 id / recordedAtEpochMs。所以这些字段保留默认值只为反序列化兼容,
 * 正常写入一律由仓库给出真实值。
 */
@Serializable
@Entity(tableName = "mood_entry")
data class MoodEntry(
    @PrimaryKey val id: String = "",
    val date: String,
    val recordedAtEpochMs: Long = 0L,
    val moodId: String,
    val note: String = "",
    val weatherId: String? = null,
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
)
