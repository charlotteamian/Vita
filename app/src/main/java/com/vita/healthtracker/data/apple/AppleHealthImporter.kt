package com.vita.healthtracker.data.apple

import android.content.Context
import android.net.Uri
import android.util.Xml
import com.vita.healthtracker.data.local.entity.CycleEntry
import com.vita.healthtracker.data.local.entity.DailyHealthSnapshot
import com.vita.healthtracker.data.local.entity.ExerciseSession
import com.vita.healthtracker.data.local.entity.SleepSession
import com.vita.healthtracker.data.repository.CycleRepository
import com.vita.healthtracker.data.repository.HealthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.InputStream
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlin.math.roundToInt
import kotlin.math.roundToLong

sealed interface AppleImportStatus {
    data object Idle : AppleImportStatus
    data class Running(val records: Long, val phase: String) : AppleImportStatus
    data class Done(
        val days: Int,
        val sleepNights: Int,
        val workouts: Int,
        val cycleDays: Int,
        val records: Long,
    ) : AppleImportStatus
    data class Error(val message: String) : AppleImportStatus
}

/**
 * 导入 iPhone「健康」App 导出的数据。
 *
 * Android 无法直连 Apple 健康 (HealthKit 只在 iOS 上)。用户在 iPhone「健康」→ 头像 → 导出所有健康数据,
 * 会得到一个 export.zip (内含 apple_health_export/export.xml)。这里支持选择该 zip 或直接选 export.xml,
 * 用流式 XmlPull 解析 (文件可能几百 MB), 按天聚合后并入 Vita 本地库, source = "apple_health"。
 *
 * 解析时把每天的步数/距离/卡路里/活动分钟/楼层/心率/静息心率/血氧/呼吸率/HRV/体重聚合进内存的
 * 「按天」表 (内存占用只跟天数有关, 跟记录条数无关), 睡眠按「醒来日期」聚合成每晚一条。
 * Workout 和经期记录直接转成 Vita 的运动/周期表, 让统计和明细复用同一套展示逻辑。
 */
