package com.vita.healthtracker.ui.screens.garmin

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Dataset
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vita.healthtracker.data.local.entity.CycleEntry
import com.vita.healthtracker.data.local.entity.DailyHealthSnapshot
import com.vita.healthtracker.data.local.entity.ExerciseSession
import com.vita.healthtracker.data.local.entity.GarminRawRecord
import com.vita.healthtracker.data.local.entity.SleepSession
import com.vita.healthtracker.domain.ExerciseClassifier
import com.vita.healthtracker.domain.healthSourceLabel
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
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GarminDataDetailScreen(
    dateText: String,
    onBack: () -> Unit,
) {
    val vm = vitaViewModel<GarminDataDetailViewModel>()
    val state by vm.state.collectAsStateWithLifecycle()
    val date = remember(dateText) {
        runCatching { LocalDate.parse(dateText) }.getOrDefault(LocalDate.now())
    }

    LaunchedEffect(date) {
        vm.load(date)
    }
    val displayRecords = remember(state.records) {
        state.records
            .map { record -> record to GarminRawDisplay.describe(record) }
            .filter { (_, block) -> block.rows.isNotEmpty() || block.chart != null }
    }
    val structuredBlocks = remember(state) { structuredBlocks(state) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("当日全量数据") },
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                FullDataHeader(
                    date = state.date,
                    structuredCount = structuredBlocks.size,
                    garminReadableCount = displayRecords.size,
                    garminRecords = state.records,
                )
            }
            if (structuredBlocks.isEmpty() && displayRecords.isEmpty()) {
                item {
                    FullDataEmptyCard()
                }
            } else {
                items(
                    items = structuredBlocks,
                    key = { block -> "structured-${block.title}-${block.source}" },
                ) { block ->
                    FullDataPayloadCard(block)
                }
                items(
                    items = displayRecords,
                    key = { (record, _) -> "${record.domain}-${record.categoryKey}" },
                ) { (record, block) ->
                    GarminRawPayloadCard(record, block)
                }
            }
        }
    }
}

private data class FullDataBlock(
    val title: String,
    val source: String,
    val rows: List<GarminDisplayRow>,
    val accent: Color,
)

