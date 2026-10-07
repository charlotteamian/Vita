package com.vita.healthtracker.domain

import com.vita.healthtracker.data.local.entity.CycleEntry
import com.vita.healthtracker.data.local.entity.DailyHealthSnapshot
import com.vita.healthtracker.data.local.entity.HabitCheckIn
import com.vita.healthtracker.data.local.entity.HabitDefinition
import com.vita.healthtracker.data.local.entity.MoodEntry
import com.vita.healthtracker.data.local.entity.SleepSession
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * 本地异常事件引擎。
 *
 * 与状态分不同，事件只有在「持续时间 + 偏离幅度 + 样本量」达到门槛时才出现。
 * 输出的是需要留意的可观测信号，不是医学诊断。
 */
object AnomalyEventEngine {

    fun detect(
        date: LocalDate,
        today: DailyHealthSnapshot?,
        lastSleep: SleepSession?,
        historyDaily: List<DailyHealthSnapshot>,
        historySleep: List<SleepSession>,
        habits: List<HabitDefinition>,
        habitCheckIns: List<HabitCheckIn>,
        moods: List<MoodEntry>,
        cycleEntries: List<CycleEntry>,
        zone: ZoneId,
    ): List<AnomalyEvent> {
        val daily = (historyDaily + listOfNotNull(today))
            .distinctBy { it.date }
            .mapNotNull { snapshot ->
                runCatching { LocalDate.parse(snapshot.date) }.getOrNull()?.let { it to snapshot }
            }
            .filter { (day, _) -> !day.isAfter(date) }
            .sortedBy { it.first }
        val sleeps = primarySleeps(historySleep + listOfNotNull(lastSleep), date, zone)
        val cycleContext = cycleEntries.any { it.date == date.toString() && it.flow > 0 }
        val events = buildList {
            recoveryStrain(daily, date, cycleContext)?.let(::add)
            respiratorySignal(daily, date)?.let(::add)
            sleepDebt(sleeps, date)?.let(::add)
            activityDrop(daily, date)?.let(::add)
            moodStreak(moods, date, zone)?.let(::add)
            explicitHabitMisses(habits, habitCheckIns, date)?.let(::add)
            habitCompletionDrop(habits, habitCheckIns, date)?.let(::add)
        }
        return events
            .distinctBy { it.id }
            .sortedWith(compareByDescending<AnomalyEvent> { it.severity.rank }.thenByDescending { it.priority })
            .take(MAX_EVENTS)
    }

    private fun recoveryStrain(
        daily: List<Pair<LocalDate, DailyHealthSnapshot>>,
        date: LocalDate,
        cycleContext: Boolean,
    ): AnomalyEvent? {
        val baselineRows = daily.filter { it.first < date.minusDays(2) }.takeLast(BASELINE_DAYS)
        val recentRows = daily.filter { it.first in date.minusDays(2)..date }
        if (recentRows.size < 2) return null

        val evidence = buildList {
            persistentDelta(
                label = "静息心率",
                baselineRows = baselineRows,
                recentRows = recentRows,
                selector = { it.restingHeartRate?.toDouble() },
                threshold = 5.0,
                higherIsAlert = true,
                unit = "bpm",
            )?.let(::add)
            persistentDelta(
                label = "压力",
                baselineRows = baselineRows,
                recentRows = recentRows,
                selector = { it.avgStress?.toDouble() },
                threshold = 8.0,
                higherIsAlert = true,
                unit = "",
            )?.let(::add)
            persistentDelta(
                label = "身体电量",
                baselineRows = baselineRows,
                recentRows = recentRows,
                selector = { it.bodyBatteryHigh?.toDouble() },
                threshold = 15.0,
                higherIsAlert = false,
                unit = "",
            )?.let(::add)
            persistentDelta(
                label = "HRV",
                baselineRows = baselineRows,
                recentRows = recentRows,
                selector = { it.hrv?.toDouble() },
                threshold = 5.0,
                higherIsAlert = false,
                unit = "ms",
            )?.let(::add)
        }
        if (evidence.size < 2) return null
        val context = if (cycleContext) "；当天有经期记录，可结合周期继续观察" else ""
        return AnomalyEvent(
            id = "recovery_strain",
            severity = if (evidence.size >= 3) AnomalySeverity.ALERT else AnomalySeverity.WATCH,
            category = AnomalyCategory.RECOVERY,
            title = "最近恢复状态不太稳",
            summary = "最近几天有 ${evidence.size} 项指标和平时不太一样$context。",
            evidence = evidence,
            guidance = "可以先少安排额外消耗，保证睡眠和补水。如果连续几天都这样，或有明显不舒服，再咨询专业人士。",
            priority = 100,
        )
    }