class AppleHealthImporter(
    private val appContext: Context,
    private val healthRepository: HealthRepository,
    private val cycleRepository: CycleRepository,
    private val scope: CoroutineScope,
) {

    private val _status = MutableStateFlow<AppleImportStatus>(AppleImportStatus.Idle)
    val status: StateFlow<AppleImportStatus> = _status.asStateFlow()

    private var job: Job? = null

    fun start(uri: Uri) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            _status.value = AppleImportStatus.Running(0L, "读取文件…")
            try {
                val result = runImport(uri)
                _status.value = AppleImportStatus.Done(
                    days = result.days,
                    sleepNights = result.sleepNights,
                    workouts = result.workouts,
                    cycleDays = result.cycleDays,
                    records = result.records,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _status.value = AppleImportStatus.Error(e.message ?: "导入失败")
            }
        }
    }

    /** 完成 / 出错的状态消费后归位, 避免重复弹同一条提示。 */
    fun acknowledge() {
        if (_status.value !is AppleImportStatus.Running) _status.value = AppleImportStatus.Idle
    }

    private data class ImportResult(
        val days: Int,
        val sleepNights: Int,
        val workouts: Int,
        val cycleDays: Int,
        val records: Long,
    )

    private class DayAgg {
        var steps = 0L
        var distanceMeters = 0.0
        var activeCalories = 0.0
        var basalCalories = 0.0
        var activeMinutes = 0L
        var floors = 0.0
        var workoutSteps = 0L
        var workoutDistanceMeters = 0.0
        var workoutActiveCalories = 0.0
        var workoutActiveMinutes = 0L
        var workoutHrSum = 0.0; var workoutHrCount = 0
        var hrSum = 0.0; var hrCount = 0
        var restingSum = 0.0; var restingCount = 0
        var spo2Sum = 0.0; var spo2Count = 0
        var respSum = 0.0; var respCount = 0
        var hrvSum = 0.0; var hrvCount = 0
        var weightKg: Double? = null
    }

    private class SleepAgg {
        var deep = 0L; var light = 0L; var rem = 0L; var awake = 0L; var inBed = 0L
        var start = Long.MAX_VALUE; var end = Long.MIN_VALUE
    }

    private class WorkoutAgg(
        private val activityType: String,
        private val sourceName: String?,
        private val startDate: String?,
        private val endDate: String?,
        duration: Double?,
        durationUnit: String?,
        distance: Double?,
        distanceUnit: String?,
        energy: Double?,
        energyUnit: String?,
    ) {
        private var title: String? = appleWorkoutLabel(activityType)
        private var note: String? = sourceName?.takeIf { it.isNotBlank() }?.let { "来源: $it" }
        private var durationMinutes: Double? = duration?.let { toMinutes(it, durationUnit) }
        private var distanceMeters: Double? = distance?.let { toMeters(it, distanceUnit) }
        private var activeCalories: Double? = energy?.let { toKilocalories(it, energyUnit) }
        private var avgHeartRate: Int? = null
        private var maxHeartRate: Int? = null
        private var steps: Long? = null
        private var elevationGainMeters: Double? = null
        private var avgSpeed: Double? = null
        private var maxSpeed: Double? = null

        fun absorbStatistic(type: String?, sum: Double?, average: Double?, maximum: Double?, unit: String?) {
            when (type) {
                "HKQuantityTypeIdentifierActiveEnergyBurned" ->
                    activeCalories = sum?.let { toKilocalories(it, unit) } ?: activeCalories
                "HKQuantityTypeIdentifierDistanceWalkingRunning",
                "HKQuantityTypeIdentifierDistanceCycling",
                "HKQuantityTypeIdentifierDistanceSwimming" ->
                    distanceMeters = sum?.let { toMeters(it, unit) } ?: distanceMeters
                "HKQuantityTypeIdentifierAppleExerciseTime" ->
                    durationMinutes = sum?.let { toMinutes(it, unit) } ?: durationMinutes
                "HKQuantityTypeIdentifierHeartRate" -> {
                    avgHeartRate = average?.roundToInt() ?: avgHeartRate
                    maxHeartRate = maximum?.roundToInt() ?: maxHeartRate
                }
                "HKQuantityTypeIdentifierStepCount" ->
                    steps = sum?.roundToLong() ?: steps
                "HKQuantityTypeIdentifierFlightsClimbed" ->
                    elevationGainMeters = sum?.times(3.0) ?: elevationGainMeters
                "HKQuantityTypeIdentifierWalkingSpeed",
                "HKQuantityTypeIdentifierRunningSpeed",
                "HKQuantityTypeIdentifierCyclingSpeed" -> {
                    avgSpeed = average?.let { toMetersPerSecond(it, unit) } ?: avgSpeed
                    maxSpeed = maximum?.let { toMetersPerSecond(it, unit) } ?: maxSpeed
                }
            }
        }

        fun absorbMetadata(key: String?, value: String?) {
            if (key.isNullOrBlank() || value.isNullOrBlank()) return
            val normalized = key.lowercase(Locale.US)
            when {
                normalized.contains("workoutname") ||
                    normalized.contains("displayname") ||
                    normalized.endsWith("name") -> title = value
                normalized.contains("note") ||
                    normalized.contains("comment") -> note = listOfNotNull(note, value).joinToString(" · ")
                normalized.contains("indoor") && value.equals("true", ignoreCase = true) -> {
                    val base = title ?: appleWorkoutLabel(activityType)
                    title = if (base.contains("室内")) base else "室内$base"
                }
            }
        }

        fun toSession(): ExerciseSession? {
            val startMs = startDate?.epochMs() ?: return null
            val endMs = endDate?.epochMs() ?: return null
            if (endMs <= startMs) return null
            val minutes = (durationMinutes?.roundToLong() ?: ((endMs - startMs) / 60_000L)).coerceAtLeast(1L)
            val typeKey = appleWorkoutTypeKey(activityType)
            val safeTitle = title?.takeIf { it.isNotBlank() } ?: appleWorkoutLabel(activityType)
            val id = "apple_workout_${startMs}_${endMs}_${typeKey}_${activityType.hashCode()}"
            return ExerciseSession(
                id = id,
                type = typeKey,
                name = safeTitle,
                startEpochMs = startMs,
                endEpochMs = endMs,
                durationMinutes = minutes,
                elapsedMinutes = minutes,
                distanceMeters = distanceMeters?.takeIf { it > 0.0 },
                activeCalories = activeCalories?.takeIf { it > 0.0 },
                totalCalories = activeCalories?.takeIf { it > 0.0 },
                avgHeartRate = avgHeartRate?.takeIf { it > 0 },
                maxHeartRate = maxHeartRate?.takeIf { it > 0 },
                steps = steps?.takeIf { it > 0L },
                avgSpeed = avgSpeed?.takeIf { it > 0.0 },
                maxSpeed = maxSpeed?.takeIf { it > 0.0 },
                elevationGainMeters = elevationGainMeters?.takeIf { it > 0.0 },
                note = note,
                source = SOURCE_APPLE,
            )
        }
    }

    private suspend fun runImport(uri: Uri): ImportResult {
        val days = HashMap<String, DayAgg>()
        val sleeps = HashMap<String, SleepAgg>()
        val workouts = mutableListOf<ExerciseSession>()
        val cycles = HashMap<String, CycleEntry>()
        var records = 0L
        var currentWorkout: WorkoutAgg? = null

        openXmlStream(uri).use { stream ->
            val parser = Xml.newPullParser()
            parser.setInput(stream, null)
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "Record" -> {
                            val type = parser.getAttributeValue(null, "type")
                            if (type != null) {
                                handleRecord(
                                    type = type,
                                    unit = parser.getAttributeValue(null, "unit"),
                                    value = parser.getAttributeValue(null, "value"),
                                    startDate = parser.getAttributeValue(null, "startDate"),
                                    endDate = parser.getAttributeValue(null, "endDate"),
                                    days = days,
                                    sleeps = sleeps,
                                    cycles = cycles,
                                )
                            }
                            records++
                            if (records % 20000L == 0L) {
                                _status.value = AppleImportStatus.Running(records, "解析中…")
                            }
                        }
                        "Workout" -> {
                            currentWorkout = WorkoutAgg(
                                activityType = parser.getAttributeValue(null, "workoutActivityType").orEmpty(),
                                sourceName = parser.getAttributeValue(null, "sourceName"),
                                startDate = parser.getAttributeValue(null, "startDate"),
                                endDate = parser.getAttributeValue(null, "endDate"),
                                duration = parser.getAttributeValue(null, "duration")?.toDoubleOrNull(),
                                durationUnit = parser.getAttributeValue(null, "durationUnit"),
                                distance = parser.getAttributeValue(null, "totalDistance")?.toDoubleOrNull(),
                                distanceUnit = parser.getAttributeValue(null, "totalDistanceUnit"),
                                energy = parser.getAttributeValue(null, "totalEnergyBurned")?.toDoubleOrNull(),
                                energyUnit = parser.getAttributeValue(null, "totalEnergyBurnedUnit"),
                            )
                        }
                        "WorkoutStatistics" -> currentWorkout?.absorbStatistic(
                            type = parser.getAttributeValue(null, "type"),
                            sum = parser.getAttributeValue(null, "sum")?.toDoubleOrNull(),
                            average = parser.getAttributeValue(null, "average")?.toDoubleOrNull(),
                            maximum = parser.getAttributeValue(null, "maximum")?.toDoubleOrNull(),
                            unit = parser.getAttributeValue(null, "unit"),
                        )
                        "MetadataEntry" -> currentWorkout?.absorbMetadata(
                            key = parser.getAttributeValue(null, "key"),
                            value = parser.getAttributeValue(null, "value"),
                        )
                    }
                    XmlPullParser.END_TAG -> if (parser.name == "Workout") {
                        currentWorkout?.toSession()?.let { session ->
                            workouts += session
                            sessionDate(session)?.let { date ->
                                days.getOrPut(date.toString()) { DayAgg() }.apply {
                                    workoutActiveMinutes += session.durationMinutes
                                    session.distanceMeters?.takeIf { it > 0.0 }?.let { workoutDistanceMeters += it }
                                    session.activeCalories?.takeIf { it > 0.0 }?.let { workoutActiveCalories += it }
                                    session.steps?.takeIf { it > 0L }?.let { workoutSteps += it }
                                    session.avgHeartRate?.takeIf { it > 0 }?.let {
                                        workoutHrSum += it
                                        workoutHrCount++
                                    }
                                }
                            }
                        }
                        currentWorkout = null
                        records++
                        if (records % 20000L == 0L) {
                            _status.value = AppleImportStatus.Running(records, "解析中…")
                        }
                    }
                }
                event = parser.next()
            }
        }

        _status.value = AppleImportStatus.Running(records, "写入数据库…")

        // ---- 按天写入日快照 (与已有数据合并, 不覆盖手表数据) ----
        var dayCount = 0
        for ((date, agg) in days) {
            val snapshot = agg.toSnapshot(date) ?: continue
            healthRepository.upsertDailyMerged(snapshot)
            dayCount++
        }

        // ---- 睡眠: 每晚一条 ----
        val sleepSessions = sleeps.mapNotNull { (night, agg) -> agg.toSleepSession(night) }
        healthRepository.saveSleepSessions(sleepSessions)

        // ---- 运动: Apple Workout 转成 Vita 运动明细 ----
        healthRepository.saveExerciseSessions(workouts)

        // ---- 经期: 保留手动记录, 外部导入只补充非手动日期 ----
        val cycleEntries = cycles.values.markPeriodStarts()
        cycleRepository.upsertExternalPreservingManual(cycleEntries)

        return ImportResult(
            days = dayCount,
            sleepNights = sleepSessions.size,
            workouts = workouts.size,
            cycleDays = cycleEntries.size,
            records = records,
        )
    }

    private fun handleRecord(
        type: String,
        unit: String?,
        value: String?,
        startDate: String?,
        endDate: String?,
        days: HashMap<String, DayAgg>,
        sleeps: HashMap<String, SleepAgg>,
        cycles: HashMap<String, CycleEntry>,
    ) {
        // 睡眠是分类型记录, 单独处理
        if (type == "HKCategoryTypeIdentifierSleepAnalysis") {
            handleSleep(value, startDate, endDate, sleeps)
            return
        }
        if (type == "HKCategoryTypeIdentifierMenstrualFlow" ||
            type == "HKCategoryTypeIdentifierIntermenstrualBleeding" ||
            type == "HKCategoryTypeIdentifierMenstruation" ||
            type == "HKCategoryTypeIdentifierPeriod"
        ) {
            handleCycle(type, value, startDate, endDate, cycles)
            return
        }

        val dayKey = startDate?.dayKey() ?: return
        val v = value?.toDoubleOrNull() ?: return
        val agg = days.getOrPut(dayKey) { DayAgg() }

        when (type) {
            "HKQuantityTypeIdentifierStepCount" -> agg.steps += v.toLong()
            "HKQuantityTypeIdentifierDistanceWalkingRunning",
            "HKQuantityTypeIdentifierDistanceCycling",
            "HKQuantityTypeIdentifierDistanceSwimming" -> agg.distanceMeters += toMeters(v, unit)
            "HKQuantityTypeIdentifierActiveEnergyBurned" -> agg.activeCalories += toKilocalories(v, unit)
            "HKQuantityTypeIdentifierBasalEnergyBurned" -> agg.basalCalories += toKilocalories(v, unit)
            "HKQuantityTypeIdentifierAppleExerciseTime" -> agg.activeMinutes += toMinutes(v, unit).roundToLong()
            "HKQuantityTypeIdentifierFlightsClimbed" -> agg.floors += v
            "HKQuantityTypeIdentifierHeartRate" -> { agg.hrSum += v; agg.hrCount++ }
            "HKQuantityTypeIdentifierRestingHeartRate" -> { agg.restingSum += v; agg.restingCount++ }
            "HKQuantityTypeIdentifierOxygenSaturation" -> { agg.spo2Sum += if (v <= 1.0) v * 100 else v; agg.spo2Count++ }
            "HKQuantityTypeIdentifierRespiratoryRate" -> { agg.respSum += v; agg.respCount++ }
            "HKQuantityTypeIdentifierHeartRateVariabilitySDNN" -> { agg.hrvSum += v; agg.hrvCount++ }
            "HKQuantityTypeIdentifierBodyMass" -> agg.weightKg = toKilograms(v, unit)
            else -> Unit
        }
    }

    private fun handleCycle(
        type: String,
        value: String?,
        startDate: String?,
        endDate: String?,
        cycles: HashMap<String, CycleEntry>,
    ) {
        val flow = when (type) {
            "HKCategoryTypeIdentifierIntermenstrualBleeding" -> 1
            else -> when (value) {
                "HKCategoryValueMenstrualFlowHeavy" -> 4
                "HKCategoryValueMenstrualFlowMedium" -> 3
                "HKCategoryValueMenstrualFlowLight" -> 2
                "HKCategoryValueMenstrualFlowUnspecified" -> 1
                "4" -> 4
                "3" -> 3
                "2" -> 2
                "1" -> 1
                else -> 0
            }
        }
        if (flow <= 0) return
        recordDates(startDate, endDate, maxDays = 14).forEach { date ->
            val key = date.toString()
            val old = cycles[key]
            cycles[key] = CycleEntry(
                date = key,
                flow = maxOf(old?.flow ?: 0, flow),
                isPeriodStart = false,
                notes = old?.notes ?: if (type == "HKCategoryTypeIdentifierIntermenstrualBleeding") "经间出血" else null,
                symptomsCsv = old?.symptomsCsv,
                source = SOURCE_APPLE,
            )
        }
    }

    private fun handleSleep(
        value: String?,
        startDate: String?,
        endDate: String?,
        sleeps: HashMap<String, SleepAgg>,
    ) {
        value ?: return
        val startMs = startDate?.epochMs() ?: return
        val endMs = endDate?.epochMs() ?: return
        if (endMs <= startMs) return
        val minutes = (endMs - startMs) / 60_000L
        // 用「醒来 (结束时间) 那天」作为这一晚的归属日期
        val night = endDate?.dayKey() ?: return
        val agg = sleeps.getOrPut(night) { SleepAgg() }
        if (startMs < agg.start) agg.start = startMs
        if (endMs > agg.end) agg.end = endMs
        when (value) {
            "HKCategoryValueSleepAnalysisAsleepDeep" -> agg.deep += minutes
            "HKCategoryValueSleepAnalysisAsleepREM" -> agg.rem += minutes
            "HKCategoryValueSleepAnalysisAsleepCore",
            "HKCategoryValueSleepAnalysisAsleepUnspecified",
            "HKCategoryValueSleepAnalysisAsleep" -> agg.light += minutes
            "HKCategoryValueSleepAnalysisAwake" -> agg.awake += minutes
            "HKCategoryValueSleepAnalysisInBed" -> agg.inBed += minutes
            else -> Unit
        }
    }

    private fun DayAgg.toSnapshot(date: String): DailyHealthSnapshot? {
        val effectiveSteps = steps.takeIf { it > 0L } ?: workoutSteps
        val effectiveDistance = distanceMeters.takeIf { it > 0.0 } ?: workoutDistanceMeters
        val effectiveActiveCalories = activeCalories.takeIf { it > 0.0 } ?: workoutActiveCalories
        val effectiveActiveMinutes = activeMinutes.takeIf { it > 0L } ?: workoutActiveMinutes
        val averageHr = when {
            hrCount > 0 -> (hrSum / hrCount).roundToInt()
            workoutHrCount > 0 -> (workoutHrSum / workoutHrCount).roundToInt()
            else -> null
        }
        val total = if (basalCalories > 0.0) effectiveActiveCalories + basalCalories else 0.0
        val snapshot = DailyHealthSnapshot(
            date = date,
            steps = effectiveSteps,
            distanceMeters = effectiveDistance.takeIf { it > 0.0 },
            activeCalories = effectiveActiveCalories.takeIf { it > 0.0 },
            totalCalories = total.takeIf { it > 0.0 },
            floorsClimbed = floors.takeIf { it > 0.0 },
            activeMinutes = effectiveActiveMinutes.takeIf { it > 0L },
            avgHeartRate = averageHr,
            restingHeartRate = if (restingCount > 0) (restingSum / restingCount).roundToInt() else null,
            avgSpo2 = if (spo2Count > 0) (spo2Sum / spo2Count).roundToInt() else null,
            avgRespiration = if (respCount > 0) respSum / respCount else null,
            hrv = if (hrvCount > 0) (hrvSum / hrvCount).roundToInt() else null,
            weightKg = weightKg,
            updatedAtEpochMs = System.currentTimeMillis(),
            source = SOURCE_APPLE,
        )
        // 完全没有任何有效信号的天直接丢弃
        val hasData = snapshot.steps > 0 ||
            (snapshot.distanceMeters ?: 0.0) > 0.0 ||
            (snapshot.activeCalories ?: 0.0) > 0.0 ||
            (snapshot.activeMinutes ?: 0L) > 0L ||
            (snapshot.floorsClimbed ?: 0.0) > 0.0 ||
            snapshot.avgHeartRate != null ||
            snapshot.restingHeartRate != null ||
            snapshot.avgSpo2 != null ||
            snapshot.avgRespiration != null ||
            snapshot.hrv != null ||
            snapshot.weightKg != null
        return snapshot.takeIf { hasData }
    }

    private fun SleepAgg.toSleepSession(night: String): SleepSession? {
        var lightMin = light
        var asleep = deep + light + rem
        if (asleep == 0L && inBed > 0L) {
            // 旧版导出只有 InBed/Asleep, 把卧床时间当作总睡眠
            lightMin = inBed
            asleep = inBed
        }
        if (asleep <= 0L) return null
        if (start == Long.MAX_VALUE || end == Long.MIN_VALUE) return null
        return SleepSession(
            id = "apple_$night",
            startEpochMs = start,
            endEpochMs = end,
            totalMinutes = asleep,
            deepMinutes = deep,
            lightMinutes = lightMin,
            remMinutes = rem,
            awakeMinutes = awake,
            source = SOURCE_APPLE,
        )
    }

    /** 打开 export.xml 的输入流: zip 包则定位到 export.xml 条目, 否则当作 xml 直接读。 */
    private fun openXmlStream(uri: Uri): InputStream {
        val raw = appContext.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("无法打开所选文件")
        val buffered = BufferedInputStream(raw)
        buffered.mark(4)
        val magic = ByteArray(4)
        val read = buffered.read(magic)
        buffered.reset()
        val isZip = read >= 2 && magic[0] == 'P'.code.toByte() && magic[1] == 'K'.code.toByte()
        if (!isZip) return buffered

        val zis = ZipInputStream(buffered)
        var entry = zis.nextEntry
        while (entry != null && !entry.name.endsWith("export.xml")) {
            entry = zis.nextEntry
        }
        if (entry == null) {
            zis.close()
            throw IllegalStateException("压缩包里找不到 export.xml")
        }
        // 此时 zis 已定位到 export.xml, 读取它会在该条目结束处返回 -1
        return zis
    }

    private companion object {
        const val SOURCE_APPLE = "apple_health"
    }
}

