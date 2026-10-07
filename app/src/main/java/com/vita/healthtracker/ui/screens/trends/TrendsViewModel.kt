package com.vita.healthtracker.ui.screens.trends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vita.healthtracker.data.ai.AiInsight
import com.vita.healthtracker.data.ai.AiInsightManager
import com.vita.healthtracker.data.prefs.SettingsPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 趋势页只负责「解读」: AI 洞察 (长期变化)。本地关联卡已按 charlotte 要求移除。 */
class TrendsViewModel(
    private val aiManager: AiInsightManager,
    private val prefs: SettingsPreferences,
) : ViewModel() {

    /** AI 洞察: 状态在 manager 里 (app scope), 离开页面分析不中断。 */
    val aiStatus: StateFlow<AiInsightManager.Status> = aiManager.status

    /** 分析区间偏好 (近三月/近一年/多年全景), 持久化, 下次打开保留。 */
    val aiRange: StateFlow<String> = prefs.aiAnalysisRange
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsPreferences.AI_RANGE_YEAR)

    /** 展示的洞察跟随所选区间: 每个区间各有一份缓存, 切区间显示各自的上次结果。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    val aiInsight: StateFlow<AiInsight?> = prefs.aiAnalysisRange
        .flatMapLatest { range -> aiManager.lastInsightFor(range) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setAiRange(range: String) {
        viewModelScope.launch { prefs.setAiAnalysisRange(range) }
    }

    fun requestAiAnalysis() = aiManager.requestAnalysis()
    fun clearAiError() = aiManager.clearError()
}
