package com.vita.healthtracker.ui.screens.life

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import com.vita.healthtracker.R
import com.vita.healthtracker.data.local.entity.CycleEntry
import com.vita.healthtracker.data.local.entity.HabitCheckIn
import com.vita.healthtracker.data.local.entity.HabitDefinition
import com.vita.healthtracker.data.local.entity.SleepSession
import com.vita.healthtracker.domain.DayStatus
import com.vita.healthtracker.domain.HabitBadge
import com.vita.healthtracker.domain.HabitBadgeCatalog
import com.vita.healthtracker.domain.PredictionConfidence
import com.vita.healthtracker.ui.theme.VitaActive
import com.vita.healthtracker.ui.theme.VitaGradients
import com.vita.healthtracker.ui.theme.VitaHeart
import com.vita.healthtracker.ui.theme.VitaOnSurfaceMuted
import com.vita.healthtracker.ui.theme.VitaPrimary
import com.vita.healthtracker.ui.theme.VitaSecondary
import com.vita.healthtracker.ui.theme.VitaTertiary
import com.vita.healthtracker.ui.vitaViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun LifeScreen(navController: NavController) {
    val vm = vitaViewModel<LifeViewModel>()
    val state by vm.state.collectAsStateWithLifecycle()
    var flow by rememberSaveable { mutableIntStateOf(2) }
    var markStart by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showHabitDialog by rememberSaveable { mutableStateOf(false) }
    var newHabitName by rememberSaveable { mutableStateOf("") }
    var expandedPeriods by rememberSaveable { mutableStateOf(false) }
    var cycleExpanded by rememberSaveable { mutableStateOf(false) }
    var badgeCheckArmed by rememberSaveable { mutableStateOf(false) }
    var pendingCelebrationToken by remember { mutableStateOf<String?>(null) }
    var celebrationBadge by remember { mutableStateOf<HabitBadge?>(null) }
    val datePickerState = androidx.compose.material3.rememberDateRangePickerState()

    LaunchedEffect(state.earnedBadgeTokens, state.shownBadgeTokens, badgeCheckArmed) {
        if (badgeCheckArmed && celebrationBadge == null) {
            val newlyEarned = state.earnedBadgeTokens - state.shownBadgeTokens
            val token = newlyEarned.lastOrNull()
            if (token == null) {
                badgeCheckArmed = false
            } else {
                pendingCelebrationToken = token
                val badgeId = HabitBadgeCatalog.badgeIdFromToken(token)
                celebrationBadge = HabitBadgeCatalog.badges.lastOrNull { it.id == badgeId }
                if (celebrationBadge == null) {
                    vm.markBadgeCelebrationShown(token)
                    pendingCelebrationToken = null
                    badgeCheckArmed = false
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            HabitBadgeSummaryCard(
                habitEarnedCount = state.earnedBadgeIds.size,
                habitTotal = HabitBadgeCatalog.badges.size,
                fitnessEarnedCount = state.fitnessEarnedIds.size,
                fitnessTotal = HabitBadgeCatalog.fitnessBadges.size,
                longestStreakDays = state.habitLongestStreakDays,
                onClick = { navController.navigate("habit_badges") },
            )
        }

        // ---- 睡眠板块 ----
        item {
            Text(
                "近期睡眠",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        
        if (state.recentSleeps.isEmpty()) {
            item {
                Text(
                    "暂无近期睡眠记录",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            item {
                val avgMins = state.recentSleeps.map { it.totalMinutes }.average().toLong()
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { navController.navigate("sleep_detail") },
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(VitaGradients.cardSurface, MaterialTheme.shapes.medium)
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Bedtime, contentDescription = null, tint = VitaTertiary, modifier = Modifier.size(36.dp))
                        Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                            Text("近期平均睡眠", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "${avgMins / 60}h ${avgMins % 60}m",
                                style = MaterialTheme.typography.headlineMedium,
                                color = VitaTertiary
                            )
                        }
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // ---- 习惯记录板块 ----
        item {
            HabitTodaySection(
                habits = state.habits,
                checkIns = state.habitCheckIns,
                onOpenDetail = { navController.navigate("habit_detail") },
                onAddHabit = { showHabitDialog = true },
                onMark = { habitId, date, status, current ->
                    badgeCheckArmed = true
                    vm.markHabit(habitId, date, status, current)
                },
            )
        }

        item {
            CycleSection(
                state = state,
                expanded = cycleExpanded,
                onExpandedChange = { cycleExpanded = it },
                flow = flow,
                onFlowChange = { flow = it },
                markStart = markStart,
                onMarkStartChange = { markStart = it },
                expandedPeriods = expandedPeriods,
                onExpandedPeriodsChange = { expandedPeriods = it },
                onOpenDatePicker = { showDatePicker = true },
                onLogToday = { vm.logCycleRange(startDate = LocalDate.now(), endDate = LocalDate.now(), flow = flow, isStart = markStart) },
                onDeletePeriod = { period -> vm.deleteCyclePeriod(period) },
            )
        }
    }

    if (showHabitDialog) {
        AlertDialog(
            onDismissRequest = {
                showHabitDialog = false
                newHabitName = ""
            },
            title = { Text("新增习惯") },
            text = {
                OutlinedTextField(
                    value = newHabitName,
                    onValueChange = { newHabitName = it },
                    label = { Text("习惯名称") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.createHabit(newHabitName)
                        showHabitDialog = false
                        newHabitName = ""
                    },
                    enabled = newHabitName.isNotBlank(),
                ) { Text("添加") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showHabitDialog = false
                        newHabitName = ""
                    },
                ) { Text("取消") }
            },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }

    celebrationBadge?.let { badge ->
        HabitBadgeCelebrationDialog(
            badge = badge,
            onDismiss = {
                pendingCelebrationToken?.let { vm.markBadgeCelebrationShown(it) }
                pendingCelebrationToken = null
                badgeCheckArmed = false
                celebrationBadge = null
            },
        )
    }

    // 实时弹出新获得的健身徽章（同步完成时也会触发）
    FitnessBadgeCelebrationHost()

    if (showDatePicker) {
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showDatePicker = false
                    val startMillis = datePickerState.selectedStartDateMillis
                    val endMillis = datePickerState.selectedEndDateMillis ?: startMillis
                    if (startMillis != null && endMillis != null) {
                        val startDate = Instant.ofEpochMilli(startMillis).atZone(ZoneId.of("UTC")).toLocalDate()
                        val endDate = Instant.ofEpochMilli(endMillis).atZone(ZoneId.of("UTC")).toLocalDate()
                        vm.logCycleRange(startDate, endDate, flow, markStart)
                    }
                }) { Text("确定") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            androidx.compose.material3.DateRangePicker(
                state = datePickerState,
                title = { Text(text = "选择经期范围", modifier = Modifier.padding(16.dp)) },
                headline = { Text(text = "请选择起止日期", modifier = Modifier.padding(horizontal = 16.dp)) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CycleSection(
    state: LifeUiState,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    flow: Int,
    onFlowChange: (Int) -> Unit,
    markStart: Boolean,
    onMarkStartChange: (Boolean) -> Unit,
    expandedPeriods: Boolean,
    onExpandedPeriodsChange: (Boolean) -> Unit,
    onOpenDatePicker: () -> Unit,
    onLogToday: () -> Unit,
    onDeletePeriod: (CyclePeriod) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "生理期",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onExpandedChange(!expanded) }) {
                Text(if (expanded) "收起" else "管理")
            }
        }

        BayesianPredictionCard(
            state = state,
            modifier = Modifier.clickable { onExpandedChange(!expanded) },
        )

        if (expanded) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(
                    modifier = Modifier
                        .background(VitaGradients.cardSurface, MaterialTheme.shapes.medium)
                        .padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("录入与补录", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = onOpenDatePicker) {
                            Icon(Icons.Outlined.DateRange, contentDescription = "选择日期")
                        }
                    }
                    Row(
                        modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(1 to "点滴", 2 to "轻", 3 to "中", 4 to "重").forEach { (level, label) ->
                            FilterChip(
                                selected = flow == level,
                                onClick = { onFlowChange(level) },
                                label = { Text(label) },
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilterChip(
                            selected = markStart,
                            onClick = { onMarkStartChange(!markStart) },
                            label = { Text("标记为周期起始日") },
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        AssistChip(
                            onClick = onLogToday,
                            label = { Text(stringResource(R.string.cycle_log_period)) },
                            leadingIcon = { Icon(Icons.Outlined.Favorite, contentDescription = null, tint = VitaSecondary) },
                            colors = AssistChipDefaults.assistChipColors(),
                        )
                    }
                }
            }

            if (state.cyclePeriods.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "历史经期",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (state.cyclePeriods.size > 3) {
                        TextButton(onClick = { onExpandedPeriodsChange(!expandedPeriods) }) {
                            Text(if (expandedPeriods) "收起" else "查看全部")
                        }
                    }
                }
                val displayPeriods = if (expandedPeriods) state.cyclePeriods else state.cyclePeriods.take(3)
                displayPeriods
                    .groupBy { it.startDate.year }
                    .toList()
                    .sortedByDescending { it.first }
                    .forEach { (year, periods) ->
                        Text(
                            text = "${year}年",
                            style = MaterialTheme.typography.labelLarge,
                            color = VitaOnSurfaceMuted,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        periods.forEach { period ->
                            CyclePeriodRow(period = period, onDelete = { onDeletePeriod(period) })
                        }
                }
            }
        }
    }
}

@Composable
private fun HabitTodaySection(
    habits: List<HabitDefinition>,
    checkIns: List<HabitCheckIn>,
    onOpenDetail: () -> Unit,
    onAddHabit: () -> Unit,
    onMark: (String, LocalDate, Int, Int?) -> Unit,
) {
    val today = LocalDate.now()
    val statusMap = remember(checkIns) { checkIns.associateBy { it.habitId to it.date } }
    val pendingHabits = habits.filter { habit -> statusMap[habit.id to today.toString()] == null }
    val doneCount = habits.count { habit ->
        statusMap[habit.id to today.toString()]?.status == HabitCheckIn.StatusDone
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "习惯记录",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onAddHabit) {
                Icon(Icons.Outlined.Add, contentDescription = "新增习惯", tint = VitaPrimary)
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(VitaGradients.cardSurface, MaterialTheme.shapes.medium)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenDetail),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Outlined.EmojiEvents, contentDescription = null, tint = VitaActive)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "今日待打卡",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "${today.format(DateTimeFormatter.ofPattern("M月d日"))} · 已完成 $doneCount / ${habits.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = VitaOnSurfaceMuted,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "查看习惯日历", tint = VitaOnSurfaceMuted)
                }

                when {
                    habits.isEmpty() -> {
                        Text(
                            "还没有习惯。点右上角 + 添加一个。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    pendingHabits.isEmpty() -> {
                        Text(
                            "今天的习惯都处理完了，点进来可以补打卡和查看徽章。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> {
                        pendingHabits.forEach { habit ->
                            val accent = Color(habit.colorHex)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(accent, CircleShape),
                                )
                                Text(
                                    text = habit.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                HabitStatusButton(
                                    selected = false,
                                    icon = Icons.Outlined.Close,
                                    contentDescription = "标记未完成",
                                    color = VitaOnSurfaceMuted,
                                    onClick = { onMark(habit.id, today, HabitCheckIn.StatusMissed, null) },
                                )
                                HabitStatusButton(
                                    selected = false,
                                    icon = Icons.Outlined.Check,
                                    contentDescription = "标记完成",
                                    color = accent,
                                    onClick = { onMark(habit.id, today, HabitCheckIn.StatusDone, null) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HabitRecordSection(
    habits: List<HabitDefinition>,
    checkIns: List<HabitCheckIn>,
    rangeDays: Int,
    onRangeChange: (Int) -> Unit,
    onAddHabit: () -> Unit,
    onMark: (String, LocalDate, Int, Int?) -> Unit,
    onArchive: (String) -> Unit,
) {
    val today = LocalDate.now()
    val visibleDates = remember(rangeDays, today) {
        (rangeDays - 1 downTo 0).map { today.minusDays(it.toLong()) }
    }
    val weekDates = remember(today) {
        (6 downTo 0).map { today.minusDays(it.toLong()) }
    }
    val statusMap = remember(checkIns) {
        checkIns.associateBy { it.habitId to it.date }
    }
    val todayDone = habits.count { habit ->
        statusMap[habit.id to today.toString()]?.status == HabitCheckIn.StatusDone
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "习惯记录",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onAddHabit) {
                Icon(Icons.Outlined.Add, contentDescription = "新增习惯", tint = VitaPrimary)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(1 to "1天", 7 to "7天", 28 to "4周", 84 to "12周").forEach { (days, label) ->
                FilterChip(
                    selected = rangeDays == days,
                    onClick = { onRangeChange(days) },
                    label = { Text(label) },
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(VitaGradients.cardSurface, MaterialTheme.shapes.medium)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "已记录的行为",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "${today.format(DateTimeFormatter.ofPattern("M月d日"))} · 今天",
                            style = MaterialTheme.typography.bodySmall,
                            color = VitaOnSurfaceMuted,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Text(
                        "$todayDone / ${habits.size}",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                HabitWeekStrip(
                    habits = habits,
                    dates = weekDates,
                    statusMap = statusMap,
                )

                if (habits.isEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Info, contentDescription = null, tint = VitaPrimary)
                        Text(
                            "点右上角 + 自定义习惯，然后每天打卡。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 10.dp),
                        )
                    }
                } else {
                    habits.forEach { habit ->
                        HabitCheckRow(
                            habit = habit,
                            dates = visibleDates,
                            today = today,
                            statusMap = statusMap,
                            onMark = onMark,
                            onArchive = onArchive,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HabitWeekStrip(
    habits: List<HabitDefinition>,
    dates: List<LocalDate>,
    statusMap: Map<Pair<String, String>, HabitCheckIn>,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        dates.forEach { date ->
            val doneCount = habits.count { habit ->
                statusMap[habit.id to date.toString()]?.status == HabitCheckIn.StatusDone
            }
            val color = when {
                habits.isEmpty() -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f)
                doneCount == habits.size -> VitaPrimary.copy(alpha = 0.86f)
                doneCount > 0 -> VitaPrimary.copy(alpha = 0.34f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(color),
                )
                Text(
                    text = weekdayLabel(date),
                    style = MaterialTheme.typography.bodySmall,
                    color = VitaOnSurfaceMuted,
                    maxLines = 1,
                )
                if (date == LocalDate.now()) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(MaterialTheme.colorScheme.onSurface, CircleShape),
                    )
                } else {
                    Spacer(modifier = Modifier.size(5.dp))
                }
            }
        }
    }
}

@Composable
private fun HabitCheckRow(
    habit: HabitDefinition,
    dates: List<LocalDate>,
    today: LocalDate,
    statusMap: Map<Pair<String, String>, HabitCheckIn>,
    onMark: (String, LocalDate, Int, Int?) -> Unit,
    onArchive: (String) -> Unit,
) {
    val accent = Color(habit.colorHex)
    val todayStatus = statusMap[habit.id to today.toString()]?.status
    val doneDays = dates.count { date ->
        statusMap[habit.id to date.toString()]?.status == HabitCheckIn.StatusDone
    }
    val progress = if (dates.isEmpty()) 0f else doneDays.toFloat() / dates.size.toFloat()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(accent, CircleShape),
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = habit.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onArchive(habit.id) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Delete, contentDescription = "删除习惯", tint = VitaOnSurfaceMuted, modifier = Modifier.size(18.dp))
                }
            }
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LinearProgressIndicator(
                    progress = { progress },
                    color = accent,
                    trackColor = accent.copy(alpha = 0.12f),
                    modifier = Modifier.weight(1f).height(5.dp),
                )
                Text(
                    text = "$doneDays/${dates.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = VitaOnSurfaceMuted,
                )
            }
        }
        HabitStatusButton(
            selected = todayStatus == HabitCheckIn.StatusMissed,
            icon = Icons.Outlined.Close,
            contentDescription = "标记未完成",
            color = VitaOnSurfaceMuted,
            onClick = { onMark(habit.id, today, HabitCheckIn.StatusMissed, todayStatus) },
        )
        HabitStatusButton(
            selected = todayStatus == HabitCheckIn.StatusDone,
            icon = Icons.Outlined.Check,
            contentDescription = "标记完成",
            color = accent,
            onClick = { onMark(habit.id, today, HabitCheckIn.StatusDone, todayStatus) },
        )
    }
}

@Composable
private fun HabitStatusButton(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    color: Color,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) color.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)),
    ) {
        Icon(icon, contentDescription = contentDescription, tint = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun weekdayLabel(date: LocalDate): String = when (date.dayOfWeek) {
    java.time.DayOfWeek.MONDAY -> "一"
    java.time.DayOfWeek.TUESDAY -> "二"
    java.time.DayOfWeek.WEDNESDAY -> "三"
    java.time.DayOfWeek.THURSDAY -> "四"
    java.time.DayOfWeek.FRIDAY -> "五"
    java.time.DayOfWeek.SATURDAY -> "六"
    java.time.DayOfWeek.SUNDAY -> "日"
}

@Composable
private fun SleepRow(sleep: SleepSession) {
    val date = Instant.ofEpochMilli(sleep.startEpochMs)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(DateTimeFormatter.ofPattern("M/d EEEE"))
    
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(VitaGradients.cardSurface, MaterialTheme.shapes.small)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Bedtime, contentDescription = null, tint = VitaTertiary, modifier = Modifier.size(24.dp))
            Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                Text(date, color = MaterialTheme.colorScheme.onSurface)
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${sleep.totalMinutes / 60}h ${sleep.totalMinutes % 60}m",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VitaTertiary
                    )
                    Text(
                        "深睡: ${sleep.deepMinutes / 60}h ${sleep.deepMinutes % 60}m",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CyclePeriodRow(period: CyclePeriod, onDelete: () -> Unit) {
    val fmt = DateTimeFormatter.ofPattern("M月d日")
    val dateRange = if (period.days == 1) {
        period.startDate.format(fmt)
    } else {
        "${period.startDate.format(fmt)} — ${period.endDate.format(fmt)}"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(VitaGradients.cardSurface, MaterialTheme.shapes.small)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(dateRange, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "  ${period.days}天",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VitaSecondary,
                    )
                }
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    // Flow dots showing average flow
                    repeat(period.avgFlow.coerceIn(0, 4)) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(VitaSecondary),
                        )
                    }
                    repeat((4 - period.avgFlow).coerceAtLeast(0)) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(VitaSecondary.copy(alpha = 0.15f)),
                        )
                    }
                    Text(
                        text = "  ${flowLabel(period.avgFlow)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "删除")
            }
        }
    }
}

