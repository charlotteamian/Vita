package com.vita.healthtracker.ui.screens.account

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vita.healthtracker.data.sync.SyncScope
import com.vita.healthtracker.ui.theme.VitaError
import com.vita.healthtracker.ui.theme.VitaGradients
import com.vita.healthtracker.ui.theme.VitaOnSurfaceMuted
import com.vita.healthtracker.ui.theme.VitaTertiary
import com.vita.healthtracker.ui.vitaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountAuthScreen(onBack: () -> Unit) {
    val vm = vitaViewModel<AccountAuthViewModel>()
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    val appleImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.importAppleHealth(it) } }

    var globalEmail by remember { mutableStateOf("") }
    var globalPass by remember { mutableStateOf("") }
    var globalMfaCode by remember { mutableStateOf("") }
    var chinaEmail by remember { mutableStateOf("") }
    var chinaPass by remember { mutableStateOf("") }
    var chinaMfaCode by remember { mutableStateOf("") }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("账号授权") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) { data -> Snackbar(snackbarData = data) } },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "连接第三方运动 / 健康账号后，Vita 可以帮你同步记录。登录信息只保存在这台设备上，不会上传到 Vita 云端。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            GarminZoneCard(
                title = "佳明国际区",
                subtitle = "Garmin Connect / garmin.com",
                connected = "garmin.com" in state.garminLoggedDomains,
                busy = state.busy || (state.syncRunning && state.syncDomain != "garmin.com"),
                syncing = state.syncRunning && state.syncDomain == "garmin.com",
                syncFraction = state.syncFraction,
                syncPhase = state.syncPhase,
                syncProcessed = state.syncProcessed,
                syncTotal = state.syncTotal,
                email = globalEmail,
                password = globalPass,
                mfaCode = globalMfaCode,
                needsMfa = state.garminNeedsMfa && state.garminMfaDomain == "garmin.com",
                mfaMethod = state.garminMfaMethod,
                syncDetails = state.garminSyncDetails.filter { it.startsWith("国际区") },
                onEmailChange = { globalEmail = it },
                onPasswordChange = { globalPass = it },
                onMfaCodeChange = { globalMfaCode = it },
                onLogin = { vm.loginGarmin(globalEmail, globalPass, useChinaServer = false) },
                onSubmitMfa = { vm.submitGarminMfa(globalMfaCode) },
                onLogout = {
                    vm.logoutGarmin(useChinaServer = false)
                    android.webkit.CookieManager.getInstance().removeAllCookies(null)
                },
                onSync = { days -> vm.syncGarminDomain(useChinaServer = false, days = days) },
                onStopSync = vm::stopSync,
            )

            GarminZoneCard(
                title = "佳明国区",
                subtitle = "Garmin Connect / garmin.cn",
                connected = "garmin.cn" in state.garminLoggedDomains,
                busy = state.busy || (state.syncRunning && state.syncDomain != "garmin.cn"),
                syncing = state.syncRunning && state.syncDomain == "garmin.cn",
                syncFraction = state.syncFraction,
                syncPhase = state.syncPhase,
                syncProcessed = state.syncProcessed,
                syncTotal = state.syncTotal,
                email = chinaEmail,
                password = chinaPass,
                mfaCode = chinaMfaCode,
                needsMfa = state.garminNeedsMfa && state.garminMfaDomain == "garmin.cn",
                mfaMethod = state.garminMfaMethod,
                syncDetails = state.garminSyncDetails.filter { it.startsWith("国区") },
                onEmailChange = { chinaEmail = it },
                onPasswordChange = { chinaPass = it },
                onMfaCodeChange = { chinaMfaCode = it },
                onLogin = { vm.loginGarmin(chinaEmail, chinaPass, useChinaServer = true) },
                onSubmitMfa = { vm.submitGarminMfa(chinaMfaCode) },
                onLogout = {
                    vm.logoutGarmin(useChinaServer = true)
                    android.webkit.CookieManager.getInstance().removeAllCookies(null)
                },
                onSync = { days -> vm.syncGarminDomain(useChinaServer = true, days = days) },
                onStopSync = vm::stopSync,
            )

            AppleHealthCard(
                importing = state.appleImporting,
                importRecords = state.appleImportRecords,
                onPickFile = { appleImportLauncher.launch(arrayOf("*/*")) },
            )

            // ─── 占位: 以后再接 ───────────────────────────────────
            Text(
                "更多数据源 (即将支持)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            ComingSoonCard("三星 Samsung Health")
            ComingSoonCard("小米 运动健康")
            ComingSoonCard("Fitbit")
        }
    }
}

