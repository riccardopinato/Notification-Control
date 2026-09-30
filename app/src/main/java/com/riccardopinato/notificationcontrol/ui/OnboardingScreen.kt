package com.riccardopinato.notificationcontrol.ui

import android.Manifest
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.riccardopinato.notificationcontrol.domain.ProductLimits

@Composable
fun OnboardingScreen(
    settings: SettingsUiState,
    apps: List<InstalledApp>,
    onToggleApp: (String) -> Boolean,
    requestNotificationAccess: () -> Unit,
    requestCameraPermission: () -> Unit,
    requestOverlayPermission: () -> Unit,
    onFlashChanged: (Boolean) -> Unit,
    onOverlayChanged: (Boolean) -> Unit,
    onVaultLockChanged: (Boolean) -> Unit,
    onTestVisuals: () -> Unit,
    onDone: () -> Unit
) {
    var step by remember { mutableIntStateOf(0) }

    when (step) {
        0 -> OnboardingPage(
            title = stringResource(R.string.welcome_title),
            body = stringResource(R.string.welcome_body),
            button = stringResource(R.string.start),
            supporting = stringResource(R.string.privacy_local_body)
        ) { step = 1 }

        1 -> AccessAndAppsPage(
            apps = apps,
            selected = settings.monitoredPackages,
            premium = settings.isPremium,
            onToggle = onToggleApp,
            requestNotificationAccess = requestNotificationAccess
        ) { step = 2 }

        2 -> VisualSetupPage(
            flashEnabled = settings.flashEnabled,
            overlayEnabled = settings.overlayEnabled,
            requestCameraPermission = requestCameraPermission,
            requestOverlayPermission = requestOverlayPermission,
            onFlashChanged = onFlashChanged,
            onOverlayChanged = onOverlayChanged
        ) { step = 3 }

        3 -> SecuritySetupPage(
            lockEnabled = settings.vaultLockEnabled,
            onLockChanged = onVaultLockChanged
        ) { step = 4 }

        else -> SetupSummaryPage(
            settings = settings,
            onTestVisuals = onTestVisuals,
            onDone = onDone
        )
    }
}

@Composable
private fun OnboardingPage(
    title: String,
    body: String,
    button: String,
    supporting: String? = null,
    onNext: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Icons.Default.NotificationsActive
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge)
        if (supporting != null) {
            Spacer(Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Text(supporting, Modifier.padding(16.dp))
            }
        }
        Spacer(Modifier.height(28.dp))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
            Text(button)
        }
    }
}

@Composable
private fun AccessAndAppsPage(
    apps: List<InstalledApp>,
    selected: Set<String>,
    premium: Boolean,
    onToggle: (String) -> Boolean,
    requestNotificationAccess: () -> Unit,
    onNext: () -> Unit
) {
    val context = LocalContext.current
    val access = NotificationManagerCompat.getEnabledListenerPackages(context)
        .contains(context.packageName)
    var limitError by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text(
            stringResource(R.string.notification_access_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(stringResource(R.string.notification_access_body))
        TextButton(
            onClick = requestNotificationAccess,
            enabled = !access
        ) {
            Text(
                if (access) {
                    stringResource(R.string.access_granted)
                } else {
                    stringResource(R.string.grant_access)
                }
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.choose_apps_title),
            fontWeight = FontWeight.Bold
        )
        Text(stringResource(R.string.choose_apps_body, ProductLimits.FREE_MONITORED_APPS))
        Text(
            stringResource(
                R.string.selected_count,
                selected.size,
                if (premium) apps.size else ProductLimits.FREE_MONITORED_APPS
            ),
            fontWeight = FontWeight.SemiBold
        )
        if (limitError) {
            Text(
                stringResource(
                    R.string.free_limit_reached,
                    ProductLimits.FREE_MONITORED_APPS
                ),
                color = MaterialTheme.colorScheme.error
            )
        }

        LazyColumn(Modifier.weight(1f)) {
            items(apps, key = { it.packageName }) { app ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
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
                        onCheckedChange = { limitError = !onToggle(app.packageName) }
                    )
                }
                HorizontalDivider()
            }
        }

        Button(
            onClick = onNext,
            enabled = access && selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.continue_label))
        }
    }
}

@Composable
private fun VisualSetupPage(
    flashEnabled: Boolean,
    overlayEnabled: Boolean,
    requestCameraPermission: () -> Unit,
    requestOverlayPermission: () -> Unit,
    onFlashChanged: (Boolean) -> Unit,
    onOverlayChanged: (Boolean) -> Unit,
    onNext: () -> Unit
) {
    val context = LocalContext.current
    val cameraGranted =
        context.checkSelfPermission(Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    val overlayGranted = Settings.canDrawOverlays(context)

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            stringResource(R.string.visual_setup_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(stringResource(R.string.visual_setup_body))
        Spacer(Modifier.height(16.dp))

        SettingToggle(
            title = stringResource(R.string.flash_alerts),
            checked = flashEnabled
        ) { enabled ->
            onFlashChanged(enabled)
            if (enabled && !cameraGranted) requestCameraPermission()
        }

        SettingToggle(
            title = stringResource(R.string.overlay_alerts),
            checked = overlayEnabled
        ) { enabled ->
            onOverlayChanged(enabled)
            if (enabled && !overlayGranted) requestOverlayPermission()
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.continue_label))
        }
    }
}

@Composable
private fun SecuritySetupPage(
    lockEnabled: Boolean,
    onLockChanged: (Boolean) -> Unit,
    onNext: () -> Unit
) {
    val context = LocalContext.current
    val authenticators =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
    val available =
        BiometricManager.from(context).canAuthenticate(authenticators) ==
            BiometricManager.BIOMETRIC_SUCCESS

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            stringResource(R.string.security_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(stringResource(R.string.security_body))
        Spacer(Modifier.height(16.dp))

        SettingToggle(
            title = stringResource(R.string.enable_vault_lock),
            checked = lockEnabled,
            enabled = available,
            onChange = onLockChanged
        )

        Card(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Text(
                stringResource(R.string.account_optional),
                Modifier.padding(16.dp)
            )
        }

        Spacer(Modifier.height(20.dp))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.continue_label))
        }
    }
}

@Composable
private fun SetupSummaryPage(
    settings: SettingsUiState,
    onTestVisuals: () -> Unit,
    onDone: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            stringResource(R.string.setup_complete_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            stringResource(
                R.string.setup_complete_body,
                settings.monitoredPackages.size
            )
        )
        Spacer(Modifier.height(16.dp))
        FilledSummaryLine(
            stringResource(R.string.flash_alerts),
            settings.flashEnabled
        )
        FilledSummaryLine(
            stringResource(R.string.overlay_alerts),
            settings.overlayEnabled
        )
        FilledSummaryLine(
            stringResource(R.string.enable_vault_lock),
            settings.vaultLockEnabled
        )
        Spacer(Modifier.height(16.dp))
        TextButton(
            onClick = onTestVisuals,
            enabled = settings.flashEnabled || settings.overlayEnabled
        ) {
            Text(stringResource(R.string.test_visual_alerts))
        }
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.finish_setup))
        }
    }
}

@Composable
private fun FilledSummaryLine(label: String, enabled: Boolean) {
    Text(label + ": " + if (enabled) stringResource(R.string.yes) else stringResource(R.string.no))
}

@Composable
private fun SettingToggle(
    title: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled
        )
    }
}