@Composable
private fun FullDataHeader(
    date: LocalDate,
    structuredCount: Int,
    garminReadableCount: Int,
    garminRecords: List<GarminRawRecord>,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(VitaGradients.primaryAccent, RoundedCornerShape(16.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = date.format(DateTimeFormatter.ofPattern("yyyy年M月d日")),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (structuredCount == 0 && garminReadableCount == 0) {
                    if (garminRecords.isEmpty()) "这天还没有同步到数据" else "这天已保存 ${garminRecords.size} 类 Garmin 数据，暂时没有可展示的指标"
                } else {
                    buildString {
                        append("${structuredCount + garminReadableCount} 类可查看数据")
                        if (garminRecords.isNotEmpty()) {
                            append(" · Garmin ")
                            append(garminRecords.map { domainLabel(it.domain) }.distinct().joinToString(" / "))
                        }
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FullDataEmptyCard() {
    GarminRawSurface {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.Dataset, contentDescription = null, tint = VitaPrimary)
            Text(
                text = "同步或导入后，这里会按日期整理 Garmin、Apple 健康、Health Connect 和手机传感器里的记录。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FullDataPayloadCard(block: FullDataBlock) {
    GarminRawSurface {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(10.dp)
                    .background(block.accent, CircleShape),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = block.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = block.accent,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = block.source,
                    style = MaterialTheme.typography.bodySmall,
                    color = VitaOnSurfaceMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
            block.rows.forEach { GarminRawRow(it) }
        }
    }
}

@Composable
private fun GarminRawPayloadCard(record: GarminRawRecord, block: GarminDisplayBlock) {
    val accent = accentFor(record.categoryKey)
    GarminRawSurface {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(10.dp)
                    .background(accent, CircleShape),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = titleFor(record),
                    style = MaterialTheme.typography.titleMedium,
                    color = accent,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = sourceLabel(record.domain),
                    style = MaterialTheme.typography.bodySmall,
                    color = VitaOnSurfaceMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
            block.chart?.let { chart ->
                GarminRawChart(chart = chart, accent = accent)
            }
            block.rows.forEach { row ->
                GarminRawRow(row)
            }
            if (block.hiddenCount > 0) {
                Text(
                    text = "还有 ${block.hiddenCount} 项记录已保存，当前页面先不展开。",
                    style = MaterialTheme.typography.bodySmall,
                    color = VitaOnSurfaceMuted,
                )
            }
        }
    }
}

@Composable
private fun GarminRawChart(chart: GarminDisplayChart, accent: Color) {
    val points = remember(chart.points) {
        chart.points
            .filter { it.hour.isFinite() && it.value.isFinite() && it.hour in 0.0..24.0 }
            .sortedBy { it.hour }
    }
    if (points.size < 2) return
    val minValue = points.minOf { it.value }
    val maxValue = points.maxOf { it.value }
    val range = (maxValue - minValue).takeIf { abs(it) > 0.0001 } ?: 1.0

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
        ) {
            val horizontalPadding = 4.dp.toPx()
            val topPadding = 10.dp.toPx()
            val bottomPadding = 10.dp.toPx()
            val chartWidth = (size.width - horizontalPadding * 2).coerceAtLeast(1f)
            val chartHeight = (size.height - topPadding - bottomPadding).coerceAtLeast(1f)
            val baseY = topPadding + chartHeight

            // 只画 4 条横向基准线, 去掉竖线, 视觉更干净。
            val gridColor = VitaOnSurfaceMuted.copy(alpha = 0.12f)
            for (i in 0..3) {
                val y = topPadding + chartHeight * i / 3f
                drawLine(
                    color = gridColor,
                    start = Offset(horizontalPadding, y),
                    end = Offset(horizontalPadding + chartWidth, y),
                    strokeWidth = 1.dp.toPx(),
                )
            }

            fun xFor(hour: Double) =
                horizontalPadding + (hour / 24.0).coerceIn(0.0, 1.0).toFloat() * chartWidth
            fun yFor(value: Double) =
                baseY - (((value - minValue) / range).coerceIn(0.0, 1.0).toFloat() * chartHeight)

            // 相邻采样间隔 > 1.5h 视为断开, 切成多段, 段内才连线。
            val segments = mutableListOf<MutableList<Offset>>()
            var current = mutableListOf<Offset>()
            points.forEachIndexed { index, p ->
                val prev = points.getOrNull(index - 1)
                if (prev != null && p.hour - prev.hour > 1.5 && current.isNotEmpty()) {
                    segments.add(current)
                    current = mutableListOf()
                }
                current.add(Offset(xFor(p.hour), yFor(p.value)))
            }
            if (current.isNotEmpty()) segments.add(current)

            segments.forEach { seg ->
                if (seg.size == 1) {
                    drawCircle(color = accent, radius = 2.5.dp.toPx(), center = seg.first())
                    return@forEach
                }
                val linePath = smoothPath(seg)
                // 面积填充: 闭合到底部 + 垂直渐变, 上浓下透。
                val areaPath = Path().apply {
                    addPath(linePath)
                    lineTo(seg.last().x, baseY)
                    lineTo(seg.first().x, baseY)
                    close()
                }
                drawPath(
                    path = areaPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(accent.copy(alpha = 0.28f), accent.copy(alpha = 0.02f)),
                        startY = topPadding,
                        endY = baseY,
                    ),
                )
                // 柔光描边 (宽而淡) 衬底。
                drawPath(
                    path = linePath,
                    color = accent.copy(alpha = 0.16f),
                    style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
                // 主曲线。
                drawPath(
                    path = linePath,
                    color = accent,
                    style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf("0", "6", "12", "18", "24").forEach {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = VitaOnSurfaceMuted,
                )
            }
        }
    }
}

@Composable
private fun GarminRawSurface(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(VitaGradients.cardSurface, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            content = content,
        )
    }
}

@Composable
private fun GarminRawRow(row: GarminDisplayRow) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = row.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.9f),
        )
        Text(
            text = row.value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1.1f),
        )
    }
}

