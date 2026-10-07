package com.vita.healthtracker.domain

import com.vita.healthtracker.data.local.entity.DailyHealthSnapshot
import com.vita.healthtracker.data.local.entity.SleepSession
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 「身体趋势」分析: 近 30 天均值 vs 上一个 30 天 (31~60 天前),
 * 反映近期作息/训练负荷的中期变化, 主要供今日洞察判断「单日波动 vs 持续走弱」。
 * (多年尺度的长期变化由 LongTermTrendAnalyzer 在趋势页负责。)
 *
 * 纯函数、不可变, 不读数据库; 数据由 ViewModel 注入。窗口任一侧样本不足则该维度跳过,
 * 全部不足时返回 null。
 */
object TrendAnalyzer {

    enum class TrendWindow(
        val label: String,
        val tagline: String,
        /** 当前窗口天数。 */
        val currentDays: Long,
        /** 基线窗口相对当前窗口整体往前推的天数。 */
        val baselineOffset: Long,
        /** 每侧最少有效样本天数, 低于此不参与对比。 */
        val minSamples: Int,
    ) {
        MONTH("月度", "近 30 天 vs 上个 30 天", 30, 30, 7),
    }

    fun analyze(
        window: TrendWindow,
        asOf: LocalDate,
        daily: List<DailyHealthSnapshot>,
        sleeps: List<SleepSession>,
        zone: ZoneId,
    ): TrendReport? {
        val curEnd = asOf
        val curStart = asOf.minusDays(window.currentDays - 1)
        val baseEnd = curEnd.minusDays(window.baselineOffset)
        val baseStart = curStart.minusDays(window.baselineOffset)

        fun dailyIn(start: LocalDate, end: LocalDate) = daily.filter {
            val d = runCatching { LocalDate.parse(it.date) }.getOrNull()
            d != null && !d.isBefore(start) && !d.isAfter(end)
        }
        fun sleepIn(start: LocalDate, end: LocalDate) = sleeps.filter { s ->
            val d = sleepDisplayDate(s, zone)
            s.totalMinutes > 0 && d != null && !d.isBefore(start) && !d.isAfter(end)
        }

        val curDaily = dailyIn(curStart, curEnd)
        val baseDaily = dailyIn(baseStart, baseEnd)
        val curSleep = sleepIn(curStart, curEnd)
        val baseSleep = sleepIn(baseStart, baseEnd)

        val metrics = mutableListOf<MetricTrend>()

        fun addDaily(
            label: String,
            kind: TrendKind,
            unit: String,
            fmt: (Double) -> String,
            selector: (DailyHealthSnapshot) -> Double?,
        ) {
            val cur = curDaily.mapNotNull(selector).filter { it > 0 }
            val base = baseDaily.mapNotNull(selector).filter { it > 0 }
            if (cur.size < window.minSamples || base.size < window.minSamples) return
            metrics += buildMetricTrend(label, kind, unit, cur.average(), base.average(), fmt)
        }

        addDaily("心率变异性", TrendKind.HIGHER_BETTER, "ms", { "%.0f".format(it) }) { it.hrv?.toDouble() }
        addDaily("静息心率", TrendKind.LOWER_BETTER, "bpm", { "%.0f".format(it) }) { it.restingHeartRate?.toDouble() }

        run {
            val cur = curSleep.map { it.totalMinutes.toDouble() }
            val base = baseSleep.map { it.totalMinutes.toDouble() }
            if (cur.size >= window.minSamples && base.size >= window.minSamples) {
                metrics += buildMetricTrend(
                    "睡眠时长", TrendKind.HIGHER_BETTER, "", cur.average(), base.average(),
                ) { minutesText(it) }
            }
        }

        addDaily("身体电量", TrendKind.HIGHER_BETTER, "", { "%.0f".format(it) }) { it.bodyBatteryHigh?.toDouble() }
        addDaily("压力", TrendKind.LOWER_BETTER, "", { "%.0f".format(it) }) { it.avgStress?.toDouble() }
        addDaily("呼吸率", TrendKind.NEUTRAL, "次/分", { "%.1f".format(it) }) { it.avgRespiration }
        addDaily("血氧", TrendKind.HIGHER_BETTER, "%", { "%.0f".format(it) }) { it.avgSpo2?.toDouble() }
        addDaily("体重", TrendKind.NEUTRAL, "kg", { "%.1f".format(it) }) { it.weightKg }

        if (metrics.isEmpty()) return null

        val ordered = metrics.sortedBy {
            when (it.direction) {
                TrendDirection.DECLINED -> 0
                TrendDirection.IMPROVED -> 1
                TrendDirection.NEUTRAL -> 2
                TrendDirection.STABLE -> 3
            }
        }
        return TrendReport(window, ordered, buildHeadline(window, ordered))
    }

    private fun buildMetricTrend(
        label: String,
        kind: TrendKind,
        unit: String,
        curMean: Double,
        baseMean: Double,
        fmt: (Double) -> String,
    ): MetricTrend {
        val diff = curMean - baseMean
        val pct = if (baseMean != 0.0) diff / baseMean * 100.0 else 0.0
        // 平稳阈值: 相对变化小于该比例视作持平 (体重波动更敏感)。
        val stableThresh = if (label == "体重") 1.5 else 3.0
        val direction = when {
            abs(pct) < stableThresh -> TrendDirection.STABLE
            kind == TrendKind.NEUTRAL -> TrendDirection.NEUTRAL
            (diff > 0) == (kind == TrendKind.HIGHER_BETTER) -> TrendDirection.IMPROVED
            else -> TrendDirection.DECLINED
        }
        val arrow = if (diff > 0) "↑" else if (diff < 0) "↓" else "→"
        val deltaText = if (label == "睡眠时长") {
            "$arrow ${minutesText(abs(diff))}"
        } else {
            val u = if (unit.isBlank()) "" else " $unit"
            val sign = if (diff > 0) "+" else "−"
            "$arrow ${fmt(abs(diff))}$u ($sign${"%.0f".format(abs(pct))}%)"
        }
        return MetricTrend(
            label = label,
            direction = direction,
            currentText = fmt(curMean),
            baselineText = fmt(baseMean),
            deltaText = deltaText,
            note = interpretation(label, direction),
        )
    }

