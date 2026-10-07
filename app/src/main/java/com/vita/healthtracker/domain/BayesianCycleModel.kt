package com.vita.healthtracker.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 贝叶斯生理期预测引擎
 *
 * 参考 Natural Cycles（FDA 批准的避孕级算法），
 * 使用 Normal-Normal 共轭先验模型对周期长度进行贝叶斯推断。
 *
 * 数学模型:
 *   先验: μ ~ N(μ₀, τ₀²)
 *   似然: x_i | μ ~ N(μ, σ²)
 *   后验: μ | data ~ N(μₙ, τₙ²)
 *     τₙ² = 1 / (1/τ₀² + n/σ²)
 *     μₙ  = τₙ² × (μ₀/τ₀² + n×x̄/σ²)
 *   预测: x_next ~ N(μₙ, τₙ² + σ²)
 */
object BayesianCycleModel {

    // ──── 先验超参数 (Population Priors) ────────────────────

    /** 人群平均周期长度 (天) */
    private const val PRIOR_MEAN: Double = 28.0

    /** 先验标准差 (天) — 表示对 μ₀ 的不确定度 */
    private const val PRIOR_STD: Double = 5.0

    /** 先验方差 τ₀² */
    private const val PRIOR_VAR: Double = PRIOR_STD * PRIOR_STD  // 25.0

    /** 默认观测噪声标准差 (个体内周期波动) */
    private const val DEFAULT_OBS_STD: Double = 3.0

    /** 黄体期先验均值 (天) — 排卵到下次经期 */
    private const val LUTEAL_MEAN: Double = 14.0

    /** 黄体期先验标准差 */
    private const val LUTEAL_STD: Double = 1.5

    /** 默认经期持续天数 */
    private const val DEFAULT_PERIOD_DAYS: Int = 5

    /** 异常值阈值 */
    private const val MIN_CYCLE_DAYS: Int = 15
    private const val MAX_CYCLE_DAYS: Int = 60
    private const val OVERDUE_GRACE_DAYS: Long = 14

    /** 参与推断的最近周期数上限与时间衰减: 越近的周期权重越高, 多年前的节奏不再主导预测。 */
    private const val MAX_HISTORY_CYCLES: Int = 12
    private const val RECENCY_DECAY: Double = 0.9

    // ──── 贝叶斯推断 ────────────────────────────────────────

