package com.vita.healthtracker.data.backup

import com.vita.healthtracker.data.local.entity.CycleEntry
import com.vita.healthtracker.data.local.entity.BodyBatterySample
import com.vita.healthtracker.data.local.entity.DailyHealthSnapshot
import com.vita.healthtracker.data.local.entity.ExerciseSession
import com.vita.healthtracker.data.local.entity.GarminRawRecord
import com.vita.healthtracker.data.local.entity.HeartRateSample
import com.vita.healthtracker.data.local.entity.HabitCheckIn
import com.vita.healthtracker.data.local.entity.HabitDefinition
import com.vita.healthtracker.data.local.entity.MoodEntry
import com.vita.healthtracker.data.local.entity.SleepSession
import com.vita.healthtracker.data.local.entity.SyncMarker
import com.vita.healthtracker.data.local.entity.WeatherEntry
import kotlinx.serialization.Serializable

@Serializable
data class BackupEnvelope(
    val version: Int = 1,
    val appVersion: String,
    val exportedAtEpochMs: Long,
    val daily: List<DailyHealthSnapshot> = emptyList(),
    val sleep: List<SleepSession> = emptyList(),
    val heartRate: List<HeartRateSample> = emptyList(),
    val exercise: List<ExerciseSession> = emptyList(),
    val bodyBattery: List<BodyBatterySample> = emptyList(),
    val garminRaw: List<GarminRawRecord> = emptyList(),
    val cycle: List<CycleEntry> = emptyList(),
    val moods: List<MoodEntry> = emptyList(),
    val habits: List<HabitDefinition> = emptyList(),
    val habitCheckIns: List<HabitCheckIn> = emptyList(),
    val weather: List<WeatherEntry> = emptyList(),
    val syncMarkers: List<SyncMarker> = emptyList(),
) {
    val totalRows: Int get() = daily.size + sleep.size + heartRate.size + exercise.size + bodyBattery.size + garminRaw.size + cycle.size + moods.size + habits.size + habitCheckIns.size + weather.size
}
