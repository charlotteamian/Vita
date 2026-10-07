package com.vita.healthtracker.ui.screens.life

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vita.healthtracker.data.local.entity.WeatherEntry
import com.vita.healthtracker.domain.Mood
import com.vita.healthtracker.domain.MoodCatalog
import com.vita.healthtracker.domain.MoodDaily
import com.vita.healthtracker.domain.MoodMoment
import com.vita.healthtracker.domain.MoodShape
import com.vita.healthtracker.domain.WeatherCatalog
import com.vita.healthtracker.domain.WeatherKind
import com.vita.healthtracker.ui.theme.VitaActive
import com.vita.healthtracker.ui.theme.VitaGradients
import com.vita.healthtracker.ui.theme.VitaOnSurfaceMuted
import com.vita.healthtracker.ui.vitaViewModel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val MOOD_HISTORY_DAYS = 56
private val HHMM = DateTimeFormatter.ofPattern("HH:mm")

/**
 * 一天的代表天气 id: 天气现在随情绪时刻记 (一天多变), 取当天最后一条带天气的时刻;
 * 都没记时退回自动获取的日天气。
 */
private fun representativeWeatherId(daily: MoodDaily?, auto: WeatherEntry?): String? =
    daily?.moments?.lastOrNull { !it.weatherId.isNullOrBlank() }?.weatherId ?: auto?.weatherId

private fun dayWeatherKind(daily: MoodDaily?, auto: WeatherEntry?): WeatherKind? =
    WeatherCatalog.byId(representativeWeatherId(daily, auto))

/** 编辑目标: 在某天新增一条时刻, 或修改某条已有时刻。 */
private sealed interface MoodEditTarget {
    val date: LocalDate
    data class Add(override val date: LocalDate) : MoodEditTarget
    data class Edit(override val date: LocalDate, val moment: MoodMoment) : MoodEditTarget
}

/** 自定义情绪编辑目标: 新建, 或修改已有的一种。 */
private sealed interface CustomMoodTarget {
    data object Create : CustomMoodTarget
    data class Edit(val mood: Mood) : CustomMoodTarget
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodJournalScreen(onBack: () -> Unit) {
    val vm = vitaViewModel<LifeViewModel>()
    val state by vm.state.collectAsStateWithLifecycle()
    val today = remember { LocalDate.now() }
    val days = remember(today) { (0 until MOOD_HISTORY_DAYS).map { today.minusDays(it.toLong()) } }

    var editTarget by remember { mutableStateOf<MoodEditTarget?>(null) }
    var dayEditorDate by remember { mutableStateOf<LocalDate?>(null) }
    var customTarget by remember { mutableStateOf<CustomMoodTarget?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的情绪轨迹") },
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
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { MoodWeekSummary(days.take(7), state.moodDaily) }
            item {
                TodayMomentsCard(
                    today = today,
                    daily = state.moodDaily[today.toString()],
                    weather = dayWeatherKind(state.moodDaily[today.toString()], state.weather[today.toString()]),
                    onAdd = { editTarget = MoodEditTarget.Add(today) },
                    onEditMoment = { moment -> editTarget = MoodEditTarget.Edit(today, moment) },
                )
            }
            item {
                Text(
                    "更早 · 最近 $MOOD_HISTORY_DAYS 天",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                )
            }
            items(days.drop(1)) { date ->
                PastDayRow(
                    date = date,
                    daily = state.moodDaily[date.toString()],
                    weather = dayWeatherKind(state.moodDaily[date.toString()], state.weather[date.toString()]),
                    onClick = { dayEditorDate = date },
                )
            }
        }
    }

