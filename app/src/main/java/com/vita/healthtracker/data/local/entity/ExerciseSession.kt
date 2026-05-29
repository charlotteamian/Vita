package com.vita.healthtracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "exercise_session")
data class ExerciseSession(
    @PrimaryKey val id: String,
    val type: String,                       // 跑步 / 骑行 / 力量 / ...
    val name: String? = null,
    val startEpochMs: Long,
    val endEpochMs: Long,
    val durationMinutes: Long,
    val elapsedMinutes: Long? = null,
    val movingMinutes: Long? = null,
    val distanceMeters: Double?,
    val activeCalories: Double?,
    val bmrCalories: Double? = null,
    val totalCalories: Double? = null,
    val avgHeartRate: Int?,
    val maxHeartRate: Int?,
    val steps: Long? = null,
    val avgCadence: Double? = null,
    val maxCadence: Double? = null,
    val avgSpeed: Double? = null,
    val maxSpeed: Double? = null,
    val elevationGainMeters: Double? = null,
    val elevationLossMeters: Double? = null,
    val minElevationMeters: Double? = null,
    val maxElevationMeters: Double? = null,
    val trainingEffect: Double? = null,
    val anaerobicTrainingEffect: Double? = null,
    val avgStrideLengthMeters: Double? = null,
    val avgPowerWatts: Double? = null,
    val maxPowerWatts: Double? = null,
    val sweatLossMl: Double? = null,
    val customTitle: String? = null,
    val customCategory: String? = null,
    val note: String? = null,
    val isDeleted: Boolean = false,
    val source: String,
)
