package com.vita.healthtracker.ui.screens.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vita.healthtracker.data.ai.AiInsight
import com.vita.healthtracker.data.ai.AiInsightManager
import com.vita.healthtracker.data.local.entity.BodyBatterySample
import com.vita.healthtracker.data.local.entity.CycleEntry
import com.vita.healthtracker.data.local.entity.DailyHealthSnapshot
import com.vita.healthtracker.data.local.entity.ExerciseSession
import com.vita.healthtracker.data.local.entity.HabitCheckIn
import com.vita.healthtracker.data.local.entity.HabitDefinition
import com.vita.healthtracker.data.local.entity.MoodEntry
import com.vita.healthtracker.data.local.entity.SleepSession
import com.vita.healthtracker.data.prefs.SettingsPreferences
import com.vita.healthtracker.data.repository.CycleRepository
import com.vita.healthtracker.data.repository.HabitRepository
import com.vita.healthtracker.data.repository.HealthRepository
import com.vita.healthtracker.data.repository.MoodRepository
import com.vita.healthtracker.data.sync.SyncCoordinator
import com.vita.healthtracker.domain.AnomalyCategory
import com.vita.healthtracker.domain.AnomalyEvent
import com.vita.healthtracker.domain.AnomalyEventEngine
import com.vita.healthtracker.domain.DailyBrief
import com.vita.healthtracker.domain.DailyBriefAnalyzer
import com.vita.healthtracker.domain.ExerciseClassifier
import com.vita.healthtracker.domain.HomeMetric
import com.vita.healthtracker.domain.ReadinessEngine
import com.vita.healthtracker.domain.ScoreMetric
import com.vita.healthtracker.domain.StatusScore
import com.vita.healthtracker.domain.TrendAnalyzer
import com.vita.healthtracker.domain.TrendReport
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.ExperimentalCoroutinesApi

