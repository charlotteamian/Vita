package com.vita.healthtracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.vita.healthtracker.data.local.dao.CustomMoodDao
import com.vita.healthtracker.data.local.dao.CycleDao
import com.vita.healthtracker.data.local.dao.BodyBatteryDao
import com.vita.healthtracker.data.local.dao.DailyHealthDao
import com.vita.healthtracker.data.local.dao.ExerciseDao
import com.vita.healthtracker.data.local.dao.GarminRawDao
import com.vita.healthtracker.data.local.dao.HabitDao
import com.vita.healthtracker.data.local.dao.HeartRateDao
import com.vita.healthtracker.data.local.dao.MoodDao
import com.vita.healthtracker.data.local.dao.SleepDao
import com.vita.healthtracker.data.local.dao.SyncMarkerDao
import com.vita.healthtracker.data.local.dao.WeatherDao
import com.vita.healthtracker.data.local.entity.CustomMood
import com.vita.healthtracker.data.local.entity.CycleEntry
import com.vita.healthtracker.data.local.entity.BodyBatterySample
import com.vita.healthtracker.data.local.entity.DailyHealthSnapshot
import com.vita.healthtracker.data.local.entity.ExerciseSession
import com.vita.healthtracker.data.local.entity.GarminRawRecord
import com.vita.healthtracker.data.local.entity.HabitCheckIn
import com.vita.healthtracker.data.local.entity.HabitDefinition
import com.vita.healthtracker.data.local.entity.HeartRateSample
import com.vita.healthtracker.data.local.entity.MoodEntry
import com.vita.healthtracker.data.local.entity.SleepSession
import com.vita.healthtracker.data.local.entity.SyncMarker
import com.vita.healthtracker.data.local.entity.WeatherEntry

@Database(
    entities = [
        DailyHealthSnapshot::class,
        SleepSession::class,
        HeartRateSample::class,
        ExerciseSession::class,
        CycleEntry::class,
        BodyBatterySample::class,
        GarminRawRecord::class,
        HabitDefinition::class,
        HabitCheckIn::class,
        MoodEntry::class,
        CustomMood::class,
        WeatherEntry::class,
        SyncMarker::class,
    ],
    version = 16,
    exportSchema = true,
)
abstract class VitaDatabase : RoomDatabase() {
    abstract fun dailyHealthDao(): DailyHealthDao
    abstract fun sleepDao(): SleepDao
    abstract fun heartRateDao(): HeartRateDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun cycleDao(): CycleDao
    abstract fun bodyBatteryDao(): BodyBatteryDao
    abstract fun garminRawDao(): GarminRawDao
    abstract fun habitDao(): HabitDao
    abstract fun moodDao(): MoodDao
    abstract fun customMoodDao(): CustomMoodDao
    abstract fun weatherDao(): WeatherDao
    abstract fun syncMarkerDao(): SyncMarkerDao
}
