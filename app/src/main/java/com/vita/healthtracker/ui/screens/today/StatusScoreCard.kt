package com.vita.healthtracker.ui.screens.today

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vita.healthtracker.domain.DailyBrief
import com.vita.healthtracker.domain.DailyInsight
import com.vita.healthtracker.domain.InsightTone
import com.vita.healthtracker.domain.MetricContribution
import com.vita.healthtracker.domain.ReadinessBand
import com.vita.healthtracker.domain.StatusScore
import com.vita.healthtracker.ui.theme.VitaActive
import com.vita.healthtracker.ui.theme.VitaError
import com.vita.healthtracker.ui.theme.VitaGradients
import com.vita.healthtracker.ui.theme.VitaPrimary
import com.vita.healthtracker.ui.theme.VitaTertiary

private fun bandColor(band: ReadinessBand): Color = when (band) {
    ReadinessBand.PRIME -> VitaTertiary
    ReadinessBand.GOOD -> VitaPrimary
    ReadinessBand.FAIR -> VitaActive
    ReadinessBand.LOW -> Color(0xFFFF9E64)
    ReadinessBand.DEPLETED -> VitaError
}

private fun toneColor(tone: InsightTone): Color = when (tone) {
    InsightTone.POSITIVE -> VitaTertiary
    InsightTone.NEUTRAL -> VitaPrimary
    InsightTone.CAUTION -> VitaActive
    InsightTone.ALERT -> VitaError
}

/**
 * 当日状态卡: 一屏读完, 没有任何展开/折叠。
 * 阅读顺序: 分数 + 一句判断 → 状态叙述/建议 (讲意义, 不报数) → 各维度条 (数字只在这里出现一遍)
 * → 1-2 条洞察 → 近 7 天走向一句话 + 迷你曲线。
 *
 * [aiTakeover] = 今天已有 AI 解读时为 true: 本地模板叙述/洞察让位给下方 AI 卡 (同一件事不用两种口径讲两遍),
 * 分数环、一句判断、维度条和 7 天轨迹仍常驻; AI 结果过期后本地叙述自动回来。
 */
@Composable
fun StatusScoreCard(
    score: StatusScore,
    brief: DailyBrief,
    modifier: Modifier = Modifier,
    aiTakeover: Boolean = false,
) {
    val accent = bandColor(score.band)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        colors = listOf(accent.copy(alpha = 0.16f), Color(0xFF0E1320)),
                    ),
                    MaterialTheme.shapes.large,
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ── 分数 + 一句判断 ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScoreRing(score = score.overall, accent = accent)
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = brief.headline,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = accent,
                    )
                    brief.changeText?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            // ── 状态叙述 + 建议: 讲身体发生了什么、对今天意味着什么 (AI 接管时由 AI 卡讲) ──
            if (!aiTakeover) {
                Text(
                    text = brief.observation,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                brief.action?.let { action ->
                    Text(
                        text = action,
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                        color = accent.copy(alpha = 0.92f),
                    )
                }
            }

            // ── 各维度现状 (常驻) ──
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                score.contributions.forEach { ContributionBar(it) }
            }

            // ── 洞察: 怎么回事 + 怎么做, 全文直接给 (AI 接管时由 AI 卡讲) ──
            if (!aiTakeover) {
                score.insights.forEach { insight -> InsightRow(insight) }
            }

            // ── 近 7 天轨迹: 先一句走向, 再上曲线 ──
            Text(
                text = "近 7 天",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            brief.trajectorySummary?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            TrajectoryStrip(brief)
            brief.discovery?.let { discovery ->
                Text(
                    text = "${discovery.title}：${discovery.text}",
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                    color = VitaTertiary,
                )
            }

            Text(
                text = "${score.confidence.label} · 已参考 ${score.baselineDays} 天记录",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}

/** 近 7 天状态分迷你曲线 + 星期标尺。 */
@Composable
private fun TrajectoryStrip(brief: DailyBrief) {
    val points = brief.trajectory
    if (points.isEmpty()) return
    val scoreColor = { value: Int ->
        when {
            value >= 80 -> VitaTertiary
            value >= 60 -> VitaPrimary
            else -> VitaActive
        }
    }
    androidx.compose.foundation.Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
    ) {
        val left = 5.dp.toPx()
        val right = size.width - 5.dp.toPx()
        val top = 6.dp.toPx()
        val bottom = size.height - 6.dp.toPx()
        val step = if (points.size <= 1) 0f else (right - left) / (points.size - 1)
        fun offset(index: Int, value: Int): Offset {
            val y = bottom - (value.coerceIn(0, 100) / 100f) * (bottom - top)
            return Offset(left + index * step, y)
        }
        points.zipWithNext().forEachIndexed { index, (first, second) ->
            val a = first.score
            val b = second.score
            if (a != null && b != null) {
                drawLine(
                    brush = Brush.linearGradient(
                        listOf(scoreColor(a), scoreColor(b)),
                        start = offset(index, a),
                        end = offset(index + 1, b),
                    ),
                    start = offset(index, a),
                    end = offset(index + 1, b),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        points.forEachIndexed { index, point ->
            point.score?.let { value ->
                drawCircle(
                    color = scoreColor(value),
                    radius = 3.5.dp.toPx(),
                    center = offset(index, value),
                )
            }
        }
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        points.forEach { point ->
            Text(
                text = weekDayShort(point.date.dayOfWeek.value),
                style = MaterialTheme.typography.labelSmall,
                color = if (point == points.last()) VitaPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (point == points.last()) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun weekDayShort(day: Int): String = when (day) {
    1 -> "一"
    2 -> "二"
    3 -> "三"
    4 -> "四"
    5 -> "五"
    6 -> "六"
    else -> "日"
}

@Composable
private fun ScoreRing(score: Int, accent: Color) {
    var target by remember { mutableFloatStateOf(0f) }
    val sweep by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 900),
        label = "scoreSweep",
    )
    LaunchedEffect(score) { target = score / 100f }

    Box(modifier = Modifier.size(88.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(modifier = Modifier.size(88.dp)) {
            val stroke = 10.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            // 轨道
            drawArc(
                color = accent.copy(alpha = 0.15f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            // 进度
            drawArc(
                brush = Brush.sweepGradient(listOf(accent.copy(alpha = 0.7f), accent)),
                startAngle = -90f,
                sweepAngle = 360f * sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$score",
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "/ 100",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ContributionBar(c: MetricContribution) {
    val color = subScoreColor(c.subScore)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = c.label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.06f)),
        ) {
            var target by remember { mutableFloatStateOf(0f) }
            val frac by animateFloatAsState(
                targetValue = target,
                animationSpec = tween(700),
                label = "barFrac",
            )
            LaunchedEffect(c.subScore) { target = c.subScore / 100f }
            Box(
                modifier = Modifier
                    .fillMaxWidth(frac)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = c.deltaText ?: c.valueText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(108.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

private fun subScoreColor(sub: Int): Color = when {
    sub >= 75 -> VitaTertiary
    sub >= 55 -> VitaPrimary
    sub >= 40 -> VitaActive
    else -> VitaError
}

/** 洞察: 结论 + 建议一次性给全, 不可点、没有第二层。 */
@Composable
private fun InsightRow(insight: DailyInsight) {
    val color = toneColor(insight.tone)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.08f))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Top)
                .padding(top = 4.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = insight.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = insight.body,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