    /**
     * 从经期段落列表生成完整预测。
     *
     * @param periodStarts 每段经期的起始日（按时间升序）
     * @param periodDurations 每段经期的持续天数（与 periodStarts 一一对应）
     * @param today 评估基准日 (默认今天; 注入便于测试和跨午夜刷新)
     */
    fun predict(
        periodStarts: List<LocalDate>,
        periodDurations: List<Int>,
        today: LocalDate = LocalDate.now(),
    ): CyclePrediction? {
        if (periodStarts.isEmpty()) return null

        // 计算观测到的周期长度
        val sortedStarts = periodStarts.sorted()
        val cycleLengths = sortedStarts.zipWithNext { a, b ->
            ChronoUnit.DAYS.between(a, b).toInt()
        }.filter { it in MIN_CYCLE_DAYS..MAX_CYCLE_DAYS }

        // 贝叶斯后验更新
        val posterior = updatePosterior(cycleLengths)

        // 预测分布
        val predictiveVar = posterior.posteriorVar + posterior.obsVar
        val predictiveStd = sqrt(predictiveVar)

        // 下次经期预测
        val lastStart = sortedStarts.last()
        val predictedCycleLength = posterior.posteriorMean
        val predictedCycleDays = predictedCycleLength.roundToInt().coerceAtLeast(1)
        val nextPeriodStart = lastStart.plusDays(predictedCycleDays.toLong())

        // 如果预测已明显过期，才向未来推进；当天到期或轻微延迟时不要直接跳到下个周期。
        var adjustedNext = nextPeriodStart
        val overdueCutoff = today.minusDays(OVERDUE_GRACE_DAYS)
        while (adjustedNext.isBefore(overdueCutoff)) {
            adjustedNext = adjustedNext.plusDays(predictedCycleDays.toLong())
        }

        // 80% 置信区间: μ ± 1.28σ
        val ciDays = (1.28 * predictiveStd).roundToInt().toLong().coerceIn(2L, 14L)
        val ciStart = adjustedNext.minusDays(ciDays)
        val ciEnd = adjustedNext.plusDays(ciDays)

        // 排卵日 & 受孕窗口
        val ovulationDay = adjustedNext.minusDays(LUTEAL_MEAN.roundToInt().toLong())
        val fertileStart = ovulationDay.minusDays(5)
        val fertileEnd = ovulationDay.plusDays(1)

        // 平均经期天数
        val avgPeriodDays = if (periodDurations.isNotEmpty()) {
            periodDurations.average().roundToInt()
        } else DEFAULT_PERIOD_DAYS

        // 当前周期第几天
        val currentCycleStart = adjustedNext.minusDays(predictedCycleDays.toLong())
        val cycleDay = ChronoUnit.DAYS.between(currentCycleStart, today).toInt().coerceAtLeast(0) + 1

        // 置信等级
        val confidence = when {
            cycleLengths.size >= 5 -> PredictionConfidence.HIGH
            cycleLengths.size >= 2 -> PredictionConfidence.MEDIUM
            else -> PredictionConfidence.LOW
        }

        val daysUntil = ChronoUnit.DAYS.between(today, adjustedNext)

        return CyclePrediction(
            nextPeriodStart = adjustedNext,
            confidenceIntervalStart = ciStart,
            confidenceIntervalEnd = ciEnd,
            confidenceIntervalDays = ciDays.toInt(),
            posteriorMeanCycleLength = posterior.posteriorMean,
            posteriorStdCycleLength = sqrt(posterior.posteriorVar),
            predictiveStd = predictiveStd,
            fertileWindowStart = fertileStart,
            fertileWindowEnd = fertileEnd,
            ovulationEstimate = ovulationDay,
            daysUntilNextPeriod = daysUntil,
            cycleDay = cycleDay,
            averagePeriodDays = avgPeriodDays,
            totalCyclesRecorded = cycleLengths.size,
            confidence = confidence,
            observedCycleLengths = cycleLengths,
        )
    }

    /**
     * 贝叶斯后验更新 (Normal-Normal conjugate)，带时间衰减加权。
     *
     * 只接收已过滤的可信周期。过短/过长的间隔通常来自漏记或导入断层，不参与预测。
     * 只取最近 [MAX_HISTORY_CYCLES] 个周期，并按 [RECENCY_DECAY] 指数加权——
     * 作息/身体节奏变化后，几年前的旧周期不应与上个月的等权。
     * 加权后用有效样本量 n_eff = (Σw)²/Σw² 代入共轭更新，全部等权时退化为原公式。
     */
    private fun updatePosterior(cycleLengths: List<Int>): PosteriorState {
        if (cycleLengths.isEmpty()) {
            return PosteriorState(
                posteriorMean = PRIOR_MEAN,
                posteriorVar = PRIOR_VAR,
                obsVar = DEFAULT_OBS_STD * DEFAULT_OBS_STD,
            )
        }

        val observed = cycleLengths.takeLast(MAX_HISTORY_CYCLES).map { it.toDouble() }
        val weights = DoubleArray(observed.size) { i ->
            RECENCY_DECAY.pow((observed.size - 1 - i).toDouble())
        }
        val weightSum = weights.sum()
        val weightSqSum = weights.sumOf { it * it }
        val weightedMean = observed.indices.sumOf { weights[it] * observed[it] } / weightSum

        // 加权样本方差 (可靠性权重的 Bessel 修正)，至少用默认观测噪声，
        // 避免一两条完全相同记录导致置信区间过窄。
        val obsVar = if (observed.size >= 2) {
            val ss = observed.indices.sumOf { i ->
                val diff = observed[i] - weightedMean
                weights[i] * diff * diff
            }
            (ss / (weightSum - weightSqSum / weightSum))
                .coerceAtLeast(DEFAULT_OBS_STD * DEFAULT_OBS_STD * 0.5)
        } else {
            DEFAULT_OBS_STD * DEFAULT_OBS_STD
        }

        val nEff = weightSum * weightSum / weightSqSum

        // τₙ² = 1 / (1/τ₀² + n_eff/σ²)
        val posteriorPrecision = 1.0 / PRIOR_VAR + nEff / obsVar
        val posteriorVar = 1.0 / posteriorPrecision

        // μₙ = τₙ² × (μ₀/τ₀² + n_eff × x̄_w/σ²)
        val posteriorMean = posteriorVar * (PRIOR_MEAN / PRIOR_VAR + nEff * weightedMean / obsVar)

        return PosteriorState(
            posteriorMean = posteriorMean,
            posteriorVar = posteriorVar,
            obsVar = obsVar,
        )
    }