private fun structuredBlocks(state: GarminDataDetailState): List<FullDataBlock> = buildList {
    state.daily?.toRows()?.takeIf { it.isNotEmpty() }?.let { rows ->
        add(
            FullDataBlock(
                title = "日汇总",
                source = healthSourceLabel(state.daily.source),
                rows = rows,
                accent = VitaPrimary,
            )
        )
    }
    val cycleRows = state.cycles.toCycleRows()
    if (cycleRows.isNotEmpty()) {
        add(
            FullDataBlock(
                title = "生理期",
                source = state.cycles.map { healthSourceLabel(it.source) }.distinct().joinToString(" · "),
                rows = cycleRows,
                accent = VitaSecondary,
            )
        )
    }
    val exerciseGroups = state.exercises.groupBy { ExerciseClassifier.displayCategory(it) }
    exerciseGroups.forEach { (category, exercises) ->
        add(
            FullDataBlock(
                title = ExerciseClassifier.displayName(category),
                source = exercises.map { healthSourceLabel(it.source) }.distinct().joinToString(" · "),
                rows = exercises.toExerciseRows(),
                accent = VitaActive,
            )
        )
    }
    if (state.bodyBattery.isNotEmpty()) {
        val values = state.bodyBattery.map { it.level }
        add(
            FullDataBlock(
                title = "身体电量",
                source = state.bodyBattery.map { healthSourceLabel(it.source) }.distinct().joinToString(" · "),
                rows = listOf(
                    GarminDisplayRow("采样", "${state.bodyBattery.size} 个点"),
                    GarminDisplayRow("平均", values.average().toInt().toString()),
                    GarminDisplayRow("范围", "${values.min()}-${values.max()}"),
                ),
                accent = Color(0xFF74D7D3),
            )
        )
    }
}

private fun DailyHealthSnapshot.toRows(): List<GarminDisplayRow> = buildList {
    if (steps > 0L) add(GarminDisplayRow("步数", "%,d 步".format(steps)))
    distanceMeters?.takeIf { it > 0.0 }?.let { add(GarminDisplayRow("距离", "%.2f km".format(it / 1000.0))) }
    activeCalories?.takeIf { it > 0.0 }?.let { add(GarminDisplayRow("活跃消耗", "${it.toInt()} kcal")) }
    totalCalories?.takeIf { it > 0.0 }?.let { add(GarminDisplayRow("总消耗", "${it.toInt()} kcal")) }
    activeMinutes?.takeIf { it > 0L }?.let { add(GarminDisplayRow("活跃时长", "$it min")) }
    floorsClimbed?.takeIf { it > 0.0 }?.let { add(GarminDisplayRow("爬楼", "${it.toInt()} 层")) }
    avgHeartRate?.takeIf { it > 0 }?.let { add(GarminDisplayRow("平均心率", "$it bpm")) }
    restingHeartRate?.takeIf { it > 0 }?.let { add(GarminDisplayRow("静息心率", "$it bpm")) }
    avgStress?.takeIf { it > 0 }?.let { add(GarminDisplayRow("平均压力", it.toString())) }
    maxStress?.takeIf { it > 0 }?.let { add(GarminDisplayRow("最高压力", it.toString())) }
    bodyBatteryHigh?.takeIf { it > 0 }?.let { add(GarminDisplayRow("身体电量最高", it.toString())) }
    bodyBatteryLow?.takeIf { it > 0 }?.let { add(GarminDisplayRow("身体电量最低", it.toString())) }
    avgSpo2?.takeIf { it > 0 }?.let { add(GarminDisplayRow("血氧", "$it%")) }
    avgRespiration?.takeIf { it > 0.0 }?.let { add(GarminDisplayRow("呼吸率", "%.1f 次/分".format(it))) }
    hrv?.takeIf { it > 0 }?.let { add(GarminDisplayRow("HRV", "$it ms")) }
    weightKg?.takeIf { it > 0.0 }?.let { add(GarminDisplayRow("体重", "%.1f kg".format(it))) }
}

private fun SleepSession.toRows(): List<GarminDisplayRow> = buildList {
    add(GarminDisplayRow("总睡眠", "${totalMinutes / 60}h ${totalMinutes % 60}m"))
    deepMinutes.takeIf { it > 0 }?.let { add(GarminDisplayRow("深睡", "$it min")) }
    lightMinutes.takeIf { it > 0 }?.let { add(GarminDisplayRow("浅睡", "$it min")) }
    remMinutes.takeIf { it > 0 }?.let { add(GarminDisplayRow("REM", "$it min")) }
    awakeMinutes.takeIf { it > 0 }?.let { add(GarminDisplayRow("清醒", "$it min")) }
    sleepScore?.takeIf { it > 0 }?.let { add(GarminDisplayRow("睡眠评分", "$it 分")) }
}