// ─── 文件内私有工具 ─────────────────────────────────────────────

// Apple 日期形如 "2024-03-15 08:30:00 -0700"
private val APPLE_DATE_FMT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XX", Locale.US)

private fun String.dayKey(): String? = if (length >= 10) substring(0, 10) else null

private fun String.epochMs(): Long? = try {
    OffsetDateTime.parse(this, APPLE_DATE_FMT).toInstant().toEpochMilli()
} catch (e: Exception) {
    null
}

private fun sessionDate(session: ExerciseSession): LocalDate? =
    runCatching {
        java.time.Instant.ofEpochMilli(session.startEpochMs)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
    }.getOrNull()

private fun recordDates(startDate: String?, endDate: String?, maxDays: Long): List<LocalDate> {
    val start = startDate?.dayKey()?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return emptyList()
    val end = endDate?.dayKey()?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: start
    val days = ChronoUnit.DAYS.between(start, end).coerceIn(0L, maxDays)
    return (0..days).map { start.plusDays(it) }
}

private fun Collection<CycleEntry>.markPeriodStarts(): List<CycleEntry> {
    val sorted = sortedBy { it.date }
    var previousPeriodDate: LocalDate? = null
    return sorted.map { entry ->
        val date = runCatching { LocalDate.parse(entry.date) }.getOrNull()
        val isIntermenstrual = entry.notes == "经间出血"
        val isStart = if (date == null || isIntermenstrual) {
            false
        } else {
            val previous = previousPeriodDate
            previousPeriodDate = date
            previous == null || ChronoUnit.DAYS.between(previous, date) > 1
        }
        entry.copy(isPeriodStart = isStart)
    }
}

