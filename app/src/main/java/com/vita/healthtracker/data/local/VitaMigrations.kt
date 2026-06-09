package com.vita.healthtracker.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_3 = object : Migration(1, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        addDailyInsightColumns(db)
        addColumnIfMissing(db, "sleep_session", "sleepScore", "INTEGER")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        addDailyInsightColumns(db)
        addColumnIfMissing(db, "sleep_session", "sleepScore", "INTEGER")
    }
}

val MIGRATION_3_5 = object : Migration(3, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        addExerciseDetailColumns(db)
        createBodyBatteryTable(db)
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        addExerciseDetailColumns(db)
        createBodyBatteryTable(db)
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        createGarminRawTable(db)
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        addExerciseUserColumns(db)
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        addExerciseUserColumns(db)
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        createHabitTables(db)
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        createMoodTable(db)
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        createWeatherTable(db)
    }
}

private fun addDailyInsightColumns(db: SupportSQLiteDatabase) {
    addColumnIfMissing(db, "daily_health", "avgStress", "INTEGER")
    addColumnIfMissing(db, "daily_health", "maxStress", "INTEGER")
    addColumnIfMissing(db, "daily_health", "bodyBatteryHigh", "INTEGER")
    addColumnIfMissing(db, "daily_health", "bodyBatteryLow", "INTEGER")
    addColumnIfMissing(db, "daily_health", "avgSpo2", "INTEGER")
    addColumnIfMissing(db, "daily_health", "avgRespiration", "REAL")
    addColumnIfMissing(db, "daily_health", "hrv", "INTEGER")
    addColumnIfMissing(db, "daily_health", "weightKg", "REAL")
}

private fun addExerciseDetailColumns(db: SupportSQLiteDatabase) {
    addColumnIfMissing(db, "exercise_session", "name", "TEXT")
    addColumnIfMissing(db, "exercise_session", "elapsedMinutes", "INTEGER")
    addColumnIfMissing(db, "exercise_session", "movingMinutes", "INTEGER")
    addColumnIfMissing(db, "exercise_session", "bmrCalories", "REAL")
    addColumnIfMissing(db, "exercise_session", "totalCalories", "REAL")
    addColumnIfMissing(db, "exercise_session", "steps", "INTEGER")
    addColumnIfMissing(db, "exercise_session", "avgCadence", "REAL")
    addColumnIfMissing(db, "exercise_session", "maxCadence", "REAL")
    addColumnIfMissing(db, "exercise_session", "avgSpeed", "REAL")
    addColumnIfMissing(db, "exercise_session", "maxSpeed", "REAL")
    addColumnIfMissing(db, "exercise_session", "elevationGainMeters", "REAL")
    addColumnIfMissing(db, "exercise_session", "elevationLossMeters", "REAL")
    addColumnIfMissing(db, "exercise_session", "minElevationMeters", "REAL")
    addColumnIfMissing(db, "exercise_session", "maxElevationMeters", "REAL")
    addColumnIfMissing(db, "exercise_session", "trainingEffect", "REAL")
    addColumnIfMissing(db, "exercise_session", "anaerobicTrainingEffect", "REAL")
    addColumnIfMissing(db, "exercise_session", "avgStrideLengthMeters", "REAL")
    addColumnIfMissing(db, "exercise_session", "avgPowerWatts", "REAL")
    addColumnIfMissing(db, "exercise_session", "maxPowerWatts", "REAL")
    addColumnIfMissing(db, "exercise_session", "sweatLossMl", "REAL")
    addExerciseUserColumns(db)
}

private fun addExerciseUserColumns(db: SupportSQLiteDatabase) {
    addColumnIfMissing(db, "exercise_session", "customTitle", "TEXT")
    addColumnIfMissing(db, "exercise_session", "customCategory", "TEXT")
    addColumnIfMissing(db, "exercise_session", "note", "TEXT")
    addColumnIfMissing(db, "exercise_session", "isDeleted", "INTEGER NOT NULL DEFAULT 0")
}

private fun createBodyBatteryTable(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `body_battery_sample` (
            `timeEpochMs` INTEGER NOT NULL,
            `level` INTEGER NOT NULL,
            `source` TEXT NOT NULL,
            PRIMARY KEY(`timeEpochMs`)
        )
        """.trimIndent()
    )
}

private fun createGarminRawTable(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `garmin_raw_record` (
            `date` TEXT NOT NULL,
            `domain` TEXT NOT NULL,
            `categoryKey` TEXT NOT NULL,
            `categoryLabel` TEXT NOT NULL,
            `endpointPath` TEXT NOT NULL,
            `payloadJson` TEXT NOT NULL,
            `fetchedAtEpochMs` INTEGER NOT NULL,
            PRIMARY KEY(`date`, `domain`, `categoryKey`)
        )
        """.trimIndent()
    )
}

private fun createHabitTables(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `habit_definition` (
            `id` TEXT NOT NULL,
            `name` TEXT NOT NULL,
            `colorHex` INTEGER NOT NULL,
            `sortOrder` INTEGER NOT NULL DEFAULT 0,
            `createdAtEpochMs` INTEGER NOT NULL,
            `isArchived` INTEGER NOT NULL DEFAULT 0,
            PRIMARY KEY(`id`)
        )
        """.trimIndent()
    )
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `habit_check_in` (
            `habitId` TEXT NOT NULL,
            `date` TEXT NOT NULL,
            `status` INTEGER NOT NULL,
            `updatedAtEpochMs` INTEGER NOT NULL,
            PRIMARY KEY(`habitId`, `date`)
        )
        """.trimIndent()
    )
}

private fun createMoodTable(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `mood_entry` (
            `date` TEXT NOT NULL,
            `moodId` TEXT NOT NULL,
            `note` TEXT NOT NULL DEFAULT '',
            `updatedAtEpochMs` INTEGER NOT NULL,
            PRIMARY KEY(`date`)
        )
        """.trimIndent()
    )
}

private fun createWeatherTable(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `weather_entry` (
            `date` TEXT NOT NULL,
            `weatherId` TEXT NOT NULL,
            `updatedAtEpochMs` INTEGER NOT NULL,
            PRIMARY KEY(`date`)
        )
        """.trimIndent()
    )
}

private fun addColumnIfMissing(
    db: SupportSQLiteDatabase,
    table: String,
    column: String,
    type: String,
) {
    db.query("PRAGMA table_info(`$table`)").use { cursor ->
        val nameIndex = cursor.getColumnIndex("name")
        while (cursor.moveToNext()) {
            if (cursor.getString(nameIndex) == column) return
        }
    }
    db.execSQL("ALTER TABLE `$table` ADD COLUMN `$column` $type")
}
