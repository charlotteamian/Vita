package com.vita.healthtracker.ui.screens.account

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vita.healthtracker.data.apple.AppleHealthImporter
import com.vita.healthtracker.data.apple.AppleImportStatus
import com.vita.healthtracker.data.garmin.GarminAuthClient
import com.vita.healthtracker.data.sync.SyncCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AccountAuthUiState(
    val busy: Boolean = false,
    val message: String? = null,
    val isGarminLogged: Boolean = false,
    val garminLoggedDomains: Set<String> = emptySet(),
    val garminNeedsMfa: Boolean = false,
    val garminMfaDomain: String? = null,
    val garminMfaMethod: String? = null,
    val syncRunning: Boolean = false,
    val syncDomain: String? = null,
    val syncFraction: Float = 0f,
    val syncPhase: String = "",
    val syncProcessed: Int = 0,
    val syncTotal: Int = 0,
    val garminSyncDetails: List<String> = emptyList(),
    val appleImporting: Boolean = false,
    val appleImportRecords: Long = 0L,
    val appleImportPhase: String = "",
)

/**
 * 「账号授权」页的状态/动作。当前只接了佳明(Garmin)直连;
 * 以后接三星 / 小米 / Fitbit 时, 在这里加对应的 login/sync 方法即可。
 */
