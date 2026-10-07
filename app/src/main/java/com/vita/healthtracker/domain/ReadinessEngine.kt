package com.vita.healthtracker.domain

import com.vita.healthtracker.data.local.entity.DailyHealthSnapshot
import com.vita.healthtracker.data.local.entity.SleepSession
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 「今日状态分 + 今日洞察」评分引擎。
 *
 * 设计依据 (2026-06 调研后收敛):
 *  - Oura Readiness 使用个人长期均值评估 RHR/HRV/睡眠规律等 9 个 contributors;
 *    Garmin Training Readiness 组合睡眠评分、HRV、睡眠历史、压力历史、恢复时间和训练负荷;
 *    WHOOP Recovery 强调 HRV/RHR/呼吸率/皮温与睡眠。这些产品都不是单看某个原始值。
 *  - AASM/SRS 对成人睡眠的共识是规律睡够 7 小时及以上; NSF 睡眠质量建议也强调连续性
 *    (效率、醒来时间) 不只是总时长。所以无设备睡眠评分时, 这里用时长 + 睡眠效率 + 阶段结构估算。
 *  - 轻中度短板不再硬封顶成固定分数, 否则不同日期很容易被压成同一个分数; 只有严重缺觉、
 *    明显低血氧和多指标同时异常才做强约束。一般短板通过子分和动态扣分体现差异。
 *
 * 这是一套面向自我观察的产品启发式，不是经过临床验证的医学评分。引擎先按个人基线加权，
 * 再用睡眠不足、低血氧和明显弱项等关键短板做动态修正，避免亮点稀释需要优先处理的信号。
 */
object ReadinessEngine {

    // ─── 各维度满配权重 (全部存在时合计 = 1.0); 缺失维度会按现存维度重新归一 ───
    private const val W_HRV = 0.28
    private const val W_RHR = 0.18
    private const val W_RESP = 0.06
    private const val W_SPO2 = 0.05
    private const val W_SLEEP = 0.25
    private const val W_STRESS = 0.10
    private const val W_BODY_BATTERY = 0.08

    /** 子分映射: 处于基线时给 ~78 (良好), 每偏离 1 个标准差 ±14 分。 */
    private const val BASELINE_SUBSCORE = 78.0
    private const val PER_SD = 14.0

    /** 计算基线标准差时的最小变异系数下限, 防止个别人波动极小导致一点偏差就爆表。 */
    private const val MIN_CV = 0.04

    /** 信任基线统计所需的最少有效天数。 */
    private const val MIN_BASELINE_DAYS = 5

