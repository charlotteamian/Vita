package com.vita.healthtracker.ui.screens.life

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vita.healthtracker.data.local.entity.HabitCheckIn
import com.vita.healthtracker.data.local.entity.HabitDefinition
import com.vita.healthtracker.data.local.entity.CycleEntry
import com.vita.healthtracker.data.local.entity.DailyHealthSnapshot
import com.vita.healthtracker.data.local.entity.ExerciseSession
import com.vita.healthtracker.data.local.entity.MoodEntry
import com.vita.healthtracker.data.local.entity.SleepSession
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
import com.vita.healthtracker.domain.PredictionConfidence
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.MutableStateFlow
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
    val symptomOptions: List<String> = emptyList(),
    val recentSleeps: List<SleepSession> = emptyList(),
    val moods: Map<String, MoodEntry> = emptyMap(),
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

    private val _prediction = MutableStateFlow<CyclePrediction?>(null)
    private val _todayStatus = MutableStateFlow(DayStatus.UNKNOWN)
    private val _todayPeriodDay = MutableStateFlow<Int?>(null)

    val state: StateFlow<LifeUiState> = run {
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val sleepFrom = LocalDate.now().minusDays(14).atStartOfDay(zone).toInstant()

        val journal = combine(
            moodRepo.observeRange(today.minusDays(370), today),
            weatherRepo.observeRange(today.minusDays(370), today),
        ) { moods, weather -> JournalData(moods, weather) }
        val cycleToday = combine(
            _todayStatus,
            _todayPeriodDay,
        ) { status, periodDay -> CycleTodayData(status, periodDay) }
        val base = combine(
            cycleRepo.observeAll(),
            _prediction,
            cycleToday,
            healthRepo.sleepRange(sleepFrom, now),
            journal,
        ) { entries, prediction, cycleTodayData, sleeps, journalData ->
            val periods = CycleLogLogic.groupIntoPeriods(entries)
            LifeUiState(
                entries = entries,
                cyclePeriods = periods,
                spottingEntries = CycleLogLogic.spottingEntries(entries),
                symptomOptions = CycleLogLogic.symptomOptions(entries),
                recentSleeps = sleeps.sortedByDescending { it.startEpochMs },
                moods = journalData.moods.associateBy { it.date },
                weather = journalData.weather.associateBy { it.date },
                prediction = prediction,
                todayStatus = cycleTodayData.status,
                todayPeriodDay = cycleTodayData.periodDay,
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
        viewModelScope.launch {
            cycleRepo.observeAll().collect { entries ->
                refreshPredictionFromEntries(entries)
            }
        }
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
            refreshPrediction()
        }
    }

    /** 删除一整段经期（传入该段内所有日期） */
    fun deleteCyclePeriod(period: CyclePeriod) {
        viewModelScope.launch {
            period.entries.forEach { cycleRepo.delete(it.date) }
            refreshPrediction()
        }
    }

    fun deleteCycle(date: String) {
        viewModelScope.launch {
            cycleRepo.delete(date)
            refreshPrediction()
        }
    }

    fun createHabit(name: String) {
        viewModelScope.launch {
            habitRepo.createHabit(name)
        }
    }

    fun setMood(date: LocalDate, moodId: String, note: String = "") {
        viewModelScope.launch {
            moodRepo.setMood(date, moodId, note)
        }
    }

    fun clearMood(date: LocalDate) {
        viewModelScope.launch {
            moodRepo.clearMood(date)
        }
    }

    fun setWeather(date: LocalDate, weatherId: String) {
        viewModelScope.launch {
            weatherRepo.setWeather(date, weatherId)
        }
    }

    fun clearWeather(date: LocalDate) {
        viewModelScope.launch {
            weatherRepo.clearWeather(date)
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

    private fun refreshPrediction() {
        viewModelScope.launch {
            refreshPredictionFromEntries(cycleRepo.getAll())
        }
    }

    private data class JournalData(
        val moods: List<MoodEntry>,
        val weather: List<WeatherEntry>,
    )

    private data class CycleTodayData(
        val status: DayStatus,
        val periodDay: Int?,
    )

    private fun refreshPredictionFromEntries(allEntries: List<CycleEntry>) {
        val periods = CycleLogLogic.groupIntoPeriods(allEntries)

        if (periods.isEmpty()) {
            _prediction.value = null
            _todayStatus.value = DayStatus.UNKNOWN
            _todayPeriodDay.value = null
            return
        }

        val hasExplicitStarts = periods.any { period -> period.entries.any { it.isPeriodStart } }
        val predictionPeriods = if (hasExplicitStarts) {
            periods.filter { period -> period.entries.any { it.isPeriodStart } }
        } else {
            periods
        }

        val starts = predictionPeriods.mapNotNull { period ->
            if (hasExplicitStarts) {
                period.entries
                    .firstOrNull { it.isPeriodStart }
                    ?.date
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            } else {
                period.startDate
            }
        }.sorted()
        val durations = predictionPeriods.map { it.days }

        val prediction = BayesianCycleModel.predict(starts, durations)
        _prediction.value = prediction

        val today = LocalDate.now()
        val lastPeriod = periods.maxByOrNull { it.startDate }
        val currentPeriod = periods.firstOrNull { period ->
            !today.isBefore(period.startDate) && !today.isAfter(period.endDate)
        }
        _todayPeriodDay.value = currentPeriod?.let {
            ChronoUnit.DAYS.between(it.startDate, today).toInt() + 1
        }
        _todayStatus.value = BayesianCycleModel.getDayStatus(
            date = today,
            prediction = prediction,
            lastPeriodStart = lastPeriod?.startDate,
            periodDays = lastPeriod?.days ?: 5,
        )
    }
}
