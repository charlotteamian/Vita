package com.vita.healthtracker.domain

import com.vita.healthtracker.data.local.entity.DailyHealthSnapshot
import com.vita.healthtracker.data.local.entity.SleepSession
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * 首页「当日简报」分析器。
 *
 * 状态分负责把单日指标归一化; 这里再把结论收敛成用户真正需要看到的内容:
 * 一句判断、一条行动建议、最多三个理由，以及基于本地历史的连续变化。
 */
object DailyBriefAnalyzer {

    fun analyze(
        date: LocalDate,
        score: StatusScore,
        today: DailyHealthSnapshot?,
        lastSleep: SleepSession?,
        historyDaily: List<DailyHealthSnapshot>,
        historySleep: List<SleepSession>,
        monthTrend: TrendReport?,
        zone: ZoneId,
    ): DailyBrief {
        val dailyByDate = buildMap {
            historyDaily.forEach { put(it.date, it) }
            today?.let { put(it.date, it) }
        }
        val sleepByDate = historySleep
            .asSequence()
            .filter { it.totalMinutes > 0 }
            .mapNotNull { sleep -> sleepDisplayDate(sleep, zone)?.let { it to sleep } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, sleeps) -> sleeps.maxByOrNull { it.endEpochMs } }

        val trajectory = (6L downTo 0L).map { offset ->
            val pointDate = date.minusDays(offset)
            val pointScore = if (pointDate == date) {
                score.overall
            } else {
                // 与当日评分同窗: 每个轨迹点都用各自往前 60 天的基线, 避免历史点用全量
                // 多年均值、今天却用 60 天均值导致曲线不可比。
                val windowStart = pointDate.minusDays(60).toString()
                ReadinessEngine.evaluate(
                    date = pointDate.toString(),
                    today = dailyByDate[pointDate.toString()],
                    lastSleep = sleepByDate[pointDate],
                    baselineDaily = historyDaily.filter { it.date >= windowStart },
                    baselineSleep = historySleep.filter { sleep ->
                        sleepDisplayDate(sleep, zone)?.let {
                            !it.isBefore(pointDate.minusDays(60)) && it.isBefore(pointDate)
                        } == true
                    },
                    zone = zone,
                )?.overall
            }
            ReadinessPoint(pointDate, pointScore)
        }

        val yesterday = trajectory.dropLast(1).lastOrNull { it.score != null }?.score
        val changeText = yesterday?.let { previous ->
            val diff = score.overall - previous
            when {
                diff > 0 -> "较昨日 +$diff"
                diff < 0 -> "较昨日 $diff"
                else -> "与昨日持平"
            }
        }