    editTarget?.let { target ->
        val deleteAction: (() -> Unit)? = (target as? MoodEditTarget.Edit)?.let { edit ->
            {
                vm.deleteMoodMoment(edit.moment.id)
                editTarget = null
            }
        }
        // 新记一刻时, 天气默认沿用这天已有的天气 (最近一条或自动获取), 可再改。
        val seedWeatherId = when (target) {
            is MoodEditTarget.Edit -> target.moment.weatherId
            is MoodEditTarget.Add -> representativeWeatherId(
                state.moodDaily[target.date.toString()],
                state.weather[target.date.toString()],
            )
        }
        MoodMomentDialog(
            target = target,
            today = today,
            customMoods = state.customMoods,
            initialWeatherId = seedWeatherId,
            onSubmit = { moodId, note, weatherId ->
                when (target) {
                    is MoodEditTarget.Add -> {
                        // 今天记的是「此刻」; 补录过去某天默认落在当天中午。
                        val at = if (target.date == today) LocalDateTime.now() else target.date.atTime(12, 0)
                        vm.logMoodMoment(moodId, at, note, weatherId)
                    }
                    is MoodEditTarget.Edit -> vm.updateMoodMoment(target.moment.id, moodId, note, weatherId)
                }
                editTarget = null
            },
            onDelete = deleteAction,
            onCreateCustom = { customTarget = CustomMoodTarget.Create },
            onEditCustom = { mood -> customTarget = CustomMoodTarget.Edit(mood) },
            onDismiss = { editTarget = null },
        )
    }

    customTarget?.let { target ->
        CustomMoodEditorDialog(
            target = target,
            onSave = { label, colorHex, shape, valence ->
                when (target) {
                    is CustomMoodTarget.Create -> vm.createCustomMood(label, colorHex, shape, valence)
                    is CustomMoodTarget.Edit -> vm.updateCustomMood(target.mood.id, label, colorHex, shape, valence)
                }
                customTarget = null
            },
            onDelete = (target as? CustomMoodTarget.Edit)?.let { edit ->
                {
                    vm.archiveCustomMood(edit.mood.id)
                    customTarget = null
                }
            },
            onDismiss = { customTarget = null },
        )
    }

    dayEditorDate?.let { date ->
        MoodDayEditorSheet(
            date = date,
            daily = state.moodDaily[date.toString()],
            weather = dayWeatherKind(state.moodDaily[date.toString()], state.weather[date.toString()]),
            onAddMoment = { editTarget = MoodEditTarget.Add(date) },
            onEditMoment = { moment -> editTarget = MoodEditTarget.Edit(date, moment) },
            onClearDay = {
                vm.clearMoodDay(date)
                dayEditorDate = null
            },
            onDismiss = { dayEditorDate = null },
        )
    }
}

