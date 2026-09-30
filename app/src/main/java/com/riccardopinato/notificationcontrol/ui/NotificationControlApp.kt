package com.riccardopinato.notificationcontrol.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Tune
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

private enum class MainTab { HOME, VAULT, FOLLOW_UP, RULES, PROFILE }

@Composable
fun NotificationControlApp(
    viewModel: NotificationControlViewModel,
    permissionEpoch: Int,
    vaultUnlockEpoch: Int,
    requestCameraPermission: () -> Unit,
    requestOverlayPermission: () -> Unit,
    requestNotificationAccess: () -> Unit,
    requestPostNotifications: () -> Unit,
    requestVaultUnlock: () -> Unit,
    setSecureWindow: (Boolean) -> Unit,
    purchasePremiumOffer: (String) -> Unit
) {
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val billingState by viewModel.billingState.collectAsStateWithLifecycle()
    val apps by viewModel.installedApps.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val count by viewModel.notificationCount.collectAsStateWithLifecycle()
    val search by viewModel.vaultSearch.collectAsStateWithLifecycle()
    val packageFilter by viewModel.vaultPackageFilter.collectAsStateWithLifecycle()
    val vaultLimit by viewModel.vaultLimit.collectAsStateWithLifecycle()
    val appFilters by viewModel.vaultAppFilters.collectAsStateWithLifecycle()
    val storageStats by viewModel.storageStats.collectAsStateWithLifecycle()
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val criticalAlerts by viewModel.criticalAlerts.collectAsStateWithLifecycle()
    val criticalPatterns by viewModel.criticalPatterns.collectAsStateWithLifecycle()
    val followUps by viewModel.followUps.collectAsStateWithLifecycle()
    val pickupCodes by viewModel.pickupCodes.collectAsStateWithLifecycle()
    val luminousProfiles by viewModel.luminousProfiles.collectAsStateWithLifecycle()
    val vaultDetail by viewModel.vaultDetail.collectAsStateWithLifecycle()

    permissionEpoch.hashCode()
    vaultUnlockEpoch.hashCode()

    LaunchedEffect(settings.sensitiveProtectionEnabled) {
        setSecureWindow(settings.sensitiveProtectionEnabled)
    }

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
    val sensitiveLocked = security.isLocked()
    val snackbar = remember { SnackbarHostState() }
    val protectedLimitMessage = stringResource(R.string.protected_limit_reached)
    val ruleLimitMessage = stringResource(R.string.rule_limit_reached)
    val criticalLimitMessage = stringResource(R.string.critical_limit_reached)
    val followUpLimitMessage = stringResource(R.string.follow_up_limit_reached)
    val luminousProfilePremiumMessage =
        stringResource(R.string.luminous_profile_premium_required)

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            val message = when (event) {
                NotificationControlUiEvent.ProtectedLimitReached -> protectedLimitMessage
                NotificationControlUiEvent.RuleLimitReached -> ruleLimitMessage
                NotificationControlUiEvent.CriticalLimitReached -> criticalLimitMessage
                NotificationControlUiEvent.FollowUpLimitReached -> followUpLimitMessage
                NotificationControlUiEvent.LuminousProfileRequiresPremium ->
                    luminousProfilePremiumMessage
            }
            snackbar.showSnackbar(message)
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
                    tab == MainTab.FOLLOW_UP,
                    { tab = MainTab.FOLLOW_UP },
                    { Icon(Icons.Default.TaskAlt, null) },
                    label = { Text(stringResource(R.string.tab_follow_up)) }
                )
                NavigationBarItem(
                    tab == MainTab.RULES,
                    { tab = MainTab.RULES },
                    { Icon(Icons.Default.Tune, null) },
                    label = { Text(stringResource(R.string.tab_rules)) }
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
                modifier = Modifier,
                contentPadding = padding,
                settings = settings,
                count = count,
                criticalAlerts = criticalAlerts,
                pickupCodes = pickupCodes,
                sensitiveLocked = sensitiveLocked,
                requestNotificationAccess = requestNotificationAccess,
                onUnlockSensitive = requestVaultUnlock,
                onHandleCritical = viewModel::handleCriticalAlert,
                onDismissPickup = viewModel::dismissPickupCode
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
                        onProtect = viewModel::setProtected,
                        onFollowUp = {
                            requestPostNotifications()
                            viewModel.createFollowUp(it, 60)
                        },
                        onOpenDetail = viewModel::openVaultDetail
                    )
                }
            }

            MainTab.FOLLOW_UP -> {
                if (sensitiveLocked) {
                    VaultLockedScreen(
                        modifier = Modifier,
                        contentPadding = padding,
                        onUnlock = requestVaultUnlock
                    )
                } else {
                    FollowUpScreen(
                        modifier = Modifier,
                        contentPadding = padding,
                        followUps = followUps,
                        isPremium = settings.isPremium,
                        requestPostNotifications = requestPostNotifications,
                        onComplete = viewModel::completeFollowUp,
                        onSnooze = viewModel::snoozeFollowUp,
                        onSchedule = viewModel::scheduleFollowUp
                    )
                }
            }

            MainTab.RULES -> RulesScreen(
                modifier = Modifier,
                contentPadding = padding,
                state = settings,
                apps = apps,
                rules = rules,
                criticalPatterns = criticalPatterns,
                luminousProfiles = luminousProfiles,
                requestCameraPermission = requestCameraPermission,
                requestOverlayPermission = requestOverlayPermission,
                onCreateRule = viewModel::createRule,
                onUpdateRule = viewModel::updateRule,
                onRuleEnabled = viewModel::setRuleEnabled,
                onDeleteRule = viewModel::deleteRule,
                onAddCriticalPattern = viewModel::addCriticalPattern,
                onDeleteCriticalPattern = viewModel::deleteCriticalPattern,
                onCreateLuminousProfile = viewModel::createLuminousProfile,
                onLuminousProfileEnabled = viewModel::setLuminousProfileEnabled,
                onDeleteLuminousProfile = viewModel::deleteLuminousProfile,
                onPausePingEnabled = viewModel::setPausePingEnabled,
                onPausePingCooldown = viewModel::setPausePingCooldownSeconds,
                onPausePingAppCooldown = viewModel::setPausePingAppCooldown,
                onRemovePausePingAppCooldown = viewModel::removePausePingAppCooldown,
                setFlash = viewModel::setFlashEnabled,
                setOverlay = viewModel::setOverlayEnabled,
                setScreenOffOnly = viewModel::setScreenOffOnly,
                setBatteryGuard = viewModel::setBatteryGuardEnabled,
                setQuietHours = viewModel::setQuietHoursEnabled,
                setCycles = viewModel::setStrobeCycles,
                setSpeed = viewModel::setStrobeSpeed,
                setThickness = viewModel::setCircleThickness,
                setGlow = viewModel::setCircleGlow
            )

            MainTab.PROFILE -> ProfileScreen(
                modifier = Modifier,
                contentPadding = padding,
                state = settings,
                billingState = billingState,
                storageStats = storageStats,
                onConfigureApps = { showPicker = true },
                onVaultLockChanged = viewModel::setVaultLockEnabled,
                onVaultTimeoutChanged = viewModel::setVaultLockTimeoutMinutes,
                onSensitiveProtectionChanged = viewModel::setSensitiveProtectionEnabled,
                onRetentionDaysChanged = viewModel::setRetentionDays,
                onVaultMaxBytesChanged = viewModel::setVaultMaxBytes,
                onBatteryGuardThresholdChanged = viewModel::setBatteryGuardThreshold,
                onQuietStartChanged = viewModel::setQuietStartMinutes,
                onQuietEndChanged = viewModel::setQuietEndMinutes,
                onCriticalBypassQuietHoursChanged = viewModel::setCriticalBypassQuietHours,
                onDeleteAll = viewModel::deleteAllVault,
                onResetLocalData = viewModel::resetLocalData,
                onPurchasePremium = purchasePremiumOffer,
                onRestorePurchases = viewModel::restorePurchases,
                onBackupRestored = viewModel::refresh,
                requestNotificationAccess = requestNotificationAccess,
                requestPostNotifications = requestPostNotifications,
                requestCameraPermission = requestCameraPermission,
                requestOverlayPermission = requestOverlayPermission
            )
        }
    }

    vaultDetail?.let { detail ->
        VaultDetailDialog(
            state = detail,
            onDismiss = viewModel::closeVaultDetail,
            onProtect = viewModel::setProtected,
            onFollowUp = {
                requestPostNotifications()
                viewModel.createFollowUp(it, 60)
            }
        )
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
