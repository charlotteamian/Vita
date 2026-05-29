package com.vita.healthtracker.ui.screens.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vita.healthtracker.data.local.entity.BodyBatterySample
import com.vita.healthtracker.data.local.entity.DailyHealthSnapshot
import com.vita.healthtracker.data.local.entity.ExerciseSession
import com.vita.healthtracker.data.local.entity.SleepSession
import com.vita.healthtracker.data.prefs.SettingsPreferences
import com.vita.healthtracker.data.repository.HealthRepository
import com.vita.healthtracker.data.sync.SyncCoordinator
import com.vita.healthtracker.domain.ExerciseClassifier
import com.vita.healthtracker.domain.HomeMetric
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
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(LocalDate.now())

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
            combine(
                dayData,
                syncCoordinator.status,
                repo.observeLastSyncedAt(),
            ) { data, sync, syncedAt ->
                val selectedSleep = data.sleeps
                    .filter { it.totalMinutes > 0 && sleepDisplayDate(it, zone) == date }
                    .maxByOrNull { it.endEpochMs }
                val visibleExercises = data.exercises
                    .filter { ExerciseClassifier.shouldShowInStats(it, zone) }
                    .sortedByDescending { it.startEpochMs }
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
        syncCoordinator.start(days, includeGarmin = true, includeHealthConnect = true)
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
}
