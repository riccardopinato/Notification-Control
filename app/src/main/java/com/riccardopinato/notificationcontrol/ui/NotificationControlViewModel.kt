package com.riccardopinato.notificationcontrol.ui

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.riccardopinato.notificationcontrol.capture.NotificationMediaStore
import com.riccardopinato.notificationcontrol.data.AppSettings
import com.riccardopinato.notificationcontrol.data.NotificationDatabase
import com.riccardopinato.notificationcontrol.data.NotificationEntity
import com.riccardopinato.notificationcontrol.data.VaultAppFilter
import com.riccardopinato.notificationcontrol.domain.MonitoredAppsPolicy
import com.riccardopinato.notificationcontrol.domain.ProductLimits
import com.riccardopinato.notificationcontrol.domain.VaultSearchQuery
import com.riccardopinato.notificationcontrol.hardware.FlashCoordinator
import com.riccardopinato.notificationcontrol.security.VaultSecurityManager
import com.riccardopinato.notificationcontrol.storage.StorageStats
import com.riccardopinato.notificationcontrol.storage.StorageStatsRepository
import com.riccardopinato.notificationcontrol.ui.overlay.LuminousCircleOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class InstalledApp(val packageName: String, val label: String)

sealed interface NotificationControlUiEvent {
    data object ProtectedLimitReached : NotificationControlUiEvent
}

data class SettingsUiState(
    val onboardingCompleted: Boolean = false,
    val monitoredPackages: Set<String> = emptySet(),
    val isPremium: Boolean = false,
    val retentionDays: Int = 7,
    val flashEnabled: Boolean = true,
    val overlayEnabled: Boolean = false,
    val screenOffOnly: Boolean = true,
    val strobeSpeedMs: Long = 150L,
    val strobeCycles: Int = 5,
    val batteryGuardEnabled: Boolean = true,
    val batteryGuardThreshold: Int = 15,
    val quietHoursEnabled: Boolean = false,
    val circleColorHex: String = "#6750A4",
    val circleThickness: Float = 24f,
    val circleGlow: Float = 30f,
    val pulseSpeedMs: Long = 1_000L,
    val vaultLockEnabled: Boolean = false,
    val vaultLockTimeoutMinutes: Int = 5
)

class NotificationControlViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = AppSettings(application)
    private val dao = NotificationDatabase.get(application).notificationDao()
    private val storageRepository = StorageStatsRepository(application)

    private val _settingsState = MutableStateFlow(readSettings())
    val settingsState: StateFlow<SettingsUiState> = _settingsState.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val installedApps: StateFlow<List<InstalledApp>> = _installedApps.asStateFlow()

    private val _vaultSearch = MutableStateFlow("")
    val vaultSearch: StateFlow<String> = _vaultSearch.asStateFlow()

    private val _vaultPackageFilter = MutableStateFlow<String?>(null)
    val vaultPackageFilter: StateFlow<String?> = _vaultPackageFilter.asStateFlow()

    private val _vaultLimit = MutableStateFlow(100)
    val vaultLimit: StateFlow<Int> = _vaultLimit.asStateFlow()

    private val _storageStats = MutableStateFlow(StorageStats())
    val storageStats: StateFlow<StorageStats> = _storageStats.asStateFlow()

    private val _events = MutableSharedFlow<NotificationControlUiEvent>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    val notifications: StateFlow<List<NotificationEntity>> =
        combine(_vaultSearch, _vaultPackageFilter, _vaultLimit) { query, app, limit ->
            Triple(query, app, limit)
        }.flatMapLatest { (query, app, limit) ->
            val fts = VaultSearchQuery.toFtsQuery(query)
            if (fts == null) {
                dao.observeFilteredByApp(app, limit)
            } else {
                dao.observeSearch(fts, app, limit)
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    val vaultAppFilters: StateFlow<List<VaultAppFilter>> =
        dao.observeAppFilters().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    val notificationCount: StateFlow<Int> =
        dao.observeCount().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            0
        )

    init {
        loadInstalledApps()
        refreshStorageStats()
    }

    fun completeOnboarding() {
        settings.onboardingCompleted = true
        refresh()
    }

    fun toggleMonitoredApp(packageName: String): Boolean {
        val result = MonitoredAppsPolicy.toggle(
            settings.monitoredPackages,
            packageName,
            settings.isPremium
        )
        if (!result.accepted) return false
        settings.monitoredPackages = result.packages
        refresh()
        return true
    }

    fun setVaultSearch(value: String) {
        _vaultSearch.value = value
    }

    fun setVaultPackageFilter(packageName: String?) {
        _vaultPackageFilter.value = packageName
    }

    fun loadMoreVault() {
        _vaultLimit.value = (_vaultLimit.value + 100).coerceAtMost(2_000)
    }

    fun setFlashEnabled(value: Boolean) {
        settings.flashEnabled = value
        refresh()
    }

    fun setOverlayEnabled(value: Boolean) {
        settings.overlayEnabled = value
        refresh()
    }

    fun setScreenOffOnly(value: Boolean) {
        settings.screenOffOnly = value
        refresh()
    }

    fun setBatteryGuardEnabled(value: Boolean) {
        settings.batteryGuardEnabled = value
        refresh()
    }

    fun setQuietHoursEnabled(value: Boolean) {
        settings.quietHoursEnabled = value
        refresh()
    }

    fun setStrobeCycles(value: Int) {
        settings.strobeCycles = value
        refresh()
    }

    fun setStrobeSpeed(value: Long) {
        settings.strobeSpeedMs = value
        refresh()
    }

    fun setCircleThickness(value: Float) {
        settings.circleThickness = value
        refresh()
    }

    fun setCircleGlow(value: Float) {
        settings.circleGlow = value
        refresh()
    }

    fun setRetentionDays(days: Int) {
        settings.retentionDays = if (settings.isPremium) days else ProductLimits.FREE_RETENTION_DAYS
        refresh()
    }

    fun setVaultLockEnabled(enabled: Boolean) {
        VaultSecurityManager(getApplication()).setEnabled(enabled)
        refresh()
    }

    fun setVaultLockTimeoutMinutes(minutes: Int) {
        settings.vaultLockTimeoutMinutes = minutes
        VaultSecurityManager(getApplication()).lock()
        refresh()
    }

    fun setProtected(key: String, value: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            if (
                value &&
                !settings.isPremium &&
                dao.countProtected() >= ProductLimits.FREE_PROTECTED_ITEMS
            ) {
                _events.tryEmit(NotificationControlUiEvent.ProtectedLimitReached)
                return@launch
            }
            dao.setProtected(key, value)
        }
    }

    fun deleteAllVault() {
        viewModelScope.launch(Dispatchers.IO) {
            val mediaStore = NotificationMediaStore(getApplication())
            val paths = dao.allThumbnailPaths()
            dao.deleteEverything()
            paths.forEach(mediaStore::delete)
            mediaStore.cleanupOrphans(emptyList())
            refreshStorageStats()
        }
    }

    fun testVisualAlerts() {
        val context = getApplication<Application>()
        if (settings.flashEnabled) {
            FlashCoordinator.get(context).startStrobe(
                cycles = 3,
                onMs = 120L,
                offMs = 120L
            )
        }
        if (settings.overlayEnabled) {
            LuminousCircleOverlay(context).show("Notification Control")
        }
    }

    fun refresh() {
        _settingsState.value = readSettings()
        refreshStorageStats()
    }

    fun refreshStorageStats() {
        viewModelScope.launch(Dispatchers.IO) {
            _storageStats.value = storageRepository.read()
        }
    }

    private fun readSettings() = SettingsUiState(
        onboardingCompleted = settings.onboardingCompleted,
        monitoredPackages = settings.monitoredPackages,
        isPremium = settings.isPremium,
        retentionDays = settings.retentionDays,
        flashEnabled = settings.flashEnabled,
        overlayEnabled = settings.overlayEnabled,
        screenOffOnly = settings.screenOffOnly,
        strobeSpeedMs = settings.strobeSpeedMs,
        strobeCycles = settings.strobeCycles,
        batteryGuardEnabled = settings.batteryGuardEnabled,
        batteryGuardThreshold = settings.batteryGuardThreshold,
        quietHoursEnabled = settings.quietHoursEnabled,
        circleColorHex = settings.circleColorHex,
        circleThickness = settings.circleThickness,
        circleGlow = settings.circleGlow,
        pulseSpeedMs = settings.pulseSpeedMs,
        vaultLockEnabled = settings.vaultLockEnabled,
        vaultLockTimeoutMinutes = settings.vaultLockTimeoutMinutes
    )

    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = getApplication<Application>().packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            _installedApps.value = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
                .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
                .distinctBy { it.packageName }
                .filterNot { it.packageName == getApplication<Application>().packageName }
                .sortedBy { it.label.lowercase() }
        }
    }
}