    /** 针对维度 + 走向给一句通俗解读。 */
    private fun interpretation(label: String, dir: TrendDirection): String = when (label) {
        "心率变异性" -> when (dir) {
            TrendDirection.IMPROVED -> "自主神经恢复变好, 通常对应训练适应、睡眠与压力管理到位。"
            TrendDirection.DECLINED -> "自主神经储备走低, 多见于累积疲劳、睡眠不足或长期压力, 留意安排恢复。"
            else -> "与前期基本持平, 自主神经状态稳定。"
        }
        "静息心率" -> when (dir) {
            TrendDirection.IMPROVED -> "静息心率下降, 是心肺效率与恢复改善的良性信号。"
            TrendDirection.DECLINED -> "静息心率抬高, 可能是疲劳、睡眠差或潜在不适, 持续升高建议关注。"
            else -> "静息心率稳定。"
        }
        "睡眠时长" -> when (dir) {
            TrendDirection.IMPROVED -> "平均睡得更久, 有利于长期恢复与精力储备。"
            TrendDirection.DECLINED -> "平均睡眠缩短, 长期睡债会拖累恢复与状态, 建议规律作息。"
            else -> "睡眠时长稳定。"
        }
        "身体电量" -> when (dir) {
            TrendDirection.IMPROVED -> "每日能量峰值上升, 整体储备更充足。"
            TrendDirection.DECLINED -> "每日能量峰值下降, 多为负荷偏重或恢复不足。"
            else -> "身体电量稳定。"
        }
        "压力" -> when (dir) {
            TrendDirection.IMPROVED -> "全天压力下降, 放松与恢复时间更充裕。"
            TrendDirection.DECLINED -> "全天压力升高, 长期高压会侵蚀恢复, 建议增加放松窗口。"
            else -> "压力水平稳定。"
        }
        "呼吸率" -> when (dir) {
            TrendDirection.NEUTRAL -> "呼吸率和前期不太一样，可以结合睡眠、鼻塞或疲惫感一起看。"
            else -> "呼吸率稳定。"
        }
        "血氧" -> when (dir) {
            TrendDirection.IMPROVED -> "夜间血氧上升, 通常对应睡眠呼吸更平稳。"
            TrendDirection.DECLINED -> "夜间血氧下降, 若持续偏低可留意睡眠呼吸质量。"
            else -> "血氧稳定。"
        }
        "体重" -> when (dir) {
            TrendDirection.NEUTRAL -> "体重出现趋势性变化, 结合饮食与训练目标判断是否符合预期。"
            else -> "体重稳定。"
        }
        else -> ""
    }

    private fun buildHeadline(window: TrendWindow, metrics: List<MetricTrend>): String {
        val declined = metrics.filter { it.direction == TrendDirection.DECLINED }.map { it.label }
        val improved = metrics.filter { it.direction == TrendDirection.IMPROVED }.map { it.label }
        fun join(xs: List<String>) = xs.take(2).joinToString("、")
        return when {
            declined.isNotEmpty() && improved.isNotEmpty() ->
                "${window.label}对比: ${join(improved)}向好, 但${join(declined)}走低, 留意身体负荷变化。"
            declined.isNotEmpty() ->
                "${window.label}对比: ${join(declined)}较前期走低, 建议关注恢复与作息。"
            improved.isNotEmpty() ->
                "${window.label}对比: ${join(improved)}较前期改善, 身体状态稳步向上。"
            else ->
                "${window.label}对比: 各项指标与前期基本持平, 身体处于稳定区间。"
        }
    }

    private fun minutesText(min: Double): String {
        val m = min.roundToInt()
        return "${m / 60}h${m % 60}m"
    }

    private fun sleepDisplayDate(sleep: SleepSession, zone: ZoneId): LocalDate? = runCatching {
        val end = Instant.ofEpochMilli(sleep.endEpochMs).atZone(zone).toLocalDate()
        val start = Instant.ofEpochMilli(sleep.startEpochMs).atZone(zone).toLocalDate()
        if (sleep.endEpochMs > sleep.startEpochMs) end else start
    }.getOrNull()
}

/** 指标的「好方向」: 越高越好 / 越低越好 / 中性 (只报变化不判好坏)。 */
enum class TrendKind { HIGHER_BETTER, LOWER_BETTER, NEUTRAL }

/** 单维度走向。 */
enum class TrendDirection { IMPROVED, DECLINED, STABLE, NEUTRAL }

/** 单维度的中长期对比结果。 */
data class MetricTrend(
    val label: String,
    val direction: TrendDirection,
    val currentText: String,   // 当前窗口均值
    val baselineText: String,  // 基线窗口均值
    val deltaText: String,     // 变化量 + 箭头 + 百分比
    val note: String,          // 通俗解读
)

/** 一个时间尺度 (月度/年度) 的完整趋势报告。 */
data class TrendReport(
    val window: TrendAnalyzer.TrendWindow,
    val metrics: List<MetricTrend>,
    val headline: String,
)