private fun flowLabel(flow: Int): String = when (flow) {
    1 -> "点滴"
    2 -> "轻"
    3 -> "中"
    4 -> "重"
    else -> "—"
}

// ──── 贝叶斯预测卡片 ────────────────────────────────────────

@Composable
private fun BayesianPredictionCard(state: LifeUiState, modifier: Modifier = Modifier) {
    val prediction = state.prediction
    val todayStatus = state.todayStatus
    val dateFmt = DateTimeFormatter.ofPattern("M月d日")

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .background(VitaGradients.cycleAccent, MaterialTheme.shapes.medium)
                .padding(20.dp)
                .fillMaxWidth(),
        ) {
            // 今日状态标签
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val statusColor = when (todayStatus) {
                    DayStatus.MENSTRUAL -> VitaHeart
                    DayStatus.PREDICTED_PERIOD -> VitaSecondary
                    DayStatus.OVULATION -> VitaActive
                    DayStatus.FERTILE -> Color(0xFF66BB6A)
                    DayStatus.SAFE -> VitaPrimary
                    DayStatus.UNKNOWN -> Color.Gray
                }
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Text(
                    text = "今日: ${todayStatus.emoji} ${todayStatus.labelCn}",
                    style = MaterialTheme.typography.labelLarge,
                    color = statusColor,
                )
                if (prediction != null) {
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "第 ${prediction.cycleDay} 天",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (prediction != null) {
                val countdownValue = when {
                    prediction.daysUntilNextPeriod < 0 -> (-prediction.daysUntilNextPeriod).toString()
                    prediction.daysUntilNextPeriod == 0L -> "今"
                    else -> prediction.daysUntilNextPeriod.toString()
                }
                val countdownUnit = when {
                    prediction.daysUntilNextPeriod < 0 -> "天已推迟"
                    prediction.daysUntilNextPeriod == 0L -> "天"
                    else -> "天后"
                }
                // 倒计时
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    Text(
                        text = countdownValue,
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 48.sp,
                        ),
                        color = VitaSecondary,
                    )
                    Text(
                        text = countdownUnit,
                        style = MaterialTheme.typography.titleLarge,
                        color = VitaSecondary.copy(alpha = 0.8f),
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }

                // 预测日期 + 置信区间
                Text(
                    text = "下次预计: ${prediction.nextPeriodStart.format(dateFmt)} ± ${prediction.confidenceIntervalDays}天",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp),
                )

                // 后验周期均值
                Text(
                    text = "贝叶斯周期估计: %.1f ± %.1f 天".format(
                        prediction.posteriorMeanCycleLength,
                        prediction.posteriorStdCycleLength
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )

                // 受孕窗口
                Row(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("🟢", fontSize = 14.sp)
                    Text(
                        text = "受孕窗口: ${prediction.fertileWindowStart.format(dateFmt)} — ${prediction.fertileWindowEnd.format(dateFmt)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                // 排卵日
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("🟡", fontSize = 14.sp)
                    Text(
                        text = "排卵估计: ${prediction.ovulationEstimate.format(dateFmt)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                // 置信等级
                Row(
                    modifier = Modifier.padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "预测置信度",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val progress = when (prediction.confidence) {
                        PredictionConfidence.LOW -> 0.33f
                        PredictionConfidence.MEDIUM -> 0.66f
                        PredictionConfidence.HIGH -> 1.0f
                    }
                    val progressColor = when (prediction.confidence) {
                        PredictionConfidence.LOW -> VitaActive
                        PredictionConfidence.MEDIUM -> VitaSecondary
                        PredictionConfidence.HIGH -> Color(0xFF66BB6A)
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = progressColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Text(
                        text = "${prediction.confidence.labelCn} (${prediction.totalCyclesRecorded}个周期)",
                        style = MaterialTheme.typography.labelSmall,
                        color = progressColor,
                    )
                }
            } else {
                // 无预测时
                Text(
                    text = "记录至少 2 次经期即可启动贝叶斯预测",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    text = "基于 Normal-Normal 共轭先验模型，记录越多预测越准确",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