    private fun respiratorySignal(
        daily: List<Pair<LocalDate, DailyHealthSnapshot>>,
        date: LocalDate,
    ): AnomalyEvent? {
        val baselineRows = daily.filter { it.first < date.minusDays(2) }.takeLast(BASELINE_DAYS)
        val recentRows = daily.filter { it.first in date.minusDays(2)..date }
        if (recentRows.size < 2) return null
        val respiration = persistentDelta(
            label = "呼吸率",
            baselineRows = baselineRows,
            recentRows = recentRows,
            selector = { it.avgRespiration },
            threshold = 1.0,
            higherIsAlert = true,
            unit = "次/分",
            oneDecimal = true,
        )
        val lowSpo2 = recentRows.mapNotNull { it.second.avgSpo2 }.filter { it < 95 }
        if (respiration == null && lowSpo2.size < 2) return null
        val evidence = buildList {
            respiration?.let(::add)
            if (lowSpo2.size >= 2) add("近 ${recentRows.size} 天有 ${lowSpo2.size} 天血氧低于 95%")
        }
        return AnomalyEvent(
            id = "respiratory_signal",
            severity = if (respiration != null && lowSpo2.size >= 2) AnomalySeverity.ALERT else AnomalySeverity.WATCH,
            category = AnomalyCategory.RESPIRATORY,
            title = "呼吸和血氧有连续变化",
            summary = "呼吸率或血氧连续几天和平时不太一样，可以先复核佩戴和身体感受。",
            evidence = evidence,
            guidance = "留意鼻塞、咳嗽、乏力或胸闷等不适；如果血氧持续偏低或症状明显，请及时就医。",
            priority = 95,
        )
    }

    private fun sleepDebt(
        sleeps: Map<LocalDate, SleepSession>,
        date: LocalDate,
    ): AnomalyEvent? {
        val recent = (0L..2L).mapNotNull { sleeps[date.minusDays(it)] }
        if (recent.size < 2) return null
        val prior = sleeps.entries
            .filter { (day, _) -> day < date.minusDays(2) }
            .sortedBy { it.key }
            .takeLast(BASELINE_DAYS)
            .map { it.value.totalMinutes.toDouble() }
        if (prior.size < MIN_BASELINE_SAMPLES) return null
        val recentAvg = recent.map { it.totalMinutes.toDouble() }.average()
        val baselineAvg = prior.average()
        val shortNights = recent.count { it.totalMinutes < 7L * 60L }
        if (shortNights < 2 || recentAvg > baselineAvg - 45.0) return null
        return AnomalyEvent(
            id = "sleep_debt",
            severity = AnomalySeverity.WATCH,
            category = AnomalyCategory.SLEEP,
            title = "最近几晚出现睡眠欠账",
            summary = "近 ${recent.size} 晚平均 ${minutesText(recentAvg)}，比你最近常见的睡眠时长少 ${minutesText(baselineAvg - recentAvg)}。",
            evidence = listOf("$shortNights 晚少于 7 小时", "近期常见水平约 ${minutesText(baselineAvg)}"),
            guidance = "优先把上床时间提前，先观察连续两晚恢复后状态是否回升。",
            priority = 82,
        )
    }