private fun appleWorkoutTypeKey(activityType: String): String {
    val key = activityType.removePrefix("HKWorkoutActivityType").lowercase(Locale.US)
    return when {
        key.contains("walking") -> "walking"
        key.contains("running") -> "running"
        key.contains("cycling") -> "cycling"
        key.contains("swimming") -> "pool_swimming"
        key.contains("hiking") -> "hiking"
        key.contains("traditionalstrength") ||
            key.contains("functionalstrength") ||
            key.contains("strength") -> "strength_training"
        key.contains("highintensity") ||
            key.contains("mixedcardio") ||
            key.contains("cross") ||
            key.contains("elliptical") ||
            key.contains("stairs") ||
            key.contains("rowing") -> "cardio_training"
        key.contains("yoga") || key.contains("pilates") || key.contains("flexibility") -> "yoga"
        else -> "other"
    }
}

private fun appleWorkoutLabel(activityType: String): String {
    val key = activityType.removePrefix("HKWorkoutActivityType").lowercase(Locale.US)
    return when {
        key.contains("walking") -> "步行"
        key.contains("running") -> "跑步"
        key.contains("cycling") -> "骑行"
        key.contains("swimming") -> "游泳"
        key.contains("hiking") -> "徒步"
        key.contains("traditionalstrength") || key.contains("functionalstrength") || key.contains("strength") -> "力量训练"
        key.contains("highintensity") -> "高强度间歇"
        key.contains("mixedcardio") || key.contains("cross") -> "有氧训练"
        key.contains("elliptical") -> "椭圆机"
        key.contains("stairs") -> "楼梯训练"
        key.contains("rowing") -> "划船"
        key.contains("yoga") -> "瑜伽"
        key.contains("pilates") -> "普拉提"
        key.contains("flexibility") -> "拉伸"
        else -> "运动"
    }
}

