package com.vita.healthtracker.domain

import com.vita.healthtracker.data.local.entity.DailyHealthSnapshot
import com.vita.healthtracker.data.local.entity.SleepSession
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
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
                ReadinessEngine.evaluate(
                    date = pointDate.toString(),
                    today = dailyByDate[pointDate.toString()],
                    lastSleep = sleepByDate[pointDate],
                    baselineDaily = historyDaily,
                    baselineSleep = historySleep.filter { sleep ->
                        sleepDisplayDate(sleep, zone)?.isBefore(pointDate) == true
                    },
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

        val reasons = reasons(score)
        return DailyBrief(
            headline = headline(reasons),
            observation = observation(score, reasons),
            action = action(score, reasons),
            changeText = changeText,
            reasons = reasons,
            trajectory = trajectory,
            discovery = discovery(date, monthTrend, trajectory, historyDaily, historySleep, zone),
        )
    }

    private fun headline(reasons: List<BriefReason>): String {
        val lead = reasons.firstOrNull() ?: return "当天可用指标较少"
        return "${lead.label} ${lead.valueText}，${lead.context}"
    }

    private fun observation(score: StatusScore, reasons: List<BriefReason>): String {
        val facts = reasons.take(3).joinToString("；") { reason ->
            "${reason.label} ${reason.valueText}，${reason.context}"
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
            reasons.firstOrNull()?.subScore?.let { it < 55 } == true ->
                "${reasons.first().label}今天拖后腿，安排上保守一点更稳。"
            else -> null
        }
        return listOfNotNull(facts.takeIf { it.isNotBlank() }, conclusion).joinToString("。")
    }

    private fun action(score: StatusScore, reasons: List<BriefReason>): String? {
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
            score.overall < 70 && reasons.isNotEmpty() ->
                "建议：今天适当做减法，优先保证恢复，明天再看这些信号是否回升。"
            else -> null
        }
    }

    private fun limitObservation(limit: ReadinessLimit?): String = when (limit?.metric) {
        ScoreMetric.SLEEP -> "睡眠还没补够，分数会保守一点；今天别硬加量。"
        ScoreMetric.SPO2 -> "血氧读数偏低，先复测确认；如果连续偏低，再认真处理。"
        null -> "有几项指标一起走弱，今天适合少消耗一点。"
        else -> "${limit.title}比较明显，今天先按保守状态安排。"
    }

    private fun reasons(score: StatusScore): List<BriefReason> {
        return score.contributions.sortedByDescending(::reasonPriority).take(3).map { contribution ->
            BriefReason(
                metric = contribution.metric,
                label = contribution.label,
                valueText = contribution.valueText,
                subScore = contribution.subScore,
                context = contribution.deltaText?.takeIf { contribution.hasMeaningfulDelta() }
                    ?: contributionContext(contribution),
            )
        }
    }

    private fun contributionContext(contribution: MetricContribution): String = when {
        contribution.subScore >= 80 -> when (contribution.metric) {
            ScoreMetric.SLEEP -> "昨晚恢复不错"
            ScoreMetric.STRESS -> "今天压力不高"
            ScoreMetric.BODY_BATTERY -> "储备还可以"
            ScoreMetric.RESTING_HR -> "心率比较稳"
            ScoreMetric.HRV -> "恢复信号不错"
            ScoreMetric.SPO2 -> "读数正常"
            ScoreMetric.RESPIRATION -> "呼吸比较稳"
        }
        contribution.subScore >= 60 -> when (contribution.metric) {
            ScoreMetric.SLEEP -> "还算够用"
            ScoreMetric.STRESS -> "压力在可控范围"
            ScoreMetric.BODY_BATTERY -> "储备中等"
            ScoreMetric.RESTING_HR -> "接近你的常态"
            ScoreMetric.HRV -> "接近你的常态"
            ScoreMetric.SPO2 -> "需要继续观察"
            ScoreMetric.RESPIRATION -> "接近你的常态"
        }
        else -> when (contribution.metric) {
            ScoreMetric.SLEEP -> "昨晚恢复不足"
            ScoreMetric.STRESS -> "今天压力偏高"
            ScoreMetric.BODY_BATTERY -> "储备偏低"
            ScoreMetric.RESTING_HR -> "心率偏紧"
            ScoreMetric.HRV -> "恢复偏弱"
            ScoreMetric.SPO2 -> "建议复测确认"
            ScoreMetric.RESPIRATION -> "呼吸有点偏离"
        }
    }

    private fun discovery(
        date: LocalDate,
        monthTrend: TrendReport?,
        trajectory: List<ReadinessPoint>,
        historyDaily: List<DailyHealthSnapshot>,
        historySleep: List<SleepSession>,
        zone: ZoneId,
    ): PersonalDiscovery? {
        val scores = trajectory.mapNotNull { it.score }
        if (scores.size >= 4) {
            val diff = scores.last() - scores.first()
            if (abs(diff) >= 8) {
                return if (diff > 0) {
                    PersonalDiscovery("最近 7 天", "状态分从 ${scores.first()} 升到 ${scores.last()}，恢复节奏正在向上。")
                } else {
                    PersonalDiscovery("最近 7 天", "状态分从 ${scores.first()} 降到 ${scores.last()}，适合主动留一点恢复余量。")
                }
            }
        }

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

    private fun MetricContribution?.hasMeaningfulDelta(): Boolean {
        val delta = this?.deltaFromRecent ?: return false
        val threshold = when (metric) {
            ScoreMetric.HRV -> 5.0
            ScoreMetric.RESTING_HR -> 3.0
            ScoreMetric.RESPIRATION -> 0.5
            ScoreMetric.SPO2 -> 1.0
            ScoreMetric.SLEEP -> 30.0
            ScoreMetric.STRESS -> 5.0
            ScoreMetric.BODY_BATTERY -> 8.0
        }
        return abs(delta) >= threshold
    }

    private fun reasonPriority(contribution: MetricContribution): Int {
        val metricWeight = when (contribution.metric) {
            ScoreMetric.SLEEP -> 40
            ScoreMetric.BODY_BATTERY -> 36
            ScoreMetric.HRV -> 34
            ScoreMetric.STRESS -> 30
            ScoreMetric.RESTING_HR -> 24
            ScoreMetric.SPO2 -> 18
            ScoreMetric.RESPIRATION -> 12
        }
        val deviation = abs(contribution.subScore - PERSONAL_BASELINE_SCORE).coerceAtMost(35)
        val weakSignalBonus = if (contribution.subScore < 55) 100 else 0
        val meaningfulDeltaBonus = if (contribution.hasMeaningfulDelta()) 55 else 0
        return metricWeight + deviation + weakSignalBonus + meaningfulDeltaBonus
    }

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

    private const val PERSONAL_BASELINE_SCORE = 78
    private const val MIN_PATTERN_GROUP = 4
}

data class DailyBrief(
    val headline: String,
    val observation: String,
    val action: String?,
    val changeText: String?,
    val reasons: List<BriefReason>,
    val trajectory: List<ReadinessPoint>,
    val discovery: PersonalDiscovery?,
)

data class BriefReason(
    val metric: ScoreMetric,
    val label: String,
    val valueText: String,
    val subScore: Int,
    val context: String,
)

data class ReadinessPoint(
    val date: LocalDate,
    val score: Int?,
)

data class PersonalDiscovery(
    val title: String,
    val text: String,
)
