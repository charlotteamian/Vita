package com.vita.healthtracker.ui.screens.life

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vita.healthtracker.data.local.entity.SleepSession
import com.vita.healthtracker.domain.SleepStage
import com.vita.healthtracker.domain.SleepStageSegment
import com.vita.healthtracker.ui.theme.VitaGradients
import com.vita.healthtracker.ui.theme.VitaOnSurfaceMuted
import com.vita.healthtracker.ui.theme.VitaTertiary
import com.vita.healthtracker.ui.vitaViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val HHMM = DateTimeFormatter.ofPattern("HH:mm")

/** 分期在图里的行序 (从上到下) / 颜色 / 中文名。 */
private data class StageStyle(val row: Int, val color: Color, val label: String)

private val STAGE_STYLES = mapOf(
    SleepStage.AWAKE to StageStyle(0, Color(0xFFFF8A65), "清醒"),
    SleepStage.REM to StageStyle(1, Color(0xFFB197FC), "REM"),
    SleepStage.LIGHT to StageStyle(2, Color(0xFF74C0FC), "浅睡"),
    SleepStage.DEEP to StageStyle(3, Color(0xFF3D6BFF), "深睡"),
)

/** 某一天的睡眠详情: 整晚分期图 + 各阶段占比 + 小睡。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepDayDetailScreen(dateText: String, onBack: () -> Unit) {
    val vm = vitaViewModel<SleepDayDetailViewModel>()
    val state by vm.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<SleepSession?>(null) }

    LaunchedEffect(dateText) {
        runCatching { LocalDate.parse(dateText) }.getOrNull()?.let(vm::setDate)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.date.format(DateTimeFormatter.ofPattern("M月d日")) + " 睡眠") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = vm::previousDay) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "前一天")
                    }
                    IconButton(
                        onClick = vm::nextDay,
                        enabled = state.date.isBefore(LocalDate.now()),
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "后一天")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        containerColor = Color.Transparent,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val main = state.mainSleep
            if (main == null) {
                item {
                    Text(
                        text = "这天没有睡眠记录。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                item { SleepSummaryCard(main, onEdit = { editing = main }) }
                item {
                    if (state.stageSegments.isNotEmpty()) {
                        HypnogramCard(segments = state.stageSegments)
                    } else {
                        Text(
                            text = "这晚没有分期数据（整晚深浅图来自佳明同步；其它来源只显示汇总）。",
                            style = MaterialTheme.typography.bodySmall,
                            color = VitaOnSurfaceMuted,
                        )
                    }
                }
                item { StageBreakdownCard(main) }
                if (state.naps.isNotEmpty()) {
                    item {
                        Text(
                            text = "这天的其它睡眠",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    items(state.naps, key = { it.id }) { nap ->
                        NapRow(nap, onEdit = { editing = nap })
                    }
                }
            }
        }
    }

    editing?.let { session ->
        SleepEditSheet(
            session = session,
            onDismiss = { editing = null },
            onSave = { newStart, newEnd ->
                vm.editSleep(session.id, newStart, newEnd)
                editing = null
            },
            onRestore = {
                vm.restoreSleep(session.id)
                editing = null
            },
            onDelete = {
                vm.deleteSleep(session.id)
                editing = null
            },
        )
    }
}

/** 时间平移/微调一档: 按钮文案 + 毫秒偏移。 */
private val ADJUST_STEPS = listOf(
    "-1时" to -3_600_000L,
    "-10分" to -600_000L,
    "+10分" to 600_000L,
    "+1时" to 3_600_000L,
)

private val EDIT_DT = DateTimeFormatter.ofPattern("M月d日 HH:mm")

