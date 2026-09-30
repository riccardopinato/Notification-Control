package com.riccardopinato.notificationcontrol.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riccardopinato.notificationcontrol.R

private enum class MainTab { HOME, VAULT, LUMINOUS, PROFILE }

@Composable
fun NotificationControlApp(
    viewModel: NotificationControlViewModel,
    permissionEpoch: Int,
    requestCameraPermission: () -> Unit,
    requestOverlayPermission: () -> Unit,
    requestNotificationAccess: () -> Unit
) {
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val apps by viewModel.installedApps.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val count by viewModel.notificationCount.collectAsStateWithLifecycle()
    permissionEpoch.hashCode()

    if (!settings.onboardingCompleted) {
        OnboardingScreen(settings, apps, viewModel::toggleMonitoredApp, requestNotificationAccess, viewModel::completeOnboarding)
        return
    }

    var tab by remember { mutableStateOf(MainTab.HOME) }
    var showPicker by remember { mutableStateOf(false) }
    Scaffold(bottomBar = {
        NavigationBar {
            NavigationBarItem(tab == MainTab.HOME, { tab = MainTab.HOME }, { Icon(Icons.Default.Home, null) }, label = { Text(stringResource(R.string.tab_home)) })
            NavigationBarItem(tab == MainTab.VAULT, { tab = MainTab.VAULT }, { Icon(Icons.Default.Archive, null) }, label = { Text(stringResource(R.string.tab_vault)) })
            NavigationBarItem(tab == MainTab.LUMINOUS, { tab = MainTab.LUMINOUS }, { Icon(Icons.Default.Bolt, null) }, label = { Text(stringResource(R.string.tab_luminous)) })
            NavigationBarItem(tab == MainTab.PROFILE, { tab = MainTab.PROFILE }, { Icon(Icons.Default.AccountCircle, null) }, label = { Text(stringResource(R.string.tab_profile)) })
        }
    }) { padding ->
        when (tab) {
            MainTab.HOME -> HomeScreen(Modifier, padding, settings, count, requestNotificationAccess) { showPicker = true }
            MainTab.VAULT -> VaultScreen(Modifier, padding, notifications, viewModel::setProtected)
            MainTab.LUMINOUS -> LuminousScreen(
                Modifier, padding, settings, requestCameraPermission, requestOverlayPermission,
                viewModel::setFlashEnabled, viewModel::setOverlayEnabled, viewModel::setScreenOffOnly,
                viewModel::setBatteryGuardEnabled, viewModel::setQuietHoursEnabled, viewModel::setStrobeCycles,
                viewModel::setStrobeSpeed, viewModel::setCircleThickness, viewModel::setCircleGlow
            )
            MainTab.PROFILE -> ProfileScreen(Modifier, padding, settings, { showPicker = true }, viewModel::deleteAllVault)
        }
    }
    if (showPicker) AppPickerDialog(apps, settings.monitoredPackages, settings.isPremium, viewModel::toggleMonitoredApp) { showPicker = false }
}
