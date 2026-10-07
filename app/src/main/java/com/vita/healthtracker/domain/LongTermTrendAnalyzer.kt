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
            synthesis = null,
        )

        val lead = changes.first()
        return LongTermTrendReport(
            firstDate = firstDate,
            lastDate = lastDate,
            trackedDays = trackedDates.size,
            changes = changes,
            headline = "这些年变化最明显的是${lead.label}：${lead.earlyValueText} → ${lead.recentValueText}。",
            synthesis = synthesis(changes),
        )
    }

    /** 跨指标互证: 两项长期变化方向一致时, 把它们连起来说成一件事。 */
    private fun synthesis(changes: List<LongTermMetricChange>): String? {
        fun deltaOf(label: String): Double? = changes.firstOrNull { it.label == label }?.delta
        val sleep = deltaOf("睡眠时长")
        val rhr = deltaOf("静息心率")
        val steps = deltaOf("日均步数")
        val weight = deltaOf("体重")
        val stress = deltaOf("压力")
        return when {
            sleep != null && rhr != null && sleep < 0 && rhr > 0 ->
                "睡眠变短和静息心率抬高互相印证：恢复的输入在变少、身体的基础负荷在变高。这是最值得优先扭转的组合——先把睡眠时间补回来，静息心率通常会跟着回落。"
            steps != null && rhr != null && steps > 0 && rhr < 0 ->
                "动得更多、静息心率更低，两者互相印证：这几年心肺在实打实地变强，保持现在的节奏就好。"
            steps != null && weight != null && steps < 0 && weight > 0 ->
                "活动量下降和体重上升同向出现，大概率是同一个生活方式变化的两面；把日常移动找回来，比单独控制饮食更容易同时改善两者。"
            sleep != null && stress != null && sleep < 0 && stress > 0 ->
                "睡眠变短和压力升高常常互为因果，容易形成循环；先固定上床时间，是打破这个循环阻力最小的一步。"
            else -> null
        }
    }

    /** 该项长期变化对生活意味着什么 + 一个可执行的方向 (按指标和方向定制)。 */
    private fun meaningFor(label: String, delta: Double): String = when (label) {
        "静息心率" -> if (delta < 0) {
            "静息心率长期下降，通常说明心肺效率和恢复能力在变好——同样的日常负荷，心脏更省力。"
        } else {
            "静息心率长期抬高值得留意：常见原因是活动量下降、长期压力、睡眠变差或体重上升。对照同期的步数和睡眠变化，通常能找到原因。"
        }
        "睡眠时长" -> {
            val perYearHours = (abs(delta) * 365 / 60).roundToInt()
            if (delta < 0) {
                "平均每晚少睡 ${formatSleepMinutes(abs(delta))}，一年累计少了约 $perYearHours 小时恢复时间。长期睡眠缩水最先体现在白天精力和恢复速度上，是这几项里最值得优先改回去的。"
            } else {
                "平均每晚多睡 ${formatSleepMinutes(abs(delta))}，一年累计多出约 $perYearHours 小时恢复时间——这是对精力最实在的长期投资。"
            }
        }
        "日均步数" -> if (delta < 0) {
            "日常移动量下降通常不是某个决定造成的，而是通勤、工作方式或习惯变化的副产品。回看变化最大的那一年发生了什么；把日常移动找回来，往往比新开一项训练更容易。"
        } else {
            "日常移动量在增加。比起刻意训练，这种嵌在生活里的活动最容易坚持，对长期健康的复利也最大。"
        }
        "日均活动时长" -> if (delta < 0) {
            "每天的活跃时间在变少，身体长期处在更久坐的状态。不必一步到位，先把每天的活跃时间往回找 10–15 分钟就有意义。"
        } else {
            "每天的活跃时间在增加，久坐被有效打散，这对代谢和精力都是正向积累。"
        }
        "压力" -> if (delta > 0) {
            "全天压力水平逐年上升，意味着身体长期更紧绷、恢复窗口被压缩。看看变化最大的那一段对应了什么生活变化——解决来源比放松技巧更有效。"
        } else {
            "全天压力水平在下降，说明这几年的生活节奏对身体更友好了。"
        }
        "体重" -> "体重的缓慢漂移最容易被忽略——每年只变一点，几年累计就很可观。按自己的目标判断方向是否符合预期；不符合时，优先调整日常活动量和饮食结构，而不是短期冲刺。"
        "身体电量" -> if (delta < 0) {
            "每日精力储备的峰值在下降，多与睡眠质量和长期负荷有关；对照同期睡眠变化，通常能解释它。"
        } else {
            "每日精力储备的峰值在上升，说明恢复系统这几年运转良好。"
        }
        else -> ""
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
                meaningText = meaningFor(label, delta),
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
    /** 跨指标互证: 两项变化方向一致时连起来给出的整体解读 (可空)。 */
    val synthesis: String?,
)

data class LongTermMetricChange(
    val label: String,
    val earlyValueText: String,
    val recentValueText: String,
    val deltaText: String,
    val note: String,
    val analysisText: String,
    /** 这项长期变化对生活意味着什么 + 可执行方向。 */
    val meaningText: String,
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
