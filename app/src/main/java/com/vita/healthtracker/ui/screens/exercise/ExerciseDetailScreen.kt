package com.vita.healthtracker.ui.screens.exercise

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.vita.healthtracker.domain.ExerciseClassifier
import com.vita.healthtracker.ui.components.MetricCard
import com.vita.healthtracker.ui.theme.VitaActive
import com.vita.healthtracker.ui.theme.VitaGradients
import com.vita.healthtracker.ui.theme.VitaHeart
import com.vita.healthtracker.ui.theme.VitaOnSurfaceMuted
import com.vita.healthtracker.ui.vitaViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(exerciseId: String, onBack: () -> Unit) {
    val vm = vitaViewModel<ExerciseDetailViewModel>()
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(exerciseId) {
        vm.load(exerciseId)
    }

    val hrProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(state.heartRates) {
        val hr = state.heartRates
        if (hr.isNotEmpty()) {
            withContext(Dispatchers.Default) {
                hrProducer.runTransaction {
                    lineSeries { series(hr.map { it.bpm.toDouble() }) }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.exercise?.let {
                            ExerciseClassifier.displayName(ExerciseClassifier.displayCategory(it))
                        } ?: "运动明细"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            state.exercise?.let { ex ->
                val date = Instant.ofEpochMilli(ex.startEpochMs)
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm"))
                
                Text(
                    text = date,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ExerciseTitleCard(
                    exercise = ex,
                    onSave = vm::updateUserFields,
                    onDelete = {
                        vm.deleteExercise()
                        onBack()
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricCard(
                        title = "时长",
                        value = "${ex.durationMinutes}",
                        unit = "分钟",
                        icon = null,
                        accent = VitaActive,
                        modifier = Modifier.weight(1f)
                    )
                    ex.activeCalories?.let { cal ->
                        MetricCard(
                            title = "消耗",
                            value = "${cal.toInt()}",
                            unit = "kcal",
                            icon = null,
                            accent = VitaActive,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ex.avgHeartRate?.let { hr ->
                        MetricCard(
                            title = "平均心率",
                            value = "${hr.toInt()}",
                            unit = "bpm",
                            icon = null,
                            accent = VitaHeart,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    ex.maxHeartRate?.let { maxHr ->
                        MetricCard(
                            title = "最大心率",
                            value = "${maxHr.toInt()}",
                            unit = "bpm",
                            icon = null,
                            accent = VitaHeart,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                ExerciseMetricSection(ex)

                if (state.heartRates.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Column(
                            modifier = Modifier
                                .background(brush = VitaGradients.cardSurface, shape = MaterialTheme.shapes.medium)
                                .padding(16.dp),
                        ) {
                            Text(
                                text = "心率变化图",
                                style = MaterialTheme.typography.titleLarge,
                                color = VitaHeart,
                                modifier = Modifier.padding(bottom = 12.dp),
                            )
                            val hrAxisLabel = rememberAxisLabelComponent(color = VitaOnSurfaceMuted)
                            CartesianChartHost(
                                chart = rememberCartesianChart(
                                    rememberLineCartesianLayer(
                                        lineProvider = LineCartesianLayer.LineProvider.series(
                                            LineCartesianLayer.rememberLine(
                                                fill = LineCartesianLayer.LineFill.single(fill(VitaHeart)),
                                                thickness = 3.dp,
                                                areaFill = LineCartesianLayer.AreaFill.single(fill(VitaHeart.copy(alpha = 0.16f))),
                                            )
                                        ),
                                    ),
                                    startAxis = VerticalAxis.rememberStart(label = hrAxisLabel),
                                    bottomAxis = HorizontalAxis.rememberBottom(label = hrAxisLabel),
                                ),
                                modelProducer = hrProducer,
                                modifier = Modifier.fillMaxWidth().height(200.dp),
                            )
                        }
                    }
                }
            } ?: run {
                Text(
                    text = "正在加载...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ExerciseTitleCard(
    exercise: com.vita.healthtracker.data.local.entity.ExerciseSession,
    onSave: (String?, String?, String?) -> Unit,
    onDelete: () -> Unit,
) {
    var editing by remember(exercise.id, exercise.customTitle, exercise.customCategory, exercise.note) { mutableStateOf(false) }
    var titleText by remember(exercise.id, exercise.customTitle) {
        mutableStateOf(exercise.customTitle ?: ExerciseClassifier.displayTitle(exercise))
    }
    var categoryText by remember(exercise.id, exercise.customCategory) {
        mutableStateOf(exercise.customCategory ?: ExerciseClassifier.displayCategory(exercise))
    }
    var categoryMenuExpanded by remember(exercise.id) { mutableStateOf(false) }
    var noteText by remember(exercise.id, exercise.note) {
        mutableStateOf(exercise.note.orEmpty())
    }
    var confirmDelete by remember(exercise.id) { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .background(brush = VitaGradients.cardSurface, shape = MaterialTheme.shapes.medium)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (editing) {
                OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    label = { Text("运动标题") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Box {
                    TextButton(onClick = { categoryMenuExpanded = true }) {
                        Text("分类：${ExerciseClassifier.displayName(categoryText)}")
                    }
                    DropdownMenu(
                        expanded = categoryMenuExpanded,
                        onDismissRequest = { categoryMenuExpanded = false },
                    ) {
                        ExerciseClassifier.editableCategories.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    categoryText = option.key
                                    categoryMenuExpanded = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("备注") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { editing = false }) {
                        Text("取消")
                    }
                    TextButton(
                        onClick = {
                            onSave(titleText, categoryText, noteText)
                            editing = false
                        },
                    ) {
                        Text("保存")
                    }
                }
            } else {
                Text(
                    text = ExerciseClassifier.displayTitle(exercise),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = sourceLabel(exercise.source),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "分类：${ExerciseClassifier.displayName(ExerciseClassifier.displayCategory(exercise))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                exercise.note?.takeIf { it.isNotBlank() }?.let { note ->
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { editing = true }) {
                        Text("编辑")
                    }
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除这条记录？") },
            text = { Text("删除后会从统计和首页中隐藏。") },
            confirmButton = {
                TextButton(onClick = onDelete) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun ExerciseMetricSection(ex: com.vita.healthtracker.data.local.entity.ExerciseSession) {
    val rows = remember(ex) {
        buildList {
            ex.distanceMeters?.takeIf { it > 0.0 }?.let { add("距离" to "%.2f km".format(it / 1000.0)) }
            ex.elapsedMinutes?.takeIf { it > 0L }?.let { add("总耗时" to "$it 分钟") }
            ex.movingMinutes?.takeIf { it > 0L }?.let { add("移动时间" to "$it 分钟") }
            ex.totalCalories?.takeIf { it > 0.0 }?.let { add("总消耗" to "${it.toInt()} kcal") }
            ex.bmrCalories?.takeIf { it > 0.0 }?.let { add("静息消耗" to "${it.toInt()} kcal") }
            ex.steps?.takeIf { it > 0L }?.let { add("步数" to "%,d 步".format(it)) }
            ex.avgCadence?.takeIf { it > 0.0 }?.let { add("平均踏频" to "%.0f 步/分".format(it)) }
            ex.maxCadence?.takeIf { it > 0.0 }?.let { add("最大踏频" to "%.0f 步/分".format(it)) }
            ex.avgSpeed?.takeIf { it > 0.0 }?.let {
                add("平均速度" to "%.1f km/h".format(it * 3.6))
                paceText(it)?.let { pace -> add("平均配速" to pace) }
            }
            ex.maxSpeed?.takeIf { it > 0.0 }?.let { add("最快速度" to "%.1f km/h".format(it * 3.6)) }
            ex.elevationGainMeters?.takeIf { it > 0.0 }?.let { add("累计爬升" to "%.0f m".format(it)) }
            ex.elevationLossMeters?.takeIf { it > 0.0 }?.let { add("累计下降" to "%.0f m".format(it)) }
            ex.minElevationMeters?.takeIf { it > 0.0 }?.let { add("最低海拔" to "%.0f m".format(it)) }
            ex.maxElevationMeters?.takeIf { it > 0.0 }?.let { add("最高海拔" to "%.0f m".format(it)) }
            ex.trainingEffect?.takeIf { it > 0.0 }?.let { add("有氧训练效果" to "%.1f".format(it)) }
            ex.anaerobicTrainingEffect?.takeIf { it > 0.0 }?.let { add("无氧训练效果" to "%.1f".format(it)) }
            ex.avgStrideLengthMeters?.takeIf { it > 0.0 }?.let { add("平均步幅" to "%.2f m".format(it)) }
            ex.avgPowerWatts?.takeIf { it > 0.0 }?.let { add("平均功率" to "%.0f W".format(it)) }
            ex.maxPowerWatts?.takeIf { it > 0.0 }?.let { add("最大功率" to "%.0f W".format(it)) }
            ex.sweatLossMl?.takeIf { it > 0.0 }?.let { add("估计汗液流失" to "%.0f ml".format(it)) }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .background(brush = VitaGradients.cardSurface, shape = MaterialTheme.shapes.medium)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "详细指标",
                style = MaterialTheme.typography.titleLarge,
                color = VitaActive,
            )
            if (rows.isEmpty()) {
                Text(
                    text = "暂无更多明细。Garmin 返回活动摘要后会自动补充。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                rows.forEach { (label, value) ->
                    ExerciseMetricLine(label = label, value = value)
                }
            }
        }
    }
}

@Composable
private fun ExerciseMetricLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
        )
    }
}

private fun sourceLabel(source: String): String = when {
    source.contains("garmin.com", ignoreCase = true) -> "Garmin Connect 国际区"
    source.contains("garmin.cn", ignoreCase = true) -> "Garmin Connect 国区"
    source.contains("garmin", ignoreCase = true) -> "Garmin Connect"
    source.contains("health", ignoreCase = true) -> "Health Connect"
    else -> source
}

private fun paceText(speedMetersPerSecond: Double): String? {
    if (speedMetersPerSecond <= 0.0) return null
    val secondsPerKm = (1000.0 / speedMetersPerSecond).toInt()
    val minutes = secondsPerKm / 60
    val seconds = secondsPerKm % 60
    return "%d'%02d\" /km".format(minutes, seconds)
}
