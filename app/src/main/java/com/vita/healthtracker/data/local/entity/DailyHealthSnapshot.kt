package com.vita.healthtracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** 一天的聚合健康指标. 主键: yyyy-MM-dd (本地时区下的日期) */
@Serializable
@Entity(tableName = "daily_health")
data class DailyHealthSnapshot(
    @PrimaryKey val date: String,
    val steps: Long = 0L,
    val distanceMeters: Double? = null,
    val activeCalories: Double? = null,
    val totalCalories: Double? = null,
    val activeMinutes: Long? = null,
    val floorsClimbed: Double? = null,
    val avgHeartRate: Int? = null,
    val restingHeartRate: Int? = null,
    // ─── 进阶指标 (主流健康 App 维度, 主要来自佳明日汇总) ───
    val avgStress: Int? = null,          // 全天平均压力 (0-100)
    val maxStress: Int? = null,          // 全天最高压力
    val bodyBatteryHigh: Int? = null,    // 身体电量最高值 (0-100)
    val bodyBatteryLow: Int? = null,     // 身体电量最低值
    val avgSpo2: Int? = null,            // 平均血氧 (%)
    val avgRespiration: Double? = null,  // 平均呼吸率 (次/分)
    val hrv: Int? = null,                // 心率变异性 HRV (ms, 昨夜平均)
    val weightKg: Double? = null,        // 体重 (kg, 来自 Health Connect)
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
    val source: String = "health_connect",
)