private fun toMeters(value: Double, unit: String?): Double = when (unit?.lowercase()) {
    "km" -> value * 1000.0
    "mi" -> value * 1609.344
    "m" -> value
    else -> value * 1000.0 // Apple 距离默认单位 km
}

private fun toKilocalories(value: Double, unit: String?): Double = when (unit?.lowercase(Locale.US)) {
    "kj" -> value * 0.239006
    "j" -> value * 0.000239006
    else -> value // Apple 的 Cal/kcal 都按千卡展示
}

private fun toMinutes(value: Double, unit: String?): Double = when (unit?.lowercase(Locale.US)) {
    "h", "hr", "hrs", "hour", "hours" -> value * 60.0
    "s", "sec", "secs", "second", "seconds" -> value / 60.0
    else -> value
}

private fun toMetersPerSecond(value: Double, unit: String?): Double = when (unit?.lowercase(Locale.US)) {
    "km/hr", "km/h", "kph" -> value / 3.6
    "mi/hr", "mph" -> value * 0.44704
    "m/min" -> value / 60.0
    else -> value
}

private fun toKilograms(value: Double, unit: String?): Double = when (unit?.lowercase()) {
    "lb" -> value * 0.453592
    "g" -> value / 1000.0
    "st" -> value * 6.35029
    else -> value // kg
}