data class TodayUiState(
    val date: LocalDate = LocalDate.now(),
    val daily: DailyHealthSnapshot? = null,
    val lastSleep: SleepSession? = null,
    val exercises: List<ExerciseSession> = emptyList(),
    val bodyBatterySamples: List<BodyBatterySample> = emptyList(),
    val syncing: Boolean = false,
    val syncFraction: Float = 0f,
    val syncPhase: String = "",
    val syncProcessed: Int = 0,
    val syncTotal: Int = 0,
    val lastSyncedAt: Instant? = null,
    val syncMessage: String? = null,
    /** 今日状态分 + 洞察 (跨维度恢复评分引擎输出); 无可评分数据时为 null。 */
    val readiness: StatusScore? = null,
    /** 首页首屏简报: 一句判断、一条建议、三个依据和连续变化。 */
    val dailyBrief: DailyBrief? = null,
    /** 满足持续时间、偏离幅度和样本量门槛的本地预警事件。 */
    val anomalyEvents: List<AnomalyEvent> = emptyList(),
    /** 月度身体趋势 (近30天 vs 上个30天), 供洞察引擎判断持续走弱; 样本不足为 null。 */
    val monthTrend: TrendReport? = null,
    /** 用户在设置里勾选要在首页展示的数据项 (HomeMetric.key)。 */
    val enabledMetrics: Set<String> = HomeMetric.DEFAULT_KEYS,
) {
    /** 是否停留在「今天」, 用于决定是否显示「回到今日」。 */
    val isToday: Boolean get() = date == LocalDate.now()
}

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    private val repo: HealthRepository,
    private val prefs: SettingsPreferences,
    private val syncCoordinator: SyncCoordinator,
    private val habitRepo: HabitRepository,
    private val moodRepo: MoodRepository,
    private val cycleRepo: CycleRepository,
    private val aiManager: AiInsightManager,
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(LocalDate.now())

    // ─── AI 今日解读: 状态在 manager (app scope), 离开页面分析不中断 ───
    val aiTodayStatus: StateFlow<AiInsightManager.Status> = aiManager.todayStatus
    val aiTodayInsight: StateFlow<AiInsight?> = aiManager.todayInsight
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun requestAiToday() = aiManager.requestTodayAnalysis()
    fun cancelAiToday() = aiManager.cancelTodayAnalysis()

    val state: StateFlow<TodayUiState> = run {
        val zone = ZoneId.systemDefault()
        val base = _selectedDate.flatMapLatest { date ->
            val dayStart = date.atStartOfDay(zone).toInstant()
            val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant()
            val dayData = combine(
                repo.dailyRange(date, date),
                repo.sleepRange(date.minusDays(1).atStartOfDay(zone).toInstant(), dayEnd),
                repo.exerciseRange(dayStart, dayEnd),
                repo.bodyBatteryRange(dayStart, dayEnd),
            ) { daily, sleeps, exercises, bodyBattery ->
                TodayDayData(daily, sleeps, exercises, bodyBattery)
            }
            // 全量本地历史: 评分仍只截近 60 天，但规律发现可以使用多年沉淀数据。
            // 数据量按“天”计，十年也只有几千行，适合直接在本机分析。
            val historyData = combine(
                repo.dailyRange(HISTORY_START, date.minusDays(1)),
                repo.sleepRange(HISTORY_START.atStartOfDay(zone).toInstant(), dayEnd),
            ) { historyDaily, historySleep ->
                BaselineData(historyDaily, historySleep)
            }
            val lifestyleData = combine(
                habitRepo.observeActiveHabits(),
                habitRepo.observeCheckIns(HISTORY_START, date),
                moodRepo.observeRange(HISTORY_START, date),
                cycleRepo.observeRange(HISTORY_START, date),
            ) { habits, checkIns, moods, cycles ->
                LifestyleData(habits, checkIns, moods, cycles)
            }
            combine(
                dayData,
                historyData,
                lifestyleData,
                syncCoordinator.status,
                repo.observeLastSyncedAt(),
            ) { data, history, lifestyle, sync, syncedAt ->
                val selectedSleep = data.sleeps
                    .filter { it.totalMinutes > 0 && sleepDisplayDate(it, zone) == date }
                    .maxByOrNull { it.endEpochMs }
                val visibleExercises = data.exercises
                    .filter { ExerciseClassifier.shouldShowInStats(it, zone) }
                    .sortedByDescending { it.startEpochMs }
                // 评分只用近 60 天个人基线; 趋势用完整历史窗口。
                val sixtyAgo = date.minusDays(BASELINE_DAYS).toString()
                val baselineDaily = history.daily.filter { it.date in sixtyAgo..date.toString() }
                val sleepCutoffMs = date.minusDays(BASELINE_DAYS).atStartOfDay(zone).toInstant().toEpochMilli()
                val baselineSleep = history.sleeps.filter { it.endEpochMs >= sleepCutoffMs }
                val monthTrend = TrendAnalyzer.analyze(
                    TrendAnalyzer.TrendWindow.MONTH, date, history.daily, history.sleeps, zone,
                )
                // 月度趋势先算好喂给评分引擎: 洞察会结合短期偏离 + 30 天走向判断是否持续走弱。
                val readiness = ReadinessEngine.evaluate(
                    date = date.toString(),
                    today = data.daily.firstOrNull(),
                    lastSleep = selectedSleep,
                    baselineDaily = baselineDaily,
                    baselineSleep = baselineSleep,
                    zone = zone,
                    monthTrend = monthTrend,
                )
                val dailyBrief = readiness?.let {
                    DailyBriefAnalyzer.analyze(
                        date = date,
                        score = it,
                        today = data.daily.firstOrNull(),
                        lastSleep = selectedSleep,
                        historyDaily = history.daily,
                        historySleep = history.sleeps,
                        monthTrend = monthTrend,
                        zone = zone,
                    )
                }
                val anomalyEvents = filterRepeatedEvents(
                    readiness = readiness,
                    events = AnomalyEventEngine.detect(
                        date = date,
                        today = data.daily.firstOrNull(),
                        lastSleep = selectedSleep,
                        historyDaily = history.daily,
                        historySleep = history.sleeps,
                        habits = lifestyle.habits,
                        habitCheckIns = lifestyle.checkIns,
                        moods = lifestyle.moods,
                        cycleEntries = lifestyle.cycles,
                        zone = zone,
                    ),
                )
                TodayUiState(
                    date = date,
                    daily = data.daily.firstOrNull(),
                    lastSleep = selectedSleep,
                    exercises = visibleExercises,
                    bodyBatterySamples = data.bodyBattery,
                    syncing = sync.running,
                    syncFraction = sync.fraction,
                    syncPhase = sync.phase,
                    syncProcessed = sync.processed,
                    syncTotal = sync.total,
                    lastSyncedAt = syncedAt,
                    syncMessage = sync.message,
                    readiness = readiness,
                    dailyBrief = dailyBrief,
                    anomalyEvents = anomalyEvents,
                    monthTrend = monthTrend,
                )
            }
        }
        // 叠加首页数据项可见性偏好 (combine 最多 5 个强类型参数, 故拆成两层)。
        combine(base, prefs.homeMetrics) { s, metrics ->
            s.copy(enabledMetrics = metrics)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())
    }

    /**
     * 触发一次同步。走应用级协调器(佳明 + Health Connect 一起), 跑在应用作用域上,
     * 切页/退出当前页都不会中断 (修 #7)。
     * @param days 回溯天数; null = 增量。
     */
    fun sync(days: Long? = null) {
        syncCoordinator.start(
            days = days,
            includeGarmin = true,
            includeHealthConnect = true,
            refreshGarminDetails = true,
        )
    }

    /** 中途停止同步 (修 #3)。 */
    fun stopSync() {
        syncCoordinator.stop()
    }

    fun previousDay() {
        _selectedDate.value = _selectedDate.value.minusDays(1)
    }

    fun nextDay() {
        val tomorrow = _selectedDate.value.plusDays(1)
        if (!tomorrow.isAfter(LocalDate.now())) _selectedDate.value = tomorrow
    }

    fun jumpToToday() {
        _selectedDate.value = LocalDate.now()
    }

    fun clearMessage() { syncCoordinator.clearMessage() }

    private fun filterRepeatedEvents(
        readiness: StatusScore?,
        events: List<AnomalyEvent>,
    ): List<AnomalyEvent> {
        if (readiness == null || events.isEmpty()) return events
        val highlightedMetrics = buildSet {
            readiness.limits.mapNotNullTo(this) { it.metric }
            readiness.weakest
                ?.takeIf { it.subScore < 55 }
                ?.let { add(it.metric) }
        }
        return events
            .filterNot { event ->
                event.category == AnomalyCategory.RESPIRATORY &&
                    (ScoreMetric.SPO2 in highlightedMetrics || ScoreMetric.RESPIRATION in highlightedMetrics)
            }
            .filterNot { event ->
                event.category == AnomalyCategory.SLEEP && ScoreMetric.SLEEP in highlightedMetrics
            }
    }

    private fun sleepDisplayDate(sleep: SleepSession, zone: ZoneId): LocalDate? =
        runCatching {
            val end = Instant.ofEpochMilli(sleep.endEpochMs).atZone(zone).toLocalDate()
            val start = Instant.ofEpochMilli(sleep.startEpochMs).atZone(zone).toLocalDate()
            if (sleep.endEpochMs > sleep.startEpochMs) end else start
        }.getOrNull()

    private data class TodayDayData(
        val daily: List<DailyHealthSnapshot>,
        val sleeps: List<SleepSession>,
        val exercises: List<ExerciseSession>,
        val bodyBattery: List<BodyBatterySample>,
    )

    private data class BaselineData(
        val daily: List<DailyHealthSnapshot>,
        val sleeps: List<SleepSession>,
    )

    private data class LifestyleData(
        val habits: List<HabitDefinition>,
        val checkIns: List<HabitCheckIn>,
        val moods: List<MoodEntry>,
        val cycles: List<CycleEntry>,
    )

    private companion object {
        const val BASELINE_DAYS = 60L
        val HISTORY_START: LocalDate = LocalDate.of(1900, 1, 1)
    }
}
