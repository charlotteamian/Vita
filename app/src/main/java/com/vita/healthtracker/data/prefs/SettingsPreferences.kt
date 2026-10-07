package com.vita.healthtracker.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vita.healthtracker.domain.HomeMetric
import java.time.DayOfWeek
import java.time.LocalTime
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

    /** AI 洞察: Mac 分析服务地址 (host[:port]); 安全起见不做自动发现。 */
    val aiServerAddress: Flow<String> = ds.data.map { prefs ->
        prefs[KEY_AI_SERVER_ADDRESS] ?: ""
    }

    suspend fun setAiServerAddress(address: String) {
        ds.edit { it[KEY_AI_SERVER_ADDRESS] = address.trim() }
    }

    /** AI 洞察: Mac 分析服务口令, 对应服务端 VITA_AI_TOKEN。 */
    val aiServerToken: Flow<String> = ds.data.map { prefs ->
        prefs[KEY_AI_SERVER_TOKEN] ?: ""
    }

    suspend fun setAiServerToken(token: String) {
        ds.edit { it[KEY_AI_SERVER_TOKEN] = token.trim() }
    }

    /** AI 洞察: 分析方式 — [AI_MODE_LAN] Mac 桥接 / [AI_MODE_API] API 直连。默认 Mac 桥接。 */
    val aiMode: Flow<String> = ds.data.map { prefs ->
        prefs[KEY_AI_MODE] ?: AI_MODE_LAN
    }

    suspend fun setAiMode(mode: String) {
        ds.edit { it[KEY_AI_MODE] = mode }
    }

    /** AI 洞察 (API 直连): 服务商预设 id, 见 AiApiPresets。 */
    val aiApiPreset: Flow<String> = ds.data.map { prefs ->
        prefs[KEY_AI_API_PRESET] ?: "deepseek"
    }

    suspend fun setAiApiPreset(id: String) {
        ds.edit { it[KEY_AI_API_PRESET] = id }
    }

    /** AI 洞察 (API 直连): base URL 覆盖; 空 = 用预设默认。 */
    val aiApiBaseUrl: Flow<String> = ds.data.map { prefs ->
        prefs[KEY_AI_API_BASE_URL] ?: ""
    }

    suspend fun setAiApiBaseUrl(url: String) {
        ds.edit { it[KEY_AI_API_BASE_URL] = url.trim() }
    }

    /** AI 洞察 (API 直连): 用户自己的 API Key, 只存本机。 */
    val aiApiKey: Flow<String> = ds.data.map { prefs ->
        prefs[KEY_AI_API_KEY] ?: ""
    }

    suspend fun setAiApiKey(key: String) {
        ds.edit { it[KEY_AI_API_KEY] = key.trim() }
    }

    /** AI 洞察 (API 直连): 模型名覆盖; 空 = 用预设默认。 */
    val aiApiModel: Flow<String> = ds.data.map { prefs ->
        prefs[KEY_AI_API_MODEL] ?: ""
    }

    suspend fun setAiApiModel(model: String) {
        ds.edit { it[KEY_AI_API_MODEL] = model.trim() }
    }

    /**
     * AI 洞察: 分析区间 — [AI_RANGE_QUARTER] 近三月 / [AI_RANGE_YEAR] 近一年 / [AI_RANGE_ALL] 多年全景。
     * 默认近一年: 多年摘要不常变, 每次都带上会让结果显得「一大堆不变的内容」。
     */
    val aiAnalysisRange: Flow<String> = ds.data.map { prefs ->
        prefs[KEY_AI_ANALYSIS_RANGE] ?: AI_RANGE_YEAR
    }

    suspend fun setAiAnalysisRange(range: String) {
        ds.edit { it[KEY_AI_ANALYSIS_RANGE] = range }
    }

    /**
     * AI 洞察: 每个分析区间各缓存一份上次结果——切区间时显示该区间自己的上次分析,
     * 而不是停留在别的区间的结果上。
     */
    fun aiLastInsightJson(range: String): Flow<String?> = ds.data.map { prefs ->
        prefs[stringPreferencesKey("ai_last_insight_$range")]?.takeIf { it.isNotBlank() }
    }

    suspend fun saveAiInsight(range: String, insightJson: String) {
        ds.edit { it[stringPreferencesKey("ai_last_insight_$range")] = insightJson }
    }

    /** 分区间缓存之前的老缓存 (单份), 只作迁移期回退显示, 不再写入。 */
    val legacyAiInsightJson: Flow<String?> = ds.data.map { prefs ->
        prefs[KEY_AI_LAST_INSIGHT]?.takeIf { it.isNotBlank() }
    }

    /** 自动获取天气 (Open-Meteo, 需大致位置权限)。默认关闭, 不在未同意时联网/取位置。 */
    val weatherAutoEnabled: Flow<Boolean> = ds.data.map { it[KEY_WEATHER_AUTO] ?: false }

    suspend fun setWeatherAutoEnabled(enabled: Boolean) {
        ds.edit { it[KEY_WEATHER_AUTO] = enabled }
    }

    /** 记录提醒总开关。默认关闭。 */
    val reminderEnabled: Flow<Boolean> = ds.data.map { it[KEY_REMINDER_ENABLED] ?: false }

    suspend fun setReminderEnabled(enabled: Boolean) {
        ds.edit { it[KEY_REMINDER_ENABLED] = enabled }
    }

    /** 每日提醒时刻 (默认中午 13:00 + 晚上 21:00)。 */
    val reminderTimes: Flow<List<LocalTime>> = ds.data.map { prefs ->
        (prefs[KEY_REMINDER_TIMES] ?: DEFAULT_REMINDER_TIMES)
            .split(",")
            .mapNotNull { parseTime(it) }
            .sorted()
    }

    suspend fun setReminderTimes(times: List<LocalTime>) {
        val encoded = times.sorted().joinToString(",") { "%02d:%02d".format(it.hour, it.minute) }
        ds.edit { it[KEY_REMINDER_TIMES] = encoded }
    }

    private fun parseTime(value: String): LocalTime? = runCatching {
        val (h, m) = value.trim().split(":")
        LocalTime.of(h.toInt(), m.toInt())
    }.getOrNull()

    companion object {
        const val AI_MODE_LAN = "lan"
        const val AI_MODE_API = "api"
        const val AI_RANGE_QUARTER = "quarter"
        const val AI_RANGE_YEAR = "year"
        const val AI_RANGE_ALL = "all"

        /** 今日页「AI 今日解读」的缓存槽位 (复用 aiLastInsightJson/saveAiInsight 的按区间存储)。 */
        const val AI_RANGE_TODAY = "today"
        private const val DEFAULT_REMINDER_TIMES = "13:00,21:00"
        private val KEY_WEEK_START = stringPreferencesKey("week_start_day")
        private val KEY_AI_SERVER_ADDRESS = stringPreferencesKey("ai_server_address")
        private val KEY_AI_SERVER_TOKEN = stringPreferencesKey("ai_server_token")
        private val KEY_AI_MODE = stringPreferencesKey("ai_mode")
        private val KEY_AI_API_PRESET = stringPreferencesKey("ai_api_preset")
        private val KEY_AI_API_BASE_URL = stringPreferencesKey("ai_api_base_url")
        private val KEY_AI_API_KEY = stringPreferencesKey("ai_api_key")
        private val KEY_AI_API_MODEL = stringPreferencesKey("ai_api_model")
        private val KEY_AI_LAST_INSIGHT = stringPreferencesKey("ai_last_insight")
        private val KEY_AI_ANALYSIS_RANGE = stringPreferencesKey("ai_analysis_range")
        private val KEY_HOME_METRICS = stringSetPreferencesKey("home_metrics")
        private val KEY_SHOWN_HABIT_BADGE_TOKENS = stringSetPreferencesKey("shown_habit_badge_tokens")
        private val KEY_SHOWN_FITNESS_BADGE_IDS = stringSetPreferencesKey("shown_fitness_badge_ids")
        private val KEY_BADGE_UNLOCK_DATES = stringSetPreferencesKey("badge_unlock_dates")
        private val KEY_WEATHER_AUTO = booleanPreferencesKey("weather_auto_enabled")
        private val KEY_REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        private val KEY_REMINDER_TIMES = stringPreferencesKey("reminder_times")
    }
}
