package com.vita.healthtracker.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vita.healthtracker.BuildConfig
import com.vita.healthtracker.R
import com.vita.healthtracker.data.update.UpdateError
import com.vita.healthtracker.data.update.UpdatePhase
import com.vita.healthtracker.ui.theme.VitaGradients

@Composable
internal fun AppUpdateCard(vm: SettingsViewModel) {
    val update by vm.updateState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var canInstall by remember { mutableStateOf(context.packageManager.canRequestPackageInstalls()) }
    val installPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        canInstall = context.packageManager.canRequestPackageInstalls()
    }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) canInstall = context.packageManager.canRequestPackageInstalls()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .background(VitaGradients.cardSurface, MaterialTheme.shapes.medium)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    stringResource(R.string.update_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Text(
                stringResource(R.string.update_current_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.update_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            update.available?.let { release ->
                Text(
                    stringResource(
                        R.string.update_available,
                        release.manifest.versionName,
                        "%.1f".format(release.sizeBytes / (1024.0 * 1024.0)),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (release.notes.isNotBlank()) {
                    Text(stringResource(R.string.update_release_notes), style = MaterialTheme.typography.labelLarge)
                    Text(
                        release.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            when (update.phase) {
                UpdatePhase.Checking -> {
                    Text(stringResource(R.string.update_checking), style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                UpdatePhase.Latest -> Text(
                    stringResource(R.string.update_latest),
                    color = MaterialTheme.colorScheme.primary,
                )
                UpdatePhase.Downloading -> {
                    Text(stringResource(R.string.update_downloading, update.progress))
                    LinearProgressIndicator(progress = { update.progress / 100f }, modifier = Modifier.fillMaxWidth())
                }
                UpdatePhase.Ready -> Text(
                    stringResource(R.string.update_download_ready),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                else -> Unit
            }
            update.error?.let { error ->
                Text(
                    stringResource(updateErrorResource(error)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (update.phase == UpdatePhase.Available || (update.phase == UpdatePhase.Failed && update.available != null)) {
                Button(onClick = vm::downloadAppUpdate, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (update.phase == UpdatePhase.Failed) R.string.update_retry_download else R.string.update_download))
                }
            }
            if (update.phase == UpdatePhase.Ready) {
                if (canInstall) {
                    Button(onClick = vm::installAppUpdate, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.update_install))
                    }
                } else {
                    Text(
                        stringResource(R.string.update_install_permission_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            runCatching {
                                installPermissionLauncher.launch(
                                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                                )
                            }.onFailure { vm.reportUpdateInstallerUnavailable() }
                        },
                    ) { Text(stringResource(R.string.update_allow_install)) }
                }
            }
            OutlinedButton(
                onClick = vm::checkForAppUpdate,
                enabled = !update.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.update_check)) }
        }
    }
}

private fun updateErrorResource(error: UpdateError): Int = when (error) {
    UpdateError.NotConfigured -> R.string.update_error_not_configured
    UpdateError.NoRelease -> R.string.update_error_no_release
    UpdateError.Network -> R.string.update_error_network
    UpdateError.RateLimited -> R.string.update_error_rate_limited
    UpdateError.InvalidRelease -> R.string.update_error_invalid_release
    UpdateError.WrongPackage -> R.string.update_error_wrong_package
    UpdateError.WrongSignature -> R.string.update_error_wrong_signature
    UpdateError.WrongVersion -> R.string.update_error_wrong_version
    UpdateError.CorruptDownload -> R.string.update_error_corrupt_download
    UpdateError.InvalidApk -> R.string.update_error_invalid_apk
    UpdateError.InstallPermission -> R.string.update_error_install_permission
    UpdateError.InstallerUnavailable -> R.string.update_error_installer
}
