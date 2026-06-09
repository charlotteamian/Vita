package com.vita.healthtracker.domain

import com.vita.healthtracker.data.local.entity.DailyHealthSnapshot
import com.vita.healthtracker.data.local.entity.SleepSession
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 多年变化分析器。
 *
 * 每个指标按自然年聚合有记录日均值，用完整逐年序列识别长期方向和最大转折。
 * 数据断档不会被补零；样本较少的年份不作为变化判断锚点。
 */
object LongTermTrendAnalyzer {

    fun analyze(
        daily: List<DailyHealthSnapshot>,
        sleeps: List<SleepSession>,
        zone: ZoneId,
    ): LongTermTrendReport? {
        val rows = daily.mapNotNull { snapshot ->
            runCatching { LocalDate.parse(snapshot.date) }.getOrNull()?.let { it to snapshot }
        }.sortedBy { it.first }
        val primarySleeps = sleeps.asSequence()
            .filter { it.totalMinutes in 1L..(16L * 60L) }
            .mapNotNull { sleep -> sleepDisplayDate(sleep, zone)?.let { it to sleep } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, sessions) -> sessions.maxByOrNull { it.totalMinutes } }
            .mapNotNull { (date, sleep) -> sleep?.let { date to it } }
            .sortedBy { it.first }

        val trackedDates = buildSet {
            rows.forEach { (date, snapshot) -> if (snapshot.hasRealSignal()) add(date) }
            primarySleeps.forEach { (date, _) -> add(date) }
        }
        val firstDate = trackedDates.minOrNull() ?: return null
        val lastDate = trackedDates.maxOrNull() ?: return null
        if (ChronoUnit.DAYS.between(firstDate, lastDate) < MIN_SPAN_DAYS) return null

        val changes = buildList {
            addMetric(
                label = "静息心率",
                unit = "bpm",
                samples = rows.mapNotNull { (date, d) -> d.restingHeartRate?.takeIf { it > 0 }?.toDouble()?.let { date to it } },
                meaningfulDelta = 3.0,
                priority = 70,
                lowerIsBetter = true,
            )
            addMetric(
                label = "睡眠时长",
                unit = "",
                samples = primarySleeps.map { (date, sleep) -> date to sleep.totalMinutes.toDouble() },
                meaningfulDelta = 20.0,
                priority = 100,
                valueFormatter = ::formatSleepMinutes,
            )
            addMetric(
                label = "日均步数",
                unit = "步",
                samples = rows.mapNotNull { (date, d) -> d.steps.takeIf { it > 0L }?.toDouble()?.let { date to it } },
                meaningfulDelta = 1_000.0,
                priority = 95,
            )
            addMetric(
                label = "日均活动时长",
                unit = "分钟",
                samples = rows.mapNotNull { (date, d) -> d.activeMinutes?.takeIf { it > 0L }?.toDouble()?.let { date to it } },
                meaningfulDelta = 15.0,
                priority = 90,
            )
            addMetric(
                label = "压力",
                unit = "",
                samples = rows.mapNotNull { (date, d) -> d.avgStress?.takeIf { it > 0 }?.toDouble()?.let { date to it } },
                meaningfulDelta = 5.0,
                priority = 65,
                lowerIsBetter = true,
            )
            addMetric(
                label = "体重",
                unit = "kg",
                samples = rows.mapNotNull { (date, d) -> d.weightKg?.takeIf { it > 0.0 }?.let { date to it } },
                meaningfulDelta = 2.0,
                priority = 62,
                valueFormatter = { "%.1f".format(it) },
            )
            addMetric(
                label = "身体电量",
                unit = "",
                samples = rows.mapNotNull { (date, d) -> d.bodyBatteryHigh?.takeIf { it > 0 }?.toDouble()?.let { date to it } },
                meaningfulDelta = 8.0,
                priority = 58,
            )
        }.sortedByDescending { it.priority }.take(3)

        if (changes.isEmpty()) return LongTermTrendReport(
            firstDate = firstDate,
            lastDate = lastDate,
            trackedDays = trackedDates.size,
            changes = emptyList(),
            headline = "已经有 ${trackedDates.size} 天记录，但还没看出稳定的长期变化。",
        )

