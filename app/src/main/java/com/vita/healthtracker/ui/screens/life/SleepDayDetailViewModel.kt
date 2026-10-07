package com.vita.healthtracker.ui.screens.life

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vita.healthtracker.data.local.entity.SleepSession
import com.vita.healthtracker.data.repository.HealthRepository
import com.vita.healthtracker.domain.SleepStageParser
import com.vita.healthtracker.domain.SleepStageSegment
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SleepDayDetailUiState(
    val date: LocalDate = LocalDate.now(),
    /** 当晚主睡眠 (跨夜归醒来那天, 同晚多条取最长)。 */
    val mainSleep: SleepSession? = null,
    /** 同一天的其它睡眠 (小睡/碎片)。 */
    val naps: List<SleepSession> = emptyList(),
    /** 佳明分期区间; 空 = 没有分期数据, 只显示汇总。 */
    val stageSegments: List<SleepStageSegment> = emptyList(),
)

/**
 * 某一天的睡眠详情: 主睡眠汇总 + 整晚分期图。
 * 分期区间从 garmin_raw_record (categoryKey="sleep") 的原始 JSON 现解析,
 * 不动数据库结构; 非佳明来源没有分期, 退化为汇总。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SleepDayDetailViewModel(
    private val repo: HealthRepository,
) : ViewModel() {

    private val _date = MutableStateFlow(LocalDate.now())

    val state: StateFlow<SleepDayDetailUiState> = _date.flatMapLatest { date ->
        val zone = ZoneId.systemDefault()
        // 醒来在 date 的觉多半从前一晚开始, 查询窗口往前多带一天。
        val from = date.minusDays(1).atStartOfDay(zone).toInstant()
        val to = date.plusDays(1).atStartOfDay(zone).toInstant()
        combine(
            repo.sleepRange(from, to),
            repo.garminRawForDate(date),
        ) { sleeps, raws ->
            val sameDay = sleeps.filter { displayDate(it, zone) == date && it.totalMinutes in 1..960 }
            val main = sameDay.maxByOrNull { it.totalMinutes }
            val segments = raws.asSequence()
                .filter { it.categoryKey == "sleep" }
                .sortedByDescending { it.fetchedAtEpochMs }
                .map { SleepStageParser.parse(it.payloadJson) }
                .firstOrNull { it.isNotEmpty() }
                .orEmpty()
            // 主睡眠被手动修正过时, 分期图跟着整体平移同样的量 (分期本身来自原始 JSON, 时刻还是修正前的)。
            val stageShiftMs = main?.takeIf { it.isEdited }
                ?.originalStartEpochMs
                ?.let { main.startEpochMs - it }
                ?: 0L
            SleepDayDetailUiState(
                date = date,
                mainSleep = main,
                naps = sameDay.filter { it.id != main?.id }.sortedBy { it.startEpochMs },
                stageSegments = if (stageShiftMs == 0L) segments else segments.map {
                    it.copy(startEpochMs = it.startEpochMs + stageShiftMs, endEpochMs = it.endEpochMs + stageShiftMs)
                },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SleepDayDetailUiState())

    fun setDate(date: LocalDate) {
        _date.value = date
    }

    fun previousDay() {
        _date.value = _date.value.minusDays(1)
    }

    fun nextDay() {
        val next = _date.value.plusDays(1)
        if (!next.isAfter(LocalDate.now())) _date.value = next
    }

    /** 手动修正一条睡眠的时间 (跨时区错乱等); 数据流会自动刷新。 */
    fun editSleep(id: String, newStartMs: Long, newEndMs: Long) {
        viewModelScope.launch { repo.editSleepSession(id, newStartMs, newEndMs) }
    }

    /** 撤销修正, 回到手表原始时间。 */
    fun restoreSleep(id: String) {
        viewModelScope.launch { repo.restoreSleepSession(id) }
    }

    /** 删除这条睡眠 (软删, 同步不会带回)。 */
    fun deleteSleep(id: String) {
        viewModelScope.launch { repo.deleteSleepSession(id) }
    }

    /** 跨夜睡眠归醒来那天 (与全 app 口径一致)。 */
    private fun displayDate(sleep: SleepSession, zone: ZoneId): LocalDate? = when {
        sleep.endEpochMs > sleep.startEpochMs ->
            Instant.ofEpochMilli(sleep.endEpochMs).atZone(zone).toLocalDate()
        sleep.startEpochMs > 0 ->
            Instant.ofEpochMilli(sleep.startEpochMs).atZone(zone).toLocalDate()
        else -> null
    }
}