    /**
     * @param date          评估日 (yyyy-MM-dd)
     * @param today         当日聚合快照
     * @param lastSleep     当日对应的「昨夜」睡眠 (可空)
     * @param baselineDaily 评估日之前最近若干天的日快照 (不含当日)
     * @param baselineSleep 同期睡眠会话 (用于睡眠时长基线; 内部会按晚去重、剔除脏数据并排除评估当晚)
     * @param zone          解析睡眠归属日所用时区
     * @param monthTrend    近 30 天 vs 上个 30 天的中期趋势 (可空; 供洞察判断「单日波动 vs 持续走弱」)
     * @return 无任何可评分维度时返回 null
     */
    fun evaluate(
        date: String,
        today: DailyHealthSnapshot?,
        lastSleep: SleepSession?,
        baselineDaily: List<DailyHealthSnapshot>,
        baselineSleep: List<SleepSession>,
        zone: ZoneId = ZoneId.systemDefault(),
        monthTrend: TrendReport? = null,
    ): StatusScore? {
        if (today == null && lastSleep == null) return null

        // 基线只用评估日之前的数据 (防止当日值污染自身基线)。
        val priorDaily = baselineDaily.filter { it.date < date }.sortedBy { it.date }

        val hrvBase = baselineOf(priorDaily) { it.hrv?.takeIf { v -> v > 0 }?.toDouble() }
        val rhrBase = baselineOf(priorDaily) { it.restingHeartRate?.takeIf { v -> v > 0 }?.toDouble() }
        val respBase = baselineOf(priorDaily) { it.avgRespiration?.takeIf { v -> v > 0 } }
        val spo2Base = baselineOf(priorDaily) { it.avgSpo2?.takeIf { v -> v > 0 }?.toDouble() }
        val stressBase = baselineOf(priorDaily) { it.avgStress?.takeIf { v -> v > 0 }?.toDouble() }
        val bbBase = baselineOf(priorDaily) { it.bodyBatteryHigh?.takeIf { v -> v > 0 }?.toDouble() }
        // 睡眠基线: 每晚只取最长一条主睡眠 (排除小睡/碎片/重复来源), 剔除 >16h 脏记录,
        // 且只用评估日之前的夜晚 (当晚由 lastSleep 单独参与评分, 不污染自身基线)。
        val priorNights = primaryNightMinutes(baselineSleep, zone).filter { it.first < date }
        val sleepMinBase = baselineOf(priorNights.map { it.second })

        // 连续偏离天数: 今天与基线偏差超过 0.5 SD, 且往前每天都同向偏离, 用于区分单日波动与持续走弱。
        fun streakOf(
            todayValue: Double,
            base: Baseline?,
            higherBetter: Boolean,
            selector: (DailyHealthSnapshot) -> Double?,
        ): Int = consecutiveBadDays(todayValue, base, higherBetter, priorDaily.asReversed().asSequence().map { selector(it)?.takeIf { v -> v > 0 } })

        val contributions = mutableListOf<MetricContribution>()

        // ── HRV: 仅在有个人基线时计入 (绝对 HRV 跨人无意义) ──
        today?.hrv?.takeIf { it > 0 }?.let { hrv ->
            if (hrvBase != null && hrvBase.n >= MIN_BASELINE_DAYS) {
                contributions += contribution(
                    metric = ScoreMetric.HRV,
                    weight = W_HRV,
                    subScore = normalizedSub(hrv.toDouble(), hrvBase, higherBetter = true),
                    valueText = "$hrv ms",
                    delta = deltaText(hrv.toDouble(), hrvBase.recentMean, "ms", oneDecimal = false),
                    deltaFromRecent = recentDelta(hrv.toDouble(), hrvBase.recentMean),
                    streakDays = streakOf(hrv.toDouble(), hrvBase, higherBetter = true) { it.hrv?.toDouble() },
                )
            }
        }

        // ── 静息心率: 有基线用基线, 否则人群参考曲线 ──
        today?.restingHeartRate?.takeIf { it > 0 }?.let { rhr ->
            val sub = if (rhrBase != null && rhrBase.n >= MIN_BASELINE_DAYS) {
                normalizedSub(rhr.toDouble(), rhrBase, higherBetter = false)
            } else restingHrReferenceSub(rhr)
            contributions += contribution(
                metric = ScoreMetric.RESTING_HR,
                weight = W_RHR,
                subScore = sub,
                valueText = "$rhr bpm",
                delta = rhrBase?.let { deltaText(rhr.toDouble(), it.recentMean, "bpm", oneDecimal = false) },
                deltaFromRecent = rhrBase?.let { recentDelta(rhr.toDouble(), it.recentMean) },
                streakDays = streakOf(rhr.toDouble(), rhrBase, higherBetter = false) { it.restingHeartRate?.toDouble() },
            )
        }

        // ── 呼吸率: 偏离基线 (尤其升高) 视作压力/生病信号; 仅基线足够时计入 ──
        today?.avgRespiration?.takeIf { it > 0 }?.let { resp ->
            if (respBase != null && respBase.n >= MIN_BASELINE_DAYS) {
                contributions += contribution(
                    metric = ScoreMetric.RESPIRATION,
                    weight = W_RESP,
                    subScore = normalizedSub(resp, respBase, higherBetter = false),
                    valueText = "%.1f 次/分".format(resp),
                    delta = deltaText(resp, respBase.recentMean, "次/分", oneDecimal = true),
                    deltaFromRecent = recentDelta(resp, respBase.recentMean),
                    streakDays = streakOf(resp, respBase, higherBetter = false) { it.avgRespiration },
                )
            }
        }

        // ── 血氧: 有基线用基线 (惩罚低于个人常态), 否则人群参考曲线 ──
        today?.avgSpo2?.takeIf { it > 0 }?.let { spo2 ->
            val sub = if (spo2Base != null && spo2Base.n >= MIN_BASELINE_DAYS) {
                normalizedSub(spo2.toDouble(), spo2Base, higherBetter = true)
            } else spo2ReferenceSub(spo2)
            contributions += contribution(
                metric = ScoreMetric.SPO2,
                weight = W_SPO2,
                subScore = sub,
                valueText = "$spo2 %",
                delta = null,
                streakDays = streakOf(spo2.toDouble(), spo2Base, higherBetter = true) { it.avgSpo2?.toDouble() },
            )
        }

        // ── 睡眠: 优先用佳明睡眠评分, 否则按时长 (有基线时对齐个人睡眠需求) ──
        if (lastSleep != null && lastSleep.totalMinutes > 0) {
            val (sub, valueText) = sleepSub(lastSleep, sleepMinBase)
            contributions += contribution(
                metric = ScoreMetric.SLEEP,
                weight = W_SLEEP,
                subScore = sub,
                valueText = valueText,
                delta = sleepMinBase?.let {
                    deltaText(lastSleep.totalMinutes.toDouble(), it.recentMean, "min", oneDecimal = false)
                },
                deltaFromRecent = sleepMinBase?.let {
                    recentDelta(lastSleep.totalMinutes.toDouble(), it.recentMean)
                },
                streakDays = consecutiveBadDays(
                    todayValue = lastSleep.totalMinutes.toDouble(),
                    base = sleepMinBase,
                    higherBetter = true,
                    priorValuesDesc = priorNights.asReversed().asSequence().map { it.second },
                ),
            )
        }

        // ── 压力: 有基线用基线 (今天比你常态高多少), 否则用 0-100 绝对反向 ──
        today?.avgStress?.takeIf { it > 0 }?.let { stress ->
            val sub = if (stressBase != null && stressBase.n >= MIN_BASELINE_DAYS) {
                normalizedSub(stress.toDouble(), stressBase, higherBetter = false)
            } else (100 - stress).coerceIn(0, 100)
            contributions += contribution(
                metric = ScoreMetric.STRESS,
                weight = W_STRESS,
                subScore = sub,
                valueText = "$stress",
                delta = stressBase?.let { deltaText(stress.toDouble(), it.recentMean, "", oneDecimal = false) },
                deltaFromRecent = stressBase?.let { recentDelta(stress.toDouble(), it.recentMean) },
                streakDays = streakOf(stress.toDouble(), stressBase, higherBetter = false) { it.avgStress?.toDouble() },
            )
        }

        // ── 身体电量: Firstbeat 本身就是 0-100 的能量储备, 直接当子分 (峰值越高越好) ──
        today?.bodyBatteryHigh?.takeIf { it > 0 }?.let { bb ->
            contributions += contribution(
                metric = ScoreMetric.BODY_BATTERY,
                weight = W_BODY_BATTERY,
                subScore = bb.coerceIn(0, 100),
                valueText = "$bb",
                delta = bbBase?.let { deltaText(bb.toDouble(), it.recentMean, "", oneDecimal = false) },
                deltaFromRecent = bbBase?.let { recentDelta(bb.toDouble(), it.recentMean) },
                streakDays = streakOf(bb.toDouble(), bbBase, higherBetter = true) { it.bodyBatteryHigh?.toDouble() },
            )
        }

        if (contributions.isEmpty()) return null

        // 现存维度重新归一化权重并加权求和。
        val totalWeight = contributions.sumOf { it.weight }
        val weightedOverall = contributions.sumOf { it.subScore * it.weight }
            .div(totalWeight)
            .roundToInt()
            .coerceIn(0, 100)
        val limits = scoreLimits(today, lastSleep, contributions, weightedOverall)
        val overall = limits.minOfOrNull { it.cap }?.let { weightedOverall.coerceAtMost(it) } ?: weightedOverall

        val baselineDays = listOfNotNull(
            hrvBase?.n, rhrBase?.n, respBase?.n, spo2Base?.n, stressBase?.n, bbBase?.n, sleepMinBase?.n,
        ).maxOrNull() ?: 0
        val confidence = when {
            baselineDays >= 21 && contributions.size >= 4 -> Confidence.HIGH
            baselineDays >= MIN_BASELINE_DAYS -> Confidence.MEDIUM
            else -> Confidence.LOW
        }

        val band = ReadinessBand.of(overall)
        val insights = InsightGenerator.generate(
            overall = overall,
            band = band,
            contributions = contributions,
            confidence = confidence,
            limits = limits,
            monthTrend = monthTrend,
        )

        return StatusScore(
            overall = overall,
            weightedOverall = weightedOverall,
            band = band,
            contributions = contributions.sortedBy { it.subScore },
            insights = insights,
            baselineDays = baselineDays,
            confidence = confidence,
            limits = limits,
        )
    }