@Composable
private fun GarminZoneCard(
    title: String,
    subtitle: String,
    connected: Boolean,
    busy: Boolean,
    syncing: Boolean,
    syncFraction: Float,
    syncPhase: String,
    syncProcessed: Int,
    syncTotal: Int,
    email: String,
    password: String,
    mfaCode: String,
    needsMfa: Boolean,
    mfaMethod: String?,
    syncDetails: List<String>,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onMfaCodeChange: (String) -> Unit,
    onLogin: () -> Unit,
    onSubmitMfa: () -> Unit,
    onLogout: () -> Unit,
    onSync: (Long?) -> Unit,
    onStopSync: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(VitaGradients.cardSurface, MaterialTheme.shapes.medium)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(connected = connected)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    if (connected) "已登录" else "未登录",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (connected) VitaTertiary else VitaOnSurfaceMuted,
                    fontWeight = FontWeight.Medium,
                )
            }

            Text(
                "直接调用 Garmin API 同步步数 / 距离 / 卡路里 / 心率 / 睡眠 / 运动记录。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (connected) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onLogout,
                        enabled = !busy && !syncing,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("退出")
                    }
                    if (syncing) {
                        Button(
                            onClick = onStopSync,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Outlined.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("停止", modifier = Modifier.padding(start = 6.dp))
                        }
                    } else {
                        SyncScopeMenuButton(
                            enabled = !busy,
                            onSync = onSync,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (syncing) {
                    GarminCoordinatorProgress(
                        fraction = syncFraction,
                        phase = syncPhase,
                        processed = syncProcessed,
                        total = syncTotal,
                    )
                }
            } else {
                OutlinedTextField(
                    value = email,
                    onValueChange = onEmailChange,
                    label = { Text("Garmin 账号") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = { Text("Garmin 密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (needsMfa) {
                    Text(
                        "验证码方式: ${mfaMethod ?: "Garmin 验证"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = mfaCode,
                        onValueChange = onMfaCodeChange,
                        label = { Text("验证码") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Button(
                    onClick = if (needsMfa) onSubmitMfa else onLogin,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (needsMfa) "提交验证码" else "登录")
                }
            }

            if (syncDetails.isNotEmpty() && !syncing) {
                Text(
                    "最近一次已有日汇总写入/更新，可到统计页日历查看具体日期。",
                    style = MaterialTheme.typography.bodySmall,
                    color = VitaOnSurfaceMuted,
                )
            }
        }
    }
}

@Composable
private fun SyncScopeMenuButton(
    enabled: Boolean,
    onSync: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Button(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("同步数据")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("增量同步") },
                onClick = { expanded = false; onSync(null) },
            )
            SyncScope.entries.forEach { scope ->
                DropdownMenuItem(
                    text = { Text(scope.label) },
                    onClick = { expanded = false; onSync(scope.days) },
                )
            }
        }
    }
}

@Composable
private fun GarminCoordinatorProgress(
    fraction: Float,
    @Suppress("UNUSED_PARAMETER")
    phase: String,
    processed: Int,
    total: Int,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = if (total > 0) {
                "同步中 ${(fraction * 100).toInt().coerceIn(0, 100)}% · $processed/$total 天"
            } else {
                "同步中"
            },
            style = MaterialTheme.typography.bodySmall,
            color = VitaOnSurfaceMuted,
        )
    }
}

@Composable
private fun ProviderCard(
    name: String,
    connected: Boolean,
    content: @Composable () -> Unit,
) {
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
                StatusDot(connected = connected)
                Text(
                    name,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            content()
        }
    }
}

@Composable
private fun AppleHealthCard(
    importing: Boolean,
    importRecords: Long,
    onPickFile: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(VitaGradients.cardSurface, MaterialTheme.shapes.medium)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Apple 健康",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "Android 无法直连 Apple 健康。请在 iPhone「健康」App → 右上角头像 → 导出所有健康数据, " +
                    "得到 export.zip 后在这里导入步数 / 距离 / 心率 / 睡眠 / 体重 / 运动记录 / 经期等数据。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (importing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = if (importRecords > 0) "导入中… 已解析 $importRecords 条记录" else "导入中…",
                    style = MaterialTheme.typography.bodySmall,
                    color = VitaOnSurfaceMuted,
                )
            } else {
                Button(
                    onClick = onPickFile,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("选择 export.zip 导入")
                }
            }
        }
    }
}

@Composable
private fun ComingSoonCard(name: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.alpha(0.5f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(VitaGradients.cardSurface, MaterialTheme.shapes.medium)
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(VitaOnSurfaceMuted),
            )
            Text(
                name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
            Text(
                "即将支持",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusDot(connected: Boolean) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(if (connected) VitaTertiary else VitaError.copy(alpha = 0.7f)),
    )
}
