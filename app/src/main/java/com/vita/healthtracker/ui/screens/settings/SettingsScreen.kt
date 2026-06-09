package com.vita.healthtracker.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.vita.healthtracker.R
import com.vita.healthtracker.data.healthconnect.HealthConnectManager
import com.vita.healthtracker.domain.HomeMetric
import com.vita.healthtracker.ui.theme.VitaError
import com.vita.healthtracker.ui.theme.VitaGradients
import com.vita.healthtracker.ui.theme.VitaTertiary
import com.vita.healthtracker.ui.vitaViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(navController: NavController) {
    val vm = vitaViewModel<SettingsViewModel>()
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    val permLauncher = rememberLauncherForActivityResult(
        contract = vm.healthConnect.permissionContract()
    ) { granted ->
        // 上报真实授权结果, 让用户清楚知道是否拿到授权 (修复「点了授权却没反应/没授权」)。
        vm.reportPermissionResult(
            grantedAll = granted.containsAll(HealthConnectManager.READ_PERMISSIONS),
            grantedAny = granted.isNotEmpty(),
        )
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(vm::exportTo) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(vm::importFrom) }

    LaunchedEffect(state.message) {
        state.message?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            vm.clearMessage()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ─── Health Connect Card ────────────────────────────────
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(
                modifier = Modifier
                    .background(VitaGradients.cardSurface, MaterialTheme.shapes.medium)
                    .padding(20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(connected = state.availability == HealthConnectManager.Availability.Installed)
                    Icon(
                        Icons.Outlined.HealthAndSafety,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                    Text(
                        stringResource(R.string.settings_health_connect),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                val statusText = when (state.availability) {
                    HealthConnectManager.Availability.Installed -> "已可用 · 已授权 ${state.grantedCount}/${state.totalPermissions} 项"
                    HealthConnectManager.Availability.NotInstalled -> "本机未安装 Health Connect (Android 13 以下需手动安装)"
                    HealthConnectManager.Availability.ProviderUpdateRequired -> "Health Connect 版本过旧,请到 Play 更新"
                    HealthConnectManager.Availability.NotSupported -> "当前设备不支持 Health Connect"
                }
                Text(
                    statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                when (state.availability) {
                    HealthConnectManager.Availability.Installed -> {
                        val fullyGranted = state.grantedCount == state.totalPermissions
                        Button(
                            onClick = {
                                runCatching { permLauncher.launch(HealthConnectManager.ALL_PERMISSIONS) }
                                    .onFailure { vm.showMessage("无法打开系统授权页面, 请确认 Health Connect 已安装并更新") }
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        ) {
                            Text(if (fullyGranted) "HC 授权设置" else stringResource(R.string.settings_grant_permissions))
                        }
                    }
                    HealthConnectManager.Availability.NotInstalled,
                    HealthConnectManager.Availability.ProviderUpdateRequired -> {
                        Button(
                            onClick = {
                                runCatching { context.startActivity(vm.installHealthConnect()) }
                                    .onFailure { vm.showMessage("打不开应用商店") }
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        ) { Text(stringResource(R.string.settings_install_hc)) }
                    }
                    HealthConnectManager.Availability.NotSupported -> Unit
                }
            }
        }

        // ─── 系统权限 ───────────────────────────────────────────
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(VitaGradients.cardSurface, MaterialTheme.shapes.medium)
                    .padding(20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        "系统权限",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Text(
                    "管理手机步数传感器、通知等 Android 权限",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Button(
                    onClick = {
                        runCatching { context.startActivity(vm.systemPermissionSettingsIntent(context.packageName)) }
                            .onFailure { vm.showMessage("打不开系统权限设置") }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                ) {
                    Text("管理授权")
                }
            }
        }

        // ─── 手机自身数据源 ─────────────────────────────────────
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(VitaGradients.cardSurface, MaterialTheme.shapes.medium)
                    .padding(20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(connected = state.phoneStepSensorRunning)
                    Icon(
                        Icons.AutoMirrored.Outlined.DirectionsWalk,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                    Text(
                        "手机自身数据",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                val phoneStatus = when {
                    !state.phoneStepSensorAvailable -> "这台手机没有可用的步数传感器"
                    state.phoneStepSensorRunning -> "已开启 · 手机步数会自动补充没有手表数据的日期"
                    else -> "可用 · 需要允许“身体活动”权限后开始记录"
                }
                Text(
                    phoneStatus,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    "手机数据不会覆盖佳明 / Health Connect / Apple 健康里已经存在的同日数据，只在缺数据时补齐步数、距离和活跃消耗。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Column(
                    modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = vm::startPhoneStepSensor,
                        enabled = state.phoneStepSensorAvailable,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (state.phoneStepSensorRunning) "重新启动同步" else "开启手机同步")
                    }
                    OutlinedButton(
                        onClick = {
                            runCatching { context.startActivity(vm.systemPermissionSettingsIntent(context.packageName)) }
                                .onFailure { vm.showMessage("打不开系统权限设置") }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("系统权限")
                    }
                }
            }
        }

        // ─── 账号授权入口 (跳转到 AccountAuthScreen) ─────────────
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.clickable { navController.navigate("account_auth") },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(VitaGradients.cardSurface, MaterialTheme.shapes.medium)
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        "账号授权",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "连接佳明等第三方账号直连同步",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ─── 通用设置: 一周开始日 ────────────────────────────────
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(
                modifier = Modifier
                    .background(VitaGradients.cardSurface, MaterialTheme.shapes.medium)
                    .padding(20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        "通用设置",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Text(
                    "一周开始日",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Text(
                    "影响统计页「周」视图的起始日。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
                )
                val weekOptions = listOf(DayOfWeek.MONDAY to "周一", DayOfWeek.SUNDAY to "周日")
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    weekOptions.forEachIndexed { index, (day, label) ->
                        SegmentedButton(
                            selected = state.weekStartDay == day,
                            onClick = { vm.setWeekStartDay(day) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = weekOptions.size),
                        ) { Text(label) }
                    }
                }
            }
        }

        // ─── 首页数据项可见性 ────────────────────────────────────
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(
                modifier = Modifier
                    .background(VitaGradients.cardSurface, MaterialTheme.shapes.medium)
                    .padding(20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.ViewAgenda, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        "首页数据项",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Text(
                    "选择「今日」页要展示哪些卡片。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
                HomeMetric.ORDERED.forEach { metric ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            metric.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = metric.key in state.homeMetrics,
                            onCheckedChange = { vm.setHomeMetricEnabled(metric.key, it) },
                        )
                    }
                }
            }
        }

        // ─── Backup Card ────────────────────────────────────────
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(
                modifier = Modifier
                    .background(VitaGradients.cardSurface, MaterialTheme.shapes.medium)
                    .padding(20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                    Text(
                        "本地备份",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Text(
                    "所有数据保存在设备本地,可导出 JSON 备份到任意位置 (Files / 网盘 / 邮件)。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Column(
                    modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            val name = "vita-backup-${LocalDate.now().format(DateTimeFormatter.ISO_DATE)}.json"
                            exportLauncher.launch(name)
                        },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.CloudUpload, contentDescription = null)
                        Text(stringResource(R.string.settings_backup_export), modifier = Modifier.padding(start = 6.dp))
                    }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json")) },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.CloudDownload, contentDescription = null)
                        Text(stringResource(R.string.settings_backup_import), modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }

        // ─── About Card ─────────────────────────────────────────
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(
                modifier = Modifier
                    .background(VitaGradients.cardSurface, MaterialTheme.shapes.medium)
                    .padding(20.dp),
            ) {
                Text(
                    stringResource(R.string.settings_about),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Vita 0.2.0\n数据源: Health Connect (佳明/三星/小米等) + 手机内置步数传感器\n本地存储,不上云。\n\n后台同步: 每 3 小时自动拉取最新数据",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        SnackbarHost(snackbar) { data -> Snackbar(snackbarData = data) }
    }
}

/**
 * 连接状态脉冲圆点: 已连接 = 绿色脉冲, 未连接 = 红色静态
 */
@Composable
private fun StatusDot(connected: Boolean) {
    if (connected) {
        val infiniteTransition = rememberInfiniteTransition(label = "statusPulse")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "dotAlpha",
        )
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .alpha(alpha)
                .background(VitaTertiary),
        )
    } else {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(VitaError.copy(alpha = 0.7f)),
        )
    }
}
