package com.vita.healthtracker.data.sync

import android.content.Context
import com.vita.healthtracker.data.garmin.GarminAuthClient
import com.vita.healthtracker.data.garmin.GarminSyncManager
import com.vita.healthtracker.data.garmin.GarminSyncResult
import com.vita.healthtracker.data.repository.CycleRepository
import com.vita.healthtracker.data.repository.HealthRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 同步范围选项 (今日页 / 账号页共用)。days = null 表示增量。 */
enum class SyncScope(val label: String, val days: Long?) {
    Week("最近一周", 7L),
    Month("最近一月", 31L),
    Year("最近一年", 365L),
    All("全部 (从有数据开始)", 3650L),
}

/** 同步全局状态, 由 UI 观察。 */
data class SyncUiStatus(
    val running: Boolean = false,
    val source: String = "",          // "garmin" / "health_connect" / ""
    val domain: String? = null,
    val processed: Int = 0,
    val total: Int = 0,
    val phase: String = "",
    val savedDaily: Int = 0,
    val message: String? = null,
) {
    val fraction: Float
        get() = if (total <= 0) 0f else (processed.toFloat() / total).coerceIn(0f, 1f)
}

/**
 * 应用级同步协调器。
 *
 * 关键点 (#3 / #7):
 *  - 运行在传入的应用作用域 [scope] 上, **不**绑定任何 ViewModel。
 *    因此用户切到别的 Tab / 退出当前页时, viewModelScope 被清掉也不会打断同步。
 *  - 暴露 [status] 进度流, 任何页面都能观察到同一份进度。
 *  - [stop] 取消正在进行的同步 (协作式取消; GarminSyncManager 已对 CancellationException 放行)。
 *  - 一次性把「佳明 + Health Connect」串起来同步 (用户选择的统一主链路)。
 */
