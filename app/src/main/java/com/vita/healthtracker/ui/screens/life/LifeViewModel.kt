package com.vita.healthtracker.ui.screens.life

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vita.healthtracker.data.local.entity.HabitCheckIn
import com.vita.healthtracker.data.local.entity.HabitDefinition
import com.vita.healthtracker.data.local.entity.CycleEntry
import com.vita.healthtracker.data.local.entity.DailyHealthSnapshot
import com.vita.healthtracker.data.local.entity.ExerciseSession
import com.vita.healthtracker.data.local.entity.MoodEntry
import com.vita.healthtracker.data.local.entity.WeatherEntry
import com.vita.healthtracker.data.prefs.SettingsPreferences
import com.vita.healthtracker.data.repository.CycleRepository
import com.vita.healthtracker.data.repository.HabitRepository
import com.vita.healthtracker.data.repository.HealthRepository
import com.vita.healthtracker.data.repository.MoodRepository
import com.vita.healthtracker.data.repository.WeatherRepository
import com.vita.healthtracker.domain.BayesianCycleModel
import com.vita.healthtracker.domain.CycleLogLogic
import com.vita.healthtracker.domain.CyclePeriod
import com.vita.healthtracker.domain.CycleRecordType
import com.vita.healthtracker.domain.CyclePrediction
import com.vita.healthtracker.domain.DayStatus
import com.vita.healthtracker.domain.FitnessBadgeEvaluator
import com.vita.healthtracker.domain.FitnessProgress
import com.vita.healthtracker.domain.HabitBadgeCatalog
import com.vita.healthtracker.domain.Mood
import com.vita.healthtracker.domain.MoodAggregator
import com.vita.healthtracker.domain.MoodDaily
import com.vita.healthtracker.domain.MoodShape
import com.vita.healthtracker.domain.PredictionConfidence
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LifeUiState(
    val entries: List<CycleEntry> = emptyList(),
    val cyclePeriods: List<CyclePeriod> = emptyList(),
    val spottingEntries: List<CycleEntry> = emptyList(),
    val symptomOnlyEntries: List<CycleEntry> = emptyList(),
    val symptomOptions: List<String> = emptyList(),
    val moodDaily: Map<String, MoodDaily> = emptyMap(),
    /** 选择器可见的自定义情绪 (未归档)。 */
    val customMoods: List<Mood> = emptyList(),
    val weather: Map<String, WeatherEntry> = emptyMap(),
    val habits: List<HabitDefinition> = emptyList(),
    val habitCheckIns: List<HabitCheckIn> = emptyList(),
    val habitCurrentStreakDays: Int = 0,
    val habitLongestStreakDays: Int = 0,
    val earnedBadgeIds: Set<String> = emptySet(),
    val earnedBadgeTokens: Set<String> = emptySet(),
    val earnedBadgeCounts: Map<String, Int> = emptyMap(),
    val earnedBadgeTotalCount: Int = 0,
    val shownBadgeTokens: Set<String> = emptySet(),
    val badgeUnlockDates: Map<String, String> = emptyMap(),
    // ── 健身徽章 ──
    val fitnessEarnedIds: Set<String> = emptySet(),
    val fitnessProgress: Map<String, FitnessProgress> = emptyMap(),
    val shownFitnessBadgeIds: Set<String> = emptySet(),
    // ── 贝叶斯预测 ──
    val prediction: CyclePrediction? = null,
    val todayStatus: DayStatus = DayStatus.UNKNOWN,
    val todayPeriodDay: Int? = null,
)