    private fun activityDrop(
        daily: List<Pair<LocalDate, DailyHealthSnapshot>>,
        date: LocalDate,
    ): AnomalyEvent? {
        val recent = daily.filter { it.first in date.minusDays(6)..date }.mapNotNull { it.second.steps.takeIf { steps -> steps > 0 } }
        val prior = daily.filter { it.first in date.minusDays(27)..date.minusDays(7) }
            .mapNotNull { it.second.steps.takeIf { steps -> steps > 0 } }
        if (recent.size < 4 || prior.size < 8) return null
        val recentAvg = recent.average()
        val priorAvg = prior.average()
        if (priorAvg < 1_000 || recentAvg > priorAvg * 0.7) return null
        val pct = ((priorAvg - recentAvg) / priorAvg * 100).roundToInt()
        return AnomalyEvent(
            id = "activity_drop",
            severity = AnomalySeverity.NOTICE,
            category = AnomalyCategory.LIFESTYLE,
            title = "近期日常活动量下降",
            summary = "近 7 天有记录日平均 ${recentAvg.roundToInt()} 步，比此前三周下降 $pct%。",
            evidence = listOf("最近有 ${recent.size} 天可比较", "此前三周有 ${prior.size} 天可比较"),
            guidance = "如果这是主动休息可以忽略；如果不是，留意作息、工作节奏或身体状态是否发生变化。",
            priority = 55,
        )
    }

    private fun moodStreak(moods: List<MoodEntry>, date: LocalDate, zone: ZoneId): AnomalyEvent? {
        // 一天可能多条时刻: 用「当天所有时刻的平均价」判断这一天整体是否偏低,
        // 不再只看主导/最后一条——大半天都正向、只末尾一条负向时, 不该算这天情绪低。
        val daily = MoodAggregator.daily(moods, zone)
        val recent = (0L..2L).mapNotNull { offset ->
            val day = date.minusDays(offset)
            daily[day]?.let { day to it }
        }
        if (recent.size < 2 || recent.any { it.second.avgValence > LOW_MOOD_VALENCE }) return null
        val labels = recent.mapNotNull { MoodCatalog.byId(it.second.dominantMoodId)?.label }.distinct()
        return AnomalyEvent(
            id = "mood_streak",
            severity = AnomalySeverity.WATCH,
            category = AnomalyCategory.LIFESTYLE,
            title = "最近几天情绪整体偏低",
            summary = "连续 ${recent.size} 天整体情绪偏低，多为${labels.joinToString("、")}。",
            evidence = recent.sortedBy { it.first }.map { (day, d) ->
                val label = MoodCatalog.byId(d.dominantMoodId)?.label ?: d.dominantMoodId
                if (d.count > 1) "$day · $label · 当天记 ${d.count} 次" else "$day · $label"
            },
            guidance = "可以回看睡眠、压力和近期安排是否一起变化；如果低落持续或影响生活，考虑和可信任的人聊聊，必要时寻求专业帮助。",
            priority = 78,
        )
    }

    private fun explicitHabitMisses(
        habits: List<HabitDefinition>,
        checkIns: List<HabitCheckIn>,
        date: LocalDate,
    ): AnomalyEvent? {
        val activeById = habits.associateBy { it.id }
        val checkInByKey = checkIns.associateBy { it.habitId to it.date }
        val missed = habits.mapNotNull { habit ->
            val streak = generateSequence(date) { it.minusDays(1) }
                .takeWhile { day -> checkInByKey[habit.id to day.toString()]?.status == HabitCheckIn.StatusMissed }
                .count()
            habit.takeIf { streak >= 2 }?.let { it to streak }
        }.sortedByDescending { it.second }
        val lead = missed.firstOrNull() ?: return null
        return AnomalyEvent(
            id = "habit_missed_${lead.first.id}",
            severity = AnomalySeverity.NOTICE,
            category = AnomalyCategory.LIFESTYLE,
            title = "习惯「${lead.first.name}」连续 ${lead.second} 天未完成",
            summary = "这是你主动标记的未完成记录，不包含没有打卡的日期。",
            evidence = missed.take(3).mapNotNull { (habit, streak) ->
                activeById[habit.id]?.let { "「${it.name}」连续 $streak 天未完成" }
            },
            guidance = "可以把目标缩小到今天容易完成的一步，再决定是否恢复原计划。",
            priority = 45,
        )
    }

