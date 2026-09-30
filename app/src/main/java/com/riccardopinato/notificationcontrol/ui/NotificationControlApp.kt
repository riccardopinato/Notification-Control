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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riccardopinato.notificationcontrol.R
import com.riccardopinato.notificationcontrol.security.VaultSecurityManager

private enum class MainTab { HOME, VAULT, LUMINOUS, PROFILE }

@Composable
fun NotificationControlApp(
    viewModel: NotificationControlViewModel,
    permissionEpoch: Int,
    vaultUnlockEpoch: Int,
    requestCameraPermission: () -> Unit,
    requestOverlayPermission: () -> Unit,
    requestNotificationAccess: () -> Unit,
    requestVaultUnlock: () -> Unit
) {
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val apps by viewModel.installedApps.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val count by viewModel.notificationCount.collectAsStateWithLifecycle()
    val search by viewModel.vaultSearch.collectAsStateWithLifecycle()
    val packageFilter by viewModel.vaultPackageFilter.collectAsStateWithLifecycle()
    val vaultLimit by viewModel.vaultLimit.collectAsStateWithLifecycle()
    val appFilters by viewModel.vaultAppFilters.collectAsStateWithLifecycle()
    val storageStats by viewModel.storageStats.collectAsStateWithLifecycle()

    permissionEpoch.hashCode()
    vaultUnlockEpoch.hashCode()

    if (!settings.onboardingCompleted) {
        OnboardingScreen(
            settings = settings,
            apps = apps,
            onToggleApp = viewModel::toggleMonitoredApp,
            requestNotificationAccess = requestNotificationAccess,
            requestCameraPermission = requestCameraPermission,
            requestOverlayPermission = requestOverlayPermission,
            onFlashChanged = viewModel::setFlashEnabled,
            onOverlayChanged = viewModel::setOverlayEnabled,
            onVaultLockChanged = viewModel::setVaultLockEnabled,
            onTestVisuals = viewModel::testVisualAlerts,
            onDone = viewModel::completeOnboarding
        )
        return
    }

    val context = LocalContext.current
    val security = remember { VaultSecurityManager(context) }
    val snackbar = remember { SnackbarHostState() }
    val protectedLimitMessage = stringResource(R.string.protected_limit_reached)

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                NotificationControlUiEvent.ProtectedLimitReached ->
                    snackbar.showSnackbar(protectedLimitMessage)
            }
        }
    }

    var tab by remember { mutableStateOf(MainTab.HOME) }
    var showPicker by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    tab == MainTab.HOME,
                    { tab = MainTab.HOME },
                    { Icon(Icons.Default.Home, null) },
                    label = { Text(stringResource(R.string.tab_home)) }
                )
                NavigationBarItem(
                    tab == MainTab.VAULT,
                    { tab = MainTab.VAULT },
                    { Icon(Icons.Default.Archive, null) },
                    label = { Text(stringResource(R.string.tab_vault)) }
                )
                NavigationBarItem(
                    tab == MainTab.LUMINOUS,
                    { tab = MainTab.LUMINOUS },
                    { Icon(Icons.Default.Bolt, null) },
                    label = { Text(stringResource(R.string.tab_luminous)) }
                )
                NavigationBarItem(
                    tab == MainTab.PROFILE,
                    { tab = MainTab.PROFILE },
                    { Icon(Icons.Default.AccountCircle, null) },
                    label = { Text(stringResource(R.string.tab_profile)) }
                )
            }
        }
    ) { padding ->
        when (tab) {
            MainTab.HOME -> HomeScreen(
                Modifier,
                padding,
                settings,
                count,
                requestNotificationAccess
            ) { showPicker = true }

            MainTab.VAULT -> {
                if (security.isLocked()) {
                    VaultLockedScreen(
                        modifier = Modifier,
                        contentPadding = padding,
                        onUnlock = requestVaultUnlock
                    )
                } else {
                    VaultScreen(
                        modifier = Modifier,
                        contentPadding = padding,
                        notifications = notifications,
                        search = search,
                        packageFilter = packageFilter,
                        appFilters = appFilters,
                        storageStats = storageStats,
                        currentLimit = vaultLimit,
                        onSearchChange = viewModel::setVaultSearch,
                        onPackageFilterChange = viewModel::setVaultPackageFilter,
                        onLoadMore = viewModel::loadMoreVault,
                        onProtect = viewModel::setProtected
                    )
                }
            }

            MainTab.LUMINOUS -> LuminousScreen(
                Modifier,
                padding,
                settings,
                requestCameraPermission,
                requestOverlayPermission,
                viewModel::setFlashEnabled,
                viewModel::setOverlayEnabled,
                viewModel::setScreenOffOnly,
                viewModel::setBatteryGuardEnabled,
                viewModel::setQuietHoursEnabled,
                viewModel::setStrobeCycles,
                viewModel::setStrobeSpeed,
                viewModel::setCircleThickness,
                viewModel::setCircleGlow
            )

            MainTab.PROFILE -> ProfileScreen(
                modifier = Modifier,
                contentPadding = padding,
                state = settings,
                storageStats = storageStats,
                onConfigureApps = { showPicker = true },
                onVaultLockChanged = viewModel::setVaultLockEnabled,
                onVaultTimeoutChanged = viewModel::setVaultLockTimeoutMinutes,
                onDeleteAll = viewModel::deleteAllVault,
                requestNotificationAccess = requestNotificationAccess,
                requestCameraPermission = requestCameraPermission,
                requestOverlayPermission = requestOverlayPermission
            )
        }
    }

    if (showPicker) {
        AppPickerDialog(
            apps,
            settings.monitoredPackages,
            settings.isPremium,
            viewModel::toggleMonitoredApp
        ) { showPicker = false }
    }
}
