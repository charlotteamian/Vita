package com.vita.healthtracker.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vita.healthtracker.domain.HomeMetric
import java.time.DayOfWeek
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 进程内单例 DataStore (本地, 不上云)。 */
private val Context.vitaDataStore: DataStore<Preferences> by preferencesDataStore(name = "vita_settings")

/**
 * App 级用户偏好:
 *  - 一周开始日 (周一 / 周日), 默认周一。
 *  - 首页(今日)展示哪些数据项, 默认全部。
 */
class SettingsPreferences(context: Context) {

    private val ds = context.applicationContext.vitaDataStore

    val weekStartDay: Flow<DayOfWeek> = ds.data.map { prefs ->
        when (prefs[KEY_WEEK_START]) {
            DayOfWeek.SUNDAY.name -> DayOfWeek.SUNDAY
            else -> DayOfWeek.MONDAY // 默认周一
        }
    }

    suspend fun setWeekStartDay(day: DayOfWeek) {
        ds.edit { it[KEY_WEEK_START] = day.name }
    }

    val homeMetrics: Flow<Set<String>> = ds.data.map { prefs ->
        prefs[KEY_HOME_METRICS] ?: HomeMetric.DEFAULT_KEYS
    }

    suspend fun setHomeMetrics(keys: Set<String>) {
        // 至少保留一项, 避免首页彻底空白。
        val safe = if (keys.isEmpty()) HomeMetric.DEFAULT_KEYS else keys
        ds.edit { it[KEY_HOME_METRICS] = safe }
    }

    val shownHabitBadgeTokens: Flow<Set<String>> = ds.data.map { prefs ->
        prefs[KEY_SHOWN_HABIT_BADGE_TOKENS] ?: emptySet()
    }

    suspend fun markHabitBadgeTokensShown(tokens: Set<String>) {
        if (tokens.isEmpty()) return
        ds.edit { prefs ->
            val existing = prefs[KEY_SHOWN_HABIT_BADGE_TOKENS] ?: emptySet()
            prefs[KEY_SHOWN_HABIT_BADGE_TOKENS] = existing + tokens
        }
    }

    /** 已弹过庆祝弹窗的健身徽章 id（健身徽章不按 habit 划分，直接用 badge id）。 */
    val shownFitnessBadgeIds: Flow<Set<String>> = ds.data.map { prefs ->
        prefs[KEY_SHOWN_FITNESS_BADGE_IDS] ?: emptySet()
    }

    suspend fun markFitnessBadgesShown(ids: Set<String>) {
        if (ids.isEmpty()) return
        ds.edit { prefs ->
            val existing = prefs[KEY_SHOWN_FITNESS_BADGE_IDS] ?: emptySet()
            prefs[KEY_SHOWN_FITNESS_BADGE_IDS] = existing + ids
        }
    }

    /**
     * 徽章首次解锁日期 (badgeId -> ISO 日期)。每条以 "badgeId|yyyy-MM-dd" 存进 Set。
     * 用于 3D 徽章卡翻面显示「解锁于 …」。第一次观察到已获得就写入, 之后不覆盖。
     */
    val badgeUnlockDates: Flow<Map<String, String>> = ds.data.map { prefs ->
        (prefs[KEY_BADGE_UNLOCK_DATES] ?: emptySet()).mapNotNull { entry ->
            val idx = entry.indexOf('|')
            if (idx <= 0 || idx >= entry.length - 1) null
            else entry.substring(0, idx) to entry.substring(idx + 1)
        }.toMap()
    }

    suspend fun recordBadgeUnlocks(ids: Set<String>, dateIso: String) {
        if (ids.isEmpty()) return
        ds.edit { prefs ->
            val existing = prefs[KEY_BADGE_UNLOCK_DATES] ?: emptySet()
            val existingIds = existing.mapNotNull { entry ->
                entry.substringBefore('|', "").takeIf { it.isNotEmpty() }
            }.toSet()
            val toAdd = ids.filter { it !in existingIds }.map { "$it|$dateIso" }
            if (toAdd.isNotEmpty()) prefs[KEY_BADGE_UNLOCK_DATES] = existing + toAdd
        }
    }

    companion object {
        private val KEY_WEEK_START = stringPreferencesKey("week_start_day")
        private val KEY_HOME_METRICS = stringSetPreferencesKey("home_metrics")
        private val KEY_SHOWN_HABIT_BADGE_TOKENS = stringSetPreferencesKey("shown_habit_badge_tokens")
        private val KEY_SHOWN_FITNESS_BADGE_IDS = stringSetPreferencesKey("shown_fitness_badge_ids")
        private val KEY_BADGE_UNLOCK_DATES = stringSetPreferencesKey("badge_unlock_dates")
    }
}