@Composable
private fun MoodWeekSummary(week: List<LocalDate>, moodDaily: Map<String, MoodDaily>) {
    val logged = week.count { moodDaily.containsKey(it.toString()) }
    val moments = week.sumOf { moodDaily[it.toString()]?.count ?: 0 }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(VitaGradients.primaryAccent)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "近 7 天记了 $logged / 7 天 · 共 $moments 次",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "心情一天可以记好几次——开心、累、烦都随手点一下，越细越能看出规律",
            style = MaterialTheme.typography.bodySmall,
            color = VitaOnSurfaceMuted,
        )
        Row(
            modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            week.reversed().forEach { date ->
                val mood = MoodCatalog.byId(moodDaily[date.toString()]?.dominantMoodId)
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    MoodGlyph(mood = mood, size = 24.dp, filled = mood != null)
                    Text(
                        weekdayLabelCn(date),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (mood == null) VitaOnSurfaceMuted else Color(mood.colorHex),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayMomentsCard(
    today: LocalDate,
    daily: MoodDaily?,
    weather: WeatherKind?,
    onAdd: () -> Unit,
    onEditMoment: (MoodMoment) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(VitaActive.copy(alpha = 0.10f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "今天",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            WeatherChip(weather = weather)
        }
        if (daily == null || daily.moments.isEmpty()) {
            Text(
                "还没记录今天的心情，点下面随手记一笔",
                style = MaterialTheme.typography.bodySmall,
                color = VitaOnSurfaceMuted,
            )
        } else {
            daily.moments.forEach { moment ->
                MomentRow(moment = moment, onClick = { onEditMoment(moment) })
            }
            if (daily.count > 1) {
                val dominant = MoodCatalog.byId(daily.dominantMoodId)
                Text(
                    buildString {
                        append("今天 ${daily.count} 次")
                        dominant?.let { append(" · 主导 ${it.label}") }
                        if (daily.volatility >= 2) append(" · 起伏较大")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = VitaOnSurfaceMuted,
                )
            }
        }
        AddMomentButton(text = "＋ 记此刻", onClick = onAdd)
    }
}

@Composable
private fun MomentRow(moment: MoodMoment, onClick: () -> Unit) {
    val mood = MoodCatalog.byId(moment.moodId)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            moment.time.format(HHMM),
            style = MaterialTheme.typography.labelMedium,
            color = VitaOnSurfaceMuted,
            modifier = Modifier.width(42.dp),
        )
        MoodGlyph(mood = mood, size = 30.dp, filled = mood != null)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                mood?.label ?: moment.moodId,
                style = MaterialTheme.typography.bodyMedium,
                color = mood?.let { Color(it.colorHex) } ?: MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            if (moment.note.isNotBlank()) {
                Text(
                    moment.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = VitaOnSurfaceMuted,
                )
            }
        }
        WeatherCatalog.byId(moment.weatherId)?.let { w ->
            Text(
                w.shortLabel,
                style = MaterialTheme.typography.labelSmall,
                color = weatherColor(w),
                modifier = Modifier.padding(end = 2.dp),
            )
        }
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = "编辑",
            tint = VitaOnSurfaceMuted,
        )
    }
}

@Composable
private fun AddMomentButton(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(VitaActive.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Add, contentDescription = null, tint = VitaActive)
        Spacer(modifier = Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = VitaActive, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PastDayRow(
    date: LocalDate,
    daily: MoodDaily?,
    weather: WeatherKind?,
    onClick: () -> Unit,
) {
    val dateLabel = remember(date) { date.format(DateTimeFormatter.ofPattern("M月d日")) }
    val dominant = MoodCatalog.byId(daily?.dominantMoodId)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MoodGlyph(mood = dominant, size = 38.dp, filled = dominant != null)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "$dateLabel · 周${weekdayLabelCn(date)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = pastDaySubtitle(daily, dominant),
                style = MaterialTheme.typography.bodySmall,
                color = dominant?.let { Color(it.colorHex) } ?: VitaOnSurfaceMuted,
            )
        }
        weather?.let {
            Text(
                it.shortLabel,
                style = MaterialTheme.typography.labelSmall,
                color = weatherColor(it),
            )
        }
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = "编辑这天",
            tint = VitaOnSurfaceMuted,
        )
    }
}

private fun pastDaySubtitle(daily: MoodDaily?, dominant: Mood?): String {
    if (daily == null || dominant == null) return "未记录 · 点开补记"
    return buildString {
        append(dominant.label)
        if (daily.count > 1) append(" · ${daily.count} 次")
        if (daily.count > 1 && daily.volatility >= 2) append(" · 有起伏")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoodDayEditorSheet(
    date: LocalDate,
    daily: MoodDaily?,
    weather: WeatherKind?,
    onAddMoment: () -> Unit,
    onEditMoment: (MoodMoment) -> Unit,
    onClearDay: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dateLabel = remember(date) { date.format(DateTimeFormatter.ofPattern("M月d日")) }
    val moments = daily?.moments.orEmpty()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$dateLabel · 周${weekdayLabelCn(date)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                WeatherChip(weather = weather)
            }
            if (moments.isEmpty()) {
                Text(
                    "这天还没记录心情，点下面补记一条",
                    style = MaterialTheme.typography.bodySmall,
                    color = VitaOnSurfaceMuted,
                )
            } else {
                moments.forEach { moment ->
                    MomentRow(moment = moment, onClick = { onEditMoment(moment) })
                }
            }
            AddMomentButton(text = "补记一条", onClick = onAddMoment)
            if (moments.isNotEmpty()) {
                TextButton(onClick = onClearDay) {
                    Text("清空这天", color = VitaOnSurfaceMuted)
                }
            }
        }
    }
}

