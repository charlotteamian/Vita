package com.vita.healthtracker.ui.screens.life

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vita.healthtracker.data.local.entity.HabitCheckIn
import com.vita.healthtracker.data.local.entity.HabitDefinition
import com.vita.healthtracker.domain.FitnessProgress
import com.vita.healthtracker.domain.HabitBadge
import com.vita.healthtracker.domain.HabitBadgeCatalog
import com.vita.healthtracker.ui.theme.VitaActive
import com.vita.healthtracker.ui.theme.VitaGradients
import com.vita.healthtracker.ui.theme.VitaOnSurfaceMuted
import com.vita.healthtracker.ui.theme.VitaPrimary
import com.vita.healthtracker.ui.vitaViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun HabitBadgeSummaryCard(
    habitEarnedCount: Int,
    habitTotal: Int,
    fitnessEarnedCount: Int,
    fitnessTotal: Int,
    longestStreakDays: Int,
    onClick: () -> Unit,
) {
    val totalEarned = habitEarnedCount + fitnessEarnedCount
    val grandTotal = habitTotal + fitnessTotal
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
            AchievementCrest(
                totalEarned = totalEarned,
                grandTotal = grandTotal.coerceAtLeast(1),
                modifier = Modifier.size(78.dp),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "成就系统",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "$totalEarned",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "/ $grandTotal 枚",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VitaOnSurfaceMuted,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                Text(
                    "习惯 $habitEarnedCount · 健身 $fitnessEarnedCount · 最长连续 $longestStreakDays 天",
                    style = MaterialTheme.typography.bodySmall,
                    color = VitaOnSurfaceMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "查看成就", tint = VitaOnSurfaceMuted)
        }
    }
}

/**
 * 成就系统标识：八角徽轮廓 + 一圈节点（亮 = 已获得）+ 旋转 + 中央脉冲核 + 大字号当前进度。
 * 习惯打卡 + 健身运动两套徽章合并显示，节点总数 = grandTotal。
 */
