package com.riccardopinato.notificationcontrol.ui

import android.Manifest
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.riccardopinato.notificationcontrol.R
import com.riccardopinato.notificationcontrol.capture.ListenerHealthStore
import com.riccardopinato.notificationcontrol.domain.ProductLimits
import com.riccardopinato.notificationcontrol.localization.LocaleController
import com.riccardopinato.notificationcontrol.storage.StorageStats

@Composable
fun ProfileScreen(
    modifier: Modifier,
    contentPadding: PaddingValues,
    state: SettingsUiState,
    storageStats: StorageStats,
    onConfigureApps: () -> Unit,
    onVaultLockChanged: (Boolean) -> Unit,
    onVaultTimeoutChanged: (Int) -> Unit,
    onDeleteAll: () -> Unit,
    requestNotificationAccess: () -> Unit,
    requestCameraPermission: () -> Unit,
    requestOverlayPermission: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val biometricAuthenticators =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
    val biometricAvailable =
        BiometricManager.from(context).canAuthenticate(biometricAuthenticators) ==
            BiometricManager.BIOMETRIC_SUCCESS

    LazyColumn(
        modifier.fillMaxSize().padding(contentPadding).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                stringResource(R.string.profile_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
        item {
            InfoCard(
                stringResource(R.string.account_title),
                stringResource(R.string.account_optional)
            )
        }
        item {
            LanguageCard()
        }
        item {
            SecurityCard(
                enabled = state.vaultLockEnabled,
                available = biometricAvailable,
                timeoutMinutes = state.vaultLockTimeoutMinutes,
                onEnabledChange = onVaultLockChanged,
                onTimeoutChange = onVaultTimeoutChanged
            )
        }
        item {
            StorageCard(storageStats, state)
        }
        item {
            PermissionHealthCard(
                requestNotificationAccess = requestNotificationAccess,
                requestCameraPermission = requestCameraPermission,
                requestOverlayPermission = requestOverlayPermission
            )
        }
        item {
            InfoCard(
                stringResource(R.string.privacy_title),
                stringResource(R.string.privacy_body)
            )
        }
        item {
            Button(
                onClick = onConfigureApps,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.configure_apps))
            }
        }
        item {
            FilledTonalButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.delete_vault))
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_vault)) },
            text = { Text(stringResource(R.string.delete_vault_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteAll()
                        confirmDelete = false
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun LanguageCard() {
    val selected = LocaleController.currentLanguageTag()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.language_title), fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.language_device))
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "" to stringResource(R.string.language_auto),
                    "it" to "IT",
                    "en" to "EN",
                    "es" to "ES",
                    "fr" to "FR",
                    "pt" to "PT"
                ).forEach { (tag, label) ->
                    FilterChip(
                        selected = selected == tag,
                        onClick = { LocaleController.setLanguage(tag) },
                        label = { Text(label) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SecurityCard(
    enabled: Boolean,
    available: Boolean,
    timeoutMinutes: Int,
    onEnabledChange: (Boolean) -> Unit,
    onTimeoutChange: (Int) -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.security_title), fontWeight = FontWeight.Bold)
                    Text(
                        if (available) {
                            stringResource(R.string.biometric_security_available)
                        } else {
                            stringResource(R.string.biometric_security_unavailable)
                        }
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    enabled = available
                )
            }
            if (enabled) {
                Text(
                    stringResource(R.string.lock_timeout),
                    modifier = Modifier.padding(top = 8.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1, 5, 15, 30).forEach { minutes ->
                        FilterChip(
                            selected = timeoutMinutes == minutes,
                            onClick = { onTimeoutChange(minutes) },
                            label = { Text(stringResource(R.string.minutes_short, minutes)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageCard(storageStats: StorageStats, state: SettingsUiState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.storage_title), fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.storage_total, formatBytesProfile(storageStats.totalBytes)))
            Text(stringResource(R.string.storage_database, formatBytesProfile(storageStats.databaseBytes)))
            Text(stringResource(R.string.storage_media, formatBytesProfile(storageStats.mediaBytes)))
            Text(
                if (state.isPremium) {
                    stringResource(R.string.retention_current, state.retentionDays)
                } else {
                    stringResource(R.string.storage_free)
                },
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun PermissionHealthCard(
    requestNotificationAccess: () -> Unit,
    requestCameraPermission: () -> Unit,
    requestOverlayPermission: () -> Unit
) {
    val context = LocalContext.current
    val health = remember { ListenerHealthStore(context) }
    val listenerPermission = NotificationManagerCompat.getEnabledListenerPackages(context)
        .contains(context.packageName)
    val cameraGranted =
        context.checkSelfPermission(Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    val overlayGranted = Settings.canDrawOverlays(context)

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.permission_health), fontWeight = FontWeight.Bold)
            PermissionHealthRow(
                stringResource(R.string.notification_access_title),
                listenerPermission && health.connected,
                requestNotificationAccess
            )
            PermissionHealthRow(
                stringResource(R.string.camera_permission),
                cameraGranted,
                requestCameraPermission
            )
            PermissionHealthRow(
                stringResource(R.string.overlay_permission),
                overlayGranted,
                requestOverlayPermission
            )
        }
    }
}

@Composable
private fun PermissionHealthRow(
    label: String,
    ok: Boolean,
    onFix: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f))
        if (ok) {
            Text(stringResource(R.string.status_ok), color = MaterialTheme.colorScheme.primary)
        } else {
            TextButton(onClick = onFix) {
                Text(stringResource(R.string.fix))
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(body)
        }
    }
}

@Composable
fun AppPickerDialog(
    apps: List<InstalledApp>,
    selected: Set<String>,
    premium: Boolean,
    onToggle: (String) -> Boolean,
    onDismiss: () -> Unit
) {
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_apps_title)) },
        text = {
            LazyColumn(Modifier.height(420.dp)) {
                items(apps, key = { it.packageName }) { app ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            app.label,
                            Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Switch(
                            checked = app.packageName in selected,
                            onCheckedChange = { error = !onToggle(app.packageName) }
                        )
                    }
                    HorizontalDivider()
                }
                if (error && !premium) {
                    item {
                        Text(
                            stringResource(
                                R.string.free_limit_reached,
                                ProductLimits.FREE_MONITORED_APPS
                            ),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.done))
            }
        }
    )
}

private fun formatBytesProfile(bytes: Long): String {
    if (bytes < 1_024) return "$bytes B"
    val kb = bytes / 1_024.0
    if (kb < 1_024) return String.format("%.1f KB", kb)
    return String.format("%.1f MB", kb / 1_024.0)
}
