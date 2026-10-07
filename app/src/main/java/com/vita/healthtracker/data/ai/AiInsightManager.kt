package com.vita.healthtracker.data.ai

import android.content.Context
import com.vita.healthtracker.data.local.entity.HabitCheckIn
import com.vita.healthtracker.data.local.entity.SleepSession
import com.vita.healthtracker.data.prefs.SettingsPreferences
import com.vita.healthtracker.data.repository.CycleRepository
import com.vita.healthtracker.data.repository.HabitRepository
import com.vita.healthtracker.data.repository.HealthRepository
import com.vita.healthtracker.data.repository.MoodRepository
import com.vita.healthtracker.domain.ExerciseClassifier
import com.vita.healthtracker.domain.MoodAggregator
import com.vita.healthtracker.domain.MoodCatalog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * AI 洞察编排器: 打包数据 → 交给 [AiInsightProvider] → 缓存结果。
 * 跑在 app scope 上, 离开页面不中断; 结果落 DataStore, 不在家也能看上次分析。
 */
class AiInsightManager(
    context: Context,
    private val scope: CoroutineScope,
    private val prefs: SettingsPreferences,
    private val provider: AiInsightProvider,
    private val healthRepo: HealthRepository,
    private val habitRepo: HabitRepository,
    private val moodRepo: MoodRepository,
    private val cycleRepo: CycleRepository,
) {
    private val appContext = context.applicationContext

    sealed interface Status {
        data object Idle : Status
        data object Running : Status
        data class Failed(val message: String) : Status
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()
    private var requestJob: Job? = null

    /** 今日页「AI 今日解读」独立于趋势页分析: 各自的状态/任务, 互不阻塞。 */
    private val _todayStatus = MutableStateFlow<Status>(Status.Idle)
    val todayStatus: StateFlow<Status> = _todayStatus.asStateFlow()
    private var todayJob: Job? = null

    /** 前台服务引用计数: 两路分析可能并行, 最后一个结束才停服务。 */
    private val serviceUsers = java.util.concurrent.atomic.AtomicInteger(0)

    /**
     * 某个分析区间上次成功的结果 (持久化), 没有则 null。
     * 老版本只有单份缓存: 若该区间还没有自己的缓存, 且老缓存正好属于这个区间
     * (rangeKey 缺失的更老结果按「多年全景」算——当时默认带多年摘要), 用老缓存回退。
     */
    fun lastInsightFor(range: String): Flow<AiInsight?> =
        combine(
            prefs.aiLastInsightJson(range),
            prefs.legacyAiInsightJson,
        ) { perRange, legacy ->
            perRange?.let(::decodeInsight)
                ?: legacy?.let(::decodeInsight)
                    ?.takeIf { (it.rangeKey ?: SettingsPreferences.AI_RANGE_ALL) == range }
        }

    private fun decodeInsight(raw: String): AiInsight? =
        runCatching { json.decodeFromString(AiInsight.serializer(), raw) }.getOrNull()

    fun requestAnalysis() {
        if (_status.value is Status.Running) return
        acquireService()
        _status.value = Status.Running
        requestJob = scope.launch {
            try {
                val range = prefs.aiAnalysisRange.first()
                val payload = buildPayload(range)
                if (payload.daily.size < MIN_DAYS) {
                    throw AiInsightException("这个区间里有效记录只有 ${payload.daily.size} 天，先同步攒一点数据、或选更长的区间再分析。")
                }
                val insight = provider.analyze(payload).copy(
                    analyzedAtEpochMs = System.currentTimeMillis(),
                    daysCovered = payload.longTerm?.trackedDays ?: payload.daily.size,
                    rangeKey = range,
                )
                prefs.saveAiInsight(range, json.encodeToString(AiInsight.serializer(), insight))
                _status.value = Status.Idle
            } catch (e: CancellationException) {
                _status.value = Status.Failed("分析已停止")
            } catch (e: Exception) {
                _status.value = Status.Failed(e.message ?: "分析失败，稍后再试。")
            } finally {
                releaseService()
                requestJob = null
            }
        }
    }

    fun cancelAnalysis() {
        requestJob?.cancel()
        if (_status.value is Status.Running) {
            _status.value = Status.Failed("分析已停止")
        }
    }

    fun clearError() {
        if (_status.value is Status.Failed) _status.value = Status.Idle
    }

    // ─── 今日页「AI 今日解读」 ────────────────────────────────

    /** 上次「今日解读」结果 (持久化); UI 自己判断 analyzedAt 是不是今天来决定要不要提示重新分析。 */
    val todayInsight: Flow<AiInsight?> = lastInsightFor(SettingsPreferences.AI_RANGE_TODAY)

    /** 结合昨晚睡眠 + 近 3-7 天短期趋势的今日快速分析 (近两周明细, 不带多年摘要)。 */
    fun requestTodayAnalysis() {
        if (_todayStatus.value is Status.Running) return
        acquireService()
        _todayStatus.value = Status.Running
        todayJob = scope.launch {
            try {
                val payload = buildPayload(
                    windowDays = TODAY_WINDOW_DAYS,
                    includeLongTerm = false,
                    cycleWindowDays = TODAY_CYCLE_WINDOW_DAYS,
                    focus = AiAnalysisPayload.FOCUS_TODAY,
                )
                if (payload.daily.size < MIN_TODAY_DAYS) {
                    throw AiInsightException("近两周有效记录只有 ${payload.daily.size} 天，先同步几天数据再分析。")
                }
                val insight = provider.analyze(payload).copy(
                    analyzedAtEpochMs = System.currentTimeMillis(),
                    daysCovered = payload.daily.size,
                    rangeKey = SettingsPreferences.AI_RANGE_TODAY,
                )
                prefs.saveAiInsight(
                    SettingsPreferences.AI_RANGE_TODAY,
                    json.encodeToString(AiInsight.serializer(), insight),
                )
                _todayStatus.value = Status.Idle
            } catch (e: CancellationException) {
                _todayStatus.value = Status.Failed("分析已停止")
            } catch (e: Exception) {
                _todayStatus.value = Status.Failed(e.message ?: "分析失败，稍后再试。")
            } finally {
                releaseService()
                todayJob = null
            }
        }
    }

    fun cancelTodayAnalysis() {
        todayJob?.cancel()
        if (_todayStatus.value is Status.Running) {
            _todayStatus.value = Status.Failed("分析已停止")
        }
    }

    fun clearTodayError() {
        if (_todayStatus.value is Status.Failed) _todayStatus.value = Status.Idle
    }

    private fun acquireService() {
        if (serviceUsers.incrementAndGet() == 1) AiInsightForegroundService.start(appContext)
    }

    private fun releaseService() {
        if (serviceUsers.decrementAndGet() <= 0) {
            serviceUsers.set(0)
            AiInsightForegroundService.stop(appContext)
        }
    }

    /**
     * 聚合日指标 + 关键明细 (charlotte 拍板的范围); 不含原始曲线 / raw JSON / 运动等其它自由文本备注。
     * 情绪备注是例外 (charlotte 要求纳入): 随每条情绪时刻一并发送。
     * [range] 决定近期明细窗口与是否附带多年摘要:
     * 近三月/近一年只带对应窗口 (结果更贴近当下, 不重复多年老结论), 多年全景才附 longTerm。
     */
    private suspend fun buildPayload(range: String): AiAnalysisPayload = buildPayload(
        windowDays = if (range == SettingsPreferences.AI_RANGE_QUARTER) QUARTER_WINDOW_DAYS else DAILY_WINDOW_DAYS,
        includeLongTerm = range == SettingsPreferences.AI_RANGE_ALL,
        cycleWindowDays = null,
        focus = null,
    )

    private suspend fun buildPayload(
        windowDays: Long,
        includeLongTerm: Boolean,
        /** null = 按老规则 (多年全景 730 天, 否则至少一年); 今日聚焦传短窗口。 */
        cycleWindowDays: Long?,
        focus: String?,
    ): AiAnalysisPayload {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val dailyStart = today.minusDays(windowDays)
        val longTermStart = today.minusYears(LONG_TERM_WINDOW_YEARS)
        val exerciseStart = today.minusDays(minOf(EXERCISE_WINDOW_DAYS, windowDays))
        val cycleStart = today.minusDays(
            cycleWindowDays ?: if (includeLongTerm) CYCLE_WINDOW_DAYS else maxOf(windowDays, 365L)
        )
        // 历史查询范围: 不带多年摘要时只查到明细窗口起点, 省一次全库扫描。
        val historyStart = if (includeLongTerm) longTermStart else dailyStart
        val historyStartInstant = historyStart.atStartOfDay(zone).toInstant()
        val endInstant = today.plusDays(1).atStartOfDay(zone).toInstant()
        val hhmm = DateTimeFormatter.ofPattern("HH:mm")
        val historyDaily = healthRepo.dailyRange(historyStart, today).first()
        val historySleeps = healthRepo.sleepRange(historyStartInstant, endInstant).first()
        val historyExercises = healthRepo.exerciseRange(historyStartInstant, endInstant).first()
            .filter { !it.isDeleted && ExerciseClassifier.shouldShowInStats(it, zone) }
        val historyCycles = cycleRepo.observeRange(minOf(historyStart, cycleStart), today).first()

        val daily = historyDaily
            .filter { it.date >= dailyStart.toString() }
            .filter { s ->
                s.steps > 0 || s.hrv != null || s.restingHeartRate != null ||
                    s.avgHeartRate != null || s.avgStress != null || s.weightKg != null
            }
            .map { s ->
                AiDailyMetric(
                    d = s.date,
                    steps = s.steps.takeIf { it > 0 },
                    km = s.distanceMeters?.let { (it / 100.0).roundToInt() / 10.0 },
                    kcal = s.activeCalories?.roundToInt(),
                    avgHr = s.avgHeartRate,
                    rhr = s.restingHeartRate,
                    hrv = s.hrv,
                    stress = s.avgStress,
                    bbHigh = s.bodyBatteryHigh,
                    bbLow = s.bodyBatteryLow,
                    spo2 = s.avgSpo2,
                    resp = s.avgRespiration?.let { (it * 10).roundToInt() / 10.0 },
                    weight = s.weightKg?.let { (it * 10).roundToInt() / 10.0 },
                )
            }

        // 每晚主睡眠: 剔除脏数据, 同晚取最长, 归醒来那天 (与全 app 口径一致)
        val sleeps = historySleeps
            .filter { it.totalMinutes in 1..960 }
            .groupBy { sleepDisplayDate(it, zone) }
            .mapNotNull { (date, sessions) ->
                val main = sessions.maxByOrNull { it.totalMinutes } ?: return@mapNotNull null
                date ?: return@mapNotNull null
                if (date < dailyStart || date > today) return@mapNotNull null
                AiNightSleep(
                    d = date.toString(),
                    start = Instant.ofEpochMilli(main.startEpochMs).atZone(zone).toLocalTime().format(hhmm),
                    min = main.totalMinutes,
                    deep = main.deepMinutes.takeIf { it > 0 },
                    light = main.lightMinutes.takeIf { it > 0 },
                    rem = main.remMinutes.takeIf { it > 0 },
                    awake = main.awakeMinutes.takeIf { it > 0 },
                    score = main.sleepScore,
                )
            }
            .sortedBy { it.d }

        val exercises = historyExercises
            .filter {
                val date = Instant.ofEpochMilli(it.startEpochMs).atZone(zone).toLocalDate()
                date >= exerciseStart && date <= today
            }
            .map { e ->
                AiExercise(
                    d = Instant.ofEpochMilli(e.startEpochMs).atZone(zone).toLocalDate().toString(),
                    type = e.customCategory ?: e.type,
                    min = e.durationMinutes,
                    km = e.distanceMeters?.let { (it / 100.0).roundToInt() / 10.0 }?.takeIf { it > 0 },
                    kcal = e.activeCalories?.roundToInt(),
                    avgHr = e.avgHeartRate,
                    te = e.trainingEffect,
                )
            }

        val cycle = historyCycles
            .filter { it.date >= cycleStart.toString() }
            .filter { it.flow > 0 || it.isPeriodStart || !it.symptomsCsv.isNullOrBlank() }
            .map { c ->
                AiCycleDay(
                    d = c.date,
                    flow = c.flow,
                    start = c.isPeriodStart,
                    symptoms = c.symptomsCsv?.takeIf { it.isNotBlank() },
                )
            }

        // 一天可能多条情绪时刻: 发主导情绪 + 当天平均价 + 每条时刻(含用户备注),
        // 让模型看清一天内的起伏和用户亲手写下的缘由 (charlotte 要求备注纳入分析),
        // 不再只发主导一条。发情绪名而非 id: 自定义情绪 id 是 UUID, 对模型没语义。
        val moods = MoodAggregator.daily(moodRepo.observeRange(dailyStart, today).first(), zone)
            .toSortedMap()
            .map { (date, day) ->
                AiMoodDay(
                    d = date.toString(),
                    mood = MoodCatalog.byId(day.dominantMoodId)?.label ?: day.dominantMoodId,
                    valence = (day.avgValence * 10).roundToInt() / 10.0,
                    moments = day.moments.map { m ->
                        AiMoodMoment(
                            t = m.time.format(hhmm),
                            mood = MoodCatalog.byId(m.moodId)?.label ?: m.moodId,
                            note = m.note.takeIf { it.isNotBlank() },
                        )
                    },
                )
            }

        val habitDefs = habitRepo.observeActiveHabits().first()
        val checkIns = habitRepo.observeCheckIns(exerciseStart, today).first()
            .filter { it.status == HabitCheckIn.StatusDone }
            .groupBy { it.habitId }
        val habits = habitDefs.mapNotNull { def ->
            val dates = checkIns[def.id]?.map { it.date }?.sorted() ?: return@mapNotNull null
            AiHabit(name = def.name, doneDates = dates)
        }

        return AiAnalysisPayload(
            generatedAt = today.toString(),
            zone = zone.id,
            windowDays = windowDays.toInt(),
            focus = focus,
            daily = daily,
            sleeps = sleeps,
            exercises = exercises,
            cycle = cycle,
            moods = moods,
            habits = habits,
            longTerm = if (includeLongTerm) {
                AiLongTermSummaryBuilder.build(
                    daily = historyDaily,
                    sleeps = historySleeps,
                    exercises = historyExercises,
                    cycles = historyCycles,
                    zone = zone,
                )
            } else {
                null
            },
        )
    }

    private fun sleepDisplayDate(sleep: SleepSession, zone: ZoneId): LocalDate? = when {
        sleep.endEpochMs > sleep.startEpochMs ->
            Instant.ofEpochMilli(sleep.endEpochMs).atZone(zone).toLocalDate()
        sleep.startEpochMs > 0 ->
            Instant.ofEpochMilli(sleep.startEpochMs).atZone(zone).toLocalDate()
        else -> null
    }

    companion object {
        private const val DAILY_WINDOW_DAYS = 365L
        private const val QUARTER_WINDOW_DAYS = 90L
        private const val LONG_TERM_WINDOW_YEARS = 10L
        private const val EXERCISE_WINDOW_DAYS = 180L
        private const val CYCLE_WINDOW_DAYS = 730L
        private const val MIN_DAYS = 7

        /** 今日解读: 近两周明细足够看清 3-7 天走向, 又比整年 payload 快得多。 */
        private const val TODAY_WINDOW_DAYS = 14L
        /** 今日解读带 90 天周期记录, 让模型知道现在处于经期哪个位置。 */
        private const val TODAY_CYCLE_WINDOW_DAYS = 90L
        private const val MIN_TODAY_DAYS = 3
    }
}
