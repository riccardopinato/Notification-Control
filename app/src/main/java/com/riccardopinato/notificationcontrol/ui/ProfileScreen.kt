package com.riccardopinato.notificationcontrol.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import com.riccardopinato.notificationcontrol.billing.BillingUiState
import com.riccardopinato.notificationcontrol.billing.EntitlementTier
import com.riccardopinato.notificationcontrol.capture.ListenerHealthStore
import com.riccardopinato.notificationcontrol.domain.ProductLimits
import com.riccardopinato.notificationcontrol.localization.LocaleController
import com.riccardopinato.notificationcontrol.storage.StorageStats

@Composable
fun ProfileScreen(
    modifier: Modifier,
    contentPadding: PaddingValues,
    state: SettingsUiState,
    apps: List<InstalledApp>,
    billingState: BillingUiState,
    storageStats: StorageStats,
    onConfigureApps: () -> Unit,
    onVaultLockChanged: (Boolean) -> Unit,
    onVaultTimeoutChanged: (Int) -> Unit,
    onSensitiveProtectionChanged: (Boolean) -> Unit,
    onRetentionDaysChanged: (Int) -> Unit,
    onVaultMaxBytesChanged: (Long) -> Unit,
    onBatteryGuardThresholdChanged: (Int) -> Unit,
    onQuietStartChanged: (Int) -> Unit,
    onQuietEndChanged: (Int) -> Unit,
    onAddQuietHoursBand: (Int, Int) -> Unit,
    onRemoveQuietHoursBand: (Int, Int) -> Unit,
    onAddQuietHoursException: (String) -> Unit,
    onRemoveQuietHoursException: (String) -> Unit,
    onCriticalBypassQuietHoursChanged: (Boolean) -> Unit,
    onDeleteAll: () -> Unit,
    onResetLocalData: () -> Unit,
    onPurchasePremium: (String) -> Unit,
    onRestorePurchases: () -> Unit,
    onBackupRestored: () -> Unit,
    requestNotificationAccess: () -> Unit,
    requestPostNotifications: () -> Unit,
    requestCameraPermission: () -> Unit,
    requestOverlayPermission: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
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
            PremiumCard(
                billingState = billingState,
                onPurchase = onPurchasePremium,
                onRestore = onRestorePurchases
            )
        }
        item { GoogleAccountCard() }
        item { EncryptedBackupCard(onBackupRestored = onBackupRestored) }
        item { LanguageCard() }
        item {
            SecurityCard(
                enabled = state.vaultLockEnabled,
                available = biometricAvailable,
                timeoutMinutes = state.vaultLockTimeoutMinutes,
                sensitiveProtectionEnabled = state.sensitiveProtectionEnabled,
                onEnabledChange = onVaultLockChanged,
                onTimeoutChange = onVaultTimeoutChanged,
                onSensitiveProtectionChange = onSensitiveProtectionChanged
            )
        }
        item {
            StorageCard(
                storageStats = storageStats,
                state = state,
                onRetentionDaysChanged = onRetentionDaysChanged,
                onVaultMaxBytesChanged = onVaultMaxBytesChanged
            )
        }
        item {
            DeviceBehaviorCard(
                state = state,
                apps = apps,
                onBatteryThresholdChanged = onBatteryGuardThresholdChanged,
                onQuietStartChanged = onQuietStartChanged,
                onQuietEndChanged = onQuietEndChanged,
                onAddQuietHoursBand = onAddQuietHoursBand,
                onRemoveQuietHoursBand = onRemoveQuietHoursBand,
                onAddQuietHoursException = onAddQuietHoursException,
                onRemoveQuietHoursException = onRemoveQuietHoursException,
                onCriticalBypassQuietHoursChanged = onCriticalBypassQuietHoursChanged
            )
        }
        item {
            PermissionHealthCard(
                state = state,
                requestNotificationAccess = requestNotificationAccess,
                requestPostNotifications = requestPostNotifications,
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
        item {
            TextButton(
                onClick = { confirmReset = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.reset_local_data))
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

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.reset_local_data)) },
            text = { Text(stringResource(R.string.reset_local_data_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onResetLocalData()
                        confirmReset = false
                    }
                ) {
                    Text(stringResource(R.string.reset))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun PremiumCard(
    billingState: BillingUiState,
    onPurchase: (String) -> Unit,
    onRestore: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.premium_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            val tierText = when (billingState.entitlement) {
                EntitlementTier.FREE -> stringResource(R.string.plan_free)
                EntitlementTier.PREMIUM_SUBSCRIPTION ->
                    stringResource(R.string.plan_premium_subscription)
                EntitlementTier.PREMIUM_LIFETIME ->
                    stringResource(R.string.plan_premium_lifetime)
            }
            Text(stringResource(R.string.current_plan, tierText))
            Text(
                stringResource(R.string.premium_value),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            if (!billingState.entitlement.isPremium) {
                billingState.offers.forEach { offer ->
                    FilledTonalButton(
                        onClick = { onPurchase(offer.key) },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                    ) {
                        Text(
                            if (offer.isLifetime) {
                                stringResource(
                                    R.string.buy_lifetime,
                                    offer.formattedPrice
                                )
                            } else {
                                stringResource(
                                    R.string.buy_subscription,
                                    offer.planLabel,
                                    offer.formattedPrice
                                )
                            }
                        )
                    }
                }
                if (!billingState.loading && billingState.offers.isEmpty()) {
                    Text(
                        stringResource(R.string.billing_products_unavailable),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            TextButton(onClick = onRestore) {
                Text(stringResource(R.string.restore_purchases))
            }
        }
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
    sensitiveProtectionEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onTimeoutChange: (Int) -> Unit,
    onSensitiveProtectionChange: (Boolean) -> Unit
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
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.sensitive_screen_protection),
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(R.string.sensitive_screen_protection_body),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = sensitiveProtectionEnabled,
                    onCheckedChange = onSensitiveProtectionChange
                )
            }
        }
    }
}