@Composable
private fun AchievementCrest(
    totalEarned: Int,
    grandTotal: Int,
    modifier: Modifier = Modifier,
) {
    val infinite = rememberInfiniteTransition(label = "crest")
    val rotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 24_000, easing = LinearEasing)),
        label = "rot",
    )
    val pulse by infinite.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val side = min(size.width, size.height)
            val cx = size.width / 2f
            val cy = size.height / 2f
            val outer = side / 2f * 0.94f
            val ringRadius = outer * 0.74f

            // —— 背景光晕 ——
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(VitaActive.copy(alpha = 0.42f * pulse), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = outer,
                ),
                radius = outer,
                center = Offset(cx, cy),
            )

            // —— 八角徽轮廓 ——
            val octPath = Path().apply {
                for (i in 0..7) {
                    val angle = (rotation + i * 45f - 22.5f) * Math.PI / 180f
                    val x = cx + outer * cos(angle).toFloat()
                    val y = cy + outer * sin(angle).toFloat()
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            drawPath(
                octPath,
                color = Color.White.copy(alpha = 0.22f),
                style = Stroke(width = 1.6f),
            )

            // —— 一圈成就节点（grandTotal 个，亮 = 已获得）——
            val safeTotal = grandTotal.coerceAtLeast(1)
            for (i in 0 until safeTotal) {
                val angle = (i * 360f / safeTotal - 90f + rotation * 0.4f) * Math.PI / 180f
                val r1 = ringRadius
                val r2 = ringRadius + outer * 0.16f
                val filled = i < totalEarned
                val color = if (filled) VitaActive else Color.White.copy(alpha = 0.18f)
                val width = if (filled) 3.4f else 1.6f
                drawLine(
                    color = color,
                    start = Offset(cx + r1 * cos(angle).toFloat(), cy + r1 * sin(angle).toFloat()),
                    end = Offset(cx + r2 * cos(angle).toFloat(), cy + r2 * sin(angle).toFloat()),
                    strokeWidth = width,
                    cap = StrokeCap.Round,
                )
            }

            // —— 内圈细环 ——
            drawCircle(
                color = Color.White.copy(alpha = 0.22f),
                radius = ringRadius * 0.72f,
                center = Offset(cx, cy),
                style = Stroke(width = 1.1f),
            )

            // —— 中央脉冲核 ——
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(VitaActive.copy(alpha = 0.85f), VitaActive.copy(alpha = 0f)),
                    center = Offset(cx, cy),
                    radius = ringRadius * 0.58f,
                ),
                radius = ringRadius * 0.58f * pulse,
                center = Offset(cx, cy),
            )
        }
        Text(
            text = totalEarned.toString(),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitDetailScreen(onBack: () -> Unit) {
    val vm = vitaViewModel<LifeViewModel>()
    val state by vm.state.collectAsStateWithLifecycle()
    var rangeDays by rememberSaveable { mutableIntStateOf(7) }
    var selectedDateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var showHabitDialog by rememberSaveable { mutableStateOf(false) }
    var showManageDialog by rememberSaveable { mutableStateOf(false) }
    var newHabitName by rememberSaveable { mutableStateOf("") }
    var pendingDeleteHabit by remember { mutableStateOf<HabitDefinition?>(null) }
    var pendingRenameHabit by remember { mutableStateOf<HabitDefinition?>(null) }
    var renamedHabitName by rememberSaveable { mutableStateOf("") }
    val today = LocalDate.now()
    val selectedDate = remember(selectedDateText) {
        runCatching { LocalDate.parse(selectedDateText) }.getOrDefault(today)
    }
    val dates = remember(rangeDays, today) { (rangeDays - 1 downTo 0).map { today.minusDays(it.toLong()) } }
    val calendarDates = remember(dates) { buildHabitCalendarDates(dates) }
    val statusMap = remember(state.habitCheckIns) { state.habitCheckIns.associateBy { it.habitId to it.date } }
    var badgeCheckArmed by rememberSaveable { mutableStateOf(false) }
    var pendingCelebrationToken by remember { mutableStateOf<String?>(null) }
    var celebrationBadge by remember { mutableStateOf<HabitBadge?>(null) }

    LaunchedEffect(rangeDays) {
        if (selectedDate !in dates) {
            selectedDateText = today.toString()
        }
    }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("习惯记录") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { showManageDialog = true }) {
                        Text("管理")
                    }
                    IconButton(onClick = { showHabitDialog = true }) {
                        Icon(Icons.Outlined.Add, contentDescription = "新增习惯")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                HabitProgressHeader(state.habitCurrentStreakDays, state.habitLongestStreakDays, state.earnedBadgeTotalCount)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(1 to "1天", 7 to "7天", 28 to "4周", 84 to "12周").forEach { (days, label) ->
                        FilterChip(
                            selected = rangeDays == days,
                            onClick = { rangeDays = days },
                            label = { Text(label) },
                        )
                    }
                }
            }
            if (state.habits.isEmpty()) {
                item {
                    Text(
                        "还没有习惯。点右上角 + 添加一个。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                item {
                    HabitCalendarGrid(
                        calendarDates = calendarDates,
                        activeDates = dates.toSet(),
                        selectedDate = selectedDate,
                        habits = state.habits,
                        statusMap = statusMap,
                        onSelect = { date -> selectedDateText = date.toString() },
                    )
                }
                item {
                    SelectedDateHabitEditor(
                        date = selectedDate,
                        habits = state.habits,
                        statusMap = statusMap,
                        onMark = { habit, date, status ->
                            val current = statusMap[habit.id to date.toString()]?.status
                            badgeCheckArmed = true
                            vm.markHabit(habit.id, date, status, current)
                        },
                    )
                }
                item {
                    HabitStatsSection(
                        habits = state.habits,
                        dates = dates,
                        statusMap = statusMap,
                    )
                }
            }
        }
    }

    if (showHabitDialog) {
        HabitCreateDialog(
            name = newHabitName,
            onNameChange = { newHabitName = it },
            onDismiss = {
                showHabitDialog = false
                newHabitName = ""
            },
            onConfirm = {
                vm.createHabit(newHabitName)
                showHabitDialog = false
                newHabitName = ""
            },
        )
    }

    if (showManageDialog) {
        HabitManageDialog(
            habits = state.habits,
            onDismiss = { showManageDialog = false },
            onRequestDelete = { habit ->
                showManageDialog = false
                pendingDeleteHabit = habit
            },
            onRequestRename = { habit ->
                showManageDialog = false
                pendingRenameHabit = habit
                renamedHabitName = habit.name
            },
        )
    }

    pendingRenameHabit?.let { habit ->
        AlertDialog(
            onDismissRequest = {
                pendingRenameHabit = null
                renamedHabitName = ""
            },
            title = { Text("修改习惯名称") },
            text = {
                OutlinedTextField(
                    value = renamedHabitName,
                    onValueChange = { renamedHabitName = it },
                    label = { Text("习惯名称") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.renameHabit(habit.id, renamedHabitName)
                        pendingRenameHabit = null
                        renamedHabitName = ""
                    },
                    enabled = renamedHabitName.isNotBlank(),
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingRenameHabit = null
                    renamedHabitName = ""
                }) { Text("取消") }
            },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }

    pendingDeleteHabit?.let { habit ->
        AlertDialog(
            onDismissRequest = { pendingDeleteHabit = null },
            title = { Text("删除习惯") },
            text = { Text("确定删除「${habit.name}」吗？历史打卡记录会保留在本机，但这个习惯不会再显示。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.archiveHabit(habit.id)
                        pendingDeleteHabit = null
                    },
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteHabit = null }) {
                    Text("取消")
                }
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitBadgeDetailScreen(onBack: () -> Unit) {
    val vm = vitaViewModel<LifeViewModel>()
    val state by vm.state.collectAsStateWithLifecycle()
    var detailBadge by remember { mutableStateOf<HabitBadge?>(null) }
    var celebrationBadge by remember { mutableStateOf<HabitBadge?>(null) }
    var celebrationFitnessId by remember { mutableStateOf<String?>(null) }

    // 进入徽章页就检查未弹的健身徽章庆祝
    LaunchedEffect(state.fitnessEarnedIds, state.shownFitnessBadgeIds) {
        if (celebrationBadge != null) return@LaunchedEffect
        val newly = state.fitnessEarnedIds - state.shownFitnessBadgeIds
        val id = newly.firstOrNull() ?: return@LaunchedEffect
        val badge = HabitBadgeCatalog.fitnessBadges.firstOrNull { it.id == id }
        if (badge == null) {
            vm.markFitnessBadgeCelebrationShown(id)
        } else {
            celebrationFitnessId = id
            celebrationBadge = badge
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("徽章") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                BadgeTotalsHeader(
                    habitEarnedCount = state.earnedBadgeIds.size,
                    habitTotal = HabitBadgeCatalog.badges.size,
                    fitnessEarnedCount = state.fitnessEarnedIds.size,
                    fitnessTotal = HabitBadgeCatalog.fitnessBadges.size,
                )
            }
            item {
                BadgeWallSection(
                    title = "习惯打卡徽章",
                    subtitle = "最长连续 ${state.habitLongestStreakDays} 天",
                    badges = HabitBadgeCatalog.badges,
                    earnedIds = state.earnedBadgeIds,
                    onTap = { detailBadge = it },
                )
            }
            item {
                BadgeWallSection(
                    title = "健身运动徽章",
                    subtitle = "按训练次数、心率、距离、热量解锁",
                    badges = HabitBadgeCatalog.fitnessBadges,
                    earnedIds = state.fitnessEarnedIds,
                    onTap = { detailBadge = it },
                )
            }
        }
    }

    detailBadge?.let { badge ->
        BadgeDetailDialog(
            badge = badge,
            earned = badge.id in state.earnedBadgeIds || badge.id in state.fitnessEarnedIds,
            fitnessProgress = state.fitnessProgress[badge.id],
            longestStreakDays = state.habitLongestStreakDays,
            unlockDateText = state.badgeUnlockDates[badge.id]?.let { iso ->
                runCatching {
                    LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("yyyy年M月d日"))
                }.getOrDefault(iso)
            },
            onDismiss = { detailBadge = null },
        )
    }

    celebrationBadge?.let { badge ->
        HabitBadgeCelebrationDialog(
            badge = badge,
            onDismiss = {
                celebrationFitnessId?.let { vm.markFitnessBadgeCelebrationShown(it) }
                celebrationFitnessId = null
                celebrationBadge = null
            },
        )
    }
}