    private fun habitCompletionDrop(
        habits: List<HabitDefinition>,
        checkIns: List<HabitCheckIn>,
        date: LocalDate,
    ): AnomalyEvent? {
        if (habits.isEmpty()) return null
        fun completion(from: LocalDate, to: LocalDate): Pair<Int, Int> {
            val rows = checkIns.filter {
                val day = runCatching { LocalDate.parse(it.date) }.getOrNull()
                day != null && !day.isBefore(from) && !day.isAfter(to)
            }
            return rows.count { it.status == HabitCheckIn.StatusDone } to rows.size
        }
        val (recentDone, recentLogged) = completion(date.minusDays(6), date)
        val (priorDone, priorLogged) = completion(date.minusDays(20), date.minusDays(7))
        if (recentLogged < 3 || priorLogged < 6) return null
        val recentRate = recentDone.toDouble() / recentLogged
        val priorRate = priorDone.toDouble() / priorLogged
        if (priorRate - recentRate < 0.4) return null
        return AnomalyEvent(
            id = "habit_completion_drop",
            severity = AnomalySeverity.NOTICE,
            category = AnomalyCategory.LIFESTYLE,
            title = "近期习惯完成率下降",
            summary = "已记录打卡中，近 7 天完成率 ${(recentRate * 100).roundToInt()}%，此前两周 ${(priorRate * 100).roundToInt()}%。",
            evidence = listOf("近期 $recentDone / $recentLogged", "此前 $priorDone / $priorLogged"),
            guidance = "先看是哪一个习惯最容易中断，再决定是调整目标还是恢复节奏。",
            priority = 40,
        )
    }

    private fun persistentDelta(
        label: String,
        baselineRows: List<Pair<LocalDate, DailyHealthSnapshot>>,
        recentRows: List<Pair<LocalDate, DailyHealthSnapshot>>,
        selector: (DailyHealthSnapshot) -> Double?,
        threshold: Double,
        higherIsAlert: Boolean,
        unit: String,
        oneDecimal: Boolean = false,
    ): String? {
        val baseline = baselineRows.mapNotNull { selector(it.second) }.filter { it > 0.0 }
        val recent = recentRows.mapNotNull { selector(it.second) }.filter { it > 0.0 }
        if (baseline.size < MIN_BASELINE_SAMPLES || recent.size < 2) return null
        val baselineAvg = baseline.average()
        val recentAvg = recent.average()
        val delta = recentAvg - baselineAvg
        val abnormal = if (higherIsAlert) delta >= threshold else delta <= -threshold
        if (!abnormal) return null
        val deltaText = number(kotlin.math.abs(delta), oneDecimal)
        return "$label 近 ${recent.size} 天平均 ${number(recentAvg, oneDecimal)}${unit.withSpace()}，比近期常见水平${if (delta > 0) "高" else "低"} $deltaText${unit.withSpace()}"
    }

    private fun primarySleeps(
        sleeps: List<SleepSession>,
        date: LocalDate,
        zone: ZoneId,
    ): Map<LocalDate, SleepSession> = sleeps.asSequence()
        .filter { it.totalMinutes in 1L..(16L * 60L) }
        .mapNotNull { sleep ->
            val day = runCatching { Instant.ofEpochMilli(sleep.endEpochMs).atZone(zone).toLocalDate() }.getOrNull()
            day?.takeIf { !it.isAfter(date) }?.let { it to sleep }
        }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, sessions) -> sessions.maxByOrNull { it.totalMinutes }!! }

    private fun number(value: Double, oneDecimal: Boolean): String =
        if (oneDecimal) "%.1f".format(value) else value.roundToInt().toString()

    private fun minutesText(value: Double): String {
        val minutes = value.roundToInt()
        return "${minutes / 60}h${minutes % 60}m"
    }

    private fun String.withSpace(): String = if (isBlank()) "" else " $this"

    private const val BASELINE_DAYS = 28
    private const val MIN_BASELINE_SAMPLES = 7
    private const val MAX_EVENTS = 4

    /** 当天平均情绪「价」≤ 此值 (1..5 制, 2=疲惫/压力档) 才算这一天整体偏低。 */
    private const val LOW_MOOD_VALENCE = 2.0
}

enum class AnomalySeverity(val rank: Int, val label: String) {
    ALERT(3, "需要关注"),
    WATCH(2, "持续观察"),
    NOTICE(1, "生活提醒"),
}

enum class AnomalyCategory { RECOVERY, RESPIRATORY, SLEEP, LIFESTYLE }

data class AnomalyEvent(
    val id: String,
    val severity: AnomalySeverity,
    val category: AnomalyCategory,
    val title: String,
    val summary: String,
    val evidence: List<String>,
    val guidance: String,
    internal val priority: Int,
)