    private fun scoreLimits(
        today: DailyHealthSnapshot?,
        lastSleep: SleepSession?,
        contributions: List<MetricContribution>,
        weightedOverall: Int,
    ): List<ReadinessLimit> = buildList {
        lastSleep?.totalMinutes?.takeIf { it > 0 }?.let { minutes ->
            val cap = { penalty: Int, ceiling: Int -> (weightedOverall - penalty).coerceAtMost(ceiling).coerceAtLeast(0) }
            when {
                minutes < 5L * 60L -> add(
                    ReadinessLimit(ScoreMetric.SLEEP, 54, "睡眠明显不足", "昨夜只有 ${minutes / 60}h${minutes % 60}m，状态分最多 54。", "今天主动减量，优先补觉和提前入睡。")
                )
                minutes < 6L * 60L -> {
                    val deficit = (6L * 60L - minutes).toInt()
                    val penalty = 10 + (deficit / 12).coerceIn(0, 8)
                    add(
                        ReadinessLimit(ScoreMetric.SLEEP, cap(penalty, 68), "睡眠不足", "昨夜只有 ${minutes / 60}h${minutes % 60}m，会拉低今天的状态。", "今天不要叠加强度，今晚尽量提前 30-60 分钟上床。")
                    )
                }
                minutes < 7L * 60L -> {
                    val deficit = (7L * 60L - minutes).toInt()
                    val penalty = 4 + (deficit / 9).coerceIn(0, 8)
                    add(
                        ReadinessLimit(ScoreMetric.SLEEP, cap(penalty, 80), "睡眠未满 7 小时", "昨夜 ${minutes / 60}h${minutes % 60}m，恢复还差一点。", "今天按保守节奏推进，不建议额外加量。")
                    )
                }
            }
        }
        today?.avgSpo2?.takeIf { it > 0 }?.let { spo2 ->
            when {
                spo2 <= 92 -> add(
                    ReadinessLimit(ScoreMetric.SPO2, 49, "血氧信号明显偏低", "平均血氧 $spo2%，状态分最多 49。", "先休息并复测；若持续偏低或伴随胸闷、明显乏力，请及时就医。")
                )
                spo2 < 95 -> add(
                    ReadinessLimit(
                        ScoreMetric.SPO2,
                        (weightedOverall - (95 - spo2) * 4).coerceAtMost(if (spo2 == 94) 84 else 76).coerceAtLeast(0),
                        "血氧信号偏低",
                        "平均血氧 $spo2%，先把它当作今天最需要复核的信号。",
                        "留意鼻塞、咳嗽、乏力或胸闷；可穿戴读数仅作观察，持续偏低时请进一步确认。",
                    )
                )
            }
        }
        val weak = contributions.filter { it.subScore < 55 }
        if (weak.size >= 2) {
            add(
                ReadinessLimit(
                    metric = null,
                    cap = (weightedOverall - 10).coerceAtMost(72).coerceAtLeast(0),
                    title = "多项恢复指标同时走弱",
                    detail = "${weak.take(3).joinToString("、") { it.label }}都偏弱，今天适合保守一点。",
                    action = "今天优先恢复，不安排额外加量。",
                )
            )
        }
    }.distinctBy { it.metric to it.title }