// === 徽章墙 · 紧凑布局组件 ====================================================

@Composable
private fun BadgeTotalsHeader(
    habitEarnedCount: Int,
    habitTotal: Int,
    fitnessEarnedCount: Int,
    fitnessTotal: Int,
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.Transparent), shape = MaterialTheme.shapes.medium) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(VitaGradients.primaryAccent, MaterialTheme.shapes.medium)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "共获得",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${habitEarnedCount + fitnessEarnedCount}",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "/${habitTotal + fitnessTotal} 枚",
                    style = MaterialTheme.typography.titleMedium,
                    color = VitaOnSurfaceMuted,
                    modifier = Modifier.padding(bottom = 11.dp),
                )
                Spacer(modifier = Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        "习惯  $habitEarnedCount / $habitTotal",
                        style = MaterialTheme.typography.bodySmall,
                        color = VitaOnSurfaceMuted,
                    )
                    Text(
                        "健身  $fitnessEarnedCount / $fitnessTotal",
                        style = MaterialTheme.typography.bodySmall,
                        color = VitaOnSurfaceMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun BadgeWallSection(
    title: String,
    subtitle: String,
    badges: List<HabitBadge>,
    earnedIds: Set<String>,
    onTap: (HabitBadge) -> Unit,
) {
    val earnedCount = badges.count { it.id in earnedIds }
    Card(colors = CardDefaults.cardColors(containerColor = Color.Transparent), shape = MaterialTheme.shapes.medium) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(VitaGradients.cardSurface, MaterialTheme.shapes.medium)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = VitaOnSurfaceMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    "$earnedCount / ${badges.size}",
                    style = MaterialTheme.typography.titleSmall,
                    color = VitaActive,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            badges.chunked(3).forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    row.forEach { badge ->
                        BadgeTile(
                            badge = badge,
                            earned = badge.id in earnedIds,
                            onClick = { onTap(badge) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun BadgeTile(
    badge: HabitBadge,
    earned: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        HabitBadgeImage(badge, earned = earned, iconSize = 72.dp)
        Text(
            badge.name,
            style = MaterialTheme.typography.bodySmall,
            color = if (earned) MaterialTheme.colorScheme.onSurface else VitaOnSurfaceMuted,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            badge.tag,
            style = MaterialTheme.typography.labelSmall,
            color = if (earned) VitaActive else VitaOnSurfaceMuted.copy(alpha = 0.75f),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun BadgeDetailDialog(
    badge: HabitBadge,
    earned: Boolean,
    fitnessProgress: FitnessProgress?,
    longestStreakDays: Int,
    unlockDateText: String?,
    onDismiss: () -> Unit,
) {
    val isHabitBadge = badge.systemId == HabitBadgeCatalog.systemId
    val ratio = fitnessProgress?.ratio
        ?: if (isHabitBadge) (longestStreakDays.toFloat() / badge.thresholdDays.toFloat()).coerceIn(0f, 1f) else 0f
    val conditionText = if (isHabitBadge) {
        "连续 ${badge.thresholdDays} 天"
    } else {
        badge.tag
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                HabitBadge3DCard(
                    badge = badge,
                    earned = earned,
                    conditionText = conditionText,
                    unlockDateText = if (earned) unlockDateText else null,
                )
                if (!earned) {
                    LinearProgressIndicator(
                        progress = { ratio },
                        color = VitaActive,
                        trackColor = VitaActive.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth().height(5.dp),
                    )
                    Text(
                        "未获得 · 已达成 ${(ratio * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = VitaOnSurfaceMuted,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

@Composable
fun HabitBadgeCelebrationDialog(badge: HabitBadge, onDismiss: () -> Unit) {
    val conditionText = if (badge.systemId == HabitBadgeCatalog.systemId) {
        "连续打卡 ${badge.thresholdDays} 天"
    } else {
        badge.tag
    }
    val todayText = remember { LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年M月d日")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "获得徽章",
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        text = {
            HabitBadge3DCard(
                badge = badge,
                earned = true,
                conditionText = conditionText,
                unlockDateText = todayText,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("收下")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

/**
 * 健身徽章实时庆祝宿主。落地在 TodayScreen / LifeScreen / HabitBadgeDetailScreen 上，
 * 任意一处当前显示且 fitnessEarnedIds 出现新成员时弹庆祝弹窗。
 *
 * 多个宿主同时存在时，先弹的那个 onDismiss 写到 prefs，其他宿主的 flow 一刷新就不会再弹。
 */
@Composable
fun FitnessBadgeCelebrationHost() {
    val vm = vitaViewModel<LifeViewModel>()
    val state by vm.state.collectAsStateWithLifecycle()
    var celebrationBadge by remember { mutableStateOf<HabitBadge?>(null) }
    var celebrationId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.fitnessEarnedIds, state.shownFitnessBadgeIds) {
        if (celebrationBadge != null) return@LaunchedEffect
        val newly = state.fitnessEarnedIds - state.shownFitnessBadgeIds
        val id = newly.firstOrNull() ?: return@LaunchedEffect
        val badge = HabitBadgeCatalog.fitnessBadges.firstOrNull { it.id == id }
        if (badge == null) {
            vm.markFitnessBadgeCelebrationShown(id)
        } else {
            celebrationId = id
            celebrationBadge = badge
        }
    }

    celebrationBadge?.let { badge ->
        HabitBadgeCelebrationDialog(
            badge = badge,
            onDismiss = {
                celebrationId?.let { vm.markFitnessBadgeCelebrationShown(it) }
                celebrationId = null
                celebrationBadge = null
            },
        )
    }
}

@Composable
private fun HabitProgressHeader(currentStreak: Int, longestStreak: Int, earnedCount: Int) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.Transparent), shape = MaterialTheme.shapes.medium) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(VitaGradients.primaryAccent, MaterialTheme.shapes.medium)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("连续打卡", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Bottom) {
                Text("$currentStreak", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                Text("天当前连续", style = MaterialTheme.typography.bodyMedium, color = VitaOnSurfaceMuted, modifier = Modifier.padding(bottom = 9.dp))
                Spacer(modifier = Modifier.weight(1f))
                Text("最长 $longestStreak 天 · 已获 $earnedCount 枚", style = MaterialTheme.typography.bodySmall, color = VitaOnSurfaceMuted)
            }
        }
    }
}

@Composable
private fun HabitCalendarGrid(
    calendarDates: List<LocalDate>,
    activeDates: Set<LocalDate>,
    selectedDate: LocalDate,
    habits: List<HabitDefinition>,
    statusMap: Map<Pair<String, String>, HabitCheckIn>,
    onSelect: (LocalDate) -> Unit,
) {
    val weeks = remember(calendarDates) { calendarDates.chunked(7) }
    Card(colors = CardDefaults.cardColors(containerColor = Color.Transparent), shape = MaterialTheme.shapes.medium) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(VitaGradients.cardSurface, MaterialTheme.shapes.medium)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                    Text(
                        label,
                        style = MaterialTheme.typography.bodySmall,
                        color = VitaOnSurfaceMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            weeks.forEachIndexed { index, week ->
                val monthTitle = monthTitleForWeek(week, index == 0)
                if (monthTitle != null) {
                    Text(
                        monthTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = if (index == 0) 0.dp else 6.dp),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    week.forEach { date ->
                        HabitCalendarDateCell(
                            date = date,
                            isActive = date in activeDates,
                            isSelected = date == selectedDate,
                            habits = habits,
                            statusMap = statusMap,
                            onClick = { onSelect(date) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HabitCalendarDateCell(
    date: LocalDate,
    isActive: Boolean,
    isSelected: Boolean,
    habits: List<HabitDefinition>,
    statusMap: Map<Pair<String, String>, HabitCheckIn>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = when {
        isSelected -> VitaPrimary.copy(alpha = 0.22f)
        isActive -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.08f)
    }
    val dayColor = when {
        isSelected -> MaterialTheme.colorScheme.onSurface
        isActive -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> VitaOnSurfaceMuted.copy(alpha = 0.38f)
    }
    Column(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = dayColor,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            habits.take(4).forEach { habit ->
                val status = statusMap[habit.id to date.toString()]?.status
                val color = when (status) {
                    HabitCheckIn.StatusDone -> Color(habit.colorHex)
                    HabitCheckIn.StatusMissed -> VitaOnSurfaceMuted.copy(alpha = 0.45f)
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isActive) 0.7f else 0.2f)
                }
                Box(modifier = Modifier.size(5.dp).background(color, CircleShape))
            }
        }
    }
}

@Composable
private fun SelectedDateHabitEditor(
    date: LocalDate,
    habits: List<HabitDefinition>,
    statusMap: Map<Pair<String, String>, HabitCheckIn>,
    onMark: (HabitDefinition, LocalDate, Int) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.Transparent), shape = MaterialTheme.shapes.medium) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(VitaGradients.cardSurface, MaterialTheme.shapes.medium)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        date.format(DateTimeFormatter.ofPattern("M月d日")),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "打卡记录",
                        style = MaterialTheme.typography.bodySmall,
                        color = VitaOnSurfaceMuted,
                    )
                }
            }
            habits.forEach { habit ->
                val accent = Color(habit.colorHex)
                val status = statusMap[habit.id to date.toString()]?.status
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(modifier = Modifier.size(10.dp).background(accent, CircleShape))
                    Text(
                        text = habit.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    HabitStatusMiniButton(
                        selected = status == HabitCheckIn.StatusMissed,
                        icon = Icons.Outlined.Close,
                        color = VitaOnSurfaceMuted,
                        contentDescription = "未完成",
                        onClick = { onMark(habit, date, HabitCheckIn.StatusMissed) },
                    )
                    HabitStatusMiniButton(
                        selected = status == HabitCheckIn.StatusDone,
                        icon = Icons.Outlined.Check,
                        color = accent,
                        contentDescription = "完成",
                        onClick = { onMark(habit, date, HabitCheckIn.StatusDone) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HabitStatusMiniButton(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) color.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)),
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(21.dp),
        )
    }
}

@Composable
private fun HabitStatsSection(
    habits: List<HabitDefinition>,
    dates: List<LocalDate>,
    statusMap: Map<Pair<String, String>, HabitCheckIn>,
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.Transparent), shape = MaterialTheme.shapes.medium) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(VitaGradients.cardSurface, MaterialTheme.shapes.medium)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "本周期统计",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            habits.forEach { habit ->
                val accent = Color(habit.colorHex)
                val doneDays = dates.count { date -> statusMap[habit.id to date.toString()]?.status == HabitCheckIn.StatusDone }
                val missedDays = dates.count { date -> statusMap[habit.id to date.toString()]?.status == HabitCheckIn.StatusMissed }
                val progress = if (dates.isEmpty()) 0f else doneDays.toFloat() / dates.size.toFloat()
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.padding(top = 7.dp).size(8.dp).background(accent, CircleShape))
                        Text(
                            habit.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "$doneDays 天",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        color = accent,
                        trackColor = accent.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth().height(5.dp),
                    )
                    Text(
                        "完成 $doneDays 天 · 未完成 $missedDays 天 · 未记录 ${dates.size - doneDays - missedDays} 天",
                        style = MaterialTheme.typography.bodySmall,
                        color = VitaOnSurfaceMuted,
                    )
                }
            }
        }
    }
}

// (原 HabitBadgeRequirementCard 已被徽章墙 / BadgeDetailDialog 取代)

@Composable
private fun HabitCreateDialog(
    name: String,
    onNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增习惯") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text("习惯名称") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = name.isNotBlank()) {
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

@Composable
private fun HabitManageDialog(
    habits: List<HabitDefinition>,
    onDismiss: () -> Unit,
    onRequestDelete: (HabitDefinition) -> Unit,
    onRequestRename: (HabitDefinition) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("管理习惯") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (habits.isEmpty()) {
                    Text(
                        "还没有习惯。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    habits.forEach { habit ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(modifier = Modifier.size(9.dp).background(Color(habit.colorHex), CircleShape))
                            Text(
                                habit.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { onRequestRename(habit) }) {
                                Text("改名")
                            }
                            TextButton(onClick = { onRequestDelete(habit) }) {
                                Text("删除")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("完成")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    )
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

private fun buildHabitCalendarDates(activeDates: List<LocalDate>): List<LocalDate> {
    if (activeDates.isEmpty()) return emptyList()
    val start = activeDates.minOrNull() ?: return emptyList()
    val end = activeDates.maxOrNull() ?: start
    val gridStart = start.minusDays((start.dayOfWeek.value - 1).toLong())
    val gridEnd = end.plusDays((7 - end.dayOfWeek.value).toLong())
    val days = java.time.temporal.ChronoUnit.DAYS.between(gridStart, gridEnd).toInt()
    return (0..days).map { gridStart.plusDays(it.toLong()) }
}

private fun monthTitleForWeek(week: List<LocalDate>, isFirstWeek: Boolean): String? {
    if (week.isEmpty()) return null
    val titleDate = if (isFirstWeek) week.first() else week.firstOrNull { it.dayOfMonth == 1 }
    return titleDate?.format(DateTimeFormatter.ofPattern("yyyy年M月"))
}
