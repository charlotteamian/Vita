package com.vita.healthtracker.ui.screens.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import com.vita.healthtracker.data.local.entity.ExerciseSession
import com.vita.healthtracker.domain.ExerciseClassifier
import com.vita.healthtracker.domain.LocalAssociationReport
import com.vita.healthtracker.domain.LongTermTrendReport
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
fun StatsScreen(navController: NavController) {
    val vm = vitaViewModel<StatsViewModel>()
    val state by vm.state.collectAsStateWithLifecycle()

    // 预计算 xLabels（直接从 state 中取）
    val xLabels = state.xLabels
    val stepSeries = remember(state.daily, xLabels) {
        state.daily.zip(xLabels).map { (day, label) ->
            label to day.steps.takeIf { it > 0L }?.toDouble()
        }
    }
    val sleepSeries = remember(state.sleeps, xLabels) {
        state.sleeps.zip(xLabels).map { (sleep, label) ->
            label to (sleep.totalMinutes.toDouble() / 60.0).takeIf { it > 0.0 }
        }
    }
    val hrSeries = remember(state.daily, xLabels) {
        state.daily.zip(xLabels).map { (day, label) ->
            label to day.avgHeartRate?.takeIf { it > 0 }?.toDouble()
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        state.longTermTrend?.let {
            LongTermTrendCard(it)
        }
        state.localAssociations?.let {
            LocalAssociationCard(it)
        }

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
        } else key(state.range, state.periodLabel) {
            // ─── 周/月/年统计: 图表 ───
            // 周期图表使用 Compose Canvas 自绘，避开 Vico beta 在稀疏数据和跨周期模型更新时的崩溃路径。
            // 周期导航条: 在固定窗口内翻页 (上一周/月/年 ↔ 下一周/月/年), 不再铺一条无限时间轴。
            PeriodNavigator(
                label = state.periodLabel,
                canGoNext = state.canGoNextPeriod,
                onPrevious = vm::previousPeriod,
                onNext = vm::nextPeriod,
                onToday = vm::jumpPeriodToToday,
            )
            ChartCard(title = "步数", accentColor = VitaPrimary) {
                val hasSteps = stepSeries.any { it.second != null }
                if (hasSteps) {
                    VitaPeriodChart(
                        series = stepSeries,
                        accentColor = VitaPrimary,
                        style = PeriodChartStyle.Bars,
                        valueLabel = { "%,.0f 步".format(it) },
                    )
                } else {
                    Text("暂无数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            ChartCard(
                title = if (state.range == StatsRange.Year) "睡眠规律" else "睡眠时长",
                accentColor = VitaTertiary,
            ) {
                val hasSleep = sleepSeries.any { it.second != null }
                if (hasSleep) {
                    if (state.range == StatsRange.Year) {
                        SleepYearOverview(state.sleepSummaries)
                    } else {
                        VitaPeriodChart(
                            series = sleepSeries,
                            accentColor = VitaTertiary,
                            style = PeriodChartStyle.Bars,
                            valueLabel = { "%.1f 小时".format(it) },
                        )
                    }
                } else {
                    Text("暂无数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            ChartCard(title = "平均心率", accentColor = VitaHeart) {
                val hasHr = hrSeries.any { it.second != null }
                if (hasHr) {
                    VitaPeriodChart(
                        series = hrSeries,
                        accentColor = VitaHeart,
                        style = PeriodChartStyle.Line,
                        valueLabel = { "%.0f bpm".format(it) },
                    )
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

/** 趋势页的第一张卡: 先回答多年发生了什么，再让图表和日历提供证据。 */
@Composable
private fun LongTermTrendCard(report: LongTermTrendReport) {
    ChartCard(title = "你的长期变化", accentColor = VitaTertiary) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val fmt = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }
            Text(
                text = "${report.firstDate.format(fmt)} ~ ${report.lastDate.format(fmt)} · ${report.trackedDays} 个有记录日",
                style = MaterialTheme.typography.bodySmall,
                color = VitaOnSurfaceMuted,
            )
            Text(
                text = report.headline,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            report.changes.forEach { change ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(VitaTertiary.copy(alpha = 0.07f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = change.label,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = change.deltaText,
                            style = MaterialTheme.typography.bodySmall,
                            color = VitaTertiary,
                        )
                    }
                    Text(
                        text = change.analysisText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${change.note} · 共 ${change.sampleDays} 天记录",
                        style = MaterialTheme.typography.bodySmall,
                        color = VitaOnSurfaceMuted,
                    )
                }
            }
            Text(
                text = "只统计已经记录到的日子；空白日期不会补成 0。",
                style = MaterialTheme.typography.bodySmall,
                color = VitaOnSurfaceMuted,
            )
        }
    }
}

@Composable
private fun LocalAssociationCard(report: LocalAssociationReport) {
    ChartCard(title = "可能有关的事", accentColor = VitaPrimary) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = if (report.insightCount > 0) {
                    "从你的记录里，暂时看到 ${report.insightCount} 个可能有关的模式。"
                } else {
                    "还在积累记录；数据不够时，Vita 不会硬下结论。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = VitaOnSurfaceMuted,
            )
            report.topics.forEach { topic ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(VitaPrimary.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = topic.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    topic.finding?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = VitaPrimary)
                    }
                    Text(topic.evidence, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(topic.note, style = MaterialTheme.typography.bodySmall, color = VitaOnSurfaceMuted)
                }
            }
            Text(
                text = "这里只提醒哪些事情常常一起出现，不代表其中一件一定导致另一件。",
                style = MaterialTheme.typography.bodySmall,
                color = VitaOnSurfaceMuted,
            )
        }
    }
}

/** 周/月/年图表的周期导航条: ‹ 周期标题 › + 「今日」回当前周期。 */
@Composable
private fun PeriodNavigator(
    label: String,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(VitaGradients.cardSurface, RoundedCornerShape(14.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "上一期", tint = VitaPrimary)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 1,
        )
        if (canGoNext) {
            AssistChip(
                onClick = onToday,
                label = { Text("今日") },
                leadingIcon = { Icon(Icons.Outlined.Today, contentDescription = null, modifier = Modifier.size(16.dp)) },
                colors = AssistChipDefaults.assistChipColors(labelColor = VitaPrimary, leadingIconContentColor = VitaPrimary),
            )
        }
        IconButton(onClick = onNext, enabled = canGoNext) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "下一期", tint = if (canGoNext) VitaPrimary else VitaOnSurfaceMuted)
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

private enum class PeriodChartStyle { Bars, Line }

/**
 * 周期统计用轻量 Canvas 图。这里刻意不走 Vico 的 producer 动画：
 * 本地历史会出现空槽、稀疏槽和翻页后槽位数量变化，Vico beta 在这些模型之间插值时会闪退。
 */
@Composable
private fun VitaPeriodChart(
    series: List<Pair<String, Double?>>,
    accentColor: Color,
    style: PeriodChartStyle,
    valueLabel: (Double) -> String,
) {
    val values = series.mapNotNull { it.second?.takeIf(Double::isFinite) }
    if (values.isEmpty()) return
    val max = values.maxOrNull() ?: 0.0
    val min = if (style == PeriodChartStyle.Bars) 0.0 else values.minOrNull() ?: 0.0
    val span = (max - min).takeIf { it > 0.0001 } ?: 1.0
    val visibleLabels = series.mapIndexedNotNull { index, (label, _) ->
        label.takeIf { series.size <= 12 || index == 0 || (index + 1) % 5 == 0 || index == series.lastIndex }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "峰值 ${valueLabel(max)}",
            style = MaterialTheme.typography.bodySmall,
            color = VitaOnSurfaceMuted,
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(168.dp),
        ) {
            val horizontalPadding = 4.dp.toPx()
            val verticalPadding = 8.dp.toPx()
            val chartWidth = (size.width - horizontalPadding * 2).coerceAtLeast(1f)
            val chartHeight = (size.height - verticalPadding * 2).coerceAtLeast(1f)
            val bottom = verticalPadding + chartHeight
            val gridColor = VitaOnSurfaceMuted.copy(alpha = 0.12f)
            for (i in 0..3) {
                val y = verticalPadding + chartHeight * i / 3f
                drawLine(gridColor, Offset(horizontalPadding, y), Offset(horizontalPadding + chartWidth, y), 1.dp.toPx())
            }
            fun yFor(value: Double): Float =
                bottom - (((value - min) / span).coerceIn(0.0, 1.0) * chartHeight).toFloat()

            if (style == PeriodChartStyle.Bars) {
                val slot = chartWidth / series.size.coerceAtLeast(1)
                val width = (slot * 0.58f).coerceAtMost(18.dp.toPx()).coerceAtLeast(3.dp.toPx())
                series.forEachIndexed { index, (_, value) ->
                    if (value == null || !value.isFinite()) return@forEachIndexed
                    val top = yFor(value)
                    val left = horizontalPadding + slot * index + (slot - width) / 2f
                    drawRoundRect(
                        color = accentColor,
                        topLeft = Offset(left, top),
                        size = androidx.compose.ui.geometry.Size(width, (bottom - top).coerceAtLeast(2.dp.toPx())),
                        cornerRadius = CornerRadius(width / 2f),
                    )
                }
            } else {
                val slot = chartWidth / (series.size - 1).coerceAtLeast(1)
                val points = series.mapIndexed { index, (_, value) ->
                    value?.takeIf(Double::isFinite)?.let { Offset(horizontalPadding + slot * index, yFor(it)) }
                }
                points.zipWithNext().forEach { (from, to) ->
                    if (from == null || to == null) return@forEach
                    drawLine(
                        color = accentColor,
                        start = from,
                        end = to,
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
                points.filterNotNull().forEach { point -> drawCircle(accentColor, 3.dp.toPx(), point) }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            visibleLabels.forEach { label ->
                Text(label, style = MaterialTheme.typography.bodySmall, color = VitaOnSurfaceMuted)
            }
        }
    }
}

/** 年视图关注长期规律，而不是把十二根「平均时长」柱孤零零地摆出来。 */
@Composable
private fun SleepYearOverview(summaries: List<StatsSleepSummary>) {
    val tracked = summaries.filter { it.nightsTracked > 0 }
    val nights = tracked.sumOf { it.nightsTracked }
    val goalNights = tracked.sumOf { it.goalNights }
    val avgMinutes = if (nights == 0) 0L else tracked.sumOf { it.avgMinutes * it.nightsTracked } / nights

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "全年记录 $nights 晚 · 平均 ${formatHm(avgMinutes)} · ≥7h 达标 $goalNights 晚",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        summaries.forEach { summary ->
            val progress = (summary.avgMinutes / (8f * 60f)).coerceIn(0f, 1f)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${summary.date.monthValue}月",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VitaTertiary,
                        modifier = Modifier.width(38.dp),
                    )
                    Text(
                        if (summary.nightsTracked > 0) "${formatHm(summary.avgMinutes)} · ${summary.goalNights}/${summary.nightsTracked} 晚达标" else "暂无记录",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(5.dp),
                    color = VitaTertiary,
                    trackColor = VitaTertiary.copy(alpha = 0.12f),
                )
            }
        }
    }
}

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