class SyncCoordinator(
    private val context: Context,
    private val scope: CoroutineScope,
    private val healthRepo: HealthRepository,
    private val cycleRepo: CycleRepository,
    private val garminAuthClient: GarminAuthClient,
    private val garminSyncManager: GarminSyncManager,
) {
    private val _status = MutableStateFlow(SyncUiStatus())
    val status: StateFlow<SyncUiStatus> = _status.asStateFlow()

    val isRunning: Boolean get() = _status.value.running

    private var job: Job? = null

    /**
     * 启动一次同步。重复调用在运行中会被忽略。
     * @param days   回溯天数; null = 增量 (佳明默认 7 天, HC 走上次终点)。
     */
    fun start(
        days: Long?,
        includeGarmin: Boolean = true,
        includeHealthConnect: Boolean = true,
    ) {
        if (_status.value.running) return
        _status.value = SyncUiStatus(running = true, phase = "同步中")
        GarminSyncForegroundService.start(context)
        job = scope.launch {
            val messages = mutableListOf<String>()
            try {
                if (includeGarmin) {
                    val domains = garminAuthClient.loggedDomains()
                    if (domains.isNotEmpty()) {
                        val toDate = LocalDate.now()
                        val fromDate = toDate.minusDays(days ?: 7L)
                        domains.forEach { domain ->
                            garminAuthClient.activateDomain(domain)
                            // 同步前先清掉这段范围里的 BMR 幽灵数据 (没戴表那天的恒定基础代谢)。
                            runCatching { healthRepo.cleanupBmrOnlyGarmin(fromDate, toDate) }
                            runCatching { healthRepo.cleanupBmrOnlyDaily(fromDate, toDate) }
                            val result = garminSyncManager.syncAll(fromDate, toDate) { p ->
                                _status.value = _status.value.copy(
                                    running = true,
                                    source = "garmin",
                                    domain = p.domain,
                                    processed = p.processedDays,
                                    total = p.totalDays,
                                    phase = "${label(p.domain)}同步中",
                                    savedDaily = p.savedDailyCount,
                                )
                            }
                            messages.add(garminMessage(result, domain))
                        }
                    }
                }
                if (includeHealthConnect) {
                    _status.value = _status.value.copy(
                        running = true,
                        source = "health_connect",
                        domain = null,
                        phase = "同步 Health Connect…",
                    )
                    val forceStart = days?.let { Instant.now().minusSeconds(it * 24 * 3600) }
                    val outcome = healthRepo.syncFromHealthConnect(forceStartFrom = forceStart)
                    runCatching { cycleRepo.syncFromHealthConnect() }
                    hcMessage(outcome)?.let(messages::add)
                }
                healthRepo.markSyncedAt()
                _status.value = SyncUiStatus(
                    running = false,
                    message = messages.filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "同步完成" },
                )
                GarminSyncForegroundService.stop(context)
            } catch (e: CancellationException) {
                _status.value = SyncUiStatus(running = false, message = "已停止同步")
                GarminSyncForegroundService.stop(context)
                // 不再向上抛: 这是用户主动停止, 已处理完毕。
            } catch (e: Exception) {
                _status.value = SyncUiStatus(
                    running = false,
                    message = "同步出错: ${e.message ?: e::class.java.simpleName}",
                )
                GarminSyncForegroundService.stop(context)
            }
        }
    }

    /** 只同步单个 Garmin 区服, 用于账号授权页每个区服旁边的同步按钮。 */
    fun startGarminDomain(domain: String, days: Long?) {
        if (_status.value.running) return
        _status.value = SyncUiStatus(running = true, source = "garmin", domain = domain, phase = "同步中")
        GarminSyncForegroundService.start(context)
        job = scope.launch {
            try {
                if (!garminAuthClient.isLogged(domain)) {
                    _status.value = SyncUiStatus(running = false, message = "${label(domain)} 尚未登录")
                    GarminSyncForegroundService.stop(context)
                    return@launch
                }
                garminAuthClient.activateDomain(domain)
                val toDate = LocalDate.now()
                val fromDate = toDate.minusDays(days ?: 7L)
                runCatching { healthRepo.cleanupBmrOnlyGarmin(fromDate, toDate) }
                runCatching { healthRepo.cleanupBmrOnlyDaily(fromDate, toDate) }
                val result = garminSyncManager.syncAll(fromDate, toDate) { p ->
                    _status.value = _status.value.copy(
                        running = true,
                        source = "garmin",
                        domain = p.domain,
                        processed = p.processedDays,
                        total = p.totalDays,
                        phase = "${label(p.domain)}同步中",
                        savedDaily = p.savedDailyCount,
                    )
                }
                healthRepo.markSyncedAt()
                _status.value = SyncUiStatus(running = false, message = garminMessage(result, domain))
                GarminSyncForegroundService.stop(context)
            } catch (e: CancellationException) {
                _status.value = SyncUiStatus(running = false, message = "已停止同步")
                GarminSyncForegroundService.stop(context)
            } catch (e: Exception) {
                _status.value = SyncUiStatus(
                    running = false,
                    message = "${label(domain)} 同步出错: ${e.message ?: e::class.java.simpleName}",
                )
                GarminSyncForegroundService.stop(context)
            }
        }
    }

    /** 立即停止当前同步。 */
    fun stop() {
        job?.cancel()
        GarminSyncForegroundService.stop(context)
    }

    fun clearMessage() {
        if (!_status.value.running) _status.value = _status.value.copy(message = null)
    }

    private fun garminMessage(result: GarminSyncResult, domain: String): String = when {
        result.stoppedByAuth -> "${label(domain)} 授权失效, 请重新登录"
        result.lastError != null -> "${label(domain)} 同步中断: ${result.lastError}"
        result.savedDailyCount > 0 || result.savedExerciseCount > 0 || result.savedRawCount > 0 ->
            "${label(domain)} 写入/更新 ${result.savedDailyCount} 天 / ${result.savedExerciseCount} 次活动 / ${result.savedRawCount} 类 Garmin 数据"
        else -> "${label(domain)} 无新数据"
    }

    private fun hcMessage(outcome: HealthRepository.SyncOutcome): String? = when (outcome) {
        is HealthRepository.SyncOutcome.Synced ->
            "Health Connect 更新 ${outcome.dailyCount} 天 / ${outcome.sleepCount} 睡眠"
        // 没装 HC / 没授权时不提示, 避免对只用佳明的用户造成噪音。
        HealthRepository.SyncOutcome.HealthConnectUnavailable -> null
        HealthRepository.SyncOutcome.MissingPermission -> null
    }

    private fun label(domain: String): String = when (domain) {
        "garmin.cn" -> "国区"
        "garmin.com" -> "国际区"
        else -> domain
    }
}