        return DailyBrief(
            headline = headline(score),
            observation = observation(score),
            action = action(score),
            changeText = changeText,
            trajectory = trajectory,
            trajectorySummary = trajectorySummary(trajectory),
            discovery = discovery(date, monthTrend, trajectory, historyDaily, historySleep, zone),
        )
    }

    /** 一句有结论的判断: 先说今天身体怎么样、该用什么节奏, 不在这里报数字 (数字在维度条上)。 */
    private fun headline(score: StatusScore): String {
        val leadLimit = score.limits.minByOrNull { it.cap }
        val weakest = score.contributions.minByOrNull { it.subScore }
        val driver = when {
            leadLimit?.metric != null -> driverPhrase(leadLimit.metric)
            weakest != null && weakest.subScore < 55 -> driverPhrase(weakest.metric)
            else -> null
        }
        return when (score.band) {
            ReadinessBand.PRIME -> "恢复充分，可以放开安排"
            ReadinessBand.GOOD -> driver?.let { "整体在线，但$it" } ?: "状态在线，按计划推进就好"
            ReadinessBand.FAIR -> driver?.let { "$it，今天留点余量" } ?: "略有消耗，安排别排太满"
            ReadinessBand.LOW -> driver?.let { "$it，今天适合减量" } ?: "恢复不足，今天适合减量"
            ReadinessBand.DEPLETED -> "身体在要求休息，今天优先恢复"
        }
    }

    /** 用人话点名今天的主要消耗/亮点。 */
    private fun driverPhrase(metric: ScoreMetric): String = when (metric) {
        ScoreMetric.SLEEP -> "昨晚睡眠没补够"
        ScoreMetric.HRV -> "身体恢复信号偏弱"
        ScoreMetric.RESTING_HR -> "基础心率比平时紧"
        ScoreMetric.STRESS -> "压力负荷偏高"
        ScoreMetric.BODY_BATTERY -> "精力储备偏低"
        ScoreMetric.SPO2 -> "血氧低于平时"
        ScoreMetric.RESPIRATION -> "呼吸比平时快"
    }

    private fun statePhrase(c: MetricContribution): String = when {
        c.subScore < 55 -> driverPhrase(c.metric)
        else -> when (c.metric) {
            ScoreMetric.SLEEP -> "昨晚睡得不错"
            ScoreMetric.HRV -> "恢复信号在线"
            ScoreMetric.RESTING_HR -> "基础心率很稳"
            ScoreMetric.STRESS -> "压力不大"
            ScoreMetric.BODY_BATTERY -> "精力储备充足"
            ScoreMetric.SPO2 -> "血氧正常"
            ScoreMetric.RESPIRATION -> "呼吸平稳"
        }
    }

    /**
     * 状态叙述: 讲身体发生了什么、对今天意味着什么。
     * 不堆数字——读数都在下方维度条上, 这里只给解读。
     */
    private fun observation(score: StatusScore): String {
        val weak = score.contributions.filter { it.subScore < 55 }
        val strong = score.contributions.filter { it.subScore >= 80 }
        val state = when {
            weak.isNotEmpty() ->
                "${weak.take(2).joinToString("、") { statePhrase(it) }}，是今天状态的主要消耗"
            strong.isNotEmpty() ->
                "${strong.take(2).joinToString("、") { statePhrase(it) }}，身体把近期负荷消化得不错"
            else -> "各项都贴着你的常态走，没有明显的消耗点"
        }
        val byMetric = score.contributions.associateBy { it.metric }
        fun c(metric: ScoreMetric) = byMetric[metric]
        val conclusion = when {
            score.limits.isNotEmpty() ->
                limitObservation(score.limits.minByOrNull { it.cap })
            c(ScoreMetric.HRV).isWeak() && c(ScoreMetric.RESTING_HR).isWeak() ->
                "两个自主神经指标一起偏离，说明恢复还没有完全回到位。"
            c(ScoreMetric.RESPIRATION).isWeak() && c(ScoreMetric.SPO2).isWeak() ->
                "呼吸率和血氧同时走弱，比单个指标波动更值得关注。"
            c(ScoreMetric.STRESS).isStrong() && c(ScoreMetric.BODY_BATTERY).isStrong() ->
                "压力和储备给出了方向一致的正向信号。"
            c(ScoreMetric.SLEEP).isStrong() && c(ScoreMetric.BODY_BATTERY).isStrong() ->
                "睡眠输入和能量储备相互印证。"
            else -> null
        }
        return if (conclusion != null) "$state。$conclusion" else "$state。"
    }

    private fun action(score: StatusScore): String? {
        val byMetric = score.contributions.associateBy { it.metric }
        fun c(metric: ScoreMetric) = byMetric[metric]
        val weakest = score.contributions.minByOrNull { it.subScore }
        return when {
            score.limits.isNotEmpty() ->
                "建议：${score.limits.minByOrNull { it.cap }?.action}"
            c(ScoreMetric.HRV).isWeak() && c(ScoreMetric.RESTING_HR).isWeak() ->
                "建议：把训练强度降一档，补水、散步或拉伸，今晚尽量提前睡。"
            c(ScoreMetric.RESPIRATION).isWeak() && c(ScoreMetric.SPO2).isWeak() ->
                "建议：今天优先休息和保暖，留意呼吸状态，先不叠加强度训练。"
            weakest?.subScore?.let { it < 55 } == true ->
                "建议：${weakMetricAdvice(weakest.metric)}"
            c(ScoreMetric.STRESS).isWeak() && c(ScoreMetric.BODY_BATTERY).isWeak() ->
                "建议：穿插几次 5 分钟慢呼吸或散步，给身体留出回充窗口。"
            score.overall < 70 && score.contributions.isNotEmpty() ->
                "建议：今天适当做减法，优先保证恢复，明天再看这些信号是否回升。"
            else -> null
        }
    }

    /** 近 7 天状态走向一句话 (常驻, 不足两天数据时为 null)。 */
    private fun trajectorySummary(trajectory: List<ReadinessPoint>): String? {
        val scores = trajectory.mapNotNull { it.score }
        if (scores.size < 2) return null
        val diff = scores.last() - scores.first()
        return when {
            diff >= 8 -> "状态分从 ${scores.first()} 回升到 ${scores.last()}，这几天恢复大于消耗。"
            diff <= -8 -> "状态分从 ${scores.first()} 降到 ${scores.last()}，这几天消耗大于恢复，适合主动留余量。"
            else -> "状态分在 ${scores.min()}–${scores.max()} 之间，节奏平稳。"
        }
    }

    private fun limitObservation(limit: ReadinessLimit?): String = when (limit?.metric) {
        ScoreMetric.SLEEP -> "睡眠还没补够，分数会保守一点；今天别硬加量。"
        ScoreMetric.SPO2 -> "血氧读数偏低，先复测确认；如果连续偏低，再认真处理。"
        null -> "有几项指标一起走弱，今天适合少消耗一点。"
        else -> "${limit.title}比较明显，今天先按保守状态安排。"
    }

    private fun discovery(
        date: LocalDate,
        monthTrend: TrendReport?,
        trajectory: List<ReadinessPoint>,
        historyDaily: List<DailyHealthSnapshot>,
        historySleep: List<SleepSession>,
        zone: ZoneId,
    ): PersonalDiscovery? {
        // 近 7 天涨跌由 trajectorySummary 常驻表达, 这里只负责更长线/更个人化的发现。
        personalizedSleepPattern(date, historyDaily, historySleep, zone)?.let { return it }

        val metric = monthTrend?.metrics?.firstOrNull {
            it.direction == TrendDirection.DECLINED || it.direction == TrendDirection.IMPROVED
        }
        if (metric != null) {
            return PersonalDiscovery("近 30 天趋势", "${metric.label}${metric.deltaText}。${metric.note}")
        }

        val sleeps = historySleep.mapNotNull { sleep ->
            sleepDisplayDate(sleep, zone)?.let { it to sleep.totalMinutes }
        }.toMap()
        val sleepStreak = generateSequence(date) { it.minusDays(1) }
            .takeWhile { (sleeps[it] ?: 0) >= 7 * 60 }
            .count()
        return sleepStreak.takeIf { it >= 3 }?.let {
            PersonalDiscovery("正在形成的节奏", "你已经连续 $it 晚睡够 7 小时，这种稳定比偶尔补觉更有价值。")
        }
    }

    /**
     * 从本机历史中寻找一条足够稳定、可解释的个人相关性。
     * 这里只陈述记录中的关联，不把相关性包装成因果结论。
     */
    private fun personalizedSleepPattern(
        date: LocalDate,
        historyDaily: List<DailyHealthSnapshot>,
        historySleep: List<SleepSession>,
        zone: ZoneId,
    ): PersonalDiscovery? {
        val dailyByDate = historyDaily.associateBy { it.date }
        val samples = historySleep.asSequence()
            .mapNotNull { sleep -> sleepDisplayDate(sleep, zone)?.let { it to sleep } }
            .filter { (sleepDate, sleep) -> !sleepDate.isAfter(date) && sleep.totalMinutes > 0 }
            .groupBy({ it.first }, { it.second })
            .mapNotNull { (sleepDate, sleeps) ->
                val sleep = sleeps.maxByOrNull { it.totalMinutes } ?: return@mapNotNull null
                val daily = dailyByDate[sleepDate.toString()] ?: return@mapNotNull null
                SleepDaySample(sleep.totalMinutes, daily.bodyBatteryHigh, daily.avgStress)
            }
        val enoughSleep = samples.filter { it.sleepMinutes >= 7 * 60 }
        val shortSleep = samples.filter { it.sleepMinutes < 7 * 60 }
        if (enoughSleep.size < MIN_PATTERN_GROUP || shortSleep.size < MIN_PATTERN_GROUP) return null

        averageOf(enoughSleep) { it.bodyBatteryHigh }?.let { enough ->
            averageOf(shortSleep) { it.bodyBatteryHigh }?.let { short ->
                val diff = enough - short
                if (diff >= 8) {
                    return PersonalDiscovery(
                        title = "你的长期规律",
                        text = "在已有 ${samples.size} 天记录里，睡够 7 小时的日子，身体电量通常高 ${diff.roundToInt()} 点左右。可以把它当作一个作息提醒。",
                    )
                }
            }
        }
        averageOf(enoughSleep) { it.avgStress }?.let { enough ->
            averageOf(shortSleep) { it.avgStress }?.let { short ->
                val diff = short - enough
                if (diff >= 6) {
                    return PersonalDiscovery(
                        title = "你的长期规律",
                        text = "在已有 ${samples.size} 天记录里，睡够 7 小时的日子，压力通常低 ${diff.roundToInt()} 点左右。可以先把它当作一个可观察的生活线索。",
                    )
                }
            }
        }
        return null
    }

    private fun averageOf(samples: List<SleepDaySample>, selector: (SleepDaySample) -> Int?): Double? {
        val values = samples.mapNotNull(selector)
        return values.takeIf { it.size >= MIN_PATTERN_GROUP }?.average()
    }

    private fun weakMetricAdvice(metric: ScoreMetric): String = when (metric) {
        ScoreMetric.HRV -> "HRV 明显偏低，今天降低强度，优先放松和睡眠。"
        ScoreMetric.RESTING_HR -> "静息心率偏高，注意补水和休息，先避免剧烈运动。"
        ScoreMetric.RESPIRATION -> "呼吸率偏离常态，今天放慢节奏，并留意是否伴随不适。"
        ScoreMetric.SPO2 -> "血氧偏低，注意通风和休息；如果连续多天偏低，需要进一步关注。"
        ScoreMetric.SLEEP -> "昨夜恢复不足，今晚尽量提前 30-60 分钟上床。"
        ScoreMetric.STRESS -> "压力偏高，安排几次短暂放松，避免连续高压。"
        ScoreMetric.BODY_BATTERY -> "身体电量储备偏低，减少额外负荷，优先把电量充回来。"
    }

    private fun MetricContribution?.isWeak(): Boolean = this?.subScore?.let { it < 55 } == true

    private fun MetricContribution?.isStrong(): Boolean = this?.subScore?.let { it >= 80 } == true

    private fun sleepDisplayDate(sleep: SleepSession, zone: ZoneId): LocalDate? = runCatching {
        val end = Instant.ofEpochMilli(sleep.endEpochMs).atZone(zone).toLocalDate()
        val start = Instant.ofEpochMilli(sleep.startEpochMs).atZone(zone).toLocalDate()
        if (sleep.endEpochMs > sleep.startEpochMs) end else start
    }.getOrNull()

    private data class SleepDaySample(
        val sleepMinutes: Long,
        val bodyBatteryHigh: Int?,
        val avgStress: Int?,
    )

    private const val MIN_PATTERN_GROUP = 4
}

data class DailyBrief(
    /** 一句有结论的判断 (不含数字)。 */
    val headline: String,
    /** 状态叙述: 身体发生了什么、对今天意味着什么 (不含数字)。 */
    val observation: String,
    /** 一行建议。 */
    val action: String?,
    val changeText: String?,
    val trajectory: List<ReadinessPoint>,
    /** 近 7 天状态走向一句话 (常驻显示在轨迹上方)。 */
    val trajectorySummary: String?,
    val discovery: PersonalDiscovery?,
)

data class ReadinessPoint(
    val date: LocalDate,
    val score: Int?,
)

data class PersonalDiscovery(
    val title: String,
    val text: String,
)