    private fun contribution(
        metric: ScoreMetric,
        weight: Double,
        subScore: Int,
        valueText: String,
        delta: String?,
        deltaFromRecent: Double? = null,
        streakDays: Int = 0,
    ) = MetricContribution(
        metric = metric,
        label = metric.label,
        subScore = subScore,
        weight = weight,
        valueText = valueText,
        deltaText = delta,
        deltaFromRecent = deltaFromRecent,
        streakDays = streakDays,
    )

    /**
     * 每晚主睡眠时长 (分钟), 键为归属日 yyyy-MM-dd, 按日期升序。
     * 同一晚多条记录 (多来源/小睡/碎片) 只取最长一条; 单条 ≤0 或 >16h 视作脏数据剔除。
     */
    private fun primaryNightMinutes(sleeps: List<SleepSession>, zone: ZoneId): List<Pair<String, Double>> =
        sleeps.asSequence()
            .filter { it.totalMinutes in 1..(16L * 60L) }
            .mapNotNull { sleep ->
                runCatching {
                    val end = Instant.ofEpochMilli(sleep.endEpochMs).atZone(zone).toLocalDate()
                    val start = Instant.ofEpochMilli(sleep.startEpochMs).atZone(zone).toLocalDate()
                    (if (sleep.endEpochMs > sleep.startEpochMs) end else start).toString() to sleep
                }.getOrNull()
            }
            .groupBy({ it.first }, { it.second })
            .map { (night, sessions) -> night to sessions.maxOf { it.totalMinutes }.toDouble() }
            .sortedBy { it.first }