private fun List<ExerciseSession>.toExerciseRows(): List<GarminDisplayRow> {
    val sessions = this
    return buildList {
    add(GarminDisplayRow("记录数", "${sessions.size} 次"))
    add(GarminDisplayRow("总时长", "${sessions.sumOf { it.durationMinutes }} min"))
    val distance = sessions.sumOf { it.distanceMeters ?: 0.0 }
    if (distance > 0.0) add(GarminDisplayRow("总距离", "%.2f km".format(distance / 1000.0)))
    val calories = sessions.sumOf { it.activeCalories ?: 0.0 }
    if (calories > 0.0) add(GarminDisplayRow("活跃消耗", "${calories.toInt()} kcal"))
    sessions.take(6).forEach { exercise ->
        val time = Instant.ofEpochMilli(exercise.startEpochMs)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
            .format(DateTimeFormatter.ofPattern("HH:mm"))
        val title = ExerciseClassifier.displayTitle(exercise)
        val parts = buildList {
            add("${exercise.durationMinutes} min")
            exercise.distanceMeters?.takeIf { it > 0.0 }?.let { add("%.2f km".format(it / 1000.0)) }
            exercise.activeCalories?.takeIf { it > 0.0 }?.let { add("${it.toInt()} kcal") }
        }.joinToString(" · ")
        add(GarminDisplayRow("$time $title", parts))
    }
    if (sessions.size > 6) add(GarminDisplayRow("更多", "还有 ${sessions.size - 6} 次记录在运动明细里"))
    }
}

private fun List<CycleEntry>.toCycleRows(): List<GarminDisplayRow> =
    filter { it.flow > 0 }
        .map { entry ->
            GarminDisplayRow(
                label = if (entry.isPeriodStart) "经期开始" else "经期",
                value = flowLabel(entry.flow),
            )
        }

private fun flowLabel(flow: Int): String = when (flow) {
    1 -> "点滴"
    2 -> "轻"
    3 -> "中"
    4 -> "重"
    else -> "已记录"
}

private fun domainLabel(domain: String): String = when (domain) {
    "garmin.cn" -> "国区"
    "garmin.com" -> "国际区"
    else -> domain
}

private fun sourceLabel(domain: String): String = when (domain) {
    "garmin.cn" -> "Garmin Connect 国区"
    "garmin.com" -> "Garmin Connect 国际区"
    else -> domain
}

private fun titleFor(record: GarminRawRecord): String =
    if (record.categoryKey.startsWith("activity_summary_")) {
        record.categoryLabel.substringAfter("·", record.categoryLabel).trim()
            .ifBlank { "运动记录" }
    } else {
        record.categoryLabel
    }

private fun accentFor(categoryKey: String): Color {
    val key = categoryKey.lowercase()
    return when {
        "heart" in key || "hrv" in key -> VitaHeart
        "sleep" in key -> VitaTertiary
        "activity" in key || "training" in key || "intensity" in key -> VitaActive
        "cycle" in key || "menstrual" in key -> VitaSecondary
        "spo2" in key || "respiration" in key || "body_battery" in key -> Color(0xFF74D7D3)
        else -> VitaPrimary
    }
}

/**
 * 用 Catmull-Rom → 三次贝塞尔把一串点连成平滑曲线, 取代原来的折线 (锯齿尖角)。
 * 张力取 1/6, 既圆润又不会过度外扩。点数 < 3 时退化为直线。
 */
private fun smoothPath(pts: List<Offset>): Path {
    val path = Path()
    if (pts.isEmpty()) return path
    path.moveTo(pts.first().x, pts.first().y)
    if (pts.size < 3) {
        for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
        return path
    }
    for (i in 0 until pts.size - 1) {
        val p0 = pts[if (i - 1 < 0) i else i - 1]
        val p1 = pts[i]
        val p2 = pts[i + 1]
        val p3 = pts[if (i + 2 >= pts.size) i + 1 else i + 2]
        val c1x = p1.x + (p2.x - p0.x) / 6f
        val c1y = p1.y + (p2.y - p0.y) / 6f
        val c2x = p2.x - (p3.x - p1.x) / 6f
        val c2y = p2.y - (p3.y - p1.y) / 6f
        path.cubicTo(c1x, c1y, c2x, c2y, p2.x, p2.y)
    }
    return path
}
