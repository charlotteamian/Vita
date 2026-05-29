package com.vita.healthtracker.ui.screens.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.vita.healthtracker.ui.theme.VitaGradients
import com.vita.healthtracker.ui.theme.VitaOnSurfaceMuted
import com.vita.healthtracker.ui.theme.VitaPrimary
import com.vita.healthtracker.ui.theme.VitaTertiary
import com.vita.healthtracker.ui.vitaViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyBatteryDetailScreen(onBack: () -> Unit) {
    val vm = vitaViewModel<BodyBatteryDetailViewModel>()
    val state by vm.state.collectAsStateWithLifecycle()
    val producer = remember { CartesianChartModelProducer() }
    val zone = ZoneId.systemDefault()
    val timeLabels = remember(state.samples) {
        state.samples.map {
            Instant.ofEpochMilli(it.timeEpochMs).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))
        }
    }
    val xAxisFormatter = remember(timeLabels) {
        CartesianValueFormatter { _, value, _ -> timeLabels.getOrNull(value.toInt()).orEmpty() }
    }
    val axisLabel = rememberAxisLabelComponent(color = VitaOnSurfaceMuted)

    LaunchedEffect(state.samples) {
        if (state.samples.size >= 2) {
            withContext(Dispatchers.Default) {
                producer.runTransaction {
                    lineSeries { series(state.samples.map { it.level.toDouble() }) }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("身体电量") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = vm::previousDay) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "前一天")
                }
                Text(
                    text = state.date.format(DateTimeFormatter.ofPattern("yyyy年M月d日")),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (state.date != LocalDate.now()) {
                    AssistChip(
                        onClick = vm::jumpToToday,
                        label = { Text("今日") },
                        leadingIcon = { Icon(Icons.Outlined.Today, contentDescription = null) },
                        colors = AssistChipDefaults.assistChipColors(
                            labelColor = VitaPrimary,
                            leadingIconContentColor = VitaPrimary,
                        ),
                    )
                }
                IconButton(
                    onClick = vm::nextDay,
                    enabled = state.date.isBefore(LocalDate.now()),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "后一天")
                }
            }

            if (state.samples.isEmpty()) {
                BodyBatteryCard {
                    Text(
                        text = "这天暂无身体电量曲线。同步 Garmin 后, 有动态采样的日期会在这里显示全天变化。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val latest = state.samples.last().level
                val high = state.samples.maxOf { it.level }
                val low = state.samples.minOf { it.level }
                BodyBatteryCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        BodyBatteryNumber("当前", latest)
                        BodyBatteryNumber("最高", high)
                        BodyBatteryNumber("最低", low)
                    }
                }

                BodyBatteryCard {
                    Text(
                        text = "全天变化",
                        style = MaterialTheme.typography.titleMedium,
                        color = VitaTertiary,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    if (state.samples.size >= 2) {
                        CartesianChartHost(
                            chart = rememberCartesianChart(
                                rememberLineCartesianLayer(),
                                startAxis = VerticalAxis.rememberStart(label = axisLabel),
                                bottomAxis = HorizontalAxis.rememberBottom(
                                    label = axisLabel,
                                    valueFormatter = xAxisFormatter,
                                ),
                            ),
                            modelProducer = producer,
                            modifier = Modifier.fillMaxWidth().height(220.dp),
                        )
                    } else {
                        Text(
                            text = "只有 1 个采样点: $latest",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BodyBatteryCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(VitaGradients.cardSurface, MaterialTheme.shapes.medium)
                .padding(18.dp),
            content = content,
        )
    }
}

@Composable
private fun BodyBatteryNumber(label: String, value: Int) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = VitaOnSurfaceMuted,
        )
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = VitaTertiary,
        )
    }
}
