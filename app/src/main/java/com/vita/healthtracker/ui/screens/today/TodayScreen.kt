package com.vita.healthtracker.ui.screens.today

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Bloodtype
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Stairs
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.vita.healthtracker.data.local.entity.ExerciseSession
import com.vita.healthtracker.data.sync.SyncScope
import com.vita.healthtracker.domain.ExerciseClassifier
import com.vita.healthtracker.domain.HomeMetric
import com.vita.healthtracker.ui.components.MetricCard
import com.vita.healthtracker.ui.theme.VitaActive
import com.vita.healthtracker.ui.theme.VitaError
import com.vita.healthtracker.ui.theme.VitaGradients
import com.vita.healthtracker.ui.theme.VitaHeart
import com.vita.healthtracker.ui.screens.life.FitnessBadgeCelebrationHost
import com.vita.healthtracker.ui.theme.VitaPrimary
import com.vita.healthtracker.ui.theme.VitaTertiary
import com.vita.healthtracker.ui.vitaViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 首页一张指标卡的数据 (含它对应的 HomeMetric, 用于按偏好过滤)。 */
private data class HomeCard(
    val metric: HomeMetric,
    val value: String,
    val unit: String?,
    val icon: ImageVector,
    val accent: Color,
    val onClick: (() -> Unit)? = null,
)

