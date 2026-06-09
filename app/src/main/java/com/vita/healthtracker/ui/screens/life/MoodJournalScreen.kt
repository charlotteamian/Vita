package com.vita.healthtracker.ui.screens.life

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.vita.healthtracker.domain.Mood
import com.vita.healthtracker.domain.MoodCatalog
import com.vita.healthtracker.domain.WeatherCatalog
import com.vita.healthtracker.domain.WeatherKind
import com.vita.healthtracker.ui.theme.VitaActive
import com.vita.healthtracker.ui.theme.VitaGradients
import com.vita.healthtracker.ui.theme.VitaOnSurfaceMuted
import com.vita.healthtracker.ui.vitaViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val MOOD_HISTORY_DAYS = 56

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodJournalScreen(onBack: () -> Unit) {
    val vm = vitaViewModel<LifeViewModel>()
    val state by vm.state.collectAsStateWithLifecycle()
    val today = remember { LocalDate.now() }
    var pickerDate by remember { mutableStateOf<LocalDate?>(null) }
    var weatherPickerDate by remember { mutableStateOf<LocalDate?>(null) }

    // 从今天往前 MOOD_HISTORY_DAYS 天, 倒序 (今天在最上)。
    val days = remember(today) { (0 until MOOD_HISTORY_DAYS).map { today.minusDays(it.toLong()) } }

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
            item { MoodWeekSummary(days.take(7), state.moods) }
            item {
                Text(
                    "近期轨迹 · 最近 $MOOD_HISTORY_DAYS 天",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                )
            }
            itemsIndexed(days) { index, date ->
                val entry = state.moods[date.toString()]
                val mood = MoodCatalog.byId(entry?.moodId)
                val weather = WeatherCatalog.byId(state.weather[date.toString()]?.weatherId)
                MoodHopscotchRow(
                    date = date,
                    today = today,
                    mood = mood,
                    weather = weather,
                    alignEnd = index % 2 == 1,
                    onMoodClick = { if (!date.isAfter(today)) pickerDate = date },
                    onWeatherClick = { if (!date.isAfter(today)) weatherPickerDate = date },
                )
            }
        }
    }

    pickerDate?.let { date ->
        val current = MoodCatalog.byId(state.moods[date.toString()]?.moodId)
        MoodPickerDialog(
            date = date,
            current = current,
            onPick = { mood ->
                vm.setMood(date, mood.id)
                pickerDate = null
            },
            onClear = {
                vm.clearMood(date)
                pickerDate = null
            },
            onDismiss = { pickerDate = null },
        )
    }

    weatherPickerDate?.let { date ->
        val current = WeatherCatalog.byId(state.weather[date.toString()]?.weatherId)
        WeatherPickerDialog(
            date = date,
            current = current,
            onPick = { weather ->
                vm.setWeather(date, weather.id)
                weatherPickerDate = null
            },
            onClear = {
                vm.clearWeather(date)
                weatherPickerDate = null
            },
            onDismiss = { weatherPickerDate = null },
        )
    }
}

@Composable
private fun MoodWeekSummary(week: List<LocalDate>, moods: Map<String, com.vita.healthtracker.data.local.entity.MoodEntry>) {
    val logged = week.count { moods.containsKey(it.toString()) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(VitaGradients.primaryAccent)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "近 7 天记录 $logged / 7 天",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "下方是最近 $MOOD_HISTORY_DAYS 天轨迹；轻点任意一天记录或修改心情",
            style = MaterialTheme.typography.bodySmall,
            color = VitaOnSurfaceMuted,
        )
        Row(
            modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            week.reversed().forEach { date ->
                val mood = MoodCatalog.byId(moods[date.toString()]?.moodId)
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
private fun MoodHopscotchRow(
    date: LocalDate,
    today: LocalDate,
    mood: Mood?,
    weather: WeatherKind?,
    alignEnd: Boolean,
    onMoodClick: () -> Unit,
    onWeatherClick: () -> Unit,
) {
    val isToday = date == today
    val isFuture = date.isAfter(today)
    Row(modifier = Modifier.fillMaxWidth()) {
        if (alignEnd) Spacer(modifier = Modifier.weight(1f))
        MoodDayCard(
            date = date,
            isToday = isToday,
            isFuture = isFuture,
            mood = mood,
            onClick = onMoodClick,
            modifier = Modifier.weight(2.4f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        WeatherDayCard(
            weather = weather,
            isFuture = isFuture,
            onClick = onWeatherClick,
            modifier = Modifier.weight(1.15f),
        )
        if (!alignEnd) Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun WeatherDayCard(
    weather: WeatherKind?,
    isFuture: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(weatherColor(weather).copy(alpha = if (weather == null) 0.10f else 0.18f))
            .clickable(enabled = !isFuture, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("天气", style = MaterialTheme.typography.labelSmall, color = VitaOnSurfaceMuted)
        Text(
            text = weather?.shortLabel ?: if (isFuture) "未到" else "记录",
            style = MaterialTheme.typography.bodyMedium,
            color = if (weather == null) VitaOnSurfaceMuted else weatherColor(weather),
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MoodDayCard(
    date: LocalDate,
    isToday: Boolean,
    isFuture: Boolean,
    mood: Mood?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateLabel = remember(date) { date.format(DateTimeFormatter.ofPattern("M月d日")) }
    val weekday = weekdayLabelCn(date)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (isToday) VitaActive.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.16f),
            )
            .clickable(enabled = !isFuture, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MoodGlyph(
            mood = mood,
            size = 46.dp,
            filled = mood != null,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "$dateLabel · 周$weekday",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = when {
                    mood != null -> mood.label
                    isFuture -> "未到"
                    isToday -> "记录今天心情"
                    else -> "未记录"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (mood != null) Color(mood.colorHex) else VitaOnSurfaceMuted,
            )
        }
    }
}

@Composable
private fun MoodPickerDialog(
    date: LocalDate,
    current: Mood?,
    onPick: (Mood) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dateLabel = remember(date) { date.format(DateTimeFormatter.ofPattern("M月d日")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$dateLabel 的心情") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MoodCatalog.moods.chunked(4).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { mood ->
                            MoodPickItem(
                                mood = mood,
                                selected = mood.id == current?.id,
                                onClick = { onPick(mood) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(4 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
            }
        },
        confirmButton = {
            if (current != null) {
                TextButton(onClick = onClear) { Text("清除", color = VitaOnSurfaceMuted) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

@Composable
private fun MoodPickItem(
    mood: Mood,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) Color(mood.colorHex).copy(alpha = 0.18f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f),
            )
            .clickable(onClick = onClick)
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
        )
    }
}

@Composable
private fun WeatherPickerDialog(
    date: LocalDate,
    current: WeatherKind?,
    onPick: (WeatherKind) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dateLabel = remember(date) { date.format(DateTimeFormatter.ofPattern("M月d日")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$dateLabel 的天气") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                WeatherCatalog.kinds.chunked(3).forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { weather ->
                            WeatherPickItem(
                                weather = weather,
                                selected = weather.id == current?.id,
                                onClick = { onPick(weather) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
            }
        },
        confirmButton = {
            if (current != null) TextButton(onClick = onClear) { Text("清除", color = VitaOnSurfaceMuted) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

@Composable
private fun WeatherPickItem(
    weather: WeatherKind,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = weather.label,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) weatherColor(weather) else MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) weatherColor(weather).copy(alpha = 0.20f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
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
