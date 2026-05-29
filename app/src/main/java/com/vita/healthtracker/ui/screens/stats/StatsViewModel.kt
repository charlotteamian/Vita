package com.vita.healthtracker.ui.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vita.healthtracker.data.local.entity.DailyHealthSnapshot
import com.vita.healthtracker.data.local.entity.ExerciseSession
import com.vita.healthtracker.data.local.entity.SleepSession
import com.vita.healthtracker.data.prefs.SettingsPreferences
import com.vita.healthtracker.data.repository.HealthRepository
import com.vita.healthtracker.domain.ExerciseClassifier
import com.vita.healthtracker.ui.components.StatsRange
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.LocalDate
import java.time.ZoneId
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlin.math.roundToInt

data class StatsUiState(
    val range: StatsRange = StatsRange.Week,
    val daily: List<DailyHealthSnapshot> = emptyList(),
    val sleeps: List<SleepSession> = emptyList(),
    val exercises: List<ExerciseSession> = emptyList(),
    val calendarMonth: YearMonth = YearMonth.now(),
    val calendarDays: List<StatsCalendarDay> = emptyList(),
    val historyMonths: List<StatsHistoryMonth> = emptyList(),
    val selectedDay: StatsDayDetail? = null,
    /** X 轴标签列表（与 daily 一一对应） */
    val xLabels: List<String> = emptyList(),
    /** 日视图下拉的「昨日 / 过去三天 / 过去七天」汇总 (#4)。 */
    val dayExtras: List<StatsRangeSummary> = emptyList(),
    /** 「全部」视图的累计总览 (不画图, 只用大数字汇总全部历史)。 */
    val allTimeSummary: AllTimeSummary? = null,
)

data class StatsHistoryMonth(
    val yearMonth: YearMonth,
    val daysTracked: Int,
)

/** 「全部」视图: 整段历史的累计/平均汇总。 */
data class AllTimeSummary(
    val daysTracked: Int,
    val firstDate: LocalDate?,
    val lastDate: LocalDate?,
    val totalSteps: Long,
    val totalDistanceMeters: Double,
    val totalActiveCalories: Double,
    val totalActiveMinutes: Long,
    val totalFloors: Double,
    val exerciseCount: Int,
    val exerciseMinutes: Long,
    val exerciseDistanceMeters: Double,
    val sleepNights: Int,
    val avgSleepMinutes: Long,
    val avgHeartRate: Int?,
    val avgRestingHeartRate: Int?,
) {
    val hasAnyData: Boolean
        get() = daysTracked > 0 || totalSteps > 0 || exerciseCount > 0 || sleepNights > 0
}

/** 日视图里「昨日 / 过去三天 / 过去七天」的一段汇总。 */
data class StatsRangeSummary(
    val label: String,
    val daily: DailyHealthSnapshot,   // 区间内步数/距离/卡路里求和, 心率取均值
    val rangeDays: Int,
    val avgSteps: Long,
    val avgTotalCalories: Int?,
    val avgRestingHeartRate: Int?,
    val avgStress: Int?,
    val sleepNights: Int,
    val avgSleepScore: Int?,
    val sleepAvgMinutes: Long,        // 区间内有睡眠的天的平均睡眠时长
    val exerciseCount: Int,
    val exerciseMinutes: Long,
    val exerciseDistanceMeters: Double,
    val exerciseBreakdowns: List<StatsExerciseSummary>,
)

data class StatsExerciseSummary(
    val category: String,
    val count: Int,
    val minutes: Long,
    val distanceMeters: Double,
)

data class StatsCalendarDay(
    val date: LocalDate,
    val inMonth: Boolean,
    val hasSteps: Boolean = false,
    val hasDistance: Boolean = false,
    val hasCalories: Boolean = false,
    val hasHeart: Boolean = false,
    val hasSleep: Boolean = false,
    val hasExercise: Boolean = false,
) {
    val hasAnyData: Boolean
        get() = hasSteps || hasDistance || hasCalories || hasHeart || hasSleep || hasExercise
}

