package com.vita.healthtracker.data.ai

import kotlinx.serialization.Serializable

/**
 * 发送给 AI 分析的数据包 (聚合日指标 + 关键明细)。
 * 字段名刻意取短: 一年数据会按 JSON 原样进入模型上下文, 短字段名省 token。
 * 不包含: 原始心率/压力曲线、Garmin raw JSON、运动等其它自由文本备注。
 * 情绪备注 (moods[].moments[].note) 是例外——charlotte 明确要求纳入分析。
 */
@Serializable
data class AiAnalysisPayload(
    val generatedAt: String,
    val zone: String,
    /** 本次分析的近期明细窗口天数 (14/90/365), 让模型知道用户选了多大的区间。 */
    val windowDays: Int? = null,
    /** [FOCUS_TODAY] = 「今日状态」快速分析 (今日页): 结合昨晚睡眠讲今天 + 近 3-7 天短期趋势。null = 趋势页常规分析。 */
    val focus: String? = null,
    /** 多年压缩摘要; 用户选「近三月/近一年」时不带, 模型只分析近期。 */
    val longTerm: AiLongTermSummary? = null,
    val daily: List<AiDailyMetric> = emptyList(),
    val sleeps: List<AiNightSleep> = emptyList(),
    val exercises: List<AiExercise> = emptyList(),
    val cycle: List<AiCycleDay> = emptyList(),
    val moods: List<AiMoodDay> = emptyList(),
    val habits: List<AiHabit> = emptyList(),
) {
    companion object {
        /** 今日聚焦分析; 服务端/直连提示词按它切换今日模板。 */
        const val FOCUS_TODAY = "today"
    }
}

@Serializable
data class AiDailyMetric(
    val d: String,
    val steps: Long? = null,
    val km: Double? = null,          // 总距离 (公里)
    val kcal: Int? = null,           // 活动消耗
    val avgHr: Int? = null,
    val rhr: Int? = null,            // 静息心率
    val hrv: Int? = null,
    val stress: Int? = null,
    val bbHigh: Int? = null,         // 身体电量高点
    val bbLow: Int? = null,
    val spo2: Int? = null,
    val resp: Double? = null,
    val weight: Double? = null,
)

/** 每晚主睡眠 (同晚多条记录只取最长一条, 归醒来那天)。 */
@Serializable
data class AiNightSleep(
    val d: String,                   // 醒来日期
    val start: String? = null,       // 入睡时刻 HH:mm
    val min: Long,
    val deep: Long? = null,
    val light: Long? = null,
    val rem: Long? = null,
    val awake: Long? = null,
    val score: Int? = null,
)

@Serializable
data class AiExercise(
    val d: String,
    val type: String,
    val min: Long,
    val km: Double? = null,
    val kcal: Int? = null,
    val avgHr: Int? = null,
    val te: Double? = null,          // 训练效果
)

@Serializable
data class AiCycleDay(
    val d: String,
    val flow: Int,                   // 0=无 1=点滴 2=轻 3=中 4=重
    val start: Boolean = false,
    val symptoms: String? = null,
)

/** 一天的情绪: 主导情绪 + 平均情绪价 + 当天所有时刻 (含用户备注)。 */
@Serializable
data class AiMoodDay(
    val d: String,
    val mood: String,                            // 当天主导 / 最能代表这天的情绪名
    val valence: Double? = null,                 // 当天平均情绪价 1-5, 越低整体越低落
    val moments: List<AiMoodMoment> = emptyList(),
)

/** 一天里的一条情绪时刻。note 是用户随手写下的处境/缘由, 明确要求纳入分析。 */
@Serializable
data class AiMoodMoment(
    val t: String,                               // 记录时刻 HH:mm
    val mood: String,
    val note: String? = null,
)

@Serializable
data class AiHabit(val name: String, val doneDates: List<String> = emptyList())

@Serializable
data class AiLongTermSummary(
    val from: String,
    val to: String,
    val trackedDays: Int,
    val years: List<AiLongTermYear> = emptyList(),
    val shifts: List<AiLongTermShift> = emptyList(),
)

@Serializable
data class AiLongTermYear(
    val y: Int,
    val days: Int = 0,
    val sleepNights: Int = 0,
    val sleepMin: Long? = null,
    val steps: Long? = null,
    val activeMin: Long? = null,
    val rhr: Int? = null,
    val hrv: Int? = null,
    val stress: Int? = null,
    val bbHigh: Int? = null,
    val spo2: Int? = null,
    val weight: Double? = null,
    val exerciseCount: Int = 0,
    val exerciseMin: Long? = null,
    val exerciseKm: Double? = null,
    val cycleStarts: Int = 0,
)

@Serializable
data class AiLongTermShift(
    val metric: String,
    val fromY: Int,
    val toY: Int,
    val from: Double,
    val to: Double,
    val delta: Double,
)

// ─────────────────────────────────────────────────────────────

@Serializable
data class AiInsightItem(val title: String, val body: String)

/** AI 返回的结构化洞察。analyzedAtEpochMs / daysCovered / rangeKey 由客户端落库时补打。 */
@Serializable
data class AiInsight(
    val overall: String,
    val longTermFindings: List<AiInsightItem> = emptyList(),
    val recentPatterns: List<AiInsightItem> = emptyList(),
    val findings: List<AiInsightItem> = emptyList(),
    val actions: List<AiInsightItem> = emptyList(),
    val watch: List<String> = emptyList(),
    val model: String? = null,
    val analyzedAtEpochMs: Long = 0L,
    val daysCovered: Int = 0,
    /** 当次选择的分析区间 (SettingsPreferences.AI_RANGE_*), 老缓存没有该字段。 */
    val rangeKey: String? = null,
)

/** AI 分析失败时抛出的、可直接展示给用户的错误。 */
class AiInsightException(message: String, cause: Throwable? = null) : Exception(message, cause)
