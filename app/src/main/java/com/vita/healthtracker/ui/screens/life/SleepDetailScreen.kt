package com.vita.healthtracker.ui.screens.life

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vita.healthtracker.data.local.entity.SleepSession
import com.vita.healthtracker.ui.theme.VitaGradients
import com.vita.healthtracker.ui.theme.VitaTertiary
import com.vita.healthtracker.ui.vitaViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepDetailScreen(
    onBack: () -> Unit,
    onOpenDay: (java.time.LocalDate) -> Unit = {},
) {
    val vm = vitaViewModel<SleepDetailViewModel>()
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("睡眠明细") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(state.sleeps) { sleep ->
                DetailedSleepRow(sleep, onOpenDay = onOpenDay)
            }
            if (state.sleeps.isEmpty()) {
                item {
                    Text(
                        text = "暂无睡眠记录",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailedSleepRow(
    sleep: SleepSession,
    onOpenDay: (java.time.LocalDate) -> Unit = {},
) {
    val startDt = Instant.ofEpochMilli(sleep.startEpochMs).atZone(ZoneId.systemDefault())
    val endDt = Instant.ofEpochMilli(sleep.endEpochMs).atZone(ZoneId.systemDefault())
    // 跨夜睡眠归属醒来那天 (和统计/评分一个口径): 6日晚睡到7日早算 7 日的觉,
    // 不再和 6 日的小睡一起挤在 6 日名下。
    val displayDt = if (sleep.endEpochMs > sleep.startEpochMs) endDt else startDt
    val dateStr = displayDt.format(DateTimeFormatter.ofPattern("yyyy年M月d日"))
    val weekStr = displayDt.format(DateTimeFormatter.ofPattern("EEEE"))
    val timeStr = "${startDt.format(DateTimeFormatter.ofPattern("HH:mm"))} - ${endDt.format(DateTimeFormatter.ofPattern("HH:mm"))}" +
        if (sleep.isEdited) " · 已修正" else ""

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenDay(displayDt.toLocalDate()) },
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .background(VitaGradients.cardSurface, MaterialTheme.shapes.medium)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = dateStr,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    Text(
                        text = weekStr,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.titleSmall,
                    color = VitaTertiary,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            
            Row(
                modifier = Modifier.padding(top = 12.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "${sleep.totalMinutes / 60}h ${sleep.totalMinutes % 60}m",
                    style = MaterialTheme.typography.headlineMedium,
                    color = VitaTertiary
                )
            }

            // Dimensions: Deep, Light, REM, Awake
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SleepMetricColumn("深睡", sleep.deepMinutes)
                SleepMetricColumn("浅睡", sleep.lightMinutes)
                SleepMetricColumn("REM", sleep.remMinutes)
                SleepMetricColumn("清醒", sleep.awakeMinutes)
            }
        }
    }
}

@Composable
private fun SleepMetricColumn(label: String, minutes: Long) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = if (minutes > 0) "${minutes / 60}h ${minutes % 60}m" else "—",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