/**
 * 睡眠时间修正弹层。典型场景: 跨时区旅行时手表按出发地时区记录, 整晚睡眠错位好几个小时——
 * 「整体平移」几下就能拨回当地时间; 入睡/醒来也可各自微调。修正只改本机记录, 同步不会再覆盖。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepEditSheet(
    session: SleepSession,
    onDismiss: () -> Unit,
    onSave: (Long, Long) -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }
    var startMs by remember(session.id) { mutableLongStateOf(session.startEpochMs) }
    var endMs by remember(session.id) { mutableLongStateOf(session.endEpochMs) }
    var confirmDelete by remember(session.id) { mutableStateOf(false) }
    val changed = startMs != session.startEpochMs || endMs != session.endEpochMs
    val valid = endMs > startMs

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "修正睡眠时间",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "跨时区旅行时手表常按出发地时区记录。用「整体平移」把这觉拨回当地时间，修正后同步不会再覆盖。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // 实时预览
            val previewStart = Instant.ofEpochMilli(startMs).atZone(zone).format(EDIT_DT)
            val previewEnd = Instant.ofEpochMilli(endMs).atZone(zone).format(EDIT_DT)
            val durMin = ((endMs - startMs) / 60_000L).coerceAtLeast(0L)
            Text(
                text = "$previewStart 入睡 → $previewEnd 醒来 · 共 ${durMin / 60}h ${durMin % 60}m",
                style = MaterialTheme.typography.bodyMedium,
                color = if (valid) VitaTertiary else MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.SemiBold,
            )
            if (session.isEdited) {
                val origStart = session.originalStartEpochMs
                val origEnd = session.originalEndEpochMs
                if (origStart != null && origEnd != null) {
                    Text(
                        text = "手表原始记录：${Instant.ofEpochMilli(origStart).atZone(zone).format(EDIT_DT)} → " +
                            Instant.ofEpochMilli(origEnd).atZone(zone).format(EDIT_DT),
                        style = MaterialTheme.typography.bodySmall,
                        color = VitaOnSurfaceMuted,
                    )
                }
            }

            AdjustRow(label = "整体平移") { delta ->
                startMs += delta
                endMs += delta
            }
            AdjustRow(label = "入睡时间") { delta -> startMs += delta }
            AdjustRow(label = "醒来时间") { delta -> endMs += delta }

            Button(
                onClick = { onSave(startMs, endMs) },
                enabled = changed && valid,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("保存修正")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (session.isEdited) {
                    TextButton(onClick = onRestore) { Text("恢复原始时间") }
                } else {
                    Spacer(Modifier.width(1.dp))
                }
                TextButton(
                    onClick = { if (confirmDelete) onDelete() else confirmDelete = true },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(if (confirmDelete) "再点一次确认删除" else "删除这条记录")
                }
            }
        }
    }
}

/** 一行四个平移按钮 (−1时/−10分/+10分/+1时)。 */
@Composable
private fun AdjustRow(label: String, onAdjust: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ADJUST_STEPS.forEach { (text, delta) ->
                OutlinedButton(
                    onClick = { onAdjust(delta) },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                ) {
                    Text(text, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun SleepSummaryCard(sleep: SleepSession, onEdit: () -> Unit) {
    val zone = remember { ZoneId.systemDefault() }
    val start = remember(sleep.startEpochMs) {
        Instant.ofEpochMilli(sleep.startEpochMs).atZone(zone).format(HHMM)
    }
    val end = remember(sleep.endEpochMs) {
        Instant.ofEpochMilli(sleep.endEpochMs).atZone(zone).format(HHMM)
    }
    SleepCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${sleep.totalMinutes / 60}h ${sleep.totalMinutes % 60}m",
                    style = MaterialTheme.typography.headlineLarge,
                    color = VitaTertiary,
                )
                Text(
                    text = "$start 入睡 · $end 醒来" + if (sleep.isEdited) " · 已修正" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            sleep.sleepScore?.let { score ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$score",
                        style = MaterialTheme.typography.headlineMedium,
                        color = VitaTertiary,
                    )
                    Text(
                        text = "睡眠评分",
                        style = MaterialTheme.typography.labelSmall,
                        color = VitaOnSurfaceMuted,
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = "修正时间",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 整晚分期图: 四行 (清醒/REM/浅睡/深睡), 按时间铺开的彩色分段, 类似成熟手表 app。 */
@Composable
private fun HypnogramCard(segments: List<SleepStageSegment>) {
    val zone = remember { ZoneId.systemDefault() }
    val startMs = remember(segments) { segments.minOf { it.startEpochMs } }
    val endMs = remember(segments) { segments.maxOf { it.endEpochMs } }
    SleepCard {
        Text(
            text = "整晚睡眠深浅",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左侧行标签
            Column(
                modifier = Modifier.height(160.dp),
                verticalArrangement = Arrangement.SpaceAround,
            ) {
                STAGE_STYLES.values.sortedBy { it.row }.forEach { style ->
                    Text(
                        text = style.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = style.color,
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(160.dp),
            ) {
                val total = (endMs - startMs).coerceAtLeast(1L).toFloat()
                val bandH = size.height / STAGE_STYLES.size
                val barH = bandH * 0.62f
                var prev: Pair<Float, Int>? = null // (x, row) 上一段结束位置, 画阶梯连接线
                segments.forEach { seg ->
                    val style = STAGE_STYLES.getValue(seg.stage)
                    val x0 = (seg.startEpochMs - startMs) / total * size.width
                    val x1 = (seg.endEpochMs - startMs) / total * size.width
                    val yCenter = style.row * bandH + bandH / 2f
                    prev?.let { (px, pRow) ->
                        if (pRow != style.row) {
                            val pyCenter = pRow * bandH + bandH / 2f
                            drawLine(
                                color = Color.White.copy(alpha = 0.14f),
                                start = Offset(px, pyCenter),
                                end = Offset(px, yCenter),
                                strokeWidth = 1.5.dp.toPx(),
                            )
                        }
                    }
                    drawRoundRect(
                        color = style.color,
                        topLeft = Offset(x0, yCenter - barH / 2f),
                        size = Size((x1 - x0).coerceAtLeast(2f), barH),
                        cornerRadius = CornerRadius(3.dp.toPx()),
                    )
                    prev = x1 to style.row
                }
            }
        }
        // 时间轴: 入睡 / 中点 / 醒来
        val startLabel = remember(startMs) { Instant.ofEpochMilli(startMs).atZone(zone).format(HHMM) }
        val midLabel = remember(startMs, endMs) {
            Instant.ofEpochMilli((startMs + endMs) / 2).atZone(zone).format(HHMM)
        }
        val endLabel = remember(endMs) { Instant.ofEpochMilli(endMs).atZone(zone).format(HHMM) }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, start = 40.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf(startLabel, midLabel, endLabel).forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = VitaOnSurfaceMuted,
                )
            }
        }
    }
}

/** 各阶段时长 + 占比条。 */
@Composable
private fun StageBreakdownCard(sleep: SleepSession) {
    val rows = listOf(
        SleepStage.DEEP to sleep.deepMinutes,
        SleepStage.LIGHT to sleep.lightMinutes,
        SleepStage.REM to sleep.remMinutes,
        SleepStage.AWAKE to sleep.awakeMinutes,
    ).filter { it.second > 0 }
    if (rows.isEmpty()) return
    val denominator = rows.sumOf { it.second }.coerceAtLeast(1)
    SleepCard {
        Text(
            text = "各阶段占比",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        Column(
            modifier = Modifier.padding(top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            rows.forEach { (stage, minutes) ->
                val style = STAGE_STYLES.getValue(stage)
                val fraction = minutes.toFloat() / denominator
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(style.color),
                    )
                    Text(
                        text = style.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 8.dp).width(44.dp),
                    )
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = style.color,
                        trackColor = style.color.copy(alpha = 0.14f),
                    )
                    Text(
                        text = "${minutes / 60}h ${minutes % 60}m · ${(fraction * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun NapRow(nap: SleepSession, onEdit: () -> Unit) {
    val zone = remember { ZoneId.systemDefault() }
    val range = remember(nap.id, nap.startEpochMs, nap.endEpochMs) {
        val start = Instant.ofEpochMilli(nap.startEpochMs).atZone(zone).format(HHMM)
        val end = Instant.ofEpochMilli(nap.endEpochMs).atZone(zone).format(HHMM)
        "$start - $end"
    }
    SleepCard(modifier = Modifier.clickable(onClick = onEdit)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = range + if (nap.isEdited) " · 已修正" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${nap.totalMinutes / 60}h ${nap.totalMinutes % 60}m",
                style = MaterialTheme.typography.bodyMedium,
                color = VitaTertiary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun SleepCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .background(VitaGradients.cardSurface, MaterialTheme.shapes.medium)
                .padding(16.dp),
        ) {
            content()
        }
    }
}
