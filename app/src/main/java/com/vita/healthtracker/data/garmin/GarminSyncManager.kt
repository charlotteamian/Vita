package com.vita.healthtracker.data.garmin

import com.vita.healthtracker.data.local.entity.CycleEntry
import com.vita.healthtracker.data.local.entity.BodyBatterySample
import com.vita.healthtracker.data.local.entity.DailyHealthSnapshot
import com.vita.healthtracker.data.local.entity.ExerciseSession
import com.vita.healthtracker.data.local.entity.GarminRawRecord
import com.vita.healthtracker.data.local.entity.SleepSession
import com.vita.healthtracker.data.repository.CycleRepository
import com.vita.healthtracker.data.repository.HealthRepository
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GarminSyncManager(
    private val authClient: GarminAuthClient,
    private val dataFetcher: GarminDataFetcher,
    private val healthRepo: HealthRepository,
    private val cycleRepo: CycleRepository
) {

    suspend fun syncAll(
        fromDate: LocalDate,
        toDate: LocalDate,
        onProgress: (GarminSyncProgress) -> Unit = {},
    ): GarminSyncResult = withContext(Dispatchers.IO) {
        val totalDays = (ChronoUnit.DAYS.between(fromDate, toDate) + 1).toInt().coerceAtLeast(0)
        val domain = authClient.activeDomain()
        if (!authClient.isLogged) {
            return@withContext GarminSyncResult(
                domain = domain,
                processedDays = 0,
                totalDays = totalDays,
                savedDailyCount = 0,
                savedExerciseCount = 0,
                savedRawCount = 0,
                savedCycleCount = 0,
                cleanedEmptyDailyCount = 0,
                noDataDays = 0,
                failedDays = 0,
                updatedDates = emptyList(),
                stoppedByAuth = true,
                lastError = "Garmin 未登录",
            )
        }

        var current = fromDate
        var processed = 0
        var savedDaily = 0
        var savedExercise = 0
        var savedRaw = 0
        var savedCycle = 0
        var noDataDays = 0
        var failedDays = 0
        var lastError: String? = null
        val updatedDates = mutableListOf<LocalDate>()
        val cleanedEmptyRows = healthRepo.deleteEmptyDailySnapshots(fromDate, toDate)

        while (!current.isAfter(toDate)) {
            try {
                onProgress(
                    GarminSyncProgress(
                        currentDate = current,
                        processedDays = processed,
                        totalDays = totalDays,
                        phase = "请求日汇总",
                        networkActive = true,
                        domain = domain,
                        failedDays = failedDays,
                        updatedDates = updatedDates.toList(),
                    )
                )
                val daily = dataFetcher.fetchDailySummary(current)
                var dayHasGarminSignal = false
                if (daily != null) {
                    dayHasGarminSignal = true
                    val hrv = runCatching { dataFetcher.fetchHrv(current) }.getOrNull()
                    val incoming = DailyHealthSnapshot(
                        date = current.toString(),
                        steps = daily.totalSteps.toLong(),
                        activeCalories = daily.activeKilocalories.toDouble(),
                        totalCalories = daily.totalKilocalories.toDouble().takeIf { it > 0.0 },
                        activeMinutes = daily.activeMinutes.takeIf { it > 0L },
                        distanceMeters = daily.totalDistanceMeters,
                        floorsClimbed = daily.floorsAscended.takeIf { it > 0.0 },
                        avgHeartRate = daily.averageHeartRate,
                        restingHeartRate = daily.restingHeartRate,
                        avgStress = daily.avgStress,
                        maxStress = daily.maxStress,
                        bodyBatteryHigh = daily.bodyBatteryHigh,
                        bodyBatteryLow = daily.bodyBatteryLow,
                        avgSpo2 = daily.avgSpo2,
                        avgRespiration = daily.avgRespiration,
                        hrv = hrv,
                        source = "garmin_api"
                    )
                    val existing = healthRepo.forDate(current)
                    val merged = existing?.mergeGarmin(incoming) ?: incoming
                    if (existing != merged) {
                        healthRepo.saveDailySnapshot(merged)
                        savedDaily++
                        updatedDates.add(current)
                    }
                }

                // ---- 睡眠 (分期 / 时长 / 评分), 之前佳明同步完全没存睡眠, 导致月视图睡眠只有一条 ----
                onProgress(
                    GarminSyncProgress(
                        currentDate = current,
                        processedDays = processed,
                        totalDays = totalDays,
                        phase = "请求睡眠数据",
                        networkActive = true,
                        domain = domain,
                        failedDays = failedDays,
                        updatedDates = updatedDates.toList(),
                    )
                )
                val sleep = runCatching { dataFetcher.fetchSleep(current) }.getOrNull()
                if (sleep != null) {
                    dayHasGarminSignal = true
                    healthRepo.saveSleepSessions(
                        listOf(
                            SleepSession(
                                id = "garmin-sleep-$current",
                                startEpochMs = sleep.startEpochMs,
                                endEpochMs = sleep.endEpochMs,
                                totalMinutes = sleep.totalMinutes,
                                deepMinutes = sleep.deepMinutes,
                                lightMinutes = sleep.lightMinutes,
                                remMinutes = sleep.remMinutes,
                                awakeMinutes = sleep.awakeMinutes,
                                sleepScore = sleep.sleepScore,
                                source = "garmin_api",
                            )
                        )
                    )
                }

                // ---- 活动列表 / 运动明细。即使当日 daily 已存在, 也必须单独拉活动, 否则刚同步到
                // Garmin App 的运动不会进入本地, 最终看起来像「无新增数据」。
                onProgress(
                    GarminSyncProgress(
                        currentDate = current,
                        processedDays = processed,
                        totalDays = totalDays,
                        phase = "请求活动数据",
                        networkActive = true,
                        domain = domain,
                        savedDailyCount = savedDaily,
                        savedExerciseCount = savedExercise,
                        savedRawCount = savedRaw,
                        savedCycleCount = savedCycle,
                        failedDays = failedDays,
                        updatedDates = updatedDates.toList(),
                    )
                )
                val activities = runCatching { dataFetcher.fetchActivities(current) }.getOrDefault(emptyList())
                if (activities.isNotEmpty()) {
                    dayHasGarminSignal = true
                    healthRepo.saveExerciseSessions(
                        activities.map { activity ->
                            ExerciseSession(
                                id = "garmin-${activity.id}",
                                type = activity.type,
                                name = activity.name,
                                note = activity.note,
                                startEpochMs = activity.startEpochMs,
                                endEpochMs = activity.endEpochMs,
                                durationMinutes = activity.durationMinutes,
                                elapsedMinutes = activity.elapsedMinutes,
                                movingMinutes = activity.movingMinutes,
                                distanceMeters = activity.distanceMeters,
                                activeCalories = activity.activeCalories,
                                bmrCalories = activity.bmrCalories,
                                totalCalories = activity.totalCalories,
                                avgHeartRate = activity.avgHeartRate,
                                maxHeartRate = activity.maxHeartRate,
                                steps = activity.steps,
                                avgCadence = activity.avgCadence,
                                maxCadence = activity.maxCadence,
                                avgSpeed = activity.avgSpeed,
                                maxSpeed = activity.maxSpeed,
                                elevationGainMeters = activity.elevationGainMeters,
                                elevationLossMeters = activity.elevationLossMeters,
                                minElevationMeters = activity.minElevationMeters,
                                maxElevationMeters = activity.maxElevationMeters,
                                trainingEffect = activity.trainingEffect,
                                anaerobicTrainingEffect = activity.anaerobicTrainingEffect,
                                avgStrideLengthMeters = activity.avgStrideLengthMeters,
                                avgPowerWatts = activity.avgPowerWatts,
                                maxPowerWatts = activity.maxPowerWatts,
                                sweatLossMl = activity.sweatLossMl,
                                source = "garmin_api",
                            )
                        }
                    )
                    savedExercise += activities.size
                    if (current !in updatedDates) updatedDates.add(current)
                }

                val bodyBattery = runCatching { dataFetcher.fetchBodyBattery(current) }.getOrDefault(emptyList())
                if (bodyBattery.isNotEmpty()) {
                    dayHasGarminSignal = true
                    healthRepo.saveBodyBatterySamples(
                        bodyBattery.map {
                            BodyBatterySample(
                                timeEpochMs = it.timeEpochMs,
                                level = it.level,
                                source = "garmin_api",
                            )
                        }
                    )
                }

                onProgress(
                    GarminSyncProgress(
                        currentDate = current,
                        processedDays = processed,
                        totalDays = totalDays,
                        phase = "请求经期数据",
                        networkActive = true,
                        domain = domain,
                        savedDailyCount = savedDaily,
                        savedExerciseCount = savedExercise,
                        savedRawCount = savedRaw,
                        failedDays = failedDays,
                        updatedDates = updatedDates.toList(),
                    )
                )
                val cycle = dataFetcher.fetchMenstrualData(current)
                if (cycle != null && cycle.isPeriodDay) {
                    dayHasGarminSignal = true
                    cycleRepo.upsert(
                        CycleEntry(
                            date = current.toString(),
                            flow = cycle.flow.coerceAtLeast(1),
                            isPeriodStart = false // We'd need to determine this based on previous day, simplified here
                        )
                    )
                    savedCycle++
                }

                if (dayHasGarminSignal) {
                    val shouldRefreshRaw = !current.isBefore(LocalDate.now().minusDays(2)) ||
                        !healthRepo.hasGarminRawRecords(current, domain)
                    onProgress(
                        GarminSyncProgress(
                            currentDate = current,
                            processedDays = processed,
                            totalDays = totalDays,
                            phase = "请求全量数据",
                            networkActive = true,
                            domain = domain,
                            savedDailyCount = savedDaily,
                            savedExerciseCount = savedExercise,
                            savedRawCount = savedRaw,
                            savedCycleCount = savedCycle,
                            failedDays = failedDays,
                            updatedDates = updatedDates.toList(),
                        )
                    )
                    val rawPayloads = if (shouldRefreshRaw) {
                        runCatching { dataFetcher.fetchDailyRawPayloads(current) }.getOrDefault(emptyList()) +
                            activities.flatMap { activity ->
                                runCatching { dataFetcher.fetchActivityRawPayloads(activity) }.getOrDefault(emptyList())
                            }
                    } else {
                        emptyList()
                    }
                    if (rawPayloads.isNotEmpty()) {
                        healthRepo.saveGarminRawRecords(
                            rawPayloads.map { raw ->
                                GarminRawRecord(
                                    date = current.toString(),
                                    domain = domain,
                                    categoryKey = raw.categoryKey,
                                    categoryLabel = raw.categoryLabel,
                                    endpointPath = raw.endpointPath,
                                    payloadJson = raw.payloadJson,
                                )
                            }
                        )
                        savedRaw += rawPayloads.size
                        if (current !in updatedDates) updatedDates.add(current)
                    }
                } else {
                    noDataDays++
                }
                
                processed++
                onProgress(
                    GarminSyncProgress(
                        currentDate = current,
                        processedDays = processed,
                        totalDays = totalDays,
                        phase = "已保存",
                        networkActive = false,
                        savedDailyCount = savedDaily,
                        savedExerciseCount = savedExercise,
                        savedRawCount = savedRaw,
                        savedCycleCount = savedCycle,
                        cleanedEmptyDailyCount = cleanedEmptyRows,
                        noDataDays = noDataDays,
                        failedDays = failedDays,
                        domain = domain,
                        updatedDates = updatedDates.toList(),
                    )
                )
            } catch (e: CancellationException) {
                // 用户点了「停止同步」: 不当作失败, 直接把已完成的结果返回, 并向上传播取消。
                throw e
            } catch (e: Exception) {
                lastError = e.message ?: e::class.java.simpleName
                failedDays++
                processed++
                onProgress(
                    GarminSyncProgress(
                        currentDate = current,
                        processedDays = processed,
                        totalDays = totalDays,
                        phase = "请求失败",
                        networkActive = false,
                        savedDailyCount = savedDaily,
                        savedExerciseCount = savedExercise,
                        savedRawCount = savedRaw,
                        savedCycleCount = savedCycle,
                        cleanedEmptyDailyCount = cleanedEmptyRows,
                        noDataDays = noDataDays,
                        failedDays = failedDays,
                        domain = domain,
                        updatedDates = updatedDates.toList(),
                        lastError = lastError,
                    )
                )
                val code = (e as? GarminApiException)?.code
                if (code == 401 || code == 403) {
                    authClient.logout()
                    return@withContext GarminSyncResult(
                        processedDays = processed,
                        totalDays = totalDays,
                        savedDailyCount = savedDaily,
                        savedExerciseCount = savedExercise,
                        savedRawCount = savedRaw,
                        savedCycleCount = savedCycle,
                        cleanedEmptyDailyCount = cleanedEmptyRows,
                        noDataDays = noDataDays,
                        failedDays = failedDays,
                        updatedDates = updatedDates.toList(),
                        stoppedByAuth = true,
                        lastError = lastError,
                        domain = domain,
                    )
                }
                if (code == 429) {
                    return@withContext GarminSyncResult(
                        domain = domain,
                        processedDays = processed,
                        totalDays = totalDays,
                        savedDailyCount = savedDaily,
                        savedExerciseCount = savedExercise,
                        savedRawCount = savedRaw,
                        savedCycleCount = savedCycle,
                        cleanedEmptyDailyCount = cleanedEmptyRows,
                        noDataDays = noDataDays,
                        failedDays = failedDays,
                        updatedDates = updatedDates.toList(),
                        stoppedByAuth = false,
                        lastError = "Garmin 限流: $lastError",
                    )
                }
            }
            current = current.plusDays(1)
        }
        GarminSyncResult(
            domain = domain,
            processedDays = processed,
            totalDays = totalDays,
            savedDailyCount = savedDaily,
            savedExerciseCount = savedExercise,
            savedRawCount = savedRaw,
            savedCycleCount = savedCycle,
            cleanedEmptyDailyCount = cleanedEmptyRows,
            noDataDays = noDataDays,
            failedDays = failedDays,
            updatedDates = updatedDates.toList(),
            stoppedByAuth = false,
            lastError = lastError,
        )
    }

    private fun DailyHealthSnapshot.mergeGarmin(garmin: DailyHealthSnapshot): DailyHealthSnapshot {
        val mergedSteps = garmin.steps.takeIf { it > 0L } ?: steps
        val mergedDistance = garmin.distanceMeters?.takeIf { it > 0.0 } ?: distanceMeters
        val mergedActiveCalories = garmin.activeCalories?.takeIf { it > 0.0 } ?: activeCalories
        val mergedTotalCalories = garmin.totalCalories?.takeIf { it > 0.0 } ?: totalCalories
        val mergedActiveMinutes = garmin.activeMinutes?.takeIf { it > 0L } ?: activeMinutes
        val mergedFloors = garmin.floorsClimbed?.takeIf { it > 0.0 } ?: floorsClimbed
        val mergedAvgHr = garmin.avgHeartRate?.takeIf { it > 0 } ?: avgHeartRate
        val mergedRestingHr = garmin.restingHeartRate?.takeIf { it > 0 } ?: restingHeartRate
        val mergedAvgStress = garmin.avgStress?.takeIf { it > 0 } ?: avgStress
        val mergedMaxStress = garmin.maxStress?.takeIf { it > 0 } ?: maxStress
        val mergedBatteryHigh = garmin.bodyBatteryHigh?.takeIf { it > 0 } ?: bodyBatteryHigh
        val mergedBatteryLow = garmin.bodyBatteryLow?.takeIf { it > 0 } ?: bodyBatteryLow
        val mergedSpo2 = garmin.avgSpo2?.takeIf { it > 0 } ?: avgSpo2
        val mergedRespiration = garmin.avgRespiration?.takeIf { it > 0.0 } ?: avgRespiration
        val mergedHrv = garmin.hrv?.takeIf { it > 0 } ?: hrv
        val mergedSource = when {
            source == "garmin_api" -> "garmin_api"
            source.contains("garmin_api") -> source
            else -> "$source+garmin_api"
        }
        val changed = mergedSteps != steps ||
            mergedDistance != distanceMeters ||
            mergedActiveCalories != activeCalories ||
            mergedTotalCalories != totalCalories ||
            mergedActiveMinutes != activeMinutes ||
            mergedFloors != floorsClimbed ||
            mergedAvgHr != avgHeartRate ||
            mergedRestingHr != restingHeartRate ||
            mergedAvgStress != avgStress ||
            mergedMaxStress != maxStress ||
            mergedBatteryHigh != bodyBatteryHigh ||
            mergedBatteryLow != bodyBatteryLow ||
            mergedSpo2 != avgSpo2 ||
            mergedRespiration != avgRespiration ||
            mergedHrv != hrv ||
            mergedSource != source

        return copy(
            steps = mergedSteps,
            distanceMeters = mergedDistance,
            activeCalories = mergedActiveCalories,
            totalCalories = mergedTotalCalories,
            activeMinutes = mergedActiveMinutes,
            floorsClimbed = mergedFloors,
            avgHeartRate = mergedAvgHr,
            restingHeartRate = mergedRestingHr,
            avgStress = mergedAvgStress,
            maxStress = mergedMaxStress,
            bodyBatteryHigh = mergedBatteryHigh,
            bodyBatteryLow = mergedBatteryLow,
            avgSpo2 = mergedSpo2,
            avgRespiration = mergedRespiration,
            hrv = mergedHrv,
            updatedAtEpochMs = if (changed) System.currentTimeMillis() else updatedAtEpochMs,
            source = mergedSource,
        )
    }
}

data class GarminSyncProgress(
    val domain: String,
    val currentDate: LocalDate,
    val processedDays: Int,
    val totalDays: Int,
    val phase: String,
    val networkActive: Boolean,
    val savedDailyCount: Int = 0,
    val savedExerciseCount: Int = 0,
    val savedRawCount: Int = 0,
    val savedCycleCount: Int = 0,
    val cleanedEmptyDailyCount: Int = 0,
    val noDataDays: Int = 0,
    val failedDays: Int = 0,
    val updatedDates: List<LocalDate> = emptyList(),
    val lastError: String? = null,
) {
    val fraction: Float
        get() = if (totalDays <= 0) 0f else (processedDays.toFloat() / totalDays).coerceIn(0f, 1f)
}

data class GarminSyncResult(
    val domain: String,
    val processedDays: Int,
    val totalDays: Int,
    val savedDailyCount: Int,
    val savedExerciseCount: Int,
    val savedRawCount: Int,
    val savedCycleCount: Int,
    val cleanedEmptyDailyCount: Int,
    val noDataDays: Int,
    val failedDays: Int,
    val updatedDates: List<LocalDate>,
    val stoppedByAuth: Boolean,
    val lastError: String?,
)
