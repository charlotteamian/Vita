package com.vita.healthtracker.data.repository

import androidx.health.connect.client.records.SleepSessionRecord
import com.vita.healthtracker.data.healthconnect.HealthConnectManager
import com.vita.healthtracker.data.healthconnect.DailyAggregate
import com.vita.healthtracker.data.local.dao.DailyHealthDao
import com.vita.healthtracker.data.local.dao.BodyBatteryDao
import com.vita.healthtracker.data.local.dao.ExerciseDao
import com.vita.healthtracker.data.local.dao.GarminRawDao
import com.vita.healthtracker.data.local.dao.HeartRateDao
import com.vita.healthtracker.data.local.dao.SleepDao
import com.vita.healthtracker.data.local.dao.SyncMarkerDao
import com.vita.healthtracker.data.local.entity.DailyHealthSnapshot
import com.vita.healthtracker.data.local.entity.BodyBatterySample
import com.vita.healthtracker.data.local.entity.ExerciseSession
import com.vita.healthtracker.data.local.entity.GarminRawRecord
import com.vita.healthtracker.data.local.entity.HeartRateSample
import com.vita.healthtracker.data.local.entity.SleepSession
import com.vita.healthtracker.data.local.entity.SyncMarker
import com.vita.healthtracker.domain.SessionDeduplicator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class HealthRepository(
    private val healthConnect: HealthConnectManager,
    private val dailyDao: DailyHealthDao,
    private val sleepDao: SleepDao,
    private val heartRateDao: HeartRateDao,
    private val exerciseDao: ExerciseDao,
    private val bodyBatteryDao: BodyBatteryDao,
    private val garminRawDao: GarminRawDao,
    private val syncDao: SyncMarkerDao,
) {

    private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun dailyRange(from: LocalDate, to: LocalDate): Flow<List<DailyHealthSnapshot>> =
        dailyDao.rangeFlow(from.format(dateFmt), to.format(dateFmt))

    fun latestDaily(): Flow<DailyHealthSnapshot?> = dailyDao.latestFlow()
    
    suspend fun saveDailySnapshot(snapshot: DailyHealthSnapshot) = dailyDao.upsert(snapshot)

    /**
     * 外部来源(如 Apple 健康导入)的日快照: 与已有数据按「每项取更有信息的值」合并,
     * 不会直接覆盖已存在的手表/Health Connect 数据。来源会被合并到 source 字段 (用 + 连接)。
     */
    suspend fun upsertDailyMerged(incoming: DailyHealthSnapshot) {
        val existing = dailyDao.forDate(incoming.date)
        dailyDao.upsert(existing?.mergeDaily(incoming) ?: incoming)
    }

    suspend fun saveSleepSessions(sessions: List<SleepSession>) {
        if (sessions.isEmpty()) return
        val from = sessions.minOf { it.startEpochMs } - SLEEP_DUPLICATE_LOOKUP_PADDING_MS
        val to = sessions.maxOf { it.endEpochMs } + SLEEP_DUPLICATE_LOOKUP_PADDING_MS
        val candidates = (sleepDao.overlapping(from, to) + sessions)
            .associateBy { it.id }
            .values
            .toList()
        val merged = SessionDeduplicator.mergeSleepSessions(candidates)
        if (merged.duplicateIds.isNotEmpty()) sleepDao.deleteByIds(merged.duplicateIds.toList())
        sleepDao.upsertAll(merged.sessions)
    }

    suspend fun forDate(date: LocalDate): DailyHealthSnapshot? =
        dailyDao.forDate(date.format(dateFmt))

    suspend fun deleteEmptyGarminSnapshots(from: LocalDate, to: LocalDate): Int =
        dailyDao.deleteEmptyGarminRows(from.format(dateFmt), to.format(dateFmt))

    suspend fun deleteEmptyDailySnapshots(from: LocalDate, to: LocalDate): Int =
        dailyDao.deleteEmptyRows(from.format(dateFmt), to.format(dateFmt))

    /** 清理 BMR-only 幽灵数据 (没戴表的日子佳明返回的恒定基础代谢)。见 #1/#2。 */
    suspend fun cleanupBmrOnlyGarmin(from: LocalDate, to: LocalDate): Int =
        dailyDao.deleteBmrOnlyGarminRows(from.format(dateFmt), to.format(dateFmt))

    /** 清理所有来源的 totalCalories-only 行, 防止基础代谢被当成真实卡路里数据。 */
    suspend fun cleanupBmrOnlyDaily(from: LocalDate, to: LocalDate): Int =
        dailyDao.deleteBmrOnlyRows(from.format(dateFmt), to.format(dateFmt))

    /** 仅刷新「上次同步时间」展示, 不改增量同步终点。供佳明同步收尾时盖戳。 */
    suspend fun markSyncedAt(now: Instant = Instant.now()) {
        val existing = syncDao.get(SYNC_KEY_FULL)
        syncDao.upsert(
            SyncMarker(
                dataType = SYNC_KEY_FULL,
                lastSyncedThroughEpochMs = existing?.lastSyncedThroughEpochMs ?: now.toEpochMilli(),
                lastSyncedAtEpochMs = now.toEpochMilli(),
            )
        )
    }

    fun sleepRange(from: Instant, to: Instant): Flow<List<SleepSession>> =
        sleepDao.rangeFlow(from.toEpochMilli(), to.toEpochMilli())
            .map(SessionDeduplicator::dedupeSleepSessions)

    fun heartRateRange(from: Instant, to: Instant): Flow<List<HeartRateSample>> =
        heartRateDao.rangeFlow(from.toEpochMilli(), to.toEpochMilli())

    fun exerciseRange(from: Instant, to: Instant): Flow<List<ExerciseSession>> =
        exerciseDao.rangeFlow(from.toEpochMilli(), to.toEpochMilli())
            .map(SessionDeduplicator::dedupeExerciseSessions)

    suspend fun saveExerciseSessions(sessions: List<ExerciseSession>) {
        if (sessions.isEmpty()) return
        val existingById = exerciseDao.byIds(sessions.map { it.id }).associateBy { it.id }
        val incoming = sessions.map { fresh ->
            val existing = existingById[fresh.id]
            if (existing?.isDeleted == true) {
                existing
            } else {
                fresh.copy(
                    customTitle = existing?.customTitle,
                    customCategory = existing?.customCategory,
                    note = existing?.note ?: fresh.note,
                    isDeleted = existing?.isDeleted ?: false,
                )
            }
        }
        val from = incoming.minOf { it.startEpochMs } - EXERCISE_DUPLICATE_LOOKUP_PADDING_MS
        val to = incoming.maxOf { it.endEpochMs } + EXERCISE_DUPLICATE_LOOKUP_PADDING_MS
        val candidates = (exerciseDao.overlapping(from, to) + incoming)
            .associateBy { it.id }
            .values
            .toList()
        val merged = SessionDeduplicator.mergeExerciseSessions(candidates)
        if (merged.duplicateIds.isNotEmpty()) exerciseDao.deleteByIds(merged.duplicateIds.toList())
        exerciseDao.upsertAll(merged.sessions)
    }

    suspend fun updateExerciseUserFields(id: String, customTitle: String?, customCategory: String?, note: String?) {
        exerciseDao.updateUserFields(
            id = id,
            customTitle = customTitle?.trim()?.takeIf { it.isNotBlank() },
            customCategory = customCategory?.trim()?.takeIf { it.isNotBlank() },
            note = note?.trim()?.takeIf { it.isNotBlank() },
        )
    }

    suspend fun deleteExercise(id: String) {
        exerciseDao.markDeleted(id)
    }

    fun bodyBatteryRange(from: Instant, to: Instant): Flow<List<BodyBatterySample>> =
        bodyBatteryDao.rangeFlow(from.toEpochMilli(), to.toEpochMilli())

    suspend fun saveBodyBatterySamples(samples: List<BodyBatterySample>) {
        if (samples.isNotEmpty()) bodyBatteryDao.upsertAll(samples)
    }

    fun garminRawForDate(date: LocalDate): Flow<List<GarminRawRecord>> =
        garminRawDao.forDateFlow(date.format(dateFmt))

    suspend fun saveGarminRawRecords(records: List<GarminRawRecord>) {
        if (records.isNotEmpty()) garminRawDao.upsertAll(records)
    }

    suspend fun hasGarminRawRecords(date: LocalDate, domain: String): Boolean =
        garminRawDao.countForDateDomain(date.format(dateFmt), domain) > 0

    suspend fun lastSyncedAt(): Instant? = syncDao.get(SYNC_KEY_FULL)?.lastSyncedAtEpochMs?.let(Instant::ofEpochMilli)

    fun observeLastSyncedAt(): Flow<Instant?> = syncDao.getFlow(SYNC_KEY_FULL).map { it?.lastSyncedAtEpochMs?.let(Instant::ofEpochMilli) }

    /** 增量同步: 从上次终点 (或最多 90 天前) 拉到现在. 也可以强制指定起点. */
    suspend fun syncFromHealthConnect(
        zone: ZoneId = ZoneId.systemDefault(),
        forceStartFrom: Instant? = null,
    ): SyncOutcome {
        if (healthConnect.availability != HealthConnectManager.Availability.Installed) {
            return SyncOutcome.HealthConnectUnavailable
        }
        // 只要授权了任意一类读权限就放行(之前要求「全部」授权, 漏一项就整体不同步, 导致睡眠等读不到)。
        if (!healthConnect.hasAnyReadPermission()) return SyncOutcome.MissingPermission

        val now = Instant.now()
        val marker = syncDao.get(SYNC_KEY_FULL)
        val defaultStart = now.minusSeconds(90L * 24 * 3600)
        val start = forceStartFrom ?: marker?.lastSyncedThroughEpochMs?.let(Instant::ofEpochMilli) ?: defaultStart
        // 睡眠/运动/心率是「会话型」记录, 常在同步点之后才补录、且时间戳落在过去(如昨晚的睡眠今早才入库)。
        // 增量同步对这类记录要往前多回看几天, 否则会漏掉。upsert 以 HC 记录 id 去重, 重复读无害。
        val recordStart = start.minusSeconds(3L * 24 * 3600)


        // ---- 睡眠会话 ----
        val sleeps = healthConnect.readSleepSessions(recordStart, now).map { r ->
            val stages = r.stages
            SleepSession(
                id = r.metadata.id,
                startEpochMs = r.startTime.toEpochMilli(),
                endEpochMs = r.endTime.toEpochMilli(),
                totalMinutes = (r.endTime.toEpochMilli() - r.startTime.toEpochMilli()) / 60_000L,
                deepMinutes = stages.totalMinutes(SleepSessionRecord.STAGE_TYPE_DEEP),
                lightMinutes = stages.totalMinutes(SleepSessionRecord.STAGE_TYPE_LIGHT),
                remMinutes = stages.totalMinutes(SleepSessionRecord.STAGE_TYPE_REM),
                awakeMinutes = stages.totalMinutes(SleepSessionRecord.STAGE_TYPE_AWAKE) +
                    stages.totalMinutes(SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED),
                source = r.metadata.dataOrigin.packageName,
            )
        }
        saveSleepSessions(sleeps)

        // ---- 心率采样 (展开所有 sample) ----
        val hrSamples = healthConnect.readHeartRate(recordStart, now).flatMap { rec ->
            rec.samples.map { s ->
                HeartRateSample(
                    timeEpochMs = s.time.toEpochMilli(),
                    bpm = s.beatsPerMinute.toInt(),
                    source = rec.metadata.dataOrigin.packageName,
                )
            }
        }
        if (hrSamples.isNotEmpty()) heartRateDao.upsertAll(hrSamples)

        // ---- 运动会话 ----
        val exercises = healthConnect.readExerciseSessions(recordStart, now).map { r ->
            ExerciseSession(
                id = r.metadata.id,
                type = r.exerciseType.toString(),
                startEpochMs = r.startTime.toEpochMilli(),
                endEpochMs = r.endTime.toEpochMilli(),
                durationMinutes = (r.endTime.toEpochMilli() - r.startTime.toEpochMilli()) / 60_000L,
                distanceMeters = null,
                activeCalories = null,
                avgHeartRate = null,
                maxHeartRate = null,
                source = r.metadata.dataOrigin.packageName,
            )
        }
        saveExerciseSessions(exercises)

        // ---- 按天聚合: steps / distance / calories / floors ----
        val fromDate = LocalDate.ofInstant(start, zone)
        val toDate = LocalDate.ofInstant(now, zone)
        dailyDao.deleteEmptyRows(fromDate.format(dateFmt), toDate.format(dateFmt))
        dailyDao.deleteBmrOnlyRows(fromDate.format(dateFmt), toDate.format(dateFmt))
        // ---- 体重: 按天取该天最后一条 (HC WeightRecord) ----
        val weightByDate = healthConnect.readWeight(recordStart, now)
            .groupBy { LocalDate.ofInstant(it.time, zone) }
            .mapValues { (_, recs) -> recs.maxByOrNull { it.time }?.weight?.inKilograms }

        val daily = healthConnect.aggregateByDay(fromDate, toDate, zone)
        if (daily.isNotEmpty()) {
            // 平均心率单独聚合
            val updatedAt = now.toEpochMilli()
            val snapshots = daily.map { d ->
                val dayStart = d.date.atStartOfDay(zone).toInstant()
                val dayEnd = d.date.plusDays(1).atStartOfDay(zone).toInstant()
                val avgHr = heartRateDao.averageBetween(dayStart.toEpochMilli(), dayEnd.toEpochMilli())?.toInt()
                var resting = healthConnect.readRestingHeartRate(dayStart, dayEnd)
                    .firstOrNull()?.beatsPerMinute?.toInt()
                if (resting == null) {
                    resting = heartRateDao.minBetween(dayStart.toEpochMilli(), dayEnd.toEpochMilli())?.toInt()
                }
                DailyHealthSnapshot(
                    date = d.date.format(dateFmt),
                    steps = d.steps,
                    distanceMeters = d.distanceMeters,
                    activeCalories = d.activeCalories,
                    totalCalories = d.totalCalories,
                    activeMinutes = 0L,           // TODO: 从 exercise sessions 推导
                    floorsClimbed = d.floorsClimbed,
                    avgHeartRate = avgHr,
                    restingHeartRate = resting,
                    weightKg = weightByDate[d.date],
                    updatedAtEpochMs = updatedAt,
                )
            }.filter { it.hasAnyData() }
            snapshots.forEach { snapshot ->
                val existing = dailyDao.forDate(snapshot.date)
                dailyDao.upsert(existing?.mergeDaily(snapshot) ?: snapshot)
            }
        }

        syncDao.upsert(
            SyncMarker(
                dataType = SYNC_KEY_FULL,
                lastSyncedThroughEpochMs = now.toEpochMilli(),
                lastSyncedAtEpochMs = now.toEpochMilli(),
            )
        )
        return SyncOutcome.Synced(
            dailyCount = daily.count { it.hasAnyData() },
            sleepCount = sleeps.size,
            heartRateCount = hrSamples.size,
            exerciseCount = exercises.size,
        )
    }

    suspend fun insertMockData() {
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val dailyList = mutableListOf<DailyHealthSnapshot>()
        val sleepList = mutableListOf<SleepSession>()
        val exerciseList = mutableListOf<ExerciseSession>()
        val types = listOf("56", "79", "8") // Running, Walking, Biking

        for (i in 0..14) {
            val date = LocalDate.now().minusDays(i.toLong())
            val dateStr = date.format(dateFmt)
            val dayStart = date.atStartOfDay(zone).toInstant()

            dailyList.add(
                DailyHealthSnapshot(
                    date = dateStr,
                    steps = (3000..12000).random().toLong(),
                    distanceMeters = (2000..8000).random().toDouble(),
                    activeCalories = (100..500).random().toDouble(),
                    totalCalories = (1500..2500).random().toDouble(),
                    activeMinutes = (10..60).random().toLong(),
                    floorsClimbed = (0..10).random().toDouble(),
                    avgHeartRate = (60..90).random(),
                    restingHeartRate = (50..70).random(),
                    updatedAtEpochMs = now.toEpochMilli()
                )
            )

            // Sleep around 10 PM
            val sleepStart = dayStart.minusSeconds(3600 * 2) 
            val sleepEnd = sleepStart.plusSeconds(3600 * 7 + (0..3600).random().toLong())
            sleepList.add(
                SleepSession(
                    id = "mock_sleep_$i",
                    startEpochMs = sleepStart.toEpochMilli(),
                    endEpochMs = sleepEnd.toEpochMilli(),
                    totalMinutes = (sleepEnd.toEpochMilli() - sleepStart.toEpochMilli()) / 60000,
                    deepMinutes = (60..120).random().toLong(),
                    lightMinutes = (180..240).random().toLong(),
                    remMinutes = (40..90).random().toLong(),
                    awakeMinutes = (10..30).random().toLong(),
                    source = "Mock Data"
                )
            )

            // Exercise every other day
            if (i % 2 == 0) {
                val exStart = dayStart.plusSeconds(3600 * 18) // 6 PM
                val exDurationMin = (20..60).random().toLong()
                val exEnd = exStart.plusSeconds(exDurationMin * 60)
                exerciseList.add(
                    ExerciseSession(
                        id = "mock_ex_$i",
                        type = types.random(),
                        startEpochMs = exStart.toEpochMilli(),
                        endEpochMs = exEnd.toEpochMilli(),
                        durationMinutes = exDurationMin,
                        distanceMeters = exDurationMin * 100.0,
                        activeCalories = exDurationMin * 10.0,
                        avgHeartRate = (120..150).random(),
                        maxHeartRate = (150..180).random(),
                        source = "Mock Data"
                    )
                )
            }
        }
        dailyDao.upsertAll(dailyList)
        sleepDao.upsertAll(sleepList)
        saveExerciseSessions(exerciseList)
        
        syncDao.upsert(
            SyncMarker(
                dataType = SYNC_KEY_FULL,
                lastSyncedThroughEpochMs = now.toEpochMilli(),
                lastSyncedAtEpochMs = now.toEpochMilli(),
            )
        )
    }

    private fun List<SleepSessionRecord.Stage>.totalMinutes(stageType: Int): Long =
        filter { it.stage == stageType }
            .sumOf { (it.endTime.toEpochMilli() - it.startTime.toEpochMilli()) / 60_000L }

    private fun DailyAggregate.hasAnyData(): Boolean =
        steps > 0 ||
            distanceMeters > 0.0 ||
            activeCalories > 0.0 ||
            floorsClimbed > 0.0

    private fun DailyHealthSnapshot.hasAnyData(): Boolean =
        steps > 0 ||
            (distanceMeters ?: 0.0) > 0.0 ||
            (activeCalories ?: 0.0) > 0.0 ||
            (activeMinutes ?: 0L) > 0L ||
            (floorsClimbed ?: 0.0) > 0.0 ||
            (avgHeartRate ?: 0) > 0 ||
            (restingHeartRate ?: 0) > 0 ||
            (avgStress ?: 0) > 0 ||
            (maxStress ?: 0) > 0 ||
            (avgSpo2 ?: 0) > 0 ||
            (avgRespiration ?: 0.0) > 0.0 ||
            (hrv ?: 0) > 0 ||
            (weightKg ?: 0.0) > 0.0

    private fun DailyHealthSnapshot.mergeDaily(incoming: DailyHealthSnapshot): DailyHealthSnapshot {
        fun maxLong(current: Long, next: Long): Long = maxOf(current, next)
        fun maxNullableDouble(current: Double?, next: Double?): Double? {
            val cur = current?.takeIf { it > 0.0 }
            val inc = next?.takeIf { it > 0.0 }
            return when {
                cur != null && inc != null -> maxOf(cur, inc)
                inc != null -> inc
                else -> cur
            }
        }
        fun maxNullableLong(current: Long?, next: Long?): Long? {
            val cur = current?.takeIf { it > 0L }
            val inc = next?.takeIf { it > 0L }
            return when {
                cur != null && inc != null -> maxOf(cur, inc)
                inc != null -> inc
                else -> cur
            }
        }
        fun preferPresent(current: Int?, next: Int?): Int? = next?.takeIf { it > 0 } ?: current?.takeIf { it > 0 }
        fun preferPresentDouble(current: Double?, next: Double?): Double? = next?.takeIf { it > 0.0 } ?: current?.takeIf { it > 0.0 }
        val mergedSource = mergeSource(source, incoming.source)
        val mergedWeight = preferPresentDouble(weightKg, incoming.weightKg)
        val changed = steps != maxLong(steps, incoming.steps) ||
            distanceMeters != maxNullableDouble(distanceMeters, incoming.distanceMeters) ||
            activeCalories != maxNullableDouble(activeCalories, incoming.activeCalories) ||
            totalCalories != maxNullableDouble(totalCalories, incoming.totalCalories) ||
            activeMinutes != maxNullableLong(activeMinutes, incoming.activeMinutes) ||
            floorsClimbed != maxNullableDouble(floorsClimbed, incoming.floorsClimbed) ||
            avgHeartRate != preferPresent(avgHeartRate, incoming.avgHeartRate) ||
            restingHeartRate != preferPresent(restingHeartRate, incoming.restingHeartRate) ||
            weightKg != mergedWeight ||
            source != mergedSource
        return copy(
            steps = maxLong(steps, incoming.steps),
            distanceMeters = maxNullableDouble(distanceMeters, incoming.distanceMeters),
            activeCalories = maxNullableDouble(activeCalories, incoming.activeCalories),
            totalCalories = maxNullableDouble(totalCalories, incoming.totalCalories),
            activeMinutes = maxNullableLong(activeMinutes, incoming.activeMinutes),
            floorsClimbed = maxNullableDouble(floorsClimbed, incoming.floorsClimbed),
            avgHeartRate = preferPresent(avgHeartRate, incoming.avgHeartRate),
            restingHeartRate = preferPresent(restingHeartRate, incoming.restingHeartRate),
            avgStress = preferPresent(avgStress, incoming.avgStress),
            maxStress = preferPresent(maxStress, incoming.maxStress),
            bodyBatteryHigh = preferPresent(bodyBatteryHigh, incoming.bodyBatteryHigh),
            bodyBatteryLow = preferPresent(bodyBatteryLow, incoming.bodyBatteryLow),
            avgSpo2 = preferPresent(avgSpo2, incoming.avgSpo2),
            avgRespiration = preferPresentDouble(avgRespiration, incoming.avgRespiration),
            hrv = preferPresent(hrv, incoming.hrv),
            weightKg = mergedWeight,
            updatedAtEpochMs = if (changed) System.currentTimeMillis() else updatedAtEpochMs,
            source = mergedSource,
        )
    }

    private fun mergeSource(current: String, incoming: String): String =
        (current.split("+") + incoming.split("+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("+")

    sealed interface SyncOutcome {
        data class Synced(
            val dailyCount: Int,
            val sleepCount: Int,
            val heartRateCount: Int,
            val exerciseCount: Int,
        ) : SyncOutcome
        data object HealthConnectUnavailable : SyncOutcome
        data object MissingPermission : SyncOutcome
    }

    companion object {
        const val SYNC_KEY_FULL = "full"
        private const val SLEEP_DUPLICATE_LOOKUP_PADDING_MS = 30L * 60L * 1000L
        private const val EXERCISE_DUPLICATE_LOOKUP_PADDING_MS = 5L * 60L * 1000L
    }
}
