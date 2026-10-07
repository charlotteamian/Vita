package com.vita.healthtracker.data.backup

import android.content.Context
import android.net.Uri
import com.vita.healthtracker.BuildConfig
import com.vita.healthtracker.data.local.VitaDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class BackupManager(
    private val context: Context,
    private val db: VitaDatabase,
) {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    suspend fun exportToUri(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val envelope = BackupEnvelope(
                appVersion = BuildConfig.VERSION_NAME,
                exportedAtEpochMs = System.currentTimeMillis(),
                daily = db.dailyHealthDao().all(),
                sleep = db.sleepDao().all(),
                heartRate = db.heartRateDao().all(),
                exercise = db.exerciseDao().all(),
                bodyBattery = db.bodyBatteryDao().all(),
                garminRaw = db.garminRawDao().all(),
                cycle = db.cycleDao().all(),
                moods = db.moodDao().all(),
                customMoods = db.customMoodDao().all(),
                habits = db.habitDao().allHabits(),
                habitCheckIns = db.habitDao().allCheckIns(),
                weather = db.weatherDao().all(),
                syncMarkers = db.syncMarkerDao().all(),
            )
            val text = json.encodeToString(BackupEnvelope.serializer(), envelope)
            context.contentResolver.openOutputStream(uri, "w")?.use { it.write(text.toByteArray()) }
                ?: error("cannot open output stream")
            true
        }.getOrElse { false }
    }

    suspend fun importFromUri(uri: Uri): BackupEnvelope? = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                ?: error("cannot open input stream")
            val envelope = json.decodeFromString(BackupEnvelope.serializer(), text)
            if (envelope.daily.isNotEmpty()) db.dailyHealthDao().upsertAll(envelope.daily)
            if (envelope.sleep.isNotEmpty()) db.sleepDao().upsertAll(envelope.sleep)
            if (envelope.heartRate.isNotEmpty()) db.heartRateDao().upsertAll(envelope.heartRate)
            if (envelope.exercise.isNotEmpty()) db.exerciseDao().upsertAll(envelope.exercise)
            if (envelope.bodyBattery.isNotEmpty()) db.bodyBatteryDao().upsertAll(envelope.bodyBattery)
            if (envelope.garminRaw.isNotEmpty()) db.garminRawDao().upsertAll(envelope.garminRaw)
            if (envelope.cycle.isNotEmpty()) db.cycleDao().upsertAll(envelope.cycle)
            // 旧备份的情绪是每天一条 (无 id / recordedAtEpochMs), 导入时补齐再写; 新备份原样保留。
            if (envelope.moods.isNotEmpty()) {
                val normalized = envelope.moods.mapIndexed { index, mood ->
                    if (mood.id.isNotBlank() && mood.recordedAtEpochMs > 0L) {
                        mood
                    } else {
                        mood.copy(
                            id = mood.id.ifBlank { "${mood.date}#$index" },
                            recordedAtEpochMs = mood.recordedAtEpochMs.takeIf { it > 0L }
                                ?: mood.updatedAtEpochMs,
                        )
                    }
                }
                db.moodDao().upsertAll(normalized)
            }
            if (envelope.customMoods.isNotEmpty()) db.customMoodDao().upsertAll(envelope.customMoods)
            if (envelope.habits.isNotEmpty()) db.habitDao().upsertHabits(envelope.habits)
            if (envelope.habitCheckIns.isNotEmpty()) db.habitDao().upsertCheckIns(envelope.habitCheckIns)
            if (envelope.weather.isNotEmpty()) db.weatherDao().upsertAll(envelope.weather)
            envelope.syncMarkers.forEach { db.syncMarkerDao().upsert(it) }
            envelope
        }.getOrNull()
    }
}
