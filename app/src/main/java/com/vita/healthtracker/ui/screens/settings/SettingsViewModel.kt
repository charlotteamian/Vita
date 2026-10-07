package com.vita.healthtracker.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vita.healthtracker.data.ai.DirectApiInsightProvider
import com.vita.healthtracker.data.backup.BackupManager
import com.vita.healthtracker.data.healthconnect.HealthConnectManager
import com.vita.healthtracker.data.prefs.SettingsPreferences
import com.vita.healthtracker.data.reminder.ReminderScheduler
import com.vita.healthtracker.data.repository.CycleRepository
import com.vita.healthtracker.data.repository.HealthRepository
import com.vita.healthtracker.data.sensor.StepSensorManager
import com.vita.healthtracker.data.weather.WeatherSyncManager
import com.vita.healthtracker.data.update.AppUpdateManager
import com.vita.healthtracker.domain.HomeMetric
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val availability: HealthConnectManager.Availability = HealthConnectManager.Availability.NotSupported,
    val grantedCount: Int = 0,
    val totalPermissions: Int = HealthConnectManager.ALL_PERMISSIONS.size,
    val message: String? = null,
    val busy: Boolean = false,
    val weekStartDay: DayOfWeek = DayOfWeek.MONDAY,
    val homeMetrics: Set<String> = HomeMetric.DEFAULT_KEYS,
    val phoneStepSensorAvailable: Boolean = false,
    val phoneStepSensorRunning: Boolean = false,
    val aiServerAddress: String = "",
    val aiServerToken: String = "",
    val aiMode: String = SettingsPreferences.AI_MODE_LAN,
    val aiApiPreset: String = "deepseek",
    val aiApiBaseUrl: String = "",
    val aiApiKey: String = "",
    val aiApiModel: String = "",
    val aiApiTesting: Boolean = false,
    val aiApiTestResult: String? = null,
    val weatherAutoEnabled: Boolean = false,
    val reminderEnabled: Boolean = false,
    val reminderTimes: List<LocalTime> = emptyList(),
)