@Composable
private fun StorageCard(
    storageStats: StorageStats,
    state: SettingsUiState,
    onRetentionDaysChanged: (Int) -> Unit,
    onVaultMaxBytesChanged: (Long) -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.storage_title), fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.storage_total, formatBytesProfile(storageStats.totalBytes)))
            Text(stringResource(R.string.storage_database, formatBytesProfile(storageStats.databaseBytes)))
            Text(stringResource(R.string.storage_media, formatBytesProfile(storageStats.mediaBytes)))

            Text(
                stringResource(R.string.retention_title),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 10.dp)
            )
            if (state.isPremium) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(ProductLimits.PREMIUM_RETENTION_OPTIONS_DAYS) { days ->
                        FilterChip(
                            selected = state.retentionDays == days,
                            onClick = { onRetentionDaysChanged(days) },
                            label = {
                                Text(
                                    if (days == Int.MAX_VALUE) {
                                        stringResource(R.string.retention_always)
                                    } else {
                                        stringResource(R.string.days_short, days)
                                    }
                                )
                            }
                        )
                    }
                }
            } else {
                Text(
                    stringResource(
                        R.string.retention_free_fixed,
                        ProductLimits.FREE_RETENTION_DAYS
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (state.isPremium) {
                Text(
                    stringResource(R.string.vault_budget_title),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 10.dp)
                )
                Text(
                    stringResource(R.string.vault_budget_body),
                    style = MaterialTheme.typography.bodySmall
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(ProductLimits.PREMIUM_VAULT_MAX_BYTES_OPTIONS) { bytes ->
                        FilterChip(
                            selected = state.vaultMaxBytes == bytes,
                            onClick = { onVaultMaxBytesChanged(bytes) },
                            label = {
                                Text(
                                    if (bytes == Long.MAX_VALUE) {
                                        stringResource(R.string.vault_budget_unlimited)
                                    } else {
                                        stringResource(
                                            R.string.vault_budget_mb,
                                            bytes / (1024L * 1024L)
                                        )
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceBehaviorCard(
    state: SettingsUiState,
    apps: List<InstalledApp>,
    onBatteryThresholdChanged: (Int) -> Unit,
    onQuietStartChanged: (Int) -> Unit,
    onQuietEndChanged: (Int) -> Unit,
    onAddQuietHoursBand: (Int, Int) -> Unit,
    onRemoveQuietHoursBand: (Int, Int) -> Unit,
    onAddQuietHoursException: (String) -> Unit,
    onRemoveQuietHoursException: (String) -> Unit,
    onCriticalBypassQuietHoursChanged: (Boolean) -> Unit
) {
    var extraStart by remember { mutableStateOf(12 * 60f) }
    var extraEnd by remember { mutableStateOf(13 * 60f) }
    var exceptionApp by remember { mutableStateOf<InstalledApp?>(null) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.behavior_controls_title),
                fontWeight = FontWeight.Bold
            )

            Text(
                stringResource(
                    R.string.battery_threshold_value,
                    state.batteryGuardThreshold
                ),
                modifier = Modifier.padding(top = 8.dp)
            )
            Slider(
                value = state.batteryGuardThreshold.toFloat(),
                onValueChange = { onBatteryThresholdChanged(it.toInt()) },
                valueRange = 5f..50f,
                enabled = state.isPremium && state.batteryGuardEnabled
            )
            if (!state.isPremium) {
                Text(
                    stringResource(R.string.battery_threshold_premium),
                    style = MaterialTheme.typography.labelSmall
                )
            }

            if (state.quietHoursEnabled) {
                Text(
                    stringResource(
                        R.string.quiet_start_value,
                        formatMinutesOfDay(state.quietStartMinutes)
                    ),
                    modifier = Modifier.padding(top = 8.dp)
                )
                Slider(
                    value = state.quietStartMinutes.coerceIn(0, 1410).toFloat(),
                    onValueChange = {
                        onQuietStartChanged(((it.toInt() / 30) * 30).coerceIn(0, 1410))
                    },
                    valueRange = 0f..1410f,
                    steps = 46
                )
                Text(
                    stringResource(
                        R.string.quiet_end_value,
                        formatMinutesOfDay(state.quietEndMinutes)
                    )
                )
                Slider(
                    value = state.quietEndMinutes.coerceIn(0, 1410).toFloat(),
                    onValueChange = {
                        onQuietEndChanged(((it.toInt() / 30) * 30).coerceIn(0, 1410))
                    },
                    valueRange = 0f..1410f,
                    steps = 46
                )
                if (state.isPremium) {
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))
                    Text(
                        stringResource(R.string.quiet_additional_title),
                        fontWeight = FontWeight.SemiBold
                    )
                    state.additionalQuietHours.forEach { band ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(
                                    R.string.quiet_band_value,
                                    formatMinutesOfDay(band.startMinutes),
                                    formatMinutesOfDay(band.endMinutes)
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = {
                                    onRemoveQuietHoursBand(
                                        band.startMinutes,
                                        band.endMinutes
                                    )
                                }
                            ) {
                                Text(stringResource(R.string.delete))
                            }
                        }
                    }
                    Text(
                        stringResource(
                            R.string.quiet_start_value,
                            formatMinutesOfDay(extraStart.toInt())
                        )
                    )
                    Slider(
                        value = extraStart,
                        onValueChange = {
                            extraStart = ((it.toInt() / 30) * 30)
                                .coerceIn(0, 1410)
                                .toFloat()
                        },
                        valueRange = 0f..1410f,
                        steps = 46
                    )
                    Text(
                        stringResource(
                            R.string.quiet_end_value,
                            formatMinutesOfDay(extraEnd.toInt())
                        )
                    )
                    Slider(
                        value = extraEnd,
                        onValueChange = {
                            extraEnd = ((it.toInt() / 30) * 30)
                                .coerceIn(0, 1410)
                                .toFloat()
                        },
                        valueRange = 0f..1410f,
                        steps = 46
                    )
                    FilledTonalButton(
                        onClick = {
                            onAddQuietHoursBand(
                                extraStart.toInt(),
                                extraEnd.toInt()
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.quiet_add_band))
                    }

                    Text(
                        stringResource(R.string.quiet_exceptions_title),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                    Text(
                        stringResource(R.string.quiet_exceptions_body),
                        style = MaterialTheme.typography.bodySmall
                    )
                    SearchableAppPickerButton(
                        apps = apps,
                        selected = exceptionApp,
                        placeholder = stringResource(R.string.choose_app),
                        allowNone = false,
                        onSelected = { exceptionApp = it }
                    )
                    FilledTonalButton(
                        onClick = {
                            exceptionApp?.let {
                                onAddQuietHoursException(it.packageName)
                                exceptionApp = null
                            }
                        },
                        enabled = exceptionApp != null,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                    ) {
                        Text(stringResource(R.string.quiet_add_exception))
                    }
                    state.quietHoursExceptionPackages.forEach { packageName ->
                        val label = apps.firstOrNull {
                            it.packageName == packageName
                        }?.label ?: packageName
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label, Modifier.weight(1f))
                            TextButton(
                                onClick = {
                                    onRemoveQuietHoursException(packageName)
                                }
                            ) {
                                Text(stringResource(R.string.delete))
                            }
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.critical_bypass_quiet))
                        Text(
                            stringResource(R.string.critical_bypass_quiet_body),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = state.criticalBypassQuietHours,
                        onCheckedChange = onCriticalBypassQuietHoursChanged
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionHealthCard(
    state: SettingsUiState,
    requestNotificationAccess: () -> Unit,
    requestPostNotifications: () -> Unit,
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
    val reminderNotificationsGranted =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.permission_health), fontWeight = FontWeight.Bold)
            PermissionHealthRow(
                stringResource(R.string.notification_access_title),
                listenerPermission && health.connected,
                requestNotificationAccess
            )
            PermissionHealthRow(
                stringResource(R.string.follow_up_notification_permission),
                reminderNotificationsGranted,
                requestPostNotifications
            )
            if (state.flashEnabled) {
                PermissionHealthRow(
                    stringResource(R.string.camera_permission),
                    cameraGranted,
                    requestCameraPermission
                )
            } else {
                PermissionNotNeededRow(stringResource(R.string.camera_permission))
            }
            if (state.overlayEnabled) {
                PermissionHealthRow(
                    stringResource(R.string.overlay_permission),
                    overlayGranted,
                    requestOverlayPermission
                )
            } else {
                PermissionNotNeededRow(stringResource(R.string.overlay_permission))
            }
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
            Text(
                stringResource(R.string.status_ok),
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            TextButton(onClick = onFix) {
                Text(stringResource(R.string.fix))
            }
        }
    }
}

@Composable
private fun PermissionNotNeededRow(label: String) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f))
        Text(
            stringResource(R.string.status_not_needed),
            style = MaterialTheme.typography.labelMedium
        )
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
    var query by remember { mutableStateOf("") }
    val filteredApps = remember(apps, query) {
        val normalized = query.trim()
        if (normalized.isBlank()) {
            apps
        } else {
            apps.filter {
                it.label.contains(normalized, ignoreCase = true) ||
                    it.packageName.contains(normalized, ignoreCase = true)
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_apps_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    singleLine = true,
                    label = { Text(stringResource(R.string.search_apps)) }
                )
                LazyColumn(Modifier.height(420.dp)) {
                items(filteredApps, key = { it.packageName }) { app ->
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
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.done))
            }
        }
    )
}

private fun formatMinutesOfDay(minutes: Int): String {
    val safe = minutes.coerceIn(0, 1439)
    return String.format("%02d:%02d", safe / 60, safe % 60)
}

private fun formatBytesProfile(bytes: Long): String {
    if (bytes < 1_024) return "$bytes B"
    val kb = bytes / 1_024.0
    if (kb < 1_024) return String.format("%.1f KB", kb)
    return String.format("%.1f MB", kb / 1_024.0)
}
