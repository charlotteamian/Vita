package com.vita.healthtracker.ui.screens.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vita.healthtracker.domain.MetricTrend
import com.vita.healthtracker.domain.TrendAnalyzer
import com.vita.healthtracker.domain.TrendDirection
import com.vita.healthtracker.domain.TrendReport
import com.vita.healthtracker.ui.theme.VitaActive
import com.vita.healthtracker.ui.theme.VitaOnSurfaceMuted
import com.vita.healthtracker.ui.theme.VitaPrimary
import com.vita.healthtracker.ui.theme.VitaTertiary

private fun directionColor(dir: TrendDirection): Color = when (dir) {
    TrendDirection.IMPROVED -> VitaTertiary
    TrendDirection.DECLINED -> Color(0xFFFF9E64)
    TrendDirection.NEUTRAL -> VitaPrimary
    TrendDirection.STABLE -> VitaOnSurfaceMuted
}

/**
 * 身体趋势卡: 在「今日状态分」对比近日之外, 再从月度 / 年度尺度对比身体的中长期变化。
 * 顶部分段切换月度/年度; 下方一句总览 + 各维度走向解读。
 */
@Composable
fun BodyTrendCard(
    monthTrend: TrendReport?,
    yearTrend: TrendReport?,
    modifier: Modifier = Modifier,
) {
    var window by remember(monthTrend, yearTrend) {
        mutableStateOf(if (monthTrend != null) TrendAnalyzer.TrendWindow.MONTH else TrendAnalyzer.TrendWindow.YEAR)
    }
    val report = when (window) {
        TrendAnalyzer.TrendWindow.MONTH -> monthTrend
        TrendAnalyzer.TrendWindow.YEAR -> yearTrend
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(listOf(VitaPrimary.copy(alpha = 0.10f), Color(0xFF0E1320))),
                    MaterialTheme.shapes.large,
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "身体趋势",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                TrendToggle(
                    selected = window,
                    onSelect = { window = it },
                )
            }

            Text(
                text = window.tagline,
                style = MaterialTheme.typography.labelMedium,
                color = VitaOnSurfaceMuted,
            )

            if (report == null) {
                Text(
                    text = "${window.label}记录还不够多。继续佩戴和同步，之后会自动出现趋势。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = report.headline,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    report.metrics.forEach { TrendRow(it) }
                }
            }
        }
    }
}

@Composable
private fun TrendToggle(
    selected: TrendAnalyzer.TrendWindow,
    onSelect: (TrendAnalyzer.TrendWindow) -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        TrendAnalyzer.TrendWindow.entries.forEach { w ->
            val active = w == selected
            Text(
                text = w.label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                ),
                color = if (active) Color(0xFF06080F) else VitaOnSurfaceMuted,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (active) VitaPrimary else Color.Transparent)
                    .clickable { onSelect(w) }
                    .padding(horizontal = 14.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun TrendRow(metric: MetricTrend) {
    val color = directionColor(metric.direction)
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = metric.label,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = metric.deltaText,
                    style = MaterialTheme.typography.labelMedium,
                    color = color,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = metric.currentText,
                    style = MaterialTheme.typography.labelSmall,
                    color = VitaActive,
                )
                Text(
                    text = "← 前期 ${metric.baselineText}",
                    style = MaterialTheme.typography.labelSmall,
                    color = VitaOnSurfaceMuted,
                )
            }
            Text(
                text = metric.note,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