@Composable
fun TodayScreen(navController: NavController) {
    val vm = vitaViewModel<TodayViewModel>()
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showExercisePicker by remember { mutableStateOf(false) }
    var showAllTodayData by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.syncMessage) {
        state.syncMessage?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ─── 头部: 日期 + 同步状态 (#8 重排) ───
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Box(
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconButton(
                    onClick = vm::previousDay,
                    modifier = Modifier.align(Alignment.CenterStart),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "前一天")
                }
                Text(
                    text = state.date.format(DateTimeFormatter.ofPattern("M月d日")),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.Center),
                )
                IconButton(
                    onClick = vm::nextDay,
                    enabled = state.date.isBefore(LocalDate.now()),
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "后一天")
                }
            }

            // 回到今日 (#6): 仅当不在今天时显示
            if (!state.isToday) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    AssistChip(
                        onClick = vm::jumpToToday,
                        label = { Text("回到今日") },
                        leadingIcon = {
                            Icon(Icons.Outlined.Today, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        colors = AssistChipDefaults.assistChipColors(labelColor = VitaPrimary, leadingIconContentColor = VitaPrimary),
                    )
                }
            }

            // 同步状态行: 右侧只保留下拉图标, 避免小屏把日期/上次同步挤没。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.lastSyncedAt
                            ?.let { "上次同步 ${it.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}" }
                            ?: "尚未同步",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                    if (state.syncing && state.syncPhase.isNotBlank()) {
                        Text(
                            text = "同步中 ${((state.syncFraction.takeIf { it > 0f } ?: 0f) * 100).toInt().coerceIn(0, 100)}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = VitaPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.size(12.dp))
                if (state.syncing) {
                    StopSyncButton(onStop = vm::stopSync)
                } else {
                    SyncMenuButton(onSync = vm::sync)
                }
            }

            if (state.syncing) {
                LinearProgressIndicator(
                    progress = { if (state.syncFraction > 0f) state.syncFraction else 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    color = VitaPrimary,
                    trackColor = VitaPrimary.copy(alpha = 0.12f),
                )
            }
        }

        // ─── 指标卡片 (按 HomeMetric 声明顺序, 再按设置勾选过滤) ───
        val daily = state.daily
        val bodyBatteryValue = state.bodyBatterySamples.lastOrNull()?.level ?: daily?.bodyBatteryHigh
        val allCards = listOf(
            HomeCard(HomeMetric.STEPS, daily?.steps?.toString() ?: "—", "步", Icons.AutoMirrored.Outlined.DirectionsRun, VitaPrimary),
            HomeCard(HomeMetric.DISTANCE, daily?.distanceMeters?.let { String.format("%.2f", it / 1000) } ?: "—", "km", Icons.Outlined.Straighten, VitaPrimary),
            HomeCard(HomeMetric.ACTIVE_CALORIES, daily?.activeCalories?.toInt()?.toString() ?: "—", "kcal", Icons.Outlined.LocalFireDepartment, VitaActive),
            HomeCard(HomeMetric.TOTAL_CALORIES, daily?.totalCalories?.toInt()?.toString() ?: "—", "kcal", Icons.Outlined.LocalFireDepartment, VitaActive),
            HomeCard(HomeMetric.ACTIVE_MINUTES, daily?.activeMinutes?.toString() ?: "—", "min", Icons.AutoMirrored.Outlined.DirectionsRun, VitaActive),
            HomeCard(HomeMetric.FLOORS, daily?.floorsClimbed?.toInt()?.toString() ?: "—", "层", Icons.Outlined.Stairs, VitaActive),
            HomeCard(HomeMetric.AVG_HEART_RATE, daily?.avgHeartRate?.toString() ?: "—", "bpm", Icons.Outlined.Favorite, VitaHeart),
            HomeCard(HomeMetric.RESTING_HEART_RATE, daily?.restingHeartRate?.toString() ?: "—", "bpm", Icons.Outlined.Favorite, VitaHeart),
            HomeCard(HomeMetric.HRV, daily?.hrv?.toString() ?: "—", "ms", Icons.Outlined.MonitorHeart, VitaHeart),
            HomeCard(HomeMetric.STRESS, daily?.avgStress?.toString() ?: "—", null, Icons.Outlined.Bolt, VitaActive),
            HomeCard(
                HomeMetric.BODY_BATTERY,
                bodyBatteryValue?.toString() ?: "—",
                null,
                Icons.Outlined.BatteryChargingFull,
                VitaTertiary,
                onClick = { navController.navigate("body_battery") },
            ),
            HomeCard(HomeMetric.SPO2, daily?.avgSpo2?.toString() ?: "—", "%", Icons.Outlined.Bloodtype, VitaHeart),
            HomeCard(HomeMetric.RESPIRATION, daily?.avgRespiration?.let { String.format("%.1f", it) } ?: "—", "次/分", Icons.Outlined.Air, VitaPrimary),
            HomeCard(HomeMetric.WEIGHT, daily?.weightKg?.let { String.format("%.1f", it) } ?: "—", "kg", Icons.Outlined.MonitorWeight, VitaPrimary),
            HomeCard(HomeMetric.SLEEP, state.lastSleep?.totalMinutes?.let { "${it / 60}h ${it % 60}m" } ?: "—", null, Icons.Outlined.Bedtime, VitaTertiary),
            HomeCard(HomeMetric.SLEEP_SCORE, state.lastSleep?.sleepScore?.toString() ?: "—", "分", Icons.Outlined.Bedtime, VitaTertiary),
            HomeCard(
                HomeMetric.EXERCISE,
                state.exercises.takeIf { it.isNotEmpty() }?.let { "${it.size} 次" } ?: "—",
                state.exercises.takeIf { it.isNotEmpty() }?.sumOf { it.durationMinutes }?.let { "$it min" },
                Icons.AutoMirrored.Outlined.DirectionsRun,
                VitaPrimary,
                onClick = if (state.exercises.isNotEmpty()) {
                    {
                        if (state.exercises.size == 1) {
                            navController.navigate("exercise/${state.exercises.first().id}")
                        } else {
                            showExercisePicker = true
                        }
                    }
                } else {
                    null
                },
            ),
        )
        val cards = allCards.filter { it.metric.key in state.enabledMetrics }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            // ─── 当日简报: 默认只讲结论、行动和三个关键理由 ───
            state.readiness?.let { readiness ->
                state.dailyBrief?.let { brief ->
                    item(key = "status_score") {
                        StatusScoreCard(score = readiness, brief = brief)
                    }
                }
            }
            if (state.anomalyEvents.isNotEmpty()) {
                item(key = "anomaly_events") {
                    AnomalyEventsCard(events = state.anomalyEvents)
                }
            }
            state.dailyBrief?.let { brief ->
                item(key = "life_trajectory") {
                    LifeTrajectoryCard(brief = brief)
                }
            }

            // 没有可评分数据时直接展示明细; 有简报时让首页保持简洁, 明细一键展开。
            if (state.readiness != null) {
                item(key = "today_data_toggle") {
                    TextButton(
                        onClick = { showAllTodayData = !showAllTodayData },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = if (showAllTodayData) "收起全部今日数据" else "查看全部今日数据 (${cards.size})",
                            color = VitaPrimary,
                        )
                    }
                }
            }

            if (state.readiness == null || showAllTodayData) {
                itemsIndexed(cards, key = { _, item -> item.metric.key }) { _, card ->
                    MetricCard(
                        title = card.metric.label,
                        value = card.value,
                        unit = card.unit,
                        icon = card.icon,
                        accent = card.accent,
                        onClick = card.onClick,
                    )
                }
                // 月度 / 年度趋势保留在明细层。简报里的「Vita 发现」只摘出最值得注意的一条。
                if (state.readiness != null) {
                    item(key = "body_trend") {
                        BodyTrendCard(monthTrend = state.monthTrend, yearTrend = state.yearTrend)
                    }
                }
            }

            // ─── 运动记录: 当天每条运动的丰富预览 (时长/距离/消耗/速度/心率) ───
            if (HomeMetric.EXERCISE.key in state.enabledMetrics && state.exercises.isNotEmpty()) {
                item(key = "exercise_section_header") {
                    Text(
                        text = "运动记录",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                    )
                }
                items(state.exercises, key = { "exercise_preview_${it.id}" }) { exercise ->
                    ExercisePreviewCard(
                        exercise = exercise,
                        onClick = { navController.navigate("exercise/${exercise.id}") },
                    )
                }
            }
        }

        if (showExercisePicker) {
            ExercisePickerDialog(
                exercises = state.exercises,
                onDismiss = { showExercisePicker = false },
                onSelect = { exercise ->
                    showExercisePicker = false
                    navController.navigate("exercise/${exercise.id}")
                },
            )
        }

        SnackbarHost(snackbar) { data -> Snackbar(snackbarData = data) }
    }

    // 同步/启动时如果刚出现新的健身徽章，这里实时弹庆祝。
    FitnessBadgeCelebrationHost()
}