    /**
     * 获取指定日期的状态。
     *
     * @param date 要查询的日期
     * @param prediction 当前预测结果
     * @param lastPeriodStart 最近一次经期起始日
     * @param periodDays 经期持续天数
     */
    fun getDayStatus(
        date: LocalDate,
        prediction: CyclePrediction?,
        lastPeriodStart: LocalDate?,
        periodDays: Int = DEFAULT_PERIOD_DAYS,
    ): DayStatus {
        if (prediction == null || lastPeriodStart == null) return DayStatus.UNKNOWN

        // 正在经期中？
        val periodEnd = lastPeriodStart.plusDays((periodDays - 1).toLong())
        if (!date.isBefore(lastPeriodStart) && !date.isAfter(periodEnd)) {
            return DayStatus.MENSTRUAL
        }

        // 预测经期
        val predictedStart = prediction.nextPeriodStart
        val predictedEnd = predictedStart.plusDays((prediction.averagePeriodDays - 1).toLong())
        if (!date.isBefore(predictedStart) && !date.isAfter(predictedEnd)) {
            return DayStatus.PREDICTED_PERIOD
        }

        // 排卵日
        if (date == prediction.ovulationEstimate) {
            return DayStatus.OVULATION
        }

        // 受孕窗口
        if (!date.isBefore(prediction.fertileWindowStart) && !date.isAfter(prediction.fertileWindowEnd)) {
            return DayStatus.FERTILE
        }

        return DayStatus.SAFE
    }

    // ──── 数据类 ────────────────────────────────────────────

    private data class PosteriorState(
        val posteriorMean: Double,
        val posteriorVar: Double,
        val obsVar: Double,
    )
}

// ──── 公共数据类 ──────────────────────────────────────────

/** 完整预测结果 */
data class CyclePrediction(
    /** 下次经期预计起始日 (MAP 估计) */
    val nextPeriodStart: LocalDate,
    /** 80% 置信区间起始日 */
    val confidenceIntervalStart: LocalDate,
    /** 80% 置信区间结束日 */
    val confidenceIntervalEnd: LocalDate,
    /** 置信区间天数 (±) */
    val confidenceIntervalDays: Int,
    /** 后验周期均值 (天) */
    val posteriorMeanCycleLength: Double,
    /** 后验周期标准差 (天) */
    val posteriorStdCycleLength: Double,
    /** 预测分布标准差 (天) */
    val predictiveStd: Double,
    /** 受孕窗口起始 */
    val fertileWindowStart: LocalDate,
    /** 受孕窗口结束 */
    val fertileWindowEnd: LocalDate,
    /** 排卵日估计 */
    val ovulationEstimate: LocalDate,
    /** 距下次经期天数 */
    val daysUntilNextPeriod: Long,
    /** 当前周期第几天 */
    val cycleDay: Int,
    /** 平均经期天数 */
    val averagePeriodDays: Int,
    /** 已记录完整周期数 */
    val totalCyclesRecorded: Int,
    /** 预测置信等级 */
    val confidence: PredictionConfidence,
    /** 观测到的周期长度列表 */
    val observedCycleLengths: List<Int>,
)

/** 预测置信等级 */
enum class PredictionConfidence(val label: String, val labelCn: String) {
    LOW("Low", "低"),
    MEDIUM("Medium", "中"),
    HIGH("High", "高"),
}

/** 日级生理状态 */
enum class DayStatus(val labelCn: String, val emoji: String) {
    MENSTRUAL("经期", "🔴"),
    PREDICTED_PERIOD("预测经期", "🟠"),
    OVULATION("排卵日", "🟡"),
    FERTILE("受孕期", "🟢"),
    SAFE("安全期", "🔵"),
    UNKNOWN("未知", "⚪"),
}
