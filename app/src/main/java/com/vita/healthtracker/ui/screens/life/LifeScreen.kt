package com.vita.healthtracker.ui.screens.life

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import com.vita.healthtracker.data.local.entity.MoodEntry
import com.vita.healthtracker.data.local.entity.SleepSession
import com.vita.healthtracker.domain.CycleLogLogic
import com.vita.healthtracker.domain.CyclePeriod
import com.vita.healthtracker.domain.CyclePredictionDisplay
import com.vita.healthtracker.domain.CycleRecordType
import com.vita.healthtracker.domain.DayStatus
import com.vita.healthtracker.domain.MoodCatalog
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
    var cycleRecordType by rememberSaveable { mutableStateOf(CycleRecordType.PERIOD) }
    var selectedCycleSymptoms by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var customCycleSymptom by rememberSaveable { mutableStateOf("") }
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
            LifeOverviewCard(
                state = state,
                onMoodClick = { navController.navigate("mood_journal") },
                onHabitClick = { navController.navigate("habit_detail") },
                onSleepClick = { navController.navigate("sleep_detail") },
            )
        }

        // 记录页先服务每天真正会做的动作: 打卡。
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
            HabitBadgeSummaryCard(
                habitEarnedCount = state.earnedBadgeIds.size,
                habitTotal = HabitBadgeCatalog.badges.size,
                fitnessEarnedCount = state.fitnessEarnedIds.size,
                fitnessTotal = HabitBadgeCatalog.fitnessBadges.size,
                longestStreakDays = state.habitLongestStreakDays,
                onClick = { navController.navigate("habit_badges") },
            )
        }

        item {
            MoodSummaryCard(
                moods = state.moods,
                onClick = { navController.navigate("mood_journal") },
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

        item {
            CycleSection(
                state = state,
                expanded = cycleExpanded,
                onExpandedChange = { cycleExpanded = it },
                flow = flow,
                onFlowChange = { flow = it },
                recordType = cycleRecordType,
                onRecordTypeChange = { cycleRecordType = it },
                markStart = markStart,
                onMarkStartChange = { markStart = it },
                selectedSymptoms = selectedCycleSymptoms,
                symptomOptions = state.symptomOptions,
                customSymptom = customCycleSymptom,
                onCustomSymptomChange = { customCycleSymptom = it },
                onToggleSymptom = { symptom ->
                    selectedCycleSymptoms = if (symptom in selectedCycleSymptoms) {
                        selectedCycleSymptoms - symptom
                    } else {
                        selectedCycleSymptoms + symptom
                    }
                },
                onAddCustomSymptom = {
                    val symptom = customCycleSymptom.trim()
                    if (symptom.isNotBlank() && symptom !in selectedCycleSymptoms) {
                        selectedCycleSymptoms = selectedCycleSymptoms + symptom
                    }
                    customCycleSymptom = ""
                },
                expandedPeriods = expandedPeriods,
                onExpandedPeriodsChange = { expandedPeriods = it },
                onOpenDatePicker = { showDatePicker = true },
                onLogToday = {
                    vm.logCycleRange(
                        startDate = LocalDate.now(),
                        endDate = LocalDate.now(),
                        flow = flow,
                        isStart = markStart,
                        recordType = cycleRecordType,
                        symptoms = selectedCycleSymptoms.toSet(),
                    )
                },
                onDeletePeriod = { period -> vm.deleteCyclePeriod(period) },
                onDeleteCycle = { date -> vm.deleteCycle(date) },
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
                        vm.logCycleRange(
                            startDate = startDate,
                            endDate = endDate,
                            flow = flow,
                            isStart = markStart,
                            recordType = cycleRecordType,
                            symptoms = selectedCycleSymptoms.toSet(),
                        )
                    }
                }) { Text("确定") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            androidx.compose.material3.DateRangePicker(
                state = datePickerState,
                title = { Text(text = "选择记录范围", modifier = Modifier.padding(16.dp)) },
                headline = { Text(text = "请选择起止日期", modifier = Modifier.padding(horizontal = 16.dp)) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun LifeOverviewCard(
    state: LifeUiState,
    onMoodClick: () -> Unit,
    onHabitClick: () -> Unit,
    onSleepClick: () -> Unit,
) {
    val today = remember { LocalDate.now() }
    val todayMood = MoodCatalog.byId(state.moods[today.toString()]?.moodId)
    val todayDone = state.habits.count { habit ->
        state.habitCheckIns.any {
            it.habitId == habit.id &&
                it.date == today.toString() &&
                it.status == HabitCheckIn.StatusDone
        }
    }
    val latestSleep = state.recentSleeps.maxByOrNull { it.endEpochMs }
    val week = remember(today) { (6 downTo 0).map { today.minusDays(it.toLong()) } }
    val moodLogged = week.count { state.moods.containsKey(it.toString()) }
    val sleepAverage = state.recentSleeps
        .take(7)
        .takeIf { it.isNotEmpty() }
        ?.map { it.totalMinutes }
        ?.average()
        ?.toLong()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(VitaGradients.primaryAccent, MaterialTheme.shapes.large)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "生活概览",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = lifeOverviewSentence(todayMood?.label, todayDone, state.habits.size, latestSleep),
                        style = MaterialTheme.typography.bodySmall,
                        color = VitaOnSurfaceMuted,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                MoodGlyph(mood = todayMood, size = 44.dp, filled = todayMood != null)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LifeSignalPill(
                    label = "情绪",
                    value = todayMood?.label ?: "未记",
                    detail = if (moodLogged == 7) "近7天都记了" else "近7天记了 $moodLogged 天",
                    color = todayMood?.let { Color(it.colorHex) } ?: VitaPrimary,
                    onClick = onMoodClick,
                    modifier = Modifier.weight(1f),
                )
                LifeSignalPill(
                    label = "习惯",
                    value = "$todayDone/${state.habits.size}",
                    detail = "最长 ${state.habitLongestStreakDays} 天",
                    color = VitaActive,
                    onClick = onHabitClick,
                    modifier = Modifier.weight(1f),
                )
                LifeSignalPill(
                    label = "睡眠",
                    value = latestSleep?.let { sleepTextShort(it.totalMinutes) } ?: "暂无",
                    detail = sleepAverage?.let { "近7次平均 ${sleepTextShort(it)}" } ?: "近14天",
                    color = VitaTertiary,
                    onClick = onSleepClick,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun LifeSignalPill(
    label: String,
    value: String,
    detail: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = VitaOnSurfaceMuted, maxLines = 1)
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            color = color,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(detail, style = MaterialTheme.typography.labelSmall, color = VitaOnSurfaceMuted, maxLines = 1)
    }
}

private fun lifeOverviewSentence(
    moodLabel: String?,
    habitDone: Int,
    habitTotal: Int,
    sleep: SleepSession?,
): String {
    val mood = moodLabel?.let { "今天心情：$it" } ?: "今天还没记心情"
    val habit = if (habitTotal > 0) "习惯完成 $habitDone/$habitTotal" else "还没设置习惯"
    val sleepText = sleep?.let { "昨晚睡了 ${sleepTextLong(it.totalMinutes)}" } ?: "暂无睡眠记录"
    return "$mood；$habit；$sleepText"
}

private fun sleepTextShort(minutes: Long): String = "${minutes / 60}h${minutes % 60}m"

private fun sleepTextLong(minutes: Long): String = "${minutes / 60} 小时 ${minutes % 60} 分钟"

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CycleSection(
    state: LifeUiState,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    flow: Int,
    onFlowChange: (Int) -> Unit,
    recordType: CycleRecordType,
    onRecordTypeChange: (CycleRecordType) -> Unit,
    markStart: Boolean,
    onMarkStartChange: (Boolean) -> Unit,
    selectedSymptoms: List<String>,
    symptomOptions: List<String>,
    customSymptom: String,
    onCustomSymptomChange: (String) -> Unit,
    onToggleSymptom: (String) -> Unit,
    onAddCustomSymptom: () -> Unit,
    expandedPeriods: Boolean,
    onExpandedPeriodsChange: (Boolean) -> Unit,
    onOpenDatePicker: () -> Unit,
    onLogToday: () -> Unit,
    onDeletePeriod: (CyclePeriod) -> Unit,
    onDeleteCycle: (String) -> Unit,
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
                        FilterChip(
                            selected = recordType == CycleRecordType.PERIOD,
                            onClick = { onRecordTypeChange(CycleRecordType.PERIOD) },
                            label = { Text("月经") },
                        )
                        FilterChip(
                            selected = recordType == CycleRecordType.SPOTTING,
                            onClick = { onRecordTypeChange(CycleRecordType.SPOTTING) },
                            label = { Text("点滴出血") },
                        )
                    }
                    if (recordType == CycleRecordType.PERIOD) {
                        Row(
                            modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
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
                    }
                    Text(
                        text = "症状",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        (symptomOptions + selectedSymptoms).distinct().forEach { symptom ->
                            FilterChip(
                                selected = symptom in selectedSymptoms,
                                onClick = { onToggleSymptom(symptom) },
                                label = { Text(symptom) },
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = customSymptom,
                            onValueChange = onCustomSymptomChange,
                            label = { Text("新增症状") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = onAddCustomSymptom,
                            enabled = customSymptom.isNotBlank(),
                        ) {
                            Text("添加")
                        }
                    }
                    Row(
                        modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (recordType == CycleRecordType.PERIOD) {
                            FilterChip(
                                selected = markStart,
                                onClick = { onMarkStartChange(!markStart) },
                                label = { Text("标记为周期起始日") },
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        AssistChip(
                            onClick = onLogToday,
                            label = {
                                Text(
                                    if (recordType == CycleRecordType.SPOTTING) {
                                        "记录点滴"
                                    } else {
                                        stringResource(R.string.cycle_log_period)
                                    }
                                )
                            },
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
            if (state.spottingEntries.isNotEmpty()) {
                Text(
                    "近期点滴出血",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp),
                )
                state.spottingEntries.take(5).forEach { entry ->
                    CycleSpottingRow(entry = entry, onDelete = { onDeleteCycle(entry.date) })
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
    val symptoms = period.entries
        .flatMap { CycleLogLogic.decodeSymptoms(it.symptomsCsv) }
        .distinct()

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
                if (symptoms.isNotEmpty()) {
                    Text(
                        text = symptoms.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = VitaOnSurfaceMuted,
                        modifier = Modifier.padding(top = 4.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "删除")
            }
        }
    }
}

@Composable
private fun CycleSpottingRow(entry: CycleEntry, onDelete: () -> Unit) {
    val fmt = DateTimeFormatter.ofPattern("M月d日")
    val dateText = runCatching { LocalDate.parse(entry.date).format(fmt) }.getOrElse { entry.date }
    val symptoms = CycleLogLogic.decodeSymptoms(entry.symptomsCsv)

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
                Text(dateText, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(VitaSecondary),
                    )
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(VitaSecondary.copy(alpha = 0.15f)),
                        )
                    }
                    Text(
                        text = "  点滴出血",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (symptoms.isNotEmpty()) {
                    Text(
                        text = symptoms.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = VitaOnSurfaceMuted,
                        modifier = Modifier.padding(top = 4.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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

// ──── 周期预测卡片 ────────────────────────────────────────

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
                        text = if (todayStatus == DayStatus.MENSTRUAL && state.todayPeriodDay != null) {
                            "经期第 ${state.todayPeriodDay} 天"
                        } else {
                            "第 ${prediction.cycleDay} 天"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (prediction != null) {
                val countdown = CyclePredictionDisplay.countdown(
                    prediction = prediction,
                    todayStatus = todayStatus,
                    todayPeriodDay = state.todayPeriodDay,
                )
                // 倒计时
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    Text(
                        text = countdown.value,
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 48.sp,
                        ),
                        color = VitaSecondary,
                    )
                    Text(
                        text = countdown.unit,
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

                Text(
                    text = "根据已记录的周期，近期平均约 %.1f 天，可能上下浮动 %.1f 天。".format(
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
                Text(
                    text = "至少记录 2 次经期后，就能开始给出预计日期",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    text = "记录越完整，预计日期会越贴近你的实际节奏。这里只作为生活提醒，不用于避孕或医疗判断。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/**
 * 生活页「情绪」入口卡：左侧今日心情发光体 + 标签/记录提示，右侧近 7 天迷你情绪条。
 * 点击进入情绪轨迹页 (mood_journal)。
 */
@Composable
private fun MoodSummaryCard(
    moods: Map<String, MoodEntry>,
    onClick: () -> Unit,
) {
    val today = remember { LocalDate.now() }
    val week = remember(today) { (6 downTo 0).map { today.minusDays(it.toLong()) } }
    val todayMood = MoodCatalog.byId(moods[today.toString()]?.moodId)
    val logged = week.count { moods.containsKey(it.toString()) }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(VitaGradients.primaryAccent, MaterialTheme.shapes.medium)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 与成就系统卡片的徽章槽 (78dp) 等宽, 保证两张卡片文本列左缘对齐。
            Box(modifier = Modifier.size(78.dp), contentAlignment = Alignment.Center) {
                MoodGlyph(mood = todayMood, size = 52.dp, filled = todayMood != null)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "情绪轨迹",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = todayMood?.let { "今天 · ${it.label}" } ?: "记录今天的心情",
                    style = MaterialTheme.typography.bodyMedium,
                    color = todayMood?.let { Color(it.colorHex) } ?: VitaOnSurfaceMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "近 7 天已记录 $logged / 7",
                    style = MaterialTheme.typography.labelSmall,
                    color = VitaOnSurfaceMuted,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    week.forEach { d ->
                        val m = MoodCatalog.byId(moods[d.toString()]?.moodId)
                        MoodGlyph(mood = m, size = 18.dp, filled = m != null)
                    }
                }
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "查看情绪轨迹", tint = VitaOnSurfaceMuted)
        }
    }
}
