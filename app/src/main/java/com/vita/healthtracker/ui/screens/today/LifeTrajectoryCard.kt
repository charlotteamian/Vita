package com.vita.healthtracker.ui.screens.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vita.healthtracker.domain.DailyBrief
import com.vita.healthtracker.ui.theme.VitaActive
import com.vita.healthtracker.ui.theme.VitaGradients
import com.vita.healthtracker.ui.theme.VitaOnSurfaceMuted
import com.vita.healthtracker.ui.theme.VitaPrimary
import com.vita.healthtracker.ui.theme.VitaTertiary

/**
 * Vita 的长期记忆入口: 用一张轻量轨迹卡把分散的日数据串起来。
 * 完整月/年对比仍在明细层，这里只展示当前最值得注意的一件事。
 */
@Composable
fun LifeTrajectoryCard(
    brief: DailyBrief,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .background(VitaGradients.cardSurface, MaterialTheme.shapes.large)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "生命轨迹",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "近 7 天状态，Vita 正在学会你的节奏",
                    style = MaterialTheme.typography.bodySmall,
                    color = VitaOnSurfaceMuted,
                )
            }

            TrajectoryChart(brief)

            brief.discovery?.let { discovery ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(VitaTertiary.copy(alpha = 0.13f), VitaPrimary.copy(alpha = 0.05f)),
                            ),
                            RoundedCornerShape(14.dp),
                        )
                        .padding(13.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = discovery.title,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = VitaTertiary,
                    )
                    Text(
                        text = discovery.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

        }
    }
}

@Composable
private fun TrajectoryChart(brief: DailyBrief) {
    val points = brief.trajectory
    val scoreColor = { score: Int ->
        when {
            score >= 80 -> VitaTertiary
            score >= 60 -> VitaPrimary
            else -> VitaActive
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(86.dp),
    ) {
        if (points.isEmpty()) return@Canvas
        val left = 5.dp.toPx()
        val right = size.width - 5.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = size.height - 8.dp.toPx()
        val step = if (points.size <= 1) 0f else (right - left) / (points.size - 1)
        fun offset(index: Int, score: Int): Offset {
            val y = bottom - (score.coerceIn(0, 100) / 100f) * (bottom - top)
            return Offset(left + index * step, y)
        }

        drawLine(
            color = Color.White.copy(alpha = 0.06f),
            start = Offset(left, bottom - 0.7f * (bottom - top)),
            end = Offset(right, bottom - 0.7f * (bottom - top)),
            strokeWidth = 1.dp.toPx(),
        )

        points.zipWithNext().forEachIndexed { index, (first, second) ->
            val firstScore = first.score
            val secondScore = second.score
            if (firstScore != null && secondScore != null) {
                drawLine(
                    brush = Brush.linearGradient(
                        listOf(scoreColor(firstScore), scoreColor(secondScore)),
                        start = offset(index, firstScore),
                        end = offset(index + 1, secondScore),
                    ),
                    start = offset(index, firstScore),
                    end = offset(index + 1, secondScore),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        points.forEachIndexed { index, point ->
            point.score?.let { score ->
                val center = offset(index, score)
                drawCircle(
                    color = scoreColor(score).copy(alpha = 0.18f),
                    radius = 7.dp.toPx(),
                    center = center,
                )
                drawCircle(
                    color = scoreColor(score),
                    radius = 3.5.dp.toPx(),
                    center = center,
                )
            }
        }
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        points.forEach { point ->
            Text(
                text = weekDay(point.date.dayOfWeek.value),
                style = MaterialTheme.typography.labelSmall,
                color = if (point == points.last()) VitaPrimary else VitaOnSurfaceMuted,
                fontWeight = if (point == points.last()) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun weekDay(day: Int): String = when (day) {
    1 -> "一"
    2 -> "二"
    3 -> "三"
    4 -> "四"
    5 -> "五"
    6 -> "六"
    else -> "日"
}