class LifeViewModel(
    private val cycleRepo: CycleRepository,
    private val healthRepo: HealthRepository,
    private val habitRepo: HabitRepository,
    private val moodRepo: MoodRepository,
    private val weatherRepo: WeatherRepository,
    private val settingsPreferences: SettingsPreferences,
) : ViewModel() {

    val state: StateFlow<LifeUiState> = run {
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()

        val journal = combine(
            moodRepo.observeRange(today.minusDays(370), today),
            moodRepo.observeActiveCustomMoods(),
            weatherRepo.observeRange(today.minusDays(370), today),
        ) { moods, customMoods, weather -> JournalData(moods, customMoods, weather) }
        val base = combine(
            cycleRepo.observeAll(),
            journal,
        ) { entries, journalData ->
            val periods = CycleLogLogic.groupIntoPeriods(entries)
            // 预测直接由当前 entries 派生, 与列表保证一致 (不再用单独的 StateFlow 手动刷新)。
            val overview = cycleOverview(periods)
            LifeUiState(
                entries = entries,
                cyclePeriods = periods,
                spottingEntries = CycleLogLogic.spottingEntries(entries),
                symptomOnlyEntries = CycleLogLogic.symptomOnlyEntries(entries),
                symptomOptions = CycleLogLogic.symptomOptions(entries),
                moodDaily = MoodAggregator.daily(journalData.moods, zone).mapKeys { it.key.toString() },
                customMoods = journalData.customMoods,
                weather = journalData.weather.associateBy { it.date },
                prediction = overview.prediction,
                todayStatus = overview.status,
                todayPeriodDay = overview.periodDay,
            )
        }
        combine(
            base,
            habitRepo.observeActiveHabits(),
            habitRepo.observeCheckIns(today.minusDays(364), today),
            settingsPreferences.shownHabitBadgeTokens,
            settingsPreferences.badgeUnlockDates,
        ) { baseState, habits, checkIns, shownTokens, unlockDates ->
            val currentStreak = habits.maxOfOrNull { habit ->
                HabitBadgeCatalog.currentStreak(checkIns, today, habit.id)
            } ?: 0
            val longestStreak = habits.maxOfOrNull { habit ->
                HabitBadgeCatalog.longestStreak(checkIns, habit.id)
            } ?: 0
            val earnedTokens = HabitBadgeCatalog.earnedBadgeTokens(habits, checkIns)
            val earnedCounts = HabitBadgeCatalog.earnedCountsByBadge(earnedTokens)
            baseState.copy(
                habits = habits,
                habitCheckIns = checkIns,
                habitCurrentStreakDays = currentStreak,
                habitLongestStreakDays = longestStreak,
                earnedBadgeIds = earnedCounts.keys,
                earnedBadgeTokens = earnedTokens,
                earnedBadgeCounts = earnedCounts,
                earnedBadgeTotalCount = earnedCounts.values.sum(),
                shownBadgeTokens = shownTokens,
                badgeUnlockDates = unlockDates,
            )
        }.let { withHabits ->
            val fitnessFrom = today.minusDays(365L).atStartOfDay(zone).toInstant()
            combine(
                withHabits,
                healthRepo.exerciseRange(fitnessFrom, now),
                healthRepo.dailyRange(today.minusDays(365), today),
                settingsPreferences.weekStartDay,
                settingsPreferences.shownFitnessBadgeIds,
            ) { stateWithHabits, sessions, snapshots, weekStart, shownFitness ->
                stateWithHabits.copy(
                    fitnessEarnedIds = FitnessBadgeEvaluator.earnedIds(sessions, snapshots, weekStart, today),
                    fitnessProgress = FitnessBadgeEvaluator.allProgress(sessions, snapshots, weekStart, today),
                    shownFitnessBadgeIds = shownFitness,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LifeUiState())
    }

    init {
        viewModelScope.launch { cycleRepo.syncFromHealthConnect() }
        // 第一次观察到某徽章已获得就记录解锁日期（幂等, 不覆盖旧日期）。
        viewModelScope.launch {
            state.collect { s ->
                val earned = s.earnedBadgeIds + s.fitnessEarnedIds
                if (earned.isNotEmpty()) {
                    settingsPreferences.recordBadgeUnlocks(earned, LocalDate.now().toString())
                }
            }
        }
    }

    /** 把日期范围内每天都记录；只有用户勾选时才把第一天标记为周期起始日。 */
    fun logCycleRange(
        startDate: LocalDate,
        endDate: LocalDate,
        flow: Int,
        isStart: Boolean,
        recordType: CycleRecordType = CycleRecordType.PERIOD,
        symptoms: Set<String> = emptySet(),
    ) {
        viewModelScope.launch {
            if (recordType == CycleRecordType.SYMPTOMS && symptoms.isEmpty()) return@launch
            var current = startDate
            while (!current.isAfter(endDate)) {
                val mark = recordType == CycleRecordType.PERIOD && isStart && current == startDate
                cycleRepo.upsert(
                    CycleLogLogic.buildEntry(
                        date = current,
                        recordType = recordType,
                        flow = flow,
                        isStart = mark,
                        symptoms = symptoms,
                    )
                )
                current = current.plusDays(1)
            }
        }
    }

    /** 删除一整段经期（传入该段内所有日期） */
    fun deleteCyclePeriod(period: CyclePeriod) {
        viewModelScope.launch {
            period.entries.forEach { cycleRepo.delete(it.date) }
        }
    }

    /** 单天经量编辑: flow<=0 删除该天记录; 保留已有症状/备注; 编辑过即视为手动数据不再被外部覆盖。 */
    fun setDayFlow(date: LocalDate, flow: Int) {
        viewModelScope.launch {
            val existing = state.value.entries.firstOrNull { it.date == date.toString() }
            if (flow <= 0) {
                if (existing != null) {
                    val symptomsCsv = existing.symptomsCsv?.takeIf { it.isNotBlank() }
                    if (symptomsCsv == null) {
                        cycleRepo.delete(existing.date)
                    } else {
                        cycleRepo.upsert(
                            existing.copy(
                                flow = 0,
                                isPeriodStart = false,
                                notes = null,
                                source = "manual",
                            )
                        )
                    }
                }
            } else {
                // 在经期编辑器里明确设了经量, 就不再是「经间出血」, 并入经期。
                val entry = existing?.copy(
                    flow = flow.coerceIn(1, 4),
                    notes = existing.notes?.takeUnless { it == CycleLogLogic.NOTE_INTERMENSTRUAL_BLEEDING },
                    source = "manual",
                )
                    ?: CycleEntry(date = date.toString(), flow = flow.coerceIn(1, 4), isPeriodStart = false)
                cycleRepo.upsert(entry)
            }
        }
    }

    /** 单天症状编辑: 无出血记录时新建「仅症状」记录。 */
    fun setDaySymptoms(date: LocalDate, symptoms: Set<String>) {
        viewModelScope.launch {
            val symptomsCsv = CycleLogLogic.encodeSymptoms(symptoms)
            val existing = state.value.entries.firstOrNull { it.date == date.toString() }
            when {
                existing != null -> {
                    if (existing.flow <= 0 && symptomsCsv == null) {
                        cycleRepo.delete(existing.date)
                    } else {
                        cycleRepo.upsert(existing.copy(symptomsCsv = symptomsCsv, source = "manual"))
                    }
                }

                symptomsCsv != null -> {
                    cycleRepo.upsert(
                        CycleLogLogic.buildEntry(
                            date = date,
                            recordType = CycleRecordType.SYMPTOMS,
                            flow = 0,
                            isStart = false,
                            symptoms = symptoms,
                        )
                    )
                }
            }
        }
    }

    /** 把一段经期的起始日改为指定日期; 该天还没有记录时先补一条, 段内其它天清除起始标记。 */
    fun setPeriodStartDay(period: CyclePeriod, startDate: LocalDate) {
        viewModelScope.launch {
            if (period.entries.none { it.date == startDate.toString() }) {
                cycleRepo.upsert(CycleEntry(startDate.toString(), flow = 2, isPeriodStart = true))
            }
            period.entries.forEach { entry ->
                val shouldBeStart = entry.date == startDate.toString()
                if (entry.isPeriodStart != shouldBeStart) {
                    cycleRepo.upsert(entry.copy(isPeriodStart = shouldBeStart, source = "manual"))
                }
            }
        }
    }

    fun deleteCycle(date: String) {
        viewModelScope.launch {
            cycleRepo.delete(date)
        }
    }

    fun createHabit(name: String) {
        viewModelScope.launch {
            habitRepo.createHabit(name)
        }
    }

    /** 记一条情绪时刻。[recordedAt] 缺省即「此刻」, 补录过去某天时由界面给出该天的时刻; [weatherId] 为当时天气 (可空)。 */
    fun logMoodMoment(
        moodId: String,
        recordedAt: LocalDateTime = LocalDateTime.now(),
        note: String = "",
        weatherId: String? = null,
    ) {
        viewModelScope.launch {
            val epochMs = recordedAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            moodRepo.addMoment(moodId, epochMs, note, weatherId)
        }
    }

    /** 修改已有时刻的情绪/备注/天气 (时间不变)。 */
    fun updateMoodMoment(id: String, moodId: String, note: String = "", weatherId: String? = null) {
        viewModelScope.launch {
            moodRepo.updateMoment(id, moodId, note, weatherId)
        }
    }

    fun deleteMoodMoment(id: String) {
        viewModelScope.launch {
            moodRepo.deleteMoment(id)
        }
    }

    /** 清掉某一天全部情绪时刻。 */
    fun clearMoodDay(date: LocalDate) {
        viewModelScope.launch {
            moodRepo.clearDay(date)
        }
    }

    // ──── 自定义情绪 ────────────────────────────────────────

    fun createCustomMood(label: String, colorHex: Long, shape: MoodShape, valence: Int) {
        viewModelScope.launch {
            moodRepo.createCustomMood(label, colorHex, shape, valence)
        }
    }

    fun updateCustomMood(id: String, label: String, colorHex: Long, shape: MoodShape, valence: Int) {
        viewModelScope.launch {
            moodRepo.updateCustomMood(id, label, colorHex, shape, valence)
        }
    }

    /** 删除 = 归档: 历史记录保留, 选择器不再出现。 */
    fun archiveCustomMood(id: String) {
        viewModelScope.launch {
            moodRepo.archiveCustomMood(id)
        }
    }

    fun markHabit(habitId: String, date: LocalDate, status: Int, currentStatus: Int?) {
        viewModelScope.launch {
            if (currentStatus == status) {
                habitRepo.clearStatus(habitId, date)
            } else {
                habitRepo.setStatus(habitId, date, status)
            }
        }
    }

    fun archiveHabit(habitId: String) {
        viewModelScope.launch {
            habitRepo.archiveHabit(habitId)
        }
    }

    fun renameHabit(habitId: String, name: String) {
        viewModelScope.launch {
            habitRepo.renameHabit(habitId, name)
        }
    }

    fun markBadgeCelebrationShown(token: String) {
        viewModelScope.launch {
            settingsPreferences.markHabitBadgeTokensShown(setOf(token))
        }
    }

    fun markFitnessBadgeCelebrationShown(badgeId: String) {
        viewModelScope.launch {
            settingsPreferences.markFitnessBadgesShown(setOf(badgeId))
        }
    }

    // ──── 贝叶斯预测 ────────────────────────────────────────

    private data class JournalData(
        val moods: List<MoodEntry>,
        val customMoods: List<Mood>,
        val weather: List<WeatherEntry>,
    )

    private data class CycleOverview(
        val prediction: CyclePrediction?,
        val status: DayStatus,
        val periodDay: Int?,
    )

    /** 由经期分段纯函数式推出预测与今日状态 (每次 entries 变化都重新派生)。 */
    private fun cycleOverview(periods: List<CyclePeriod>): CycleOverview {
        if (periods.isEmpty()) return CycleOverview(null, DayStatus.UNKNOWN, null)

        // 每段经期各自取显式标记的起始日, 没标记就用该段第一天——
        // 只给其中一段标过起始日时, 其它段照样参与预测, 不会被整段排除。
        val starts = periods.map { period ->
            period.entries
                .firstOrNull { it.isPeriodStart }
                ?.date
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: period.startDate
        }.sorted()
        val durations = periods.map { it.days }

        val today = LocalDate.now()
        val prediction = BayesianCycleModel.predict(starts, durations, today)

        val lastPeriod = periods.maxByOrNull { it.startDate }
        val currentPeriod = periods.firstOrNull { period ->
            !today.isBefore(period.startDate) && !today.isAfter(period.endDate)
        }
        val periodDay = currentPeriod?.let {
            ChronoUnit.DAYS.between(it.startDate, today).toInt() + 1
        }
        val status = BayesianCycleModel.getDayStatus(
            date = today,
            prediction = prediction,
            lastPeriodStart = lastPeriod?.startDate,
            periodDays = lastPeriod?.days ?: 5,
        )
        return CycleOverview(prediction, status, periodDay)
    }
}
