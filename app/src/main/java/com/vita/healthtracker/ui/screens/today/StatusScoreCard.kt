package com.vita.healthtracker.ui.screens.today

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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

/** 当日简报: 首屏只保留判断、建议和三个理由，完整评分依据按需展开。 */
@Composable
fun StatusScoreCard(score: StatusScore, brief: DailyBrief, modifier: Modifier = Modifier) {
    val accent = bandColor(score.band)
    var showDetails by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf<DailyInsight?>(null) }

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
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // ── 顶部: 一句判断 + 一条行动建议 ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScoreRing(score = score.overall, accent = accent)
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "当日简报",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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

            TextButton(onClick = { showDetails = !showDetails }) {
                Text(
                    text = if (showDetails) "收起详情" else "看看为什么",
                    color = accent,
                )
            }

            if (showDetails) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "${score.confidence.label} · 已参考 ${score.baselineDays} 天记录",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    )
                    if (score.limits.isNotEmpty()) {
                        val leadLimit = score.limits.minByOrNull { it.cap }
                        Text(
                            text = "如果只看各项读数，大约是 ${score.weightedOverall} 分；考虑到${leadLimit?.title ?: "今天有需要留意的地方"}，今天更适合按 ${score.overall} 分来安排。",
                            style = MaterialTheme.typography.bodySmall,
                            color = VitaActive,
                        )
                        Text(
                            text = "这个分数是给今天做取舍用的，不是健康诊断。读数和平时差很多时，先复核佩戴和身体感受。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                        )
                        score.limits.sortedBy { it.cap }.forEach { limit ->
                            Text(
                                text = "· ${limit.detail}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    score.contributions.forEach { ContributionBar(it) }
                    if (score.insights.isNotEmpty()) {
                        Text(
                            text = "更多说明",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        score.insights.forEach { insight ->
                            InsightRow(insight = insight, onClick = { selected = insight })
                        }
                    }
                }
            }
        }
    }

    selected?.let { insight ->
        InsightDetailSheet(insight = insight, onDismiss = { selected = null })
    }
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

@Composable
private fun InsightRow(insight: DailyInsight, onClick: () -> Unit) {
    val color = toneColor(insight.tone)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
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
            Text(
                text = "展开说明 ›",
                style = MaterialTheme.typography.labelMedium,
                color = color,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun InsightDetailSheet(insight: DailyInsight, onDismiss: () -> Unit) {
    val color = toneColor(insight.tone)
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0E1320),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(color),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = insight.title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = insight.detail,
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