    /**
     * 今天偏离基线超过 0.5 SD 时, 往前数同向偏离的连续天数 (含今天)。
     * 中途缺数据即中断, 宁可低估也不虚报「连续 N 天」。
     */
    private fun consecutiveBadDays(
        todayValue: Double,
        base: Baseline?,
        higherBetter: Boolean,
        priorValuesDesc: Sequence<Double?>,
    ): Int {
        if (base == null || base.n < MIN_BASELINE_DAYS) return 0
        val sd = base.sd.coerceAtLeast(abs(base.mean) * MIN_CV).coerceAtLeast(1e-6)
        fun isBad(value: Double): Boolean {
            val deviation = if (higherBetter) base.mean - value else value - base.mean
            return deviation >= 0.5 * sd
        }
        if (!isBad(todayValue)) return 0
        var streak = 1
        for (value in priorValuesDesc) {
            if (value == null || !isBad(value)) break
            streak++
        }
        return streak
    }

    /** z 分数 → 子分。偏离基线 1 个标准差 ±14 分, 截断到合理范围。 */
    private fun normalizedSub(value: Double, base: Baseline, higherBetter: Boolean): Int {
        val sd = base.sd.coerceAtLeast(abs(base.mean) * MIN_CV).coerceAtLeast(1e-6)
        val z = ((value - base.mean) / sd).coerceIn(-3.0, 3.0)
        val signed = if (higherBetter) z else -z
        return (BASELINE_SUBSCORE + PER_SD * signed).roundToInt().coerceIn(0, 100)
    }

    /** 静息心率人群参考曲线 (无个人基线时的兜底)。 */
    private fun restingHrReferenceSub(rhr: Int): Int = interpolate(
        rhr.toDouble(),
        listOf(45.0 to 95.0, 50.0 to 90.0, 55.0 to 84.0, 60.0 to 78.0, 65.0 to 70.0, 70.0 to 60.0, 75.0 to 48.0, 80.0 to 38.0, 90.0 to 25.0),
    )

    /** 血氧人群参考曲线 (无个人基线时的兜底)。 */
    private fun spo2ReferenceSub(spo2: Int): Int = interpolate(
        spo2.toDouble(),
        listOf(90.0 to 35.0, 92.0 to 55.0, 94.0 to 72.0, 95.0 to 82.0, 96.0 to 90.0, 98.0 to 95.0, 100.0 to 96.0),
    )