        val lead = changes.first()
        return LongTermTrendReport(
            firstDate = firstDate,
            lastDate = lastDate,
            trackedDays = trackedDates.size,
            changes = changes,
            headline = "这些年变化最明显的是${lead.label}：${lead.earlyValueText} → ${lead.recentValueText}。",
        )
    }

    private fun MutableList<LongTermMetricChange>.addMetric(
        label: String,
        unit: String,
        samples: List<Pair<LocalDate, Double>>,
        meaningfulDelta: Double,
        priority: Int,
        lowerIsBetter: Boolean? = null,
        valueFormatter: (Double) -> String = { "%,.0f".format(it) },
    ) {
        if (samples.size < MIN_TOTAL_SAMPLES) return
        val yearly = samples
            .groupBy { it.first.year }
            .toSortedMap()
            .map { (year, rows) ->
                val average = rows.map { it.second }.average()
                LongTermYearValue(
                    year = year,
                    valueText = "${valueFormatter(average)}${unit.withSpace()}",
                    sampleDays = rows.size,
                    internalValue = average,
                )
            }
        val anchors = yearly.filter { it.sampleDays >= MIN_YEAR_SAMPLES }
        val early = anchors.firstOrNull() ?: return
        val recent = anchors.lastOrNull() ?: return
        if (recent.year <= early.year) return
        val delta = recent.internalValue - early.internalValue
        if (abs(delta) < meaningfulDelta) return
        val largestShift = anchors.zipWithNext()
            .maxByOrNull { (first, second) -> abs(second.internalValue - first.internalValue) }

        val direction = when {
            lowerIsBetter == null -> if (delta > 0) "上升" else "下降"
            (delta < 0) == lowerIsBetter -> "改善"
            else -> "走高"
        }
        add(
            LongTermMetricChange(
                label = label,
                earlyValueText = early.valueText,
                recentValueText = recent.valueText,
                deltaText = "${if (delta > 0) "+" else "−"}${valueFormatter(abs(delta))}${unit.withSpace()}",
                note = "$direction；只比较有记录的日子",
                analysisText = buildString {
                    append("${early.year} 到 ${recent.year} 年，$label${if (delta > 0) "多了" else "少了"} ${valueFormatter(abs(delta))}${unit.withSpace()}。")
                    largestShift?.let { (first, second) ->
                        val shift = second.internalValue - first.internalValue
                        if (abs(shift) >= meaningfulDelta) {
                            append(" 变化最大的一段是 ${first.year} 到 ${second.year} 年：${if (shift > 0) "多了" else "少了"} ${valueFormatter(abs(shift))}${unit.withSpace()}。")
                        }
                    }
                },
                sampleDays = samples.size,
                earlyYear = early.year,
                recentYear = recent.year,
                yearlyValues = yearly,
                delta = delta,
                priority = priority,
            )
        )
    }

    private fun String.withSpace(): String = if (isBlank()) "" else " $this"

    private fun formatSleepMinutes(value: Double): String {
        val minutes = value.roundToInt()
        return "${minutes / 60}h${minutes % 60}m"
    }

    private fun sleepDisplayDate(sleep: SleepSession, zone: ZoneId): LocalDate? = runCatching {
        Instant.ofEpochMilli(sleep.endEpochMs).atZone(zone).toLocalDate()
    }.getOrNull()

    private fun DailyHealthSnapshot.hasRealSignal(): Boolean =
        steps > 0 ||
            (distanceMeters ?: 0.0) > 0.0 ||
            (activeCalories ?: 0.0) > 0.0 ||
            (activeMinutes ?: 0L) > 0 ||
            (avgHeartRate ?: 0) > 0 ||
            (restingHeartRate ?: 0) > 0 ||
            (avgStress ?: 0) > 0 ||
            (bodyBatteryHigh ?: 0) > 0 ||
            (avgSpo2 ?: 0) > 0 ||
            (hrv ?: 0) > 0 ||
            (weightKg ?: 0.0) > 0.0

    private const val MIN_SPAN_DAYS = 365L
    private const val MIN_TOTAL_SAMPLES = 20
    private const val MIN_YEAR_SAMPLES = 5
}

data class LongTermTrendReport(
    val firstDate: LocalDate,
    val lastDate: LocalDate,
    val trackedDays: Int,
    val changes: List<LongTermMetricChange>,
    val headline: String,
)

data class LongTermMetricChange(
    val label: String,
    val earlyValueText: String,
    val recentValueText: String,
    val deltaText: String,
    val note: String,
    val analysisText: String,
    val sampleDays: Int,
    val earlyYear: Int,
    val recentYear: Int,
    val yearlyValues: List<LongTermYearValue>,
    internal val delta: Double,
    internal val priority: Int,
)

data class LongTermYearValue(
    val year: Int,
    val valueText: String,
    val sampleDays: Int,
    internal val internalValue: Double,
)
