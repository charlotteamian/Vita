package com.vita.healthtracker.ui.screens.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vita.healthtracker.data.ai.AiInsight
import com.vita.healthtracker.data.ai.AiInsightItem
import com.vita.healthtracker.data.ai.AiInsightManager
import com.vita.healthtracker.ui.theme.VitaActive
import com.vita.healthtracker.ui.theme.VitaGradients
import com.vita.healthtracker.ui.theme.VitaOnSurfaceMuted
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 洞察的分析日期 (按本机时区); 用来判断结果是不是今天的。 */
fun aiInsightDate(insight: AiInsight): LocalDate =
    Instant.ofEpochMilli(insight.analyzedAtEpochMs).atZone(ZoneId.systemDefault()).toLocalDate()

/**
 * AI 今日解读卡: 结合昨晚睡眠判断今天的状态 + 近 3-7 天短期趋势 (长期规律归趋势页)。
 * 结果是今天的 → 全文平铺 (今日状态 → 近几天走向 → 今天可以 → 留意), 顶部小按钮可重新分析;
 * 没结果/结果过期 → 一句说明 + 全宽按钮。零折叠, 所有内容直接可见。
 */
@Composable
fun AiTodayCard(
    insight: AiInsight?,
    status: AiInsightManager.Status,
    onAnalyze: () -> Unit,
) {
    val running = status is AiInsightManager.Status.Running
    val fresh = insight != null && aiInsightDate(insight) == LocalDate.now()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .background(VitaGradients.cardSurface, MaterialTheme.shapes.large)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "AI 今日解读",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = VitaActive,
                    modifier = Modifier.weight(1f),
                )
                if (fresh && !running) {
                    TextButton(onClick = onAnalyze) { Text("重新分析", color = VitaOnSurfaceMuted) }
                }
            }

            (status as? AiInsightManager.Status.Failed)?.let { failed ->
                Text(
                    text = failed.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (fresh && insight != null) {
                val hhmm = remember { DateTimeFormatter.ofPattern("HH:mm") }
                val time = remember(insight.analyzedAtEpochMs) {
                    Instant.ofEpochMilli(insight.analyzedAtEpochMs)
                        .atZone(ZoneId.systemDefault()).toLocalTime().format(hhmm)
                }
                Text(
                    text = "今天 $time 分析 · 参考近 ${insight.daysCovered} 天记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = VitaOnSurfaceMuted,
                )
                Text(
                    text = insight.overall,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                AiTodaySection(title = "近几天的走向", items = insight.recentPatterns)
                AiTodaySection(title = "今天可以", items = insight.actions)
                insight.watch.forEach { line ->
                    Text(
                        text = "留意：$line",
                        style = MaterialTheme.typography.bodySmall,
                        color = VitaOnSurfaceMuted,
                    )
                }
            } else {
                Text(
                    text = if (insight != null) {
                        val fmt = remember { DateTimeFormatter.ofPattern("M月d日") }
                        "上次解读是 ${aiInsightDate(insight).format(fmt)} 的，点一下按今天的睡眠和近几天数据重新看。"
                    } else {
                        "让 AI 结合昨晚睡眠判断今天的状态，并看看近 3-7 天的短期走向。长期规律在趋势页。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onAnalyze,
                    enabled = !running,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VitaActive.copy(alpha = 0.9f),
                        contentColor = Color(0xFF1A1205),
                        disabledContainerColor = VitaActive.copy(alpha = 0.25f),
                    ),
                ) {
                    if (running) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = VitaActive,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = if (running) "分析中，可以先去别的页面…" else "AI 分析今天",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun AiTodaySection(title: String, items: List<AiInsightItem>) {
    if (items.isEmpty()) return
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = VitaActive,
    )
    items.forEach { item ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(VitaActive.copy(alpha = 0.07f), RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = item.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