    /** 睡眠子分: 有设备评分优先用评分; 否则按时长、效率、阶段结构估算。 */
    private fun sleepSub(sleep: SleepSession, base: Baseline?): Pair<Int, String> {
        val durText = "${sleep.totalMinutes / 60}h${sleep.totalMinutes % 60}m"
        sleep.sleepScore?.takeIf { it > 0 }?.let { return it.coerceIn(0, 100) to "评分 $it" }
        val hours = sleep.totalMinutes / 60.0
        val needHours = base?.takeIf { it.n >= MIN_BASELINE_DAYS }?.mean?.div(60.0)?.coerceIn(6.5, 9.0) ?: 8.0
        // 以个人睡眠需求为「满分锚点」: 达到需求 ~90 分, 每少 1 小时大幅扣分。
        val durationSub = interpolate(
            hours,
            listOf(
                3.0 to 25.0,
                4.0 to 38.0,
                5.0 to 52.0,
                (needHours - 1.5) to 66.0,
                (needHours - 0.75) to 80.0,
                needHours to 90.0,
                (needHours + 1.0) to 88.0,
                (needHours + 2.5) to 78.0,
            ).sortedBy { it.first },
        )
        val totalInBed = sleep.totalMinutes + sleep.awakeMinutes
        val efficiencySub = if (totalInBed > 0) {
            sleepEfficiencySub(sleep.totalMinutes.toDouble() / totalInBed.toDouble())
        } else null
        val architectureSub = sleepArchitectureSub(sleep)
        val weighted = weightedAverage(
            durationSub to 0.60,
            efficiencySub to 0.25,
            architectureSub to 0.15,
        )
        val valueText = if (efficiencySub != null && sleep.awakeMinutes > 0) {
            "$durText · 醒${sleep.awakeMinutes}m"
        } else {
            durText
        }
        return weighted to valueText
    }

    private fun sleepEfficiencySub(efficiency: Double): Int = interpolate(
        (efficiency * 100.0).coerceIn(0.0, 100.0),
        listOf(70.0 to 35.0, 80.0 to 58.0, 85.0 to 75.0, 90.0 to 88.0, 95.0 to 94.0, 100.0 to 94.0),
    )

    private fun sleepArchitectureSub(sleep: SleepSession): Int? {
        val stageMinutes = sleep.deepMinutes + sleep.lightMinutes + sleep.remMinutes
        if (stageMinutes <= 0) return null
        val deepRatio = sleep.deepMinutes.toDouble() / stageMinutes.toDouble()
        val remRatio = sleep.remMinutes.toDouble() / stageMinutes.toDouble()
        val deepSub = interpolate(deepRatio * 100.0, listOf(0.0 to 45.0, 8.0 to 68.0, 13.0 to 84.0, 20.0 to 92.0, 30.0 to 88.0))
        val remSub = interpolate(remRatio * 100.0, listOf(0.0 to 45.0, 12.0 to 70.0, 18.0 to 86.0, 25.0 to 92.0, 35.0 to 86.0))
        return ((deepSub + remSub) / 2.0).roundToInt().coerceIn(0, 100)
    }

    private fun weightedAverage(vararg values: Pair<Int?, Double>): Int {
        val present = values.filter { it.first != null && it.second > 0.0 }
        if (present.isEmpty()) return 0
        val totalWeight = present.sumOf { it.second }
        return present.sumOf { requireNotNull(it.first).toDouble() * it.second }
            .div(totalWeight)
            .roundToInt()
            .coerceIn(0, 100)
    }

    // ─── 基线统计 ───

    private fun <T> baselineOf(rows: List<T>, selector: (T) -> Double?): Baseline? =
        baselineOf(rows.mapNotNull(selector))

    private fun baselineOf(values: List<Double>): Baseline? {
        if (values.isEmpty()) return null
        val mean = values.average()
        val sd = if (values.size >= 2) {
            sqrt(values.sumOf { (it - mean) * (it - mean) } / (values.size - 1))
        } else 0.0
        // 近 7 个样本均值 (用于趋势对比); 列表按时间顺序传入, 取末尾。
        val recent = values.takeLast(7)
        return Baseline(mean = mean, sd = sd, n = values.size, recentMean = recent.average())
    }

    private fun deltaText(value: Double, ref: Double?, unit: String, oneDecimal: Boolean): String? {
        if (ref == null || ref == 0.0) return null
        val diff = value - ref
        if (abs(diff) < if (oneDecimal) 0.05 else 0.5) return null
        val sign = if (diff > 0) "+" else "−"
        val mag = abs(diff)
        val num = if (oneDecimal) "%.1f".format(mag) else mag.roundToInt().toString()
        val u = if (unit.isBlank()) "" else " $unit"
        return "比近 7 天 $sign$num$u"
    }