class AccountAuthViewModel(
    private val garminAuthClient: GarminAuthClient,
    private val syncCoordinator: SyncCoordinator,
    private val appleHealthImporter: AppleHealthImporter,
) : ViewModel() {

    private val _state = MutableStateFlow(
        AccountAuthUiState(
            isGarminLogged = garminAuthClient.loggedDomains().isNotEmpty(),
            garminLoggedDomains = garminAuthClient.loggedDomains().toSet(),
        )
    )
    val state: StateFlow<AccountAuthUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            syncCoordinator.status.collect { sync ->
                _state.value = _state.value.copy(
                    syncRunning = sync.running,
                    syncDomain = sync.domain,
                    syncFraction = sync.fraction,
                    syncPhase = sync.phase,
                    syncProcessed = sync.processed,
                    syncTotal = sync.total,
                    message = sync.message ?: _state.value.message,
                )
            }
        }
        viewModelScope.launch {
            appleHealthImporter.status.collect { st ->
                _state.value = when (st) {
                    is AppleImportStatus.Idle ->
                        _state.value.copy(appleImporting = false, appleImportPhase = "")
                    is AppleImportStatus.Running ->
                        _state.value.copy(appleImporting = true, appleImportRecords = st.records, appleImportPhase = st.phase)
                    is AppleImportStatus.Done ->
                        _state.value.copy(
                            appleImporting = false,
                            appleImportPhase = "",
                            message = "Apple 健康导入完成: ${st.days} 天日数据 · ${st.sleepNights} 晚睡眠 · ${st.workouts} 次运动 · ${st.cycleDays} 天周期",
                        )
                    is AppleImportStatus.Error ->
                        _state.value.copy(
                            appleImporting = false,
                            appleImportPhase = "",
                            message = "Apple 健康导入失败: ${st.message}",
                        )
                }
            }
        }
    }

    fun importAppleHealth(uri: Uri) {
        appleHealthImporter.start(uri)
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
        syncCoordinator.clearMessage()
        appleHealthImporter.acknowledge()
    }

    fun loginGarmin(email: String, password: String, useChinaServer: Boolean) {
        if (_state.value.busy) return
        if (email.isBlank() || password.isBlank()) {
            _state.value = _state.value.copy(message = "请输入 Garmin 账号和密码")
            return
        }
        viewModelScope.launch {
            val domain = if (useChinaServer) "garmin.cn" else "garmin.com"
            _state.value = _state.value.copy(busy = true, message = "正在登录 Garmin...")
            when (val result = garminAuthClient.login(email.trim(), password, domain)) {
                is GarminAuthClient.AuthResult.Success ->
                    refreshLoginState(
                        message = "${domain.label()} 登录成功",
                        garminNeedsMfa = false,
                        garminMfaMethod = null,
                    )
                is GarminAuthClient.AuthResult.NeedsMFA ->
                    _state.value = _state.value.copy(
                        busy = false,
                        garminNeedsMfa = true,
                        garminMfaDomain = domain,
                        garminMfaMethod = result.method,
                        message = "需要输入 Garmin 验证码",
                    )
                is GarminAuthClient.AuthResult.Error ->
                    _state.value = _state.value.copy(busy = false, message = "登录失败: ${result.msg}")
            }
        }
    }

    fun submitGarminMfa(code: String) {
        if (_state.value.busy) return
        if (code.isBlank()) {
            _state.value = _state.value.copy(message = "请输入 Garmin 验证码")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, message = "正在验证 Garmin 验证码...")
            when (val result = garminAuthClient.submitMfa(code.trim())) {
                is GarminAuthClient.AuthResult.Success ->
                    refreshLoginState(
                        message = "登录成功",
                        garminNeedsMfa = false,
                        garminMfaDomain = null,
                        garminMfaMethod = null,
                    )
                is GarminAuthClient.AuthResult.Error ->
                    _state.value = _state.value.copy(busy = false, message = "验证码验证失败: ${result.msg}")
                is GarminAuthClient.AuthResult.NeedsMFA ->
                    _state.value = _state.value.copy(busy = false, garminNeedsMfa = true, garminMfaMethod = result.method)
            }
        }
    }

    fun saveGarminCookies(cookieStr: String, domain: String) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, message = "处理登录凭证中...")
            when (val result = garminAuthClient.saveSessionCookies(cookieStr, domain)) {
                is GarminAuthClient.AuthResult.Success ->
                    refreshLoginState(message = "登录成功")
                is GarminAuthClient.AuthResult.Error ->
                    _state.value = _state.value.copy(busy = false, message = "登录凭证无效: ${result.msg}")
                is GarminAuthClient.AuthResult.NeedsMFA ->
                    _state.value = _state.value.copy(busy = false, garminNeedsMfa = true, garminMfaMethod = result.method)
            }
        }
    }

    fun logoutGarmin(useChinaServer: Boolean? = null) {
        val domain = useChinaServer?.let { if (it) "garmin.cn" else "garmin.com" }
        garminAuthClient.logout(domain)
        _state.value = _state.value.copy(
            isGarminLogged = garminAuthClient.loggedDomains().isNotEmpty(),
            garminLoggedDomains = garminAuthClient.loggedDomains().toSet(),
            garminNeedsMfa = false,
            garminMfaDomain = null,
            garminMfaMethod = null,
            message = if (domain == null) "已退出佳明账号" else "已退出 ${domain.label()}",
        )
    }

    fun syncGarmin(days: Long? = 365L) {
        val domains = garminAuthClient.loggedDomains()
        if (_state.value.busy || domains.isEmpty()) {
            if (domains.isEmpty()) _state.value = _state.value.copy(message = "请先登录至少一个 Garmin 区服")
            return
        }
        syncCoordinator.start(days, includeGarmin = true, includeHealthConnect = false)
    }

    fun syncGarminDomain(useChinaServer: Boolean, days: Long? = 365L) {
        val domain = if (useChinaServer) "garmin.cn" else "garmin.com"
        if (_state.value.busy || !garminAuthClient.isLogged(domain)) {
            if (!garminAuthClient.isLogged(domain)) _state.value = _state.value.copy(message = "请先登录 ${domain.label()}")
            return
        }
        syncCoordinator.startGarminDomain(domain, days)
    }

    fun stopSync() {
        syncCoordinator.stop()
    }

    private fun refreshLoginState(
        message: String,
        garminNeedsMfa: Boolean = _state.value.garminNeedsMfa,
        garminMfaDomain: String? = _state.value.garminMfaDomain,
        garminMfaMethod: String? = _state.value.garminMfaMethod,
    ) {
        _state.value = _state.value.copy(
            busy = false,
            isGarminLogged = garminAuthClient.loggedDomains().isNotEmpty(),
            garminLoggedDomains = garminAuthClient.loggedDomains().toSet(),
            garminNeedsMfa = garminNeedsMfa,
            garminMfaDomain = if (garminNeedsMfa) garminMfaDomain else null,
            garminMfaMethod = garminMfaMethod,
            message = message,
        )
    }

    private fun String.label(): String =
        when (this) {
            "garmin.cn" -> "国区"
            "garmin.com" -> "国际区"
            else -> this
        }
}
