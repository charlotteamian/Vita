package com.vita.healthtracker.ui.screens.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.vita.healthtracker.R
import com.vita.healthtracker.BuildConfig
import com.vita.healthtracker.data.ai.AiApiPresets
import com.vita.healthtracker.data.healthconnect.HealthConnectManager
import com.vita.healthtracker.data.prefs.SettingsPreferences
import com.vita.healthtracker.domain.HomeMetric
import com.vita.healthtracker.ui.theme.VitaError
import com.vita.healthtracker.ui.theme.VitaGradients
import com.vita.healthtracker.ui.theme.VitaTertiary
import com.vita.healthtracker.ui.vitaViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
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

    val locationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.setWeatherAutoEnabled(true)
        else vm.showMessage("未授予位置权限，无法自动获取天气")
    }

    val notifPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.setReminderEnabled(true)
        else vm.showMessage("未授予通知权限，提醒无法弹出")
    }

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

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(
                            "自动获取天气",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "用大致位置从 Open-Meteo 取天气（每天一次，免费）；关闭则不联网取位置。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.weatherAutoEnabled,
                        onCheckedChange = { checked ->
                            if (!checked) {
                                vm.setWeatherAutoEnabled(false)
                            } else {
                                val granted = ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.ACCESS_COARSE_LOCATION,
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) vm.setWeatherAutoEnabled(true)
                                else locationPermLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                            }
                        },
                    )
                }

                Text(
                    "AI 分析",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Text(
                    "趋势页「AI 洞察」的分析方式: Mac 桥接走家里电脑的订阅额度 (需同一 Wi-Fi); API 直连由手机直接调大模型, 出门也能用, 费用走自己的 API Key。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.aiMode == SettingsPreferences.AI_MODE_LAN,
                        onClick = { vm.setAiMode(SettingsPreferences.AI_MODE_LAN) },
                        label = { Text("Mac 桥接") },
                    )
                    FilterChip(
                        selected = state.aiMode == SettingsPreferences.AI_MODE_API,
                        onClick = { vm.setAiMode(SettingsPreferences.AI_MODE_API) },
                        label = { Text("API 直连") },
                    )
                }
                if (state.aiMode == SettingsPreferences.AI_MODE_API) {
                    val preset = AiApiPresets.byId(state.aiApiPreset)
                    var apiKey by remember(state.aiApiKey) { mutableStateOf(state.aiApiKey) }
                    var apiModel by remember(state.aiApiModel) { mutableStateOf(state.aiApiModel) }
                    var apiBase by remember(state.aiApiBaseUrl) { mutableStateOf(state.aiApiBaseUrl) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AiApiPresets.ALL.forEach { p ->
                            FilterChip(
                                selected = state.aiApiPreset == p.id,
                                onClick = { vm.setAiApiPreset(p.id) },
                                label = { Text(p.label) },
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            placeholder = { Text("API Key", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        )
                        OutlinedTextField(
                            value = apiModel,
                            onValueChange = { apiModel = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = {
                                Text(
                                    if (preset.defaultModel.isNotEmpty()) "模型 · 默认 ${preset.defaultModel}" else "模型名称",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                        )
                        OutlinedTextField(
                            value = apiBase,
                            onValueChange = { apiBase = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = {
                                Text(
                                    if (preset.baseUrl.isNotEmpty()) "地址 · 默认 ${preset.baseUrl}" else "https://api.example.com/v1",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                onClick = { vm.saveAiApiConfig(apiBase, apiKey, apiModel) },
                                enabled = apiBase.trim() != state.aiApiBaseUrl ||
                                    apiKey.trim() != state.aiApiKey ||
                                    apiModel.trim() != state.aiApiModel,
                            ) { Text("保存 API 设置") }
                            TextButton(
                                onClick = { vm.saveAndTestAiApi(apiBase, apiKey, apiModel) },
                                enabled = !state.aiApiTesting,
                            ) { Text(if (state.aiApiTesting) "测试中…" else "测试连接") }
                        }
                        state.aiApiTestResult?.let { result ->
                            Text(
                                result,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (result.startsWith("连接正常")) MaterialTheme.colorScheme.primary else VitaError,
                            )
                        }
                    }
                } else {
                    Text(
                        "自动发现已关闭, 需手动填写 Mac 局域网地址和服务口令。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                    )
                    var aiAddress by remember(state.aiServerAddress) { mutableStateOf(state.aiServerAddress) }
                    var aiToken by remember(state.aiServerToken) { mutableStateOf(state.aiServerToken) }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = aiAddress,
                            onValueChange = { aiAddress = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("192.168.1.5:8787", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        )
                        OutlinedTextField(
                            value = aiToken,
                            onValueChange = { aiToken = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            placeholder = { Text("VITA_AI_TOKEN", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        )
                        TextButton(
                            onClick = {
                                vm.setAiServerAddress(aiAddress)
                                vm.setAiServerToken(aiToken)
                            },
                            enabled = aiAddress.trim() != state.aiServerAddress || aiToken.trim() != state.aiServerToken,
                        ) { Text("保存 AI 设置") }
                    }
                }
            }
        }

        // ─── 记录提醒 ────────────────────────────────────────────
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
                    Icon(Icons.Outlined.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(
                            "记录提醒",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "到点提醒记一下心情，避免一整天忘了记。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.reminderEnabled,
                        onCheckedChange = { checked ->
                            if (!checked) {
                                vm.setReminderEnabled(false)
                            } else {
                                val needNotifPerm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.POST_NOTIFICATIONS,
                                    ) != PackageManager.PERMISSION_GRANTED
                                if (needNotifPerm) notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                else vm.setReminderEnabled(true)
                            }
                        },
                    )
                }
                Text(
                    "提醒时刻",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                )
                val presetTimes = remember {
                    listOf(LocalTime.of(9, 0), LocalTime.of(13, 0), LocalTime.of(18, 0), LocalTime.of(21, 0))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    presetTimes.forEach { time ->
                        FilterChip(
                            selected = state.reminderTimes.any { it.hour == time.hour && it.minute == time.minute },
                            onClick = { vm.toggleReminderTime(time) },
                            enabled = state.reminderEnabled,
                            label = {
                                Text(
                                    "%02d:%02d".format(time.hour, time.minute),
                                    maxLines = 1,
                                )
                            },
                            modifier = Modifier.weight(1f),
                        )
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

        AppUpdateCard(vm)

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
                    stringResource(R.string.settings_about_body, BuildConfig.VERSION_NAME),
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
