package com.vita.healthtracker.ui.screens.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Today
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.shape.CorneredShape
import com.vita.healthtracker.data.local.entity.ExerciseSession
import com.vita.healthtracker.domain.ExerciseClassifier
import com.vita.healthtracker.domain.healthSourceLabel
import com.vita.healthtracker.ui.components.RangeSelector
import com.vita.healthtracker.ui.components.StatsRange
import com.vita.healthtracker.ui.theme.VitaActive
import com.vita.healthtracker.ui.theme.VitaGradients
import com.vita.healthtracker.ui.theme.VitaHeart
import com.vita.healthtracker.ui.theme.VitaOnSurfaceMuted
import com.vita.healthtracker.ui.theme.VitaPrimary
import com.vita.healthtracker.ui.theme.VitaTertiary
import com.vita.healthtracker.ui.vitaViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
fun StatsScreen(navController: NavController) {
    val vm = vitaViewModel<StatsViewModel>()
    val state by vm.state.collectAsStateWithLifecycle()

    // 关键: 按 range 重新创建 producer。Vico 2.0.0-beta.3 在切换区间(列数变化, 如周7根→月12根→年5根)
    // 时会对新旧模型做差值动画, 列数不一致会在主线程绘制时崩溃(月/年闪退)。
    // 每次切换都给一个全新的空 producer, 走 空→N 这条安全路径即可避免。
    val stepsProducer = remember(state.range) { CartesianChartModelProducer() }
    val sleepProducer = remember(state.range) { CartesianChartModelProducer() }
    val hrProducer = remember(state.range) { CartesianChartModelProducer() }

    // 深色背景下, Vico 默认坐标轴文字是深色看不清; 统一换成浅色标签。
    val axisLabel = rememberAxisLabelComponent(color = VitaOnSurfaceMuted)

    // 预计算 xLabels（直接从 state 中取）
    val xLabels = state.xLabels
    val stepSeries = remember(state.daily, xLabels) {
        state.daily.zip(xLabels).mapNotNull { (day, label) ->
            day.steps.takeIf { it > 0L }?.let { label to it.toDouble() }
        }
    }
    val sleepSeries = remember(state.sleeps, xLabels) {
        state.sleeps.zip(xLabels).mapNotNull { (sleep, label) ->
            (sleep.totalMinutes.toDouble() / 60.0).takeIf { it > 0.0 }?.let { label to it }
        }
    }
    val hrSeries = remember(state.daily, xLabels) {
        state.daily.zip(xLabels).mapNotNull { (day, label) ->
            day.avgHeartRate?.takeIf { it > 0 }?.let { label to it.toDouble() }
        }
    }
    fun formatterFor(labels: List<String>) = CartesianValueFormatter { _, value, _ ->
        labels.getOrNull(value.toInt()).orEmpty()
    }
    val stepAxisFormatter = remember(stepSeries) { formatterFor(stepSeries.map { it.first }) }
    val sleepAxisFormatter = remember(sleepSeries) { formatterFor(sleepSeries.map { it.first }) }
    val hrAxisFormatter = remember(hrSeries) { formatterFor(hrSeries.map { it.first }) }
    val initialChartScroll = if (state.range == StatsRange.Month) {
        com.patrykandpatrick.vico.core.cartesian.Scroll.Absolute.Start
    } else {
        com.patrykandpatrick.vico.core.cartesian.Scroll.Absolute.End
    }
    val chartHorizontalSpacing = when (state.range) {
        StatsRange.Month -> 30.dp
        StatsRange.Year -> 34.dp
        else -> 12.dp
    }

    LaunchedEffect(stepSeries, sleepSeries, hrSeries, state.range) {
        if (state.range != StatsRange.Day) {
            withContext(Dispatchers.Default) {
                try {
                    val stepValues = stepSeries.map { it.second }
                    if (stepValues.any { it > 0 }) {
                        stepsProducer.runTransaction {
                            columnSeries { series(stepValues) }
                        }
                    }
                } catch (_: Exception) {}
                try {
                    val hrValues = hrSeries.map { it.second }
                    if (hrValues.any { it > 0 }) {
                        hrProducer.runTransaction {
                            lineSeries { series(hrValues) }
                        }
                    }
                } catch (_: Exception) {}
            }
        }
        if (state.range != StatsRange.Day) {
            withContext(Dispatchers.Default) {
                try {
                    val sleepValues = sleepSeries.map { it.second }
                    if (sleepValues.any { it > 0 }) {
                        sleepProducer.runTransaction {
                            columnSeries { series(sleepValues) }
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StatsCalendarCard(
            state = state,
            onPreviousMonth = vm::previousCalendarMonth,
            onNextMonth = vm::nextCalendarMonth,
            onSelectDay = vm::selectCalendarDay,
            onJumpToToday = vm::jumpToToday,
            onJumpToMonth = vm::jumpToHistoryMonth,
            onOpenGarminData = { navController.navigate("garmin_day/$it") },
        )

        RangeSelector(current = state.range, onSelect = vm::setRange)

        if (state.range == StatsRange.Day) {
            // ─── 日统计: 今日汇总 + 下拉「昨日 / 过去三天 / 过去七天」(#4) ───
            DailySummaryCard(state)
            state.dayExtras.forEach { summary ->
                RangeSummaryCard(summary)
            }
        } else if (state.range == StatsRange.All) {
            // ─── 全部: 整段历史累计总览卡片 (不画图) ───
            state.allTimeSummary?.let { AllTimeSummaryCard(it) }
        } else key(state.range) {
            // ─── 周/月/年统计: 图表 ───
            // 用 key(range) 包住整个图表子树, 切换区间时整体重建, 杜绝跨区间的图表状态残留。
            ChartCard(title = "步数", accentColor = VitaPrimary) {
                val hasSteps = stepSeries.isNotEmpty()
                if (hasSteps) {
                    if (stepSeries.size <= 1) {
                        SinglePeriodMetric(
                            label = stepSeries.first().first,
                            value = "%,.0f".format(stepSeries.first().second),
                            unit = "步",
                            accentColor = VitaPrimary,
                        )
                    } else {
                        CartesianChartHost(
                            chart = rememberCartesianChart(
                                rememberVitaColumnLayer(VitaPrimary, state.range, chartHorizontalSpacing),
                                startAxis = VerticalAxis.rememberStart(label = axisLabel),
                                bottomAxis = HorizontalAxis.rememberBottom(
                                    label = axisLabel,
                                    valueFormatter = stepAxisFormatter,
                                    itemPlacer = com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis.ItemPlacer.aligned(spacing = 1)
                                ),
                            ),
                            modelProducer = stepsProducer,
                            scrollState = com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState(initialScroll = initialChartScroll),
                            modifier = Modifier.fillMaxWidth().height(220.dp),
                        )
                    }
                } else {
                    Text("暂无数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            ChartCard(title = "睡眠时长", accentColor = VitaTertiary) {
                val hasSleep = sleepSeries.isNotEmpty()
                if (hasSleep) {
                    if (sleepSeries.size <= 1) {
                        SinglePeriodMetric(
                            label = sleepSeries.first().first,
                            value = "%.1f".format(sleepSeries.first().second),
                            unit = "小时",
                            accentColor = VitaTertiary,
                        )
                    } else {
                        CartesianChartHost(
                            chart = rememberCartesianChart(
                                rememberVitaColumnLayer(VitaTertiary, state.range, chartHorizontalSpacing),
                                startAxis = VerticalAxis.rememberStart(label = axisLabel),
                                bottomAxis = HorizontalAxis.rememberBottom(
                                    label = axisLabel,
                                    valueFormatter = sleepAxisFormatter,
                                    itemPlacer = com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis.ItemPlacer.aligned(spacing = 1)
                                ),
                            ),
                            modelProducer = sleepProducer,
                            scrollState = com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState(initialScroll = initialChartScroll),
                            modifier = Modifier.fillMaxWidth().height(220.dp),
                        )
                    }
                } else {
                    Text("暂无数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            ChartCard(title = "平均心率", accentColor = VitaHeart) {
                val hasHr = hrSeries.isNotEmpty()
                if (hasHr) {
                    if (hrSeries.size <= 1) {
                        SinglePeriodMetric(
                            label = hrSeries.first().first,
                            value = "%.0f".format(hrSeries.first().second),
                            unit = "bpm",
                            accentColor = VitaHeart,
                        )
                    } else {
                        CartesianChartHost(
                            chart = rememberCartesianChart(
                                rememberVitaLineLayer(VitaHeart, chartHorizontalSpacing),
                                startAxis = VerticalAxis.rememberStart(label = axisLabel),
                                bottomAxis = HorizontalAxis.rememberBottom(
                                    label = axisLabel,
                                    valueFormatter = hrAxisFormatter,
                                    itemPlacer = com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis.ItemPlacer.aligned(spacing = 1)
                                ),
                            ),
                            modelProducer = hrProducer,
                            scrollState = com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState(initialScroll = initialChartScroll),
                            modifier = Modifier.fillMaxWidth().height(220.dp),
                        )
                    }
                } else {
                    Text("暂无数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (state.range != StatsRange.All && state.daily.isEmpty() && state.sleeps.isEmpty() && state.exercises.isEmpty()) {
            Text(
                "尚无数据,先到\"设置\"页同步一次。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (state.exercises.isNotEmpty()) {
            Text(
                "运动明细",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
            )
            val grouped = state.exercises.groupBy { ExerciseClassifier.displayCategory(it) }
            grouped.forEach { (type, list) ->
                ExerciseCategoryCard(
                    type = type,
                    exercises = list,
                    onExerciseClick = { ex -> navController.navigate("exercise/${ex.id}") }
                )
            }
        }
    }
}

@Composable
private fun StatsCalendarCard(
    state: StatsUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDay: (LocalDate) -> Unit,
    onJumpToToday: () -> Unit,
    onJumpToMonth: (YearMonth) -> Unit,
    onOpenGarminData: (LocalDate) -> Unit,
) {
    ChartCard(title = "数据日历", accentColor = MaterialTheme.colorScheme.onSurface) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPreviousMonth) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "上个月")
            }
            Text(
                text = "${state.calendarMonth.year}年${state.calendarMonth.monthValue}月",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            // 回到今日 (#6)
            val onToday = java.time.YearMonth.now() == state.calendarMonth &&
                state.selectedDay?.date == java.time.LocalDate.now()
            if (!onToday) {
                AssistChip(
                    onClick = onJumpToToday,
                    label = { Text("今日") },
                    leadingIcon = { Icon(Icons.Outlined.Today, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    colors = AssistChipDefaults.assistChipColors(labelColor = VitaPrimary, leadingIconContentColor = VitaPrimary),
                )
            }
            IconButton(
                onClick = onNextMonth,
                enabled = state.calendarMonth.isBefore(java.time.YearMonth.now()),
            ) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "下个月")
            }
        }

        HistoryJumpStrip(
            historyMonths = state.historyMonths,
            currentMonth = state.calendarMonth,
            onJumpToMonth = onJumpToMonth,
            modifier = Modifier.padding(bottom = 10.dp),
        )

        CalendarLegend(modifier = Modifier.padding(bottom = 8.dp))

        val weekdays = listOf("一", "二", "三", "四", "五", "六", "日")
        Row(modifier = Modifier.fillMaxWidth()) {
            weekdays.forEach {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = VitaOnSurfaceMuted,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        state.calendarDays.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    CalendarDayCell(
                        day = day,
                        selected = state.selectedDay?.date == day.date,
                        onClick = { onSelectDay(day.date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        SelectedDaySummary(
            detail = state.selectedDay,
            onOpenGarminData = onOpenGarminData,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun HistoryJumpStrip(
    historyMonths: List<StatsHistoryMonth>,
    currentMonth: YearMonth,
    onJumpToMonth: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (historyMonths.isEmpty()) return
    val years = remember(historyMonths) { historyMonths.map { it.yearMonth.year }.distinct() }
    var selectedYear by remember(historyMonths) {
        androidx.compose.runtime.mutableStateOf(
            currentMonth.year.takeIf { it in years } ?: years.firstOrNull()
        )
    }
    LaunchedEffect(currentMonth, historyMonths) {
        if (currentMonth.year in years) selectedYear = currentMonth.year
    }
    val months = remember(historyMonths, selectedYear) {
        historyMonths.filter { it.yearMonth.year == selectedYear }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "历史跳转",
            style = MaterialTheme.typography.bodySmall,
            color = VitaOnSurfaceMuted,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            years.forEach { year ->
                val selected = selectedYear == year
                AssistChip(
                    onClick = { selectedYear = year },
                    label = { Text("${year}年") },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (selected) VitaPrimary.copy(alpha = 0.16f) else Color.Transparent,
                        labelColor = if (selected) VitaPrimary else VitaOnSurfaceMuted,
                    ),
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            months.forEach { item ->
                val selected = currentMonth == item.yearMonth
                AssistChip(
                    onClick = { onJumpToMonth(item.yearMonth) },
                    label = { Text("${item.yearMonth.monthValue}月 · ${item.daysTracked}天") },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (selected) VitaPrimary.copy(alpha = 0.16f) else Color.Transparent,
                        labelColor = if (selected) VitaPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        }
    }
}

@Composable
private fun CalendarLegend(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LegendItem("步", VitaPrimary)
        LegendItem("距", Color(0xFF74D7D3))
        LegendItem("卡", VitaActive)
        LegendItem("心", VitaHeart)
        LegendItem("睡", VitaTertiary)
        LegendItem("动", VitaActive.copy(alpha = 0.75f))
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(color, CircleShape),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = VitaOnSurfaceMuted,
            modifier = Modifier.padding(start = 3.dp),
        )
    }
}

@Composable
private fun CalendarDayCell(
    day: StatsCalendarDay,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = when {
        selected -> VitaPrimary.copy(alpha = 0.16f)
        day.hasAnyData -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
        else -> Color.Transparent
    }
    Column(
        modifier = modifier
            .height(52.dp)
            .padding(2.dp)
            .background(background, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = day.date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = when {
                selected -> VitaPrimary
                day.inMonth -> MaterialTheme.colorScheme.onSurface
                else -> VitaOnSurfaceMuted.copy(alpha = 0.38f)
            },
            fontWeight = if (selected || day.hasAnyData) FontWeight.Bold else FontWeight.Normal,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            day.markerColors().take(4).forEach { color ->
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .background(color, CircleShape),
                )
            }
        }
    }
}

private fun StatsCalendarDay.markerColors(): List<Color> = buildList {
    if (hasSteps) add(VitaPrimary)
    if (hasDistance) add(Color(0xFF74D7D3))
    if (hasCalories) add(VitaActive)
    if (hasHeart) add(VitaHeart)
    if (hasSleep) add(VitaTertiary)
    if (hasExercise) add(VitaActive.copy(alpha = 0.75f))
}

@Composable
private fun SelectedDaySummary(
    detail: StatsDayDetail?,
    onOpenGarminData: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (detail == null) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = detail.date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            AssistChip(
                onClick = { onOpenGarminData(detail.date) },
                label = { Text("当日全量") },
                colors = AssistChipDefaults.assistChipColors(
                    labelColor = VitaPrimary,
                    leadingIconContentColor = VitaPrimary,
                ),
            )
        }
        if (!detail.hasAnyData) {
            Text("这天暂无数据", style = MaterialTheme.typography.bodySmall, color = VitaOnSurfaceMuted)
            return@Column
        }
        val daily = detail.daily
        daily?.steps?.takeIf { it > 0 }?.let { DataLine("步数", "%,d 步".format(it), VitaPrimary) }
        daily?.distanceMeters?.takeIf { it > 0.0 }?.let { DataLine("距离", "%.2f km".format(it / 1000.0), Color(0xFF74D7D3)) }
        daily?.activeCalories?.takeIf { it > 0.0 }?.let { DataLine("活跃卡路里", "${it.toInt()} kcal", VitaActive) }
        daily?.totalCalories?.takeIf { it > 0.0 }?.let { DataLine("总消耗", "${it.toInt()} kcal", VitaActive) }
        daily?.activeMinutes?.takeIf { it > 0L }?.let { DataLine("活跃分钟", "$it min", VitaActive) }
        daily?.floorsClimbed?.takeIf { it > 0.0 }?.let { DataLine("爬楼", "${it.toInt()} 层", VitaActive) }
        daily?.avgHeartRate?.takeIf { it > 0 }?.let { DataLine("平均心率", "$it bpm", VitaHeart) }
        daily?.restingHeartRate?.takeIf { it > 0 }?.let { DataLine("静息心率", "$it bpm", VitaHeart) }
        detail.sleeps.filter { it.totalMinutes > 0 }.forEach {
            DataLine("睡眠", "${it.totalMinutes / 60}h ${it.totalMinutes % 60}m", VitaTertiary)
        }
        if (detail.exercises.isNotEmpty()) {
            DataLine("运动", "${detail.exercises.size} 次 / ${detail.exercises.sumOf { it.durationMinutes }} min", VitaActive)
        }
        daily?.avgStress?.takeIf { it > 0 }?.let { DataLine("压力", "$it", VitaActive) }
        daily?.avgSpo2?.takeIf { it > 0 }?.let { DataLine("血氧", "$it%", VitaHeart) }
        daily?.avgRespiration?.takeIf { it > 0.0 }?.let { DataLine("呼吸率", "%.1f 次/分".format(it), VitaPrimary) }
        daily?.hrv?.takeIf { it > 0 }?.let { DataLine("HRV", "$it ms", VitaHeart) }
        daily?.weightKg?.takeIf { it > 0.0 }?.let { DataLine("体重", "%.1f kg".format(it), VitaPrimary) }
    }
}

@Composable
private fun DataLine(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 8.dp).weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RangeSummaryCard(summary: StatsRangeSummary) {
    if (summary.label == "过去7天") {
        PastSevenDaysCard(summary)
        return
    }
    ChartCard(title = summary.label, accentColor = MaterialTheme.colorScheme.onSurface) {
        val daily = summary.daily
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DataLine("步数", "%,d 步".format(daily.steps), VitaPrimary)
            daily.distanceMeters?.takeIf { it > 0.0 }?.let {
                DataLine("距离", "%.2f km".format(it / 1000.0), Color(0xFF74D7D3))
            }
            daily.activeCalories?.takeIf { it > 0.0 }?.let {
                DataLine("活跃卡路里", "${it.toInt()} kcal", VitaActive)
            }
            daily.avgHeartRate?.takeIf { it > 0 }?.let {
                DataLine("平均心率", "$it bpm", VitaHeart)
            }
            summary.sleepAvgMinutes.takeIf { it > 0 }?.let {
                DataLine("平均睡眠", "${it / 60}h ${it % 60}m", VitaTertiary)
            }
            summary.exerciseCount.takeIf { it > 0 }?.let {
                DataLine("运动", "$it 次 / ${summary.exerciseMinutes} min", VitaActive)
            }
        }
    }
}

@Composable
private fun PastSevenDaysCard(summary: StatsRangeSummary) {
    ChartCard(title = "过去7天", accentColor = MaterialTheme.colorScheme.onSurface) {
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            summary.exerciseBreakdowns.forEach { breakdown ->
                val name = pastSevenExerciseLabel(breakdown.category)
                val value = if (breakdown.distanceMeters > 0.0) {
                    "%.1f 公里".format(breakdown.distanceMeters / 1000.0)
                } else {
                    formatHm(breakdown.minutes)
                }
                PastSevenLine(
                    color = colorForExerciseCategory(breakdown.category),
                    label = "${breakdown.count} 次$name",
                    value = value,
                )
            }
            if (summary.sleepAvgMinutes > 0L) {
                val sleepValue = if (summary.avgSleepScore != null) {
                    "平均 ${summary.avgSleepScore} / 平均 ${summary.sleepAvgMinutes / 60}时 ${summary.sleepAvgMinutes % 60}分"
                } else {
                    "${summary.sleepNights} 晚 · 平均 ${summary.sleepAvgMinutes / 60}时 ${summary.sleepAvgMinutes % 60}分"
                }
                PastSevenLine(VitaTertiary, "睡眠", sleepValue)
            }
            if (summary.avgSteps > 0L) {
                PastSevenLine(VitaPrimary, "步数", "平均 %,d".format(summary.avgSteps))
            }
            summary.avgRestingHeartRate?.let {
                PastSevenLine(VitaHeart, "心率", "$it 平均静止")
            }
            summary.avgTotalCalories?.let {
                PastSevenLine(VitaActive, "热量消耗", "平均 %,d".format(it))
            }
            summary.avgStress?.let {
                PastSevenLine(VitaActive, "压力", "平均 $it")
            }
        }
    }
}

@Composable
private fun PastSevenLine(color: Color, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .background(color, CircleShape),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 2,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            modifier = Modifier.weight(1.1f),
        )
    }
}

private fun colorForExerciseCategory(category: String): Color = when (ExerciseClassifier.displayName(category)) {
    "步行" -> Color(0xFF61D394)
    "跑步" -> VitaPrimary
    "骑行" -> Color(0xFF35C26B)
    "强度健身", "力量训练" -> VitaActive
    else -> VitaPrimary
}

private fun pastSevenExerciseLabel(category: String): String = when (ExerciseClassifier.displayName(category)) {
    "强度健身", "力量训练" -> "训练"
    else -> ExerciseClassifier.displayName(category)
}

/** 「全部」视图: 整段历史累计总览卡片。 */
@Composable
private fun AllTimeSummaryCard(summary: AllTimeSummary) {
    ChartCard(title = "全部历史", accentColor = VitaPrimary) {
        if (!summary.hasAnyData) {
            Text(
                "尚无数据,先到\"设置\"页同步一次。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            return@ChartCard
        }
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // 时间跨度 + 记录天数
            val fmt = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }
            val span = buildString {
                if (summary.firstDate != null && summary.lastDate != null) {
                    append(summary.firstDate.format(fmt))
                    append(" ~ ")
                    append(summary.lastDate.format(fmt))
                    append(" · ")
                }
                append("共 ${summary.daysTracked} 天有记录")
            }
            Text(span, style = MaterialTheme.typography.bodySmall, color = VitaOnSurfaceMuted)

            // 累计 Hero: 小屏两列排布, 避免 kcal 被挤成竖排。
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    HeroStat(
                        modifier = Modifier.weight(1f),
                        value = compactCount(summary.totalSteps.toDouble()),
                        unit = "步",
                        label = "累计步数",
                        accent = VitaPrimary,
                    )
                    HeroStat(
                        modifier = Modifier.weight(1f),
                        value = "%,.0f".format(summary.totalDistanceMeters / 1000.0),
                        unit = "km",
                        label = "累计距离",
                        accent = Color(0xFF74D7D3),
                    )
                }
                HeroStat(
                    modifier = Modifier.fillMaxWidth(),
                    value = compactCount(summary.totalActiveCalories),
                    unit = "kcal",
                    label = "累计活跃消耗",
                    accent = VitaActive,
                )
            }

            // 明细
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                summary.totalActiveMinutes.takeIf { it > 0L }?.let {
                    DataLine("累计活跃时长", formatHm(it), VitaActive)
                }
                summary.totalFloors.takeIf { it > 0.0 }?.let {
                    DataLine("累计爬楼", "${it.toInt()} 层", VitaActive)
                }
                summary.exerciseCount.takeIf { it > 0 }?.let {
                    val distPart = if (summary.exerciseDistanceMeters > 0.0) {
                        " · %.1f km".format(summary.exerciseDistanceMeters / 1000.0)
                    } else ""
                    DataLine("运动", "$it 次 / ${summary.exerciseMinutes} min$distPart", VitaPrimary)
                }
                summary.sleepNights.takeIf { it > 0 }?.let {
                    DataLine(
                        "睡眠",
                        "$it 晚 · 平均 ${summary.avgSleepMinutes / 60}h ${summary.avgSleepMinutes % 60}m",
                        VitaTertiary,
                    )
                }
                summary.avgHeartRate?.let { DataLine("平均心率", "$it bpm", VitaHeart) }
                summary.avgRestingHeartRate?.let { DataLine("平均静息心率", "$it bpm", VitaHeart) }
            }
        }
    }
}

@Composable
private fun HeroStat(
    value: String,
    unit: String,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(accent.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .padding(vertical = 14.dp, horizontal = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = unit,
                style = MaterialTheme.typography.bodySmall,
                color = accent,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(start = 3.dp, bottom = 3.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = VitaOnSurfaceMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** 大数字紧凑显示: 1.2万 / 3.4亿, 小于一万直接千分位。 */
private fun compactCount(value: Double): String = when {
    value >= 1e8 -> "%.2f亿".format(value / 1e8)
    value >= 1e4 -> "%.1f万".format(value / 1e4)
    else -> "%,.0f".format(value)
}

private fun formatHm(minutes: Long): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

@Composable
private fun SinglePeriodMetric(
    label: String,
    value: String,
    unit: String,
    accentColor: Color,
) {
    val showLabel = label.length > 1
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        if (showLabel) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.padding(top = if (showLabel) 10.dp else 0.dp, bottom = 18.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = unit,
                style = MaterialTheme.typography.titleMedium,
                color = accentColor,
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
            )
        }
        LinearProgressIndicator(
            progress = { 1f },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = accentColor,
            trackColor = accentColor.copy(alpha = 0.12f),
        )
    }
}

@Composable
private fun rememberVitaColumnLayer(
    accentColor: Color,
    range: StatsRange,
    columnCollectionSpacing: androidx.compose.ui.unit.Dp,
): ColumnCartesianLayer {
    val columnWidth = when (range) {
        StatsRange.Month, StatsRange.Year -> 12.dp
        else -> 10.dp
    }
    val columnProvider = ColumnCartesianLayer.ColumnProvider.series(
        rememberLineComponent(
            fill = fill(accentColor),
            thickness = columnWidth,
            shape = CorneredShape.rounded(45),
        )
    )
    return rememberColumnCartesianLayer(
        columnProvider = columnProvider,
        columnCollectionSpacing = columnCollectionSpacing,
    )
}

@Composable
private fun rememberVitaLineLayer(
    accentColor: Color,
    pointSpacing: androidx.compose.ui.unit.Dp,
): LineCartesianLayer =
    rememberLineCartesianLayer(
        lineProvider = LineCartesianLayer.LineProvider.series(
            LineCartesianLayer.rememberLine(
                fill = LineCartesianLayer.LineFill.single(fill(accentColor)),
                thickness = 3.dp,
                // 线下加一层淡淡的同色面积填充, 比光秃秃的折线更耐看。
                areaFill = LineCartesianLayer.AreaFill.single(fill(accentColor.copy(alpha = 0.16f))),
            )
        ),
        pointSpacing = pointSpacing,
    )

/**
 * 日统计：紧凑的汇总卡片
 */
@Composable
private fun DailySummaryCard(state: StatsUiState) {
    val daily = state.daily.firstOrNull()
    val sleep = state.sleeps.maxByOrNull { it.startEpochMs }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .background(VitaGradients.cardSurface, MaterialTheme.shapes.medium)
                .padding(20.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "今日数据",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                daily?.source?.takeIf { it.isNotBlank() }?.let { src ->
                    Text(
                        text = "数据来源 · ${healthSourceLabel(src)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // 步数
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Outlined.DirectionsWalk,
                    contentDescription = null,
                    tint = VitaPrimary,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("步数", style = MaterialTheme.typography.bodyMedium, color = VitaPrimary)
                    Text(
                        text = "${daily?.steps ?: 0}",
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (daily != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        val dist = daily.distanceMeters ?: 0.0
                        if (dist > 0) {
                            Text(
                                text = "%.1f km".format(dist / 1000),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val cal = daily.activeCalories ?: 0.0
                        if (cal > 0) {
                            Text(
                                text = "${cal.toInt()} kcal",
                                style = MaterialTheme.typography.bodyMedium,
                                color = VitaActive,
                            )
                        }
                    }
                }
            }

            // 睡眠
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Bedtime,
                    contentDescription = null,
                    tint = VitaTertiary,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("睡眠", style = MaterialTheme.typography.bodyMedium, color = VitaTertiary)
                    if (sleep != null) {
                        Text(
                            text = "${sleep.totalMinutes / 60}h ${sleep.totalMinutes % 60}m",
                            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    } else {
                        Text(
                            text = "无数据",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                if (sleep != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("深睡 ${sleep.deepMinutes}m", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("浅睡 ${sleep.lightMinutes}m", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("清醒 ${sleep.awakeMinutes}m", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // 心率
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.FavoriteBorder,
                    contentDescription = null,
                    tint = VitaHeart,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("平均心率", style = MaterialTheme.typography.bodyMedium, color = VitaHeart)
                    val hr = daily?.avgHeartRate
                    Text(
                        text = if (hr != null && hr > 0) "$hr bpm" else "无数据",
                        style = if (hr != null && hr > 0) MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold)
                        else MaterialTheme.typography.headlineSmall,
                        color = if (hr != null && hr > 0) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                if (daily?.restingHeartRate != null && daily.restingHeartRate > 0) {
                    Text(
                        "静息 ${daily.restingHeartRate} bpm",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ExerciseCategoryCard(
    type: String,
    exercises: List<ExerciseSession>,
    onExerciseClick: (ExerciseSession) -> Unit
) {
    var expanded by remember { androidx.compose.runtime.mutableStateOf(false) }
    val totalMin = exercises.sumOf { it.durationMinutes }

    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.background(VitaGradients.cardSurface, MaterialTheme.shapes.medium)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Outlined.DirectionsRun, contentDescription = null, tint = VitaActive, modifier = Modifier.size(28.dp))
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(
                        text = ExerciseClassifier.displayName(type),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${exercises.size} 次记录",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "$totalMin min",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            // Expanded List
            androidx.compose.animation.AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    exercises.sortedByDescending { it.startEpochMs }.forEach { exercise ->
                        ExerciseRow(exercise = exercise) { onExerciseClick(exercise) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseRow(exercise: ExerciseSession, onClick: () -> Unit) {
    val date = Instant.ofEpochMilli(exercise.startEpochMs)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()
        .format(DateTimeFormatter.ofPattern("yyyy/M/d HH:mm"))
    val title = ExerciseClassifier.displayTitle(exercise)

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.padding(start = 44.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = date,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${exercise.durationMinutes} min",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (exercise.activeCalories != null && exercise.activeCalories > 0) {
                    Text(
                        text = "${exercise.activeCalories.toInt()} kcal",
                        style = MaterialTheme.typography.bodySmall,
                        color = VitaActive
                    )
                }
            }
        }
    }
}

@Composable
private fun ChartCard(
    title: String,
    accentColor: Color = VitaPrimary,
    content: @Composable () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .background(
                    brush = VitaGradients.cardSurface,
                    shape = MaterialTheme.shapes.medium,
                )
                .padding(16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = accentColor,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            content()
        }
    }
}