    private fun recentDelta(value: Double, ref: Double?): Double? =
        ref?.let { value - it }

    /** 分段线性插值, x 落在控制点之间取线性, 超界取端点。控制点需按 x 升序。 */
    private fun interpolate(x: Double, points: List<Pair<Double, Double>>): Int {
        if (points.isEmpty()) return 0
        if (x <= points.first().first) return points.first().second.roundToInt().coerceIn(0, 100)
        if (x >= points.last().first) return points.last().second.roundToInt().coerceIn(0, 100)
        for (i in 0 until points.size - 1) {
            val (x0, y0) = points[i]
            val (x1, y1) = points[i + 1]
            if (x in x0..x1) {
                val t = if (x1 == x0) 0.0 else (x - x0) / (x1 - x0)
                return (y0 + t * (y1 - y0)).roundToInt().coerceIn(0, 100)
            }
        }
        return points.last().second.roundToInt().coerceIn(0, 100)
    }
}

/** 单维度的基线统计。 */
data class Baseline(
    val mean: Double,
    val sd: Double,
    val n: Int,
    val recentMean: Double?,
)

/** 参与评分的生理维度。 */
enum class ScoreMetric(val label: String) {
    HRV("心率变异性"),
    RESTING_HR("静息心率"),
    RESPIRATION("呼吸率"),
    SPO2("血氧"),
    SLEEP("睡眠"),
    STRESS("压力"),
    BODY_BATTERY("身体电量"),
}

/** 单维度对总分的贡献。 */
data class MetricContribution(
    val metric: ScoreMetric,
    val label: String,
    val subScore: Int,        // 0-100
    val weight: Double,       // 归一化前的满配权重
    val valueText: String,    // 原始值展示
    val deltaText: String?,   // 相对 7 日均值的趋势 (可空)
    val deltaFromRecent: Double? = null, // 结构化偏离值, 用于判断变化是否值得展示
    val streakDays: Int = 0,  // 含今天的连续偏离天数 (>0.5 SD); 0 = 今天没有明显偏离
)

/** 状态分档位。 */
enum class ReadinessBand(val label: String, val tagline: String) {
    PRIME("优秀", "恢复充分"),
    GOOD("良好", "状态在线"),
    FAIR("一般", "略有消耗"),
    LOW("偏低", "建议放缓"),
    DEPLETED("需休息", "优先恢复");

    companion object {
        fun of(score: Int): ReadinessBand = when {
            score >= 85 -> PRIME
            score >= 70 -> GOOD
            score >= 55 -> FAIR
            score >= 40 -> LOW
            else -> DEPLETED
        }
    }
}

enum class Confidence(val label: String) {
    HIGH("参考较稳定"),
    MEDIUM("正在熟悉你"),
    LOW("记录还不多"),
}

enum class InsightTone { POSITIVE, NEUTRAL, CAUTION, ALERT }

/** 一条今日洞察: 结论 + 建议。 */
data class DailyInsight(
    val tone: InsightTone,
    val title: String,
    /** 卡片上的简版一两句。 */
    val body: String,
    /** 点开后的详细版: 现象 → 原理 → 建议 (必要时含何时就医)。默认回退到简版。 */
    val detail: String = body,
)

/** 评分引擎对外结果。 */
data class StatusScore(
    val overall: Int,
    val weightedOverall: Int,
    val band: ReadinessBand,
    val contributions: List<MetricContribution>,   // 按子分升序 (最弱在前)
    val insights: List<DailyInsight>,
    val baselineDays: Int,
    val confidence: Confidence,
    val limits: List<ReadinessLimit>,
) {
    val weakest: MetricContribution? get() = contributions.firstOrNull()
    val strongest: MetricContribution? get() = contributions.lastOrNull()
}

/** 关键短板对最终分的约束。cap 越低，越应优先处理。 */
data class ReadinessLimit(
    val metric: ScoreMetric?,
    val cap: Int,
    val title: String,
    val detail: String,
    val action: String,
)
