package com.vita.healthtracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 每天一条天气。从 2026-06-13 起改为 Open-Meteo 自动获取 ([source]="auto"),
 * 不再手动选择; 历史手动记录 ([source]="manual") 仍保留, 自动获取不会覆盖。
 *
 * [weatherId] 仍对应 [com.vita.healthtracker.domain.WeatherCatalog]; 自动获取时由
 * [com.vita.healthtracker.domain.WeatherCodeMapper] 从 WMO weather code 映射而来。
 * [tempMaxC]/[tempMinC]/[weatherCode] 只在自动来源时有值。
 */
@Serializable
@Entity(tableName = "weather_entry")
data class WeatherEntry(
    @PrimaryKey val date: String,
    val weatherId: String,
    val source: String = "manual",
    val tempMaxC: Double? = null,
    val tempMinC: Double? = null,
    val weatherCode: Int? = null,
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
)