@Composable
private fun ExercisePickerDialog(
    exercises: List<ExerciseSession>,
    onDismiss: () -> Unit,
    onSelect: (ExerciseSession) -> Unit,
) {
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val zone = remember { ZoneId.systemDefault() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择运动记录") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                exercises.forEach { exercise ->
                    val startTime = remember(exercise.startEpochMs) {
                        Instant.ofEpochMilli(exercise.startEpochMs).atZone(zone).format(timeFormatter)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(exercise) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = ExerciseClassifier.displayTitle(exercise),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "$startTime · ${ExerciseClassifier.displayName(ExerciseClassifier.displayCategory(exercise))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = "${exercise.durationMinutes} min",
                            style = MaterialTheme.typography.bodyMedium,
                            color = VitaPrimary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExercisePreviewCard(
    exercise: ExerciseSession,
    onClick: () -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }
    val timeRange = remember(exercise.startEpochMs, exercise.endEpochMs) {
        val fmt = DateTimeFormatter.ofPattern("HH:mm")
        val start = Instant.ofEpochMilli(exercise.startEpochMs).atZone(zone).format(fmt)
        val end = Instant.ofEpochMilli(exercise.endEpochMs).atZone(zone).format(fmt)
        if (exercise.endEpochMs > exercise.startEpochMs) "$start - $end" else start
    }
    val category = remember(exercise) {
        ExerciseClassifier.displayName(ExerciseClassifier.displayCategory(exercise))
    }
    // 自适应明细: 有哪个字段就展示哪个。
    val stats = remember(exercise) {
        buildList {
            add("时长" to "${exercise.durationMinutes} min")
            exercise.distanceMeters?.takeIf { it > 0.0 }?.let { add("距离" to "%.2f km".format(it / 1000.0)) }
            (exercise.activeCalories ?: exercise.totalCalories)?.takeIf { it > 0.0 }?.let {
                add("消耗" to "${it.toInt()} kcal")
            }
            exercise.avgSpeed?.takeIf { it > 0.0 }?.let { add("平均速度" to "%.1f km/h".format(it * 3.6)) }
            exercise.avgHeartRate?.takeIf { it > 0 }?.let { add("平均心率" to "$it bpm") }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .background(VitaGradients.cardSurface, MaterialTheme.shapes.medium)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(VitaActive.copy(alpha = 0.14f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.DirectionsRun,
                        contentDescription = null,
                        tint = VitaActive,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(
                        text = ExerciseClassifier.displayTitle(exercise),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "$timeRange · $category",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                stats.forEach { (label, value) -> ExerciseStat(label = label, value = value) }
            }
        }
    }
}

@Composable
private fun ExerciseStat(label: String, value: String) {
    Column {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SyncMenuButton(onSync: (Long?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(
            onClick = { expanded = true },
        ) {
            Icon(
                Icons.Outlined.Refresh,
                contentDescription = "同步",
                tint = VitaPrimary,
            )
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("增量同步 (默认)") },
                onClick = { expanded = false; onSync(null) },
            )
            SyncScope.entries.forEach { scope ->
                DropdownMenuItem(
                    text = { Text(scope.label) },
                    onClick = { expanded = false; onSync(scope.days) },
                )
            }
        }
    }
}

@Composable
private fun StopSyncButton(onStop: () -> Unit) {
    // 同步中: 脉冲的「停止」按钮
    val infiniteTransition = rememberInfiniteTransition(label = "syncPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(animation = tween(800), repeatMode = RepeatMode.Reverse),
        label = "pulseAlpha",
    )
    IconButton(
        onClick = onStop,
        modifier = Modifier.alpha(pulseAlpha),
    ) {
        Icon(
            Icons.Outlined.Stop,
            contentDescription = "停止同步",
            tint = VitaError,
        )
    }
}