/** 天气只读展示 (自动获取); 没取到就不显示。 */
@Composable
private fun WeatherChip(weather: WeatherKind?) {
    if (weather == null) return
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(weatherColor(weather).copy(alpha = 0.18f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = weather.shortLabel,
            style = MaterialTheme.typography.labelMedium,
            color = weatherColor(weather),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoodMomentDialog(
    target: MoodEditTarget,
    today: LocalDate,
    customMoods: List<Mood>,
    initialWeatherId: String?,
    onSubmit: (moodId: String, note: String, weatherId: String?) -> Unit,
    onDelete: (() -> Unit)?,
    onCreateCustom: () -> Unit,
    onEditCustom: (Mood) -> Unit,
    onDismiss: () -> Unit,
) {
    val editing = target as? MoodEditTarget.Edit
    var selected by remember(target) { mutableStateOf(editing?.moment?.moodId) }
    var note by remember(target) { mutableStateOf(editing?.moment?.note ?: "") }
    var weatherId by remember(target) { mutableStateOf(editing?.moment?.weatherId ?: initialWeatherId) }
    val dateLabel = remember(target.date) { target.date.format(DateTimeFormatter.ofPattern("M月d日")) }
    val titleText = when (target) {
        is MoodEditTarget.Add -> if (target.date == today) "此刻心情" else "$dateLabel · 补记心情"
        is MoodEditTarget.Edit -> "${target.moment.time.format(HHMM)} 的心情"
    }
    val pickerMoods = MoodCatalog.builtIn + customMoods
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titleText) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 内置 + 自定义 + 末尾一格「新情绪」。
                val cells = pickerMoods.size + 1
                (0 until cells).chunked(4).forEach { rowIndices ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowIndices.forEach { index ->
                            val mood = pickerMoods.getOrNull(index)
                            if (mood != null) {
                                MoodPickItem(
                                    mood = mood,
                                    selected = mood.id == selected,
                                    onClick = { selected = mood.id },
                                    onLongClick = if (mood.isCustom) ({ onEditCustom(mood) }) else null,
                                    modifier = Modifier.weight(1f),
                                )
                            } else {
                                AddCustomMoodItem(
                                    onClick = onCreateCustom,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        repeat(4 - rowIndices.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
                if (customMoods.isNotEmpty()) {
                    Text(
                        "长按自己加的情绪可以修改或删除",
                        style = MaterialTheme.typography.labelSmall,
                        color = VitaOnSurfaceMuted,
                    )
                }
                // 天气随情绪一起记 (一天多变); 可选, 再点选中的可取消。
                Text(
                    "这会儿的天气（可选）",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    WeatherCatalog.kinds.forEach { kind ->
                        FilterChip(
                            selected = weatherId == kind.id,
                            onClick = { weatherId = if (weatherId == kind.id) null else kind.id },
                            label = { Text(kind.shortLabel) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = weatherColor(kind).copy(alpha = 0.22f),
                                selectedLabelColor = weatherColor(kind),
                            ),
                        )
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("随手记一句（可选）") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 1,
                    maxLines = 3,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selected?.let { onSubmit(it, note, weatherId) } },
                enabled = selected != null,
            ) { Text("保存") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("删除", color = VitaOnSurfaceMuted) }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MoodPickItem(
    mood: Mood,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) Color(mood.colorHex).copy(alpha = 0.18f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f),
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MoodGlyph(mood = mood, size = 40.dp)
        Text(
            mood.label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Color(mood.colorHex) else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

/** 选择器末尾的「＋ 新情绪」格。 */
@Composable
private fun AddCustomMoodItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null, tint = VitaOnSurfaceMuted)
        }
        Text(
            "新情绪",
            style = MaterialTheme.typography.labelMedium,
            color = VitaOnSurfaceMuted,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

// ──── 自定义情绪编辑器 ────────────────────────────────────────

private val CUSTOM_MOOD_PALETTE = listOf(
    0xFFFFD166, 0xFFFF8A65, 0xFFFF6B6B, 0xFFF783AC,
    0xFFB197FC, 0xFF74C0FC, 0xFF5C8DD6, 0xFF63E6BE,
    0xFF8CE99A, 0xFFA9E34B, 0xFFE9C46A, 0xFF9AA4BF,
)

/** 倾向 (情绪价 1..5): 决定这个情绪在聚合/关联分析里算正向还是负向。 */
private val VALENCE_OPTIONS = listOf(
    5 to "很好",
    4 to "偏好",
    3 to "中性",
    2 to "偏差",
    1 to "很差",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomMoodEditorDialog(
    target: CustomMoodTarget,
    onSave: (label: String, colorHex: Long, shape: MoodShape, valence: Int) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val editing = (target as? CustomMoodTarget.Edit)?.mood
    var label by remember(target) { mutableStateOf(editing?.label ?: "") }
    var colorHex by remember(target) { mutableStateOf(editing?.colorHex ?: CUSTOM_MOOD_PALETTE.first()) }
    var shape by remember(target) { mutableStateOf(editing?.shape ?: MoodShape.CIRCLE) }
    var valence by remember(target) { mutableStateOf(MoodCatalog.valence(editing?.id) ?: 3) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing == null) "新情绪" else "修改「${editing.label}」") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // 实时预览
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    MoodGlyph(
                        mood = Mood("preview", label, colorHex, shape, isCustom = true),
                        size = 56.dp,
                    )
                }
                OutlinedTextField(
                    value = label,
                    onValueChange = { if (it.length <= 6) label = it },
                    label = { Text("名字 (最多 6 个字)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Text("这个情绪对你来说偏正面还是负面？", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    VALENCE_OPTIONS.forEach { (value, text) ->
                        FilterChip(
                            selected = valence == value,
                            onClick = { valence = value },
                            label = { Text(text) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(colorHex).copy(alpha = 0.22f),
                                selectedLabelColor = Color(colorHex),
                            ),
                        )
                    }
                }
                Text("颜色", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CUSTOM_MOOD_PALETTE.forEach { hex ->
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(hex))
                                .border(
                                    width = if (colorHex == hex) 3.dp else 0.dp,
                                    color = if (colorHex == hex) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape,
                                )
                                .clickable { colorHex = hex },
                        )
                    }
                }
                Text("造型", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MoodShape.entries.forEach { s ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (shape == s) Color(colorHex).copy(alpha = 0.18f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f),
                                )
                                .clickable { shape = s }
                                .padding(6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            MoodGlyph(
                                mood = Mood("shape_$s", "", colorHex, s, isCustom = true),
                                size = 36.dp,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(label.trim(), colorHex, shape, valence) },
                enabled = label.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("删除", color = VitaOnSurfaceMuted) }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

private fun weatherColor(weather: WeatherKind?): Color = when (weather?.severity) {
    2 -> Color(0xFFFF8A65)
    1 -> Color(0xFF74C0FC)
    else -> Color(0xFFFFD166)
}

private fun weekdayLabelCn(date: LocalDate): String = when (date.dayOfWeek) {
    java.time.DayOfWeek.MONDAY -> "一"
    java.time.DayOfWeek.TUESDAY -> "二"
    java.time.DayOfWeek.WEDNESDAY -> "三"
    java.time.DayOfWeek.THURSDAY -> "四"
    java.time.DayOfWeek.FRIDAY -> "五"
    java.time.DayOfWeek.SATURDAY -> "六"
    java.time.DayOfWeek.SUNDAY -> "日"
}