data class StatsDayDetail(
    val date: LocalDate,
    val daily: DailyHealthSnapshot?,
    val sleeps: List<SleepSession>,
    val exercises: List<ExerciseSession>,
) {
    val hasAnyData: Boolean
        get() = daily?.hasAnyDisplayData() == true || sleeps.any { it.totalMinutes > 0 } || exercises.isNotEmpty()
}

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(
    private val repo: HealthRepository,
    private val prefs: SettingsPreferences,
) : ViewModel() {

    private val _range = MutableStateFlow(StatsRange.Week)
    private val _calendarMonth = MutableStateFlow(YearMonth.now())
    private val _selectedDate = MutableStateFlow(LocalDate.now())

    val state: StateFlow<StatsUiState> =
        combine(_range, prefs.weekStartDay, _calendarMonth, _selectedDate) { range, weekStart, calendarMonth, selectedDate ->
            StatsInputs(range, weekStart, calendarMonth, selectedDate)
        }.flatMapLatest { input ->
        val range = input.range
        val weekStart = input.weekStart
        val calendarMonth = input.calendarMonth
        val selectedDate = input.selectedDate
        val today = LocalDate.now()
        val from = when (range) {
            // 日视图: 多拉最近 7 天, 供「昨日 / 过去三天 / 过去七天」汇总 (#4)。
            StatsRange.Day -> today.minusDays(6)
            // 周视图: 对齐到「一周开始日」偏好(周一/周日), 取本周起始日。
            StatsRange.Week -> startOfWeek(today, weekStart)
            // 月视图要从数据库里最早有数据的月份开始, 所以先拉完整本地历史。
            StatsRange.Month -> LocalDate.of(1900, 1, 1)
            StatsRange.Year -> LocalDate.of(1900, 1, 1)
            // 「全部」: 整段历史, 同样从最早拉起。
            StatsRange.All -> LocalDate.of(1900, 1, 1)
        }
        val zone = ZoneId.systemDefault()
        val fromInstant = from.atStartOfDay(zone).toInstant()
        val sleepFromInstant = from.minusDays(1).atStartOfDay(zone).toInstant()
        val toInstant = today.plusDays(1).atStartOfDay(zone).toInstant()
        val calendarStart = calendarMonth.atDay(1)
        val calendarEnd = calendarMonth.atEndOfMonth()
        val calendarStartInstant = calendarStart.atStartOfDay(zone).toInstant()
        val calendarSleepStartInstant = calendarStart.minusDays(1).atStartOfDay(zone).toInstant()
        val calendarEndInstant = calendarEnd.plusDays(1).atStartOfDay(zone).toInstant()

        val chartFlow = combine(
            repo.dailyRange(from, today),
            repo.sleepRange(sleepFromInstant, toInstant),
            repo.exerciseRange(fromInstant, toInstant),
        ) { daily, sleeps, exercises -> ChartSource(daily, sleeps, exercises) }

        val calendarFlow = combine(
            repo.dailyRange(calendarStart, calendarEnd),
            repo.sleepRange(calendarSleepStartInstant, calendarEndInstant),
            repo.exerciseRange(calendarStartInstant, calendarEndInstant),
        ) { daily, sleeps, exercises -> CalendarSource(daily, sleeps, exercises) }

        val historyFlow = combine(
            repo.dailyRange(LocalDate.of(1900, 1, 1), today),
            repo.sleepRange(LocalDate.of(1900, 1, 1).atStartOfDay(zone).toInstant(), toInstant),
            repo.exerciseRange(LocalDate.of(1900, 1, 1).atStartOfDay(zone).toInstant(), toInstant),
        ) { daily, sleeps, exercises -> ChartSource(daily, sleeps, exercises) }

        combine(chartFlow, calendarFlow, historyFlow) { chart, calendar, history ->
            val daily = chart.daily
            val sleeps = chart.sleeps
            val exercises = chart.exercises.filter { ExerciseClassifier.shouldShowInStats(it, zone) }
            val historyExercises = history.exercises.filter { ExerciseClassifier.shouldShowInStats(it, zone) }
            val historyMonths = buildHistoryMonths(history.daily, history.sleeps, historyExercises, zone)

            // 「全部」视图不画图, 直接汇总整段历史返回 (不用走下面的网格/图表逻辑)。
            if (range == StatsRange.All) {
                return@combine StatsUiState(
                    range = range,
                    daily = emptyList(),
                    sleeps = emptyList(),
                    exercises = exercises.sortedByDescending { it.startEpochMs },
                    calendarMonth = calendarMonth,
                    calendarDays = buildCalendarDays(calendarMonth, calendar, zone),
                    historyMonths = historyMonths,
                    selectedDay = buildDayDetail(selectedDate, calendar, zone),
                    xLabels = emptyList(),
                    dayExtras = emptyList(),
                    allTimeSummary = buildAllTimeSummary(daily, sleeps, exercises, zone),
                )
            }

            // Generate full date grid based on range
            val dateGrid = mutableListOf<LocalDate>()
            
            when (range) {
                StatsRange.All -> Unit // 已在上方提前返回, 这里不会执行。
                StatsRange.Day -> dateGrid.add(today)
                StatsRange.Week -> {
                    for (i in 0..6) dateGrid.add(from.plusDays(i.toLong()))
                }
                StatsRange.Month -> {
                    val monthsWithData = mutableSetOf<LocalDate>()
                    daily.filter { it.hasChartableData() }.forEach {
                        runCatching { monthsWithData.add(LocalDate.parse(it.date).withDayOfMonth(1)) }
                    }
                    sleeps.filter { it.totalMinutes > 0 }.forEach {
                        runCatching { sleepDisplayDate(it, zone)?.withDayOfMonth(1)?.let(monthsWithData::add) }
                    }
                    exercises.forEach {
                        runCatching { monthsWithData.add(Instant.ofEpochMilli(it.startEpochMs).atZone(zone).toLocalDate().withDayOfMonth(1)) }
                    }

                    dateGrid.addAll(monthsWithData.sorted())
                }
                StatsRange.Year -> {
                    val yearsWithData = mutableSetOf<LocalDate>()
                    daily.filter { it.hasChartableData() }.forEach { runCatching { yearsWithData.add(LocalDate.parse(it.date).withDayOfYear(1)) } }
                    sleeps.filter { it.totalMinutes > 0 }.forEach { runCatching { sleepDisplayDate(it, zone)?.withDayOfYear(1)?.let(yearsWithData::add) } }
                    exercises.forEach { runCatching { yearsWithData.add(Instant.ofEpochMilli(it.startEpochMs).atZone(zone).toLocalDate().withDayOfYear(1)) } }
                    
                    val sortedYears = yearsWithData.filter { !it.isBefore(from) }.sorted()
                    if (sortedYears.isEmpty()) {
                        dateGrid.add(today.withDayOfYear(1))
                    } else {
                        dateGrid.addAll(sortedYears)
                    }
                }
            }

            // Map DB items to grid
            val aggDaily = dateGrid.map { gridDate ->
                val items = when (range) {
                    StatsRange.Day, StatsRange.Week, StatsRange.All -> daily.filter { it.date == gridDate.toString() }
                    StatsRange.Month -> daily.filter { runCatching { LocalDate.parse(it.date).withDayOfMonth(1) }.getOrNull() == gridDate }
                    StatsRange.Year -> daily.filter { runCatching { LocalDate.parse(it.date).withDayOfYear(1) }.getOrNull() == gridDate }
                }
                val exerciseItems = exerciseItemsForGrid(range, gridDate, exercises, zone)
                val base = if (items.isNotEmpty()) aggregateDaily(range, gridDate, items)
                else DailyHealthSnapshot(gridDate.toString(), steps = 0)
                base.withExerciseFallback(exerciseItems)
            }

            val aggSleeps = dateGrid.map { gridDate ->
                val items = when (range) {
                    StatsRange.Day, StatsRange.Week, StatsRange.All -> sleeps.filter { sleepDisplayDate(it, zone) == gridDate }
                    StatsRange.Month -> sleeps.filter { sleepDisplayDate(it, zone)?.withDayOfMonth(1) == gridDate }
                    StatsRange.Year -> sleeps.filter { sleepDisplayDate(it, zone)?.withDayOfYear(1) == gridDate }
                }
                if (items.isNotEmpty()) aggregateSleep(gridDate, items, zone)
                else SleepSession(
                    id = gridDate.toString(),
                    startEpochMs = gridDate.atStartOfDay(zone).toInstant().toEpochMilli(),
                    endEpochMs = gridDate.atStartOfDay(zone).toInstant().toEpochMilli(),
                    totalMinutes = 0,
                    deepMinutes = 0,
                    lightMinutes = 0,
                    remMinutes = 0,
                    awakeMinutes = 0,
                    sleepScore = null,
                    source = "Empty",
                )
            }

            // 生成 X 轴标签
            val xLabels = dateGrid.mapIndexed { index, localDate ->
                when (range) {
                    StatsRange.Day, StatsRange.All -> ""
                    StatsRange.Week -> localDate.dayOfWeek.let {
                        when (it) {
                            java.time.DayOfWeek.MONDAY -> "一"
                            java.time.DayOfWeek.TUESDAY -> "二"
                            java.time.DayOfWeek.WEDNESDAY -> "三"
                            java.time.DayOfWeek.THURSDAY -> "四"
                            java.time.DayOfWeek.FRIDAY -> "五"
                            java.time.DayOfWeek.SATURDAY -> "六"
                            java.time.DayOfWeek.SUNDAY -> "日"
                        }
                    }
                    StatsRange.Month -> {
                        "${localDate.year}年${localDate.monthValue}月"
                    }
                    StatsRange.Year -> "${localDate.year}"
                }
            }

            val dayExtras = if (range == StatsRange.Day) {
                listOf(
                    buildRangeSummary("昨日", today.minusDays(1), today.minusDays(1), daily, sleeps, exercises, zone),
                    buildRangeSummary("过去三天", today.minusDays(2), today, daily, sleeps, exercises, zone),
                    buildRangeSummary("过去7天", today.minusDays(6), today, daily, sleeps, exercises, zone),
                )
            } else emptyList()

            StatsUiState(
                range = range,
                daily = aggDaily,
                sleeps = aggSleeps,
                exercises = exercises.sortedByDescending { it.startEpochMs },
                calendarMonth = calendarMonth,
                calendarDays = buildCalendarDays(calendarMonth, calendar, zone),
                historyMonths = historyMonths,
                selectedDay = buildDayDetail(selectedDate, calendar, zone),
                xLabels = xLabels,
                dayExtras = dayExtras,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    private fun aggregateDaily(range: StatsRange, date: LocalDate, items: List<DailyHealthSnapshot>): DailyHealthSnapshot {
        val size = items.size.coerceAtLeast(1)
        val hrs = items.mapNotNull { it.avgHeartRate }.takeIf { it.isNotEmpty() }
        val sumTotals = range == StatsRange.Month || range == StatsRange.Year
        fun totalLong(value: (DailyHealthSnapshot) -> Long?): Long =
            items.sumOf { value(it) ?: 0L }.let { if (sumTotals) it else it / size }
        fun totalDouble(value: (DailyHealthSnapshot) -> Double?): Double? {
            val sum = items.sumOf { value(it) ?: 0.0 }
            val aggregated = if (sumTotals) sum else sum / size
            return aggregated.takeIf { it > 0.0 }
        }
        return DailyHealthSnapshot(
            date = date.toString(),
            steps = totalLong { it.steps },
            distanceMeters = totalDouble { it.distanceMeters },
            activeCalories = totalDouble { it.activeCalories },
            avgHeartRate = hrs?.average()?.roundToInt(),
            totalCalories = totalDouble { it.totalCalories },
            activeMinutes = totalLong { it.activeMinutes }.takeIf { it > 0L },
            floorsClimbed = totalDouble { it.floorsClimbed },
            restingHeartRate = items.mapNotNull { it.restingHeartRate }.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
            updatedAtEpochMs = items.maxOfOrNull { it.updatedAtEpochMs } ?: System.currentTimeMillis()
        )
    }

    private fun aggregateSleep(date: LocalDate, items: List<SleepSession>, zone: ZoneId): SleepSession {
        val size = items.size.coerceAtLeast(1)
        val ms = date.atStartOfDay(zone).toInstant().toEpochMilli()
        return SleepSession(
            id = date.toString(),
            startEpochMs = ms,
            endEpochMs = ms,
            totalMinutes = items.sumOf { it.totalMinutes } / size,
            deepMinutes = items.sumOf { it.deepMinutes } / size,
            lightMinutes = items.sumOf { it.lightMinutes } / size,
            remMinutes = items.sumOf { it.remMinutes } / size,
            awakeMinutes = items.sumOf { it.awakeMinutes } / size,
            source = items.firstOrNull()?.source ?: "Aggregated"
        )
    }

    /** 返回 today 当周的起始日: 距离 today 最近的、在其之前(含当天)的 weekStart 那天。 */
    private fun startOfWeek(today: LocalDate, weekStart: DayOfWeek): LocalDate {
        val diff = (today.dayOfWeek.value - weekStart.value + 7) % 7
        return today.minusDays(diff.toLong())
    }

    private fun sleepDisplayDate(sleep: SleepSession, zone: ZoneId): LocalDate? =
        runCatching {
            val end = Instant.ofEpochMilli(sleep.endEpochMs).atZone(zone).toLocalDate()
            val start = Instant.ofEpochMilli(sleep.startEpochMs).atZone(zone).toLocalDate()
            if (sleep.endEpochMs > sleep.startEpochMs) end else start
        }.getOrNull()

    /** 汇总一段日期范围 (闭区间) 供日视图下拉展示。 */
    private fun buildRangeSummary(
        label: String,
        fromDate: LocalDate,
        toDate: LocalDate,
        daily: List<DailyHealthSnapshot>,
        sleeps: List<SleepSession>,
        exercises: List<ExerciseSession>,
        zone: ZoneId,
    ): StatsRangeSummary {
        fun LocalDate.inRange() = !isBefore(fromDate) && !isAfter(toDate)
        val dItems = daily.filter { runCatching { LocalDate.parse(it.date) }.getOrNull()?.inRange() == true }
        val rangeDays = (ChronoUnit.DAYS.between(fromDate, toDate).toInt() + 1).coerceAtLeast(1)
        // 复用 Month 聚合: 步数/距离/卡路里求和, 心率取均值。
        val agg = aggregateDaily(StatsRange.Month, toDate, dItems)
        val sItems = sleeps.filter {
            it.totalMinutes > 0 &&
                sleepDisplayDate(it, zone)?.inRange() == true
        }
        val sleepAvg = if (sItems.isEmpty()) 0L else sItems.sumOf { it.totalMinutes } / sItems.size
        val eItems = exercises.filter {
            runCatching { Instant.ofEpochMilli(it.startEpochMs).atZone(zone).toLocalDate() }.getOrNull()?.inRange() == true
        }
        val breakdowns = eItems
            .groupBy { ExerciseClassifier.displayCategory(it) }
            .map { (category, items) ->
                StatsExerciseSummary(
                    category = category,
                    count = items.size,
                    minutes = items.sumOf { it.durationMinutes },
                    distanceMeters = items.sumOf { it.distanceMeters ?: 0.0 },
                )
            }
            .sortedBy { categoryPriority(it.category) }
        return StatsRangeSummary(
            label = label,
            daily = agg.withExerciseFallback(eItems),
            rangeDays = rangeDays,
            avgSteps = dItems.sumOf { it.steps } / rangeDays,
            avgTotalCalories = dItems.mapNotNull { it.totalCalories?.takeIf { value -> value > 0.0 } }
                .takeIf { it.isNotEmpty() }
                ?.average()
                ?.roundToInt(),
            avgRestingHeartRate = dItems.mapNotNull { it.restingHeartRate?.takeIf { value -> value > 0 } }
                .takeIf { it.isNotEmpty() }
                ?.average()
                ?.roundToInt(),
            avgStress = dItems.mapNotNull { it.avgStress?.takeIf { value -> value > 0 } }
                .takeIf { it.isNotEmpty() }
                ?.average()
                ?.roundToInt(),
            sleepNights = sItems.size,
            avgSleepScore = sItems.mapNotNull { it.sleepScore?.takeIf { value -> value > 0 } }
                .takeIf { it.isNotEmpty() }
                ?.average()
                ?.roundToInt(),
            sleepAvgMinutes = sleepAvg,
            exerciseCount = eItems.size,
            exerciseMinutes = eItems.sumOf { it.durationMinutes },
            exerciseDistanceMeters = eItems.sumOf { it.distanceMeters ?: 0.0 },
            exerciseBreakdowns = breakdowns,
        )
    }

    private fun categoryPriority(category: String): Int = when (ExerciseClassifier.displayName(category)) {
        "步行" -> 0
        "强度健身" -> 1
        "跑步" -> 2
        "骑行" -> 3
        "力量训练" -> 4
        "游泳" -> 5
        "瑜伽/拉伸" -> 6
        else -> 9
    }

    /** 汇总「全部」视图: 整段历史的累计步数/距离/卡路里、运动、睡眠与平均心率。 */
    private fun buildAllTimeSummary(
        daily: List<DailyHealthSnapshot>,
        sleeps: List<SleepSession>,
        exercises: List<ExerciseSession>,
        zone: ZoneId,
    ): AllTimeSummary {
        val realDaily = daily.filter { it.hasChartableData() }
        val realSleeps = sleeps.filter { it.totalMinutes > 0 }

        // 覆盖到的有效数据日期 (步数/活动/睡眠/运动任一)。
        val dataDates = buildSet {
            realDaily.forEach { runCatching { LocalDate.parse(it.date) }.getOrNull()?.let(::add) }
            realSleeps.forEach { sleepDisplayDate(it, zone)?.let(::add) }
            exercises.forEach {
                runCatching { Instant.ofEpochMilli(it.startEpochMs).atZone(zone).toLocalDate() }.getOrNull()?.let(::add)
            }
        }

        val hrValues = daily.mapNotNull { it.avgHeartRate?.takeIf { hr -> hr > 0 } }
        val restingValues = daily.mapNotNull { it.restingHeartRate?.takeIf { hr -> hr > 0 } }

        return AllTimeSummary(
            daysTracked = dataDates.size,
            firstDate = dataDates.minOrNull(),
            lastDate = dataDates.maxOrNull(),
            totalSteps = daily.sumOf { it.steps },
            totalDistanceMeters = daily.sumOf { it.distanceMeters ?: 0.0 },
            totalActiveCalories = daily.sumOf { it.activeCalories ?: 0.0 },
            totalActiveMinutes = daily.sumOf { it.activeMinutes ?: 0L },
            totalFloors = daily.sumOf { it.floorsClimbed ?: 0.0 },
            exerciseCount = exercises.size,
            exerciseMinutes = exercises.sumOf { it.durationMinutes },
            exerciseDistanceMeters = exercises.sumOf { it.distanceMeters ?: 0.0 },
            sleepNights = realSleeps.size,
            avgSleepMinutes = if (realSleeps.isEmpty()) 0L else realSleeps.sumOf { it.totalMinutes } / realSleeps.size,
            avgHeartRate = hrValues.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
            avgRestingHeartRate = restingValues.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
        )
    }

    private fun exerciseItemsForGrid(
        range: StatsRange,
        gridDate: LocalDate,
        exercises: List<ExerciseSession>,
        zone: ZoneId,
    ): List<ExerciseSession> =
        exercises.filter {
            val date = runCatching { Instant.ofEpochMilli(it.startEpochMs).atZone(zone).toLocalDate() }.getOrNull()
                ?: return@filter false
            when (range) {
                StatsRange.Day, StatsRange.Week, StatsRange.All -> date == gridDate
                StatsRange.Month -> date.withDayOfMonth(1) == gridDate
                StatsRange.Year -> date.withDayOfYear(1) == gridDate
            }
        }

    private fun DailyHealthSnapshot.withExerciseFallback(exercises: List<ExerciseSession>): DailyHealthSnapshot {
        if (exercises.isEmpty()) return this
        val exerciseSteps = exercises.sumOf { it.steps ?: 0L }
        val exerciseDistance = exercises.sumOf { it.distanceMeters ?: 0.0 }
        val exerciseCalories = exercises.sumOf { it.activeCalories ?: 0.0 }
        val exerciseMinutes = exercises.sumOf { it.durationMinutes }
        val exerciseHr = exercises.mapNotNull { it.avgHeartRate }.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
        return copy(
            steps = steps.takeIf { it > 0L } ?: exerciseSteps,
            distanceMeters = distanceMeters?.takeIf { it > 0.0 } ?: exerciseDistance.takeIf { it > 0.0 },
            activeCalories = activeCalories?.takeIf { it > 0.0 } ?: exerciseCalories.takeIf { it > 0.0 },
            activeMinutes = activeMinutes?.takeIf { it > 0L } ?: exerciseMinutes.takeIf { it > 0L },
            avgHeartRate = avgHeartRate?.takeIf { it > 0 } ?: exerciseHr,
        )
    }

    /**
     * 是否有「可上图」的真实数据。
     * 故意**排除** totalCalories(总消耗) —— 没戴表那天佳明返回的恒定基础代谢若算进来,
     * 月/年视图会被拉回很多年前(每年/每月都"有数据"), 见 #2。必须有真实活动或生理信号。
     */
    private fun DailyHealthSnapshot.hasChartableData(): Boolean =
        steps > 0 ||
            (distanceMeters ?: 0.0) > 0.0 ||
            (activeCalories ?: 0.0) > 0.0 ||
            (activeMinutes ?: 0L) > 0L ||
            (floorsClimbed ?: 0.0) > 0.0 ||
            (avgHeartRate ?: 0) > 0 ||
            (restingHeartRate ?: 0) > 0 ||
            (avgStress ?: 0) > 0 ||
            (maxStress ?: 0) > 0 ||
            (avgSpo2 ?: 0) > 0 ||
            (avgRespiration ?: 0.0) > 0.0 ||
            (hrv ?: 0) > 0 ||
            (weightKg ?: 0.0) > 0.0

    private fun buildCalendarDays(month: YearMonth, source: CalendarSource, zone: ZoneId): List<StatsCalendarDay> {
        val first = month.atDay(1)
        val gridStart = first.minusDays(((first.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7).toLong())
        return (0 until 42).map { offset ->
            val date = gridStart.plusDays(offset.toLong())
            val daily = source.daily.firstOrNull { it.date == date.toString() }
            val daySleeps = source.sleeps.filter { sleepDisplayDate(it, zone) == date }
            val dayExercises = source.exercises.filter {
                ExerciseClassifier.shouldShowInStats(it, zone) &&
                    runCatching { Instant.ofEpochMilli(it.startEpochMs).atZone(zone).toLocalDate() }.getOrNull() == date
            }
            StatsCalendarDay(
                date = date,
                inMonth = YearMonth.from(date) == month,
                hasSteps = (daily?.steps ?: 0L) > 0L,
                hasDistance = (daily?.distanceMeters ?: 0.0) > 0.0,
                hasCalories = (daily?.activeCalories ?: 0.0) > 0.0,
                hasHeart = (daily?.avgHeartRate ?: 0) > 0 || (daily?.restingHeartRate ?: 0) > 0,
                hasSleep = daySleeps.any { it.totalMinutes > 0 },
                hasExercise = dayExercises.isNotEmpty(),
            )
        }
    }

    private fun buildDayDetail(date: LocalDate, source: CalendarSource, zone: ZoneId): StatsDayDetail {
        val sleeps = source.sleeps.filter {
            sleepDisplayDate(it, zone) == date
        }
        val exercises = source.exercises.filter {
            ExerciseClassifier.shouldShowInStats(it, zone) &&
                runCatching { Instant.ofEpochMilli(it.startEpochMs).atZone(zone).toLocalDate() }.getOrNull() == date
        }
        return StatsDayDetail(
            date = date,
            daily = source.daily.firstOrNull { it.date == date.toString() },
            sleeps = sleeps,
            exercises = exercises,
        )
    }

    fun setRange(range: StatsRange) { _range.value = range }

    fun previousCalendarMonth() {
        _calendarMonth.value = _calendarMonth.value.minusMonths(1)
        _selectedDate.value = _calendarMonth.value.atDay(1)
    }

    fun nextCalendarMonth() {
        val next = _calendarMonth.value.plusMonths(1)
        if (!next.isAfter(YearMonth.now())) {
            _calendarMonth.value = next
            _selectedDate.value = next.atDay(1)
        }
    }

    fun selectCalendarDay(date: LocalDate) {
        _selectedDate.value = date
        _calendarMonth.value = YearMonth.from(date)
    }

    fun jumpToHistoryMonth(month: YearMonth) {
        if (month.isAfter(YearMonth.now())) return
        _calendarMonth.value = month
        _selectedDate.value = month.atDay(1)
    }

    /** 日历回到本月并选中今天 (#6)。 */
    fun jumpToToday() {
        val today = LocalDate.now()
        _calendarMonth.value = YearMonth.from(today)
        _selectedDate.value = today
    }

    private data class StatsInputs(
        val range: StatsRange,
        val weekStart: DayOfWeek,
        val calendarMonth: YearMonth,
        val selectedDate: LocalDate,
    )

    private data class ChartSource(
        val daily: List<DailyHealthSnapshot>,
        val sleeps: List<SleepSession>,
        val exercises: List<ExerciseSession>,
    )

    private data class CalendarSource(
        val daily: List<DailyHealthSnapshot>,
        val sleeps: List<SleepSession>,
        val exercises: List<ExerciseSession>,
    )

    private fun buildHistoryMonths(
        daily: List<DailyHealthSnapshot>,
        sleeps: List<SleepSession>,
        exercises: List<ExerciseSession>,
        zone: ZoneId,
    ): List<StatsHistoryMonth> {
        val datesByMonth = linkedMapOf<YearMonth, MutableSet<LocalDate>>()
        fun add(date: LocalDate) {
            datesByMonth.getOrPut(YearMonth.from(date)) { mutableSetOf() }.add(date)
        }
        daily.filter { it.hasChartableData() }.forEach {
            runCatching { LocalDate.parse(it.date) }.getOrNull()?.let(::add)
        }
        sleeps.filter { it.totalMinutes > 0 }.forEach {
            sleepDisplayDate(it, zone)?.let(::add)
        }
        exercises.forEach {
            runCatching {
                Instant.ofEpochMilli(it.startEpochMs).atZone(zone).toLocalDate()
            }.getOrNull()?.let(::add)
        }
        return datesByMonth
            .map { (month, dates) -> StatsHistoryMonth(month, dates.size) }
            .sortedWith(compareByDescending<StatsHistoryMonth> { it.yearMonth.year }.thenByDescending { it.yearMonth.monthValue })
    }
}

private fun DailyHealthSnapshot.hasAnyDisplayData(): Boolean =
    steps > 0 ||
        (distanceMeters ?: 0.0) > 0.0 ||
        (activeCalories ?: 0.0) > 0.0 ||
        (activeMinutes ?: 0L) > 0L ||
        (floorsClimbed ?: 0.0) > 0.0 ||
        (avgHeartRate ?: 0) > 0 ||
        (restingHeartRate ?: 0) > 0 ||
        (avgStress ?: 0) > 0 ||
        (maxStress ?: 0) > 0 ||
        (avgSpo2 ?: 0) > 0 ||
        (avgRespiration ?: 0.0) > 0.0 ||
        (hrv ?: 0) > 0 ||
        (weightKg ?: 0.0) > 0.0
