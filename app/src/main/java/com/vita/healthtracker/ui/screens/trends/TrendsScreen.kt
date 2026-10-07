package com.vita.healthtracker.ui.screens.trends

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.vita.healthtracker.data.ai.AiInsight
import com.vita.healthtracker.data.ai.AiInsightItem
import com.vita.healthtracker.data.ai.AiInsightManager
import com.vita.healthtracker.data.prefs.SettingsPreferences
import com.vita.healthtracker.ui.theme.VitaActive
import com.vita.healthtracker.ui.theme.VitaGradients
import com.vita.healthtracker.ui.theme.VitaOnSurfaceMuted
import com.vita.healthtracker.ui.theme.VitaPrimary
import com.vita.healthtracker.ui.vitaViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 趋势页: 只放「解读」——AI 对身体长期变化的分析。数字图表在数据页。 */
@Composable
fun TrendsScreen(navController: NavController) {
    val vm = vitaViewModel<TrendsViewModel>()
    val aiStatus by vm.aiStatus.collectAsStateWithLifecycle()
    val aiInsight by vm.aiInsight.collectAsStateWithLifecycle()
    val aiRange by vm.aiRange.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AiInsightCard(
            insight = aiInsight,
            status = aiStatus,
            range = aiRange,
            onRangeChange = vm::setAiRange,
            onAnalyze = vm::requestAiAnalysis,
        )
    }
}

/** 分析区间选项: 偏好 key → 选择器文案 (短文案, 三个并排不换行)。 */
private val AI_RANGE_OPTIONS = listOf(
    SettingsPreferences.AI_RANGE_QUARTER to "近三月",
    SettingsPreferences.AI_RANGE_YEAR to "近一年",
    SettingsPreferences.AI_RANGE_ALL to "全部",
)

private fun aiRangeLabel(key: String?): String? =
    AI_RANGE_OPTIONS.firstOrNull { it.first == key }?.second

/**
 * AI 洞察卡: 把记录交给大模型做跨维度深度分析。
 * 区间自选 (近三月/近一年/多年全景), 按钮和区间常驻卡片顶部, 不用翻到结果末尾找。
 * 结果顺序: 总评 → 近期规律 → 可以试试 → 多年变化 (选多年全景才有, 放最后——它不常变)。
 * 分析方式在设置页选: Mac 桥接 (家里电脑, 订阅额度) 或 API 直连 (自己的 Key, 出门可用)。
 */
@Composable
private fun AiInsightCard(
    insight: AiInsight?,
    status: AiInsightManager.Status,
    range: String,
    onRangeChange: (String) -> Unit,
    onAnalyze: () -> Unit,
) {
    TrendCard(title = "AI 洞察", accentColor = VitaActive) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val running = status is AiInsightManager.Status.Running

            // ── 控制区常驻顶部: 选区间 → 一键分析。三个 chip 均分等宽, 文字不换行。──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AI_RANGE_OPTIONS.forEach { (key, label) ->
                    FilterChip(
                        selected = range == key,
                        onClick = { onRangeChange(key) },
                        enabled = !running,
                        modifier = Modifier.weight(1f),
                        label = {
                            Text(
                                text = label,
                                maxLines = 1,
                                softWrap = false,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VitaActive.copy(alpha = 0.22f),
                            selectedLabelColor = VitaActive,
                        ),
                    )
                }
            }
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
                    text = when {
                        running -> "分析中，可以先去别的页面…"
                        insight == null -> "开始分析"
                        else -> "重新分析"
                    },
                    fontWeight = FontWeight.SemiBold,
                )
            }

            (status as? AiInsightManager.Status.Failed)?.let { failed ->
                Text(
                    text = failed.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (insight == null) {
                Text(
                    text = "选好区间点上面按钮，把这段时间的记录交给 AI 做一次跨维度深度分析。近三月/近一年更贴近当下；「全部」才会带上跨年的长期变化。每个区间各自记住上次结果，切换随时回看。分析方式（家里 Mac / API 直连）在设置页选择。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val fmt = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm") }
                val time = remember(insight.analyzedAtEpochMs) {
                    Instant.ofEpochMilli(insight.analyzedAtEpochMs)
                        .atZone(ZoneId.systemDefault()).toLocalDateTime().format(fmt)
                }
                val rangeText = aiRangeLabel(insight.rangeKey)?.let { " · $it" }.orEmpty()
                Text(
                    text = "分析于 $time$rangeText · 覆盖 ${insight.daysCovered} 个有记录日",
                    style = MaterialTheme.typography.bodySmall,
                    color = VitaOnSurfaceMuted,
                )
                Text(
                    text = insight.overall,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                // 近期规律在前——每次分析都会变, 是最值得先看的部分。
                InsightSection(
                    title = "这段时间的规律",
                    items = insight.recentPatterns,
                    accentColor = VitaActive,
                )
                if (insight.longTermFindings.isEmpty() && insight.recentPatterns.isEmpty()) {
                    InsightSection(
                        title = "发现",
                        items = insight.findings,
                        accentColor = VitaActive,
                    )
                }
                if (insight.actions.isNotEmpty()) {
                    Text(
                        text = "可以试试",
                        style = MaterialTheme.typography.labelLarge,
                        color = VitaActive,
                    )
                    insight.actions.forEach { item ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "· ${item.title}",
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
                // 多年变化只在「全部」区间下显示 (近三月/近一年即使模型多写了也不渲染),
                // 放最后——它不常变。rangeKey 为空的老缓存当年默认带多年摘要, 按「全部」算。
                if (insight.rangeKey == null || insight.rangeKey == SettingsPreferences.AI_RANGE_ALL) {
                    InsightSection(
                        title = "多年变化",
                        items = insight.longTermFindings,
                        accentColor = VitaPrimary,
                    )
                }
                insight.watch.forEach { line ->
                    Text(
                        text = "继续观察：$line",
                        style = MaterialTheme.typography.bodySmall,
                        color = VitaOnSurfaceMuted,
                    )
                }
            }

            Text(
                text = "AI 观察仅供参考，不构成医疗建议；持续异常请咨询医生。",
                style = MaterialTheme.typography.bodySmall,
                color = VitaOnSurfaceMuted,
            )
        }
    }
}

@Composable
private fun InsightSection(
    title: String,
    items: List<AiInsightItem>,
    accentColor: Color,
) {
    if (items.isEmpty()) return
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = accentColor,
    )
    items.forEach { item ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(accentColor.copy(alpha = 0.07f), RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
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

@Composable
private fun TrendCard(
    title: String,
    accentColor: Color,
    content: @Composable () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .background(brush = VitaGradients.cardSurface, shape = MaterialTheme.shapes.medium)
                .padding(16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = accentColor,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            content()
        }
    }
}