class SettingsViewModel(
    val healthConnect: HealthConnectManager,
    private val healthRepo: HealthRepository,
    private val cycleRepo: CycleRepository,
    private val backupManager: BackupManager,
    private val prefs: SettingsPreferences,
    private val stepSensorManager: StepSensorManager,
    private val weatherSyncManager: WeatherSyncManager,
    private val reminderScheduler: ReminderScheduler,
    private val directApiProvider: DirectApiInsightProvider,
    private val appUpdateManager: AppUpdateManager,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()
    val updateState = appUpdateManager.state

    fun checkForAppUpdate() = appUpdateManager.checkForUpdate()
    fun downloadAppUpdate() = appUpdateManager.downloadUpdate()
    fun installAppUpdate() = appUpdateManager.installDownloadedUpdate()
    fun reportUpdateInstallerUnavailable() = appUpdateManager.reportInstallerUnavailable()

    init {
        refreshStatus()
        refreshPhoneDataSource()
        viewModelScope.launch {
            prefs.weekStartDay.collect { day -> _state.value = _state.value.copy(weekStartDay = day) }
        }
        viewModelScope.launch {
            prefs.homeMetrics.collect { metrics -> _state.value = _state.value.copy(homeMetrics = metrics) }
        }
        viewModelScope.launch {
            prefs.aiServerAddress.collect { addr -> _state.value = _state.value.copy(aiServerAddress = addr) }
        }
        viewModelScope.launch {
            prefs.aiServerToken.collect { token -> _state.value = _state.value.copy(aiServerToken = token) }
        }
        viewModelScope.launch {
            prefs.aiMode.collect { mode -> _state.value = _state.value.copy(aiMode = mode) }
        }
        viewModelScope.launch {
            prefs.aiApiPreset.collect { id -> _state.value = _state.value.copy(aiApiPreset = id) }
        }
        viewModelScope.launch {
            prefs.aiApiBaseUrl.collect { url -> _state.value = _state.value.copy(aiApiBaseUrl = url) }
        }
        viewModelScope.launch {
            prefs.aiApiKey.collect { key -> _state.value = _state.value.copy(aiApiKey = key) }
        }
        viewModelScope.launch {
            prefs.aiApiModel.collect { model -> _state.value = _state.value.copy(aiApiModel = model) }
        }
        viewModelScope.launch {
            prefs.weatherAutoEnabled.collect { on -> _state.value = _state.value.copy(weatherAutoEnabled = on) }
        }
        viewModelScope.launch {
            prefs.reminderEnabled.collect { on -> _state.value = _state.value.copy(reminderEnabled = on) }
        }
        viewModelScope.launch {
            prefs.reminderTimes.collect { times -> _state.value = _state.value.copy(reminderTimes = times) }
        }
    }

    /** 开关天气自动获取; 开启后立即尝试拉一次 (权限/位置在 manager 内部自检)。 */
    fun setWeatherAutoEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setWeatherAutoEnabled(enabled)
            if (enabled) weatherSyncManager.refreshIfEnabled()
        }
    }

    fun setReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setReminderEnabled(enabled)
            reminderScheduler.reschedule()
        }
    }

    fun toggleReminderTime(time: LocalTime) {
        viewModelScope.launch {
            val current = _state.value.reminderTimes
            val next = if (time in current) current - time else current + time
            // 至少保留一个时刻, 否则开关开着却没提醒。
            if (next.isEmpty()) {
                _state.value = _state.value.copy(message = "至少保留一个提醒时刻")
                return@launch
            }
            prefs.setReminderTimes(next.sorted())
            reminderScheduler.reschedule()
        }
    }

    fun setAiServerAddress(address: String) {
        viewModelScope.launch { prefs.setAiServerAddress(address) }
    }

    fun setAiServerToken(token: String) {
        viewModelScope.launch { prefs.setAiServerToken(token) }
    }

    fun setAiMode(mode: String) {
        viewModelScope.launch { prefs.setAiMode(mode) }
    }

    /** 切换服务商预设: 清掉上一家的地址/模型覆盖 (Key 保留, 填错会得到明确的 401)。 */
    fun setAiApiPreset(id: String) {
        viewModelScope.launch {
            prefs.setAiApiPreset(id)
            prefs.setAiApiBaseUrl("")
            prefs.setAiApiModel("")
            _state.value = _state.value.copy(aiApiTestResult = null)
        }
    }

    fun saveAiApiConfig(baseUrl: String, key: String, model: String) {
        viewModelScope.launch {
            prefs.setAiApiBaseUrl(baseUrl)
            prefs.setAiApiKey(key)
            prefs.setAiApiModel(model)
        }
    }

    /** 保存当前填写的 API 配置并发一个最小请求验证连通性。 */
    fun saveAndTestAiApi(baseUrl: String, key: String, model: String) {
        if (_state.value.aiApiTesting) return
        viewModelScope.launch {
            _state.value = _state.value.copy(aiApiTesting = true, aiApiTestResult = null)
            prefs.setAiApiBaseUrl(baseUrl)
            prefs.setAiApiKey(key)
            prefs.setAiApiModel(model)
            val result = runCatching { directApiProvider.testConnection() }
                .getOrElse { e -> e.message ?: "连接失败" }
            _state.value = _state.value.copy(aiApiTesting = false, aiApiTestResult = result)
        }
    }

    fun refreshStatus() {
        viewModelScope.launch {
            val avail = healthConnect.availability
            val all = HealthConnectManager.ALL_PERMISSIONS
            val granted = healthConnect.grantedPermissions().count { it in all }
            _state.value = _state.value.copy(availability = avail, grantedCount = granted)
        }
    }

    fun installHealthConnect(): Intent = healthConnect.installHealthConnectIntent()

    fun systemPermissionSettingsIntent(packageName: String): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

    fun refreshPhoneDataSource() {
        _state.value = _state.value.copy(
            phoneStepSensorAvailable = stepSensorManager.isAvailable,
            phoneStepSensorRunning = stepSensorManager.isListening,
        )
    }

    fun startPhoneStepSensor() {
        val ok = stepSensorManager.startListening()
        _state.value = _state.value.copy(
            phoneStepSensorAvailable = stepSensorManager.isAvailable,
            phoneStepSensorRunning = stepSensorManager.isListening,
            message = when {
                ok -> "手机步数同步已开启。走动后会自动写入今日数据。"
                !stepSensorManager.isAvailable -> "这台手机没有可用的步数传感器"
                else -> "无法启动手机步数同步，请先在系统权限里允许“身体活动”"
            },
        )
    }

    /** 由权限授权流程的回调上报结果, 让用户知道是否真的拿到了授权。 */
    fun reportPermissionResult(grantedAll: Boolean, grantedAny: Boolean) {
        refreshStatus()
        _state.value = _state.value.copy(
            message = when {
                grantedAll -> "授权成功, 已可读取健康数据"
                grantedAny -> "已部分授权, 部分数据可能读不到。可点「授权数据读取」补齐。"
                else -> "未获得授权。请在弹窗中允许 Vita 读取健康数据。"
            }
        )
    }

    fun showMessage(msg: String) { _state.value = _state.value.copy(message = msg) }

    fun exportTo(uri: Uri) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, message = null)
            val ok = backupManager.exportToUri(uri)
            _state.value = _state.value.copy(
                busy = false,
                message = if (ok) "已导出到 $uri" else "导出失败",
            )
        }
    }

    fun importFrom(uri: Uri) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, message = null)
            val result = backupManager.importFromUri(uri)
            _state.value = _state.value.copy(
                busy = false,
                message = result?.let { "已导入 ${it.totalRows} 条记录" } ?: "导入失败,文件格式不对?",
            )
        }
    }

    fun clearMessage() { _state.value = _state.value.copy(message = null) }

    // ─── 偏好设置 ──────────────────────────────────────────────

    fun setWeekStartDay(day: DayOfWeek) {
        viewModelScope.launch { prefs.setWeekStartDay(day) }
    }

    fun setHomeMetricEnabled(key: String, enabled: Boolean) {
        viewModelScope.launch {
            val cur = _state.value.homeMetrics.toMutableSet()
            if (enabled) cur.add(key) else cur.remove(key)
            if (cur.isEmpty()) {
                // 不允许全关, 否则首页空白; 保持现状并提示。
                _state.value = _state.value.copy(message = "至少保留一个数据项")
                return@launch
            }
            prefs.setHomeMetrics(cur)
        }
    }
}
