package com.vita.healthtracker.data.repository

import com.vita.healthtracker.data.local.dao.CustomMoodDao
import com.vita.healthtracker.data.local.dao.MoodDao
import com.vita.healthtracker.data.local.entity.CustomMood
import com.vita.healthtracker.data.local.entity.MoodEntry
import com.vita.healthtracker.domain.CustomMoodRegistration
import com.vita.healthtracker.domain.Mood
import com.vita.healthtracker.domain.MoodCatalog
import com.vita.healthtracker.domain.MoodShape
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MoodRepository(
    private val moodDao: MoodDao,
    private val customMoodDao: CustomMoodDao,
    scope: CoroutineScope,
) {

    init {
        // 把自定义情绪持续灌进 MoodCatalog 运行时注册表 (含归档, 供历史记录解析与分析用)。
        scope.launch {
            customMoodDao.observeAll().collect { list ->
                MoodCatalog.setCustomMoods(list.map { it.toRegistration() })
            }
        }
    }
    fun observeRange(fromDate: LocalDate, toDate: LocalDate): Flow<List<MoodEntry>> =
        moodDao.observeRange(fromDate.toString(), toDate.toString())

    fun observeAll(): Flow<List<MoodEntry>> = moodDao.observeAll()

    /** 新增一条情绪时刻; date 由记录时刻所在本地日期派生。[weatherId] 为记这一刻时的天气 (可空)。 */
    suspend fun addMoment(moodId: String, recordedAtEpochMs: Long, note: String = "", weatherId: String? = null) {
        val date = Instant.ofEpochMilli(recordedAtEpochMs)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .toString()
        moodDao.upsert(
            MoodEntry(
                id = UUID.randomUUID().toString(),
                date = date,
                recordedAtEpochMs = recordedAtEpochMs,
                moodId = moodId,
                note = note.trim(),
                weatherId = weatherId,
            ),
        )
    }

    /** 修改某条时刻的情绪/备注/天气 (时间不变)。 */
    suspend fun updateMoment(id: String, moodId: String, note: String = "", weatherId: String? = null) {
        moodDao.updateContent(id, moodId, note.trim(), weatherId, System.currentTimeMillis())
    }

    suspend fun deleteMoment(id: String) {
        moodDao.deleteById(id)
    }

    /** 清掉某一天的所有情绪时刻。 */
    suspend fun clearDay(date: LocalDate) {
        moodDao.deleteByDate(date.toString())
    }

    // ──── 自定义情绪 ────────────────────────────────────────

    /** 选择器可见的自定义情绪 (未归档), UI 通过 ViewModel 状态订阅以便重组。 */
    fun observeActiveCustomMoods(): Flow<List<Mood>> =
        customMoodDao.observeAll().map { list ->
            list.filter { !it.isArchived }.map { it.toMood() }
        }

    suspend fun createCustomMood(label: String, colorHex: Long, shape: MoodShape, valence: Int) {
        customMoodDao.upsert(
            CustomMood(
                id = UUID.randomUUID().toString(),
                label = label.trim(),
                colorHex = colorHex,
                shape = shape.name,
                valence = valence.coerceIn(1, 5),
                createdAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun updateCustomMood(id: String, label: String, colorHex: Long, shape: MoodShape, valence: Int) {
        val existing = customMoodDao.all().firstOrNull { it.id == id } ?: return
        customMoodDao.upsert(
            existing.copy(
                label = label.trim(),
                colorHex = colorHex,
                shape = shape.name,
                valence = valence.coerceIn(1, 5),
            ),
        )
    }

    /** 删除 = 归档: 历史记录仍能解析出名字/颜色/价, 只是选择器不再出现。 */
    suspend fun archiveCustomMood(id: String) {
        customMoodDao.archive(id)
    }

    private fun CustomMood.toMood(): Mood = Mood(
        id = id,
        label = label,
        colorHex = colorHex,
        shape = runCatching { MoodShape.valueOf(shape) }.getOrDefault(MoodShape.CIRCLE),
        isCustom = true,
    )

    private fun CustomMood.toRegistration() = CustomMoodRegistration(
        mood = toMood(),
        valence = valence,
        isArchived = isArchived,
    )
}
