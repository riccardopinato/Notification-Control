package com.riccardopinato.notificationcontrol.ui

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.riccardopinato.notificationcontrol.automation.AutomationRepository
import com.riccardopinato.notificationcontrol.automation.CriticalAlertScheduler
import com.riccardopinato.notificationcontrol.automation.FollowUpScheduler
import com.riccardopinato.notificationcontrol.billing.BillingUiState
import com.riccardopinato.notificationcontrol.billing.PlayBillingManager
import com.riccardopinato.notificationcontrol.capture.NotificationMediaStore
import com.riccardopinato.notificationcontrol.data.AppSettings
import com.riccardopinato.notificationcontrol.data.CriticalAlertEntity
import com.riccardopinato.notificationcontrol.data.CriticalPatternEntity
import com.riccardopinato.notificationcontrol.data.FollowUpEntity
import com.riccardopinato.notificationcontrol.data.LuminousProfileEntity
import com.riccardopinato.notificationcontrol.data.MessageEntity
import com.riccardopinato.notificationcontrol.data.NotificationDatabase
import com.riccardopinato.notificationcontrol.data.NotificationEntity
import com.riccardopinato.notificationcontrol.data.NotificationRevisionEntity
import com.riccardopinato.notificationcontrol.data.PickupCodeEntity
import com.riccardopinato.notificationcontrol.data.RuleWithActions
import com.riccardopinato.notificationcontrol.data.VaultAppFilter
import com.riccardopinato.notificationcontrol.domain.MonitoredAppsPolicy
import com.riccardopinato.notificationcontrol.domain.PickupCodeCandidate
import com.riccardopinato.notificationcontrol.domain.PickupCodeExtractor
import com.riccardopinato.notificationcontrol.domain.ProductLimits
import com.riccardopinato.notificationcontrol.domain.VaultSearchQuery
import com.riccardopinato.notificationcontrol.hardware.FlashCoordinator
import com.riccardopinato.notificationcontrol.luminous.LuminousProfileRepository
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

data class VaultDetailUiState(
    val notification: NotificationEntity,
    val messages: List<MessageEntity> = emptyList(),
    val revisions: List<NotificationRevisionEntity> = emptyList(),
    val pickupCandidate: PickupCodeCandidate? = null,
    val loading: Boolean = true
)

sealed interface NotificationControlUiEvent {
    data object ProtectedLimitReached : NotificationControlUiEvent
    data object RuleLimitReached : NotificationControlUiEvent
    data object CriticalLimitReached : NotificationControlUiEvent
    data object FollowUpLimitReached : NotificationControlUiEvent
    data object LuminousProfileRequiresPremium : NotificationControlUiEvent
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
    val quietStartMinutes: Int = 22 * 60,
    val quietEndMinutes: Int = 7 * 60,
    val circleColorHex: String = "#6750A4",
    val circleThickness: Float = 24f,
    val circleGlow: Float = 30f,
    val pulseSpeedMs: Long = 1_000L,
    val vaultLockEnabled: Boolean = false,
    val vaultLockTimeoutMinutes: Int = 5,
    val sensitiveProtectionEnabled: Boolean = true,
    val pausePingEnabled: Boolean = true,
    val pausePingCooldownSeconds: Int = 20,
    val pausePingPerAppCooldowns: Map<String, Int> = emptyMap(),
    val criticalBypassQuietHours: Boolean = true
)

class NotificationControlViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = AppSettings(application)
    private val billingManager = PlayBillingManager.get(application)
    private val database = NotificationDatabase.get(application)
    private val dao = database.notificationDao()
    private val automationDao = database.automationDao()
    private val automationRepository = AutomationRepository(application, settings, automationDao)
    private val luminousProfileDao = database.luminousProfileDao()
    private val luminousProfileRepository =
        LuminousProfileRepository(settings, luminousProfileDao)
    private val storageRepository = StorageStatsRepository(application)

    val billingState: StateFlow<BillingUiState> = billingManager.state

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

    private val _vaultDetail = MutableStateFlow<VaultDetailUiState?>(null)
    val vaultDetail: StateFlow<VaultDetailUiState?> = _vaultDetail.asStateFlow()

    private val _events = MutableSharedFlow<NotificationControlUiEvent>(extraBufferCapacity = 8)
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

    val rules: StateFlow<List<RuleWithActions>> =
        automationDao.observeRules().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    val criticalAlerts: StateFlow<List<CriticalAlertEntity>> =
        automationDao.observeActiveCriticalAlerts().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    val criticalPatterns: StateFlow<List<CriticalPatternEntity>> =
        automationDao.observeCriticalPatterns().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    val followUps: StateFlow<List<FollowUpEntity>> =
        automationDao.observeActiveFollowUps().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    val pickupCodes: StateFlow<List<PickupCodeEntity>> =
        automationDao.observePickupCodes().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    val luminousProfiles: StateFlow<List<LuminousProfileEntity>> =
        luminousProfileDao.observeProfiles().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    init {
        loadInstalledApps()
        refreshStorageStats()
        viewModelScope.launch {
            billingManager.state.collect {
                _settingsState.value = readSettings()
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            automationRepository.cleanupPickupCodes()
        }
    }

    fun restorePurchases() {
        billingManager.refresh()
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

    fun openVaultDetail(notification: NotificationEntity) {
        _vaultDetail.value = VaultDetailUiState(notification = notification)
        viewModelScope.launch(Dispatchers.IO) {
            val messages = dao.messagesFor(notification.sbnKey)
            val revisions = dao.revisionsFor(notification.sbnKey)
            _vaultDetail.value = VaultDetailUiState(
                notification = notification,
                messages = messages,
                revisions = revisions,
                pickupCandidate = PickupCodeExtractor.extract(notification, messages),
                loading = false
            )
        }
    }

    fun closeVaultDetail() {
        _vaultDetail.value = null
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

    fun setBatteryGuardThreshold(value: Int) {
        settings.batteryGuardThreshold = value
        refresh()
    }

    fun setQuietHoursEnabled(value: Boolean) {
        settings.quietHoursEnabled = value
        refresh()
    }

    fun setQuietStartMinutes(value: Int) {
        settings.quietStartMinutes = value
        refresh()
    }

    fun setQuietEndMinutes(value: Int) {
        settings.quietEndMinutes = value
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
        settings.retentionDays =
            if (settings.isPremium) days else ProductLimits.FREE_RETENTION_DAYS
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

    fun setSensitiveProtectionEnabled(enabled: Boolean) {
        settings.sensitiveProtectionEnabled = enabled
        refresh()
    }

    fun setCriticalBypassQuietHours(enabled: Boolean) {
        settings.criticalBypassQuietHours = enabled
        refresh()
    }

    fun setPausePingEnabled(enabled: Boolean) {
        settings.pausePingEnabled = enabled
        refresh()
    }

    fun setPausePingCooldownSeconds(seconds: Int) {
        settings.pausePingCooldownSeconds = seconds
        refresh()
    }

    fun setPausePingAppCooldown(packageName: String, seconds: Int) {
        if (!settings.isPremium) return
        settings.pausePingPerAppCooldowns =
            settings.pausePingPerAppCooldowns.toMutableMap().apply {
                put(packageName, seconds.coerceIn(0, 300))
            }
        refresh()
    }

    fun removePausePingAppCooldown(packageName: String) {
        if (!settings.isPremium) return
        settings.pausePingPerAppCooldowns =
            settings.pausePingPerAppCooldowns.toMutableMap().apply {
                remove(packageName)
            }
        refresh()
    }

    fun createRule(
        name: String,
        packageName: String?,
        senderQuery: String?,
        textQuery: String?,
        matchMode: String,
        actions: List<Pair<String, String?>>
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            if (
                !automationRepository.createRule(
                    name,
                    packageName,
                    senderQuery,
                    textQuery,
                    matchMode,
                    actions
                )
            ) {
                _events.tryEmit(NotificationControlUiEvent.RuleLimitReached)
            }
        }
    }

    fun updateRule(
        id: Long,
        name: String,
        packageName: String?,
        senderQuery: String?,
        textQuery: String?,
        matchMode: String,
        actions: List<Pair<String, String?>>
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            automationRepository.updateRule(
                id = id,
                name = name,
                packageName = packageName,
                senderQuery = senderQuery,
                textQuery = textQuery,
                matchMode = matchMode,
                actions = actions
            )
        }
    }

    fun setRuleEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            automationDao.setRuleEnabled(id, enabled)
        }
    }

    fun deleteRule(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            automationDao.deleteRule(id)
        }
    }

    fun addCriticalPattern(type: String, value: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!automationRepository.addCriticalPattern(type, value)) {
                _events.tryEmit(NotificationControlUiEvent.CriticalLimitReached)
            }
        }
    }

    fun deleteCriticalPattern(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            automationDao.deleteCriticalPattern(id)
        }
    }

    fun handleCriticalAlert(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            automationRepository.handleCriticalAlert(id)
        }
    }

    fun createFollowUp(notification: NotificationEntity, delayMinutes: Int = 60) {
        viewModelScope.launch(Dispatchers.IO) {
            if (
                automationRepository.createFollowUpFromNotification(
                    notification,
                    delayMinutes
                ) == null
            ) {
                _events.tryEmit(NotificationControlUiEvent.FollowUpLimitReached)
            }
        }
    }

    fun completeFollowUp(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            automationRepository.completeFollowUp(id)
        }
    }

    fun snoozeFollowUp(id: Long, delayMinutes: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            automationRepository.snoozeFollowUp(id, delayMinutes)
        }
    }

    fun scheduleFollowUp(id: Long, dueAt: Long, repeatMinutes: Int?) {
        viewModelScope.launch(Dispatchers.IO) {
            automationRepository.scheduleFollowUp(id, dueAt, repeatMinutes)
        }
    }

    fun dismissPickupCode(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            automationDao.dismissPickupCode(id)
        }
    }

    fun createLuminousProfile(
        name: String,
        packageName: String?,
        senderQuery: String?,
        colorHex: String,
        flashEnabled: Boolean,
        overlayEnabled: Boolean,
        strobeCycles: Int,
        strobeSpeedMs: Long,
        circleThickness: Float,
        circleGlow: Float,
        pulseSpeedMs: Long,
        displayDurationMs: Long
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val created = luminousProfileRepository.create(
                name = name,
                packageName = packageName,
                senderQuery = senderQuery,
                colorHex = colorHex,
                flashEnabled = flashEnabled,
                overlayEnabled = overlayEnabled,
                strobeCycles = strobeCycles,
                strobeSpeedMs = strobeSpeedMs,
                circleThickness = circleThickness,
                circleGlow = circleGlow,
                pulseSpeedMs = pulseSpeedMs,
                displayDurationMs = displayDurationMs
            )
            if (!created) {
                _events.tryEmit(
                    NotificationControlUiEvent.LuminousProfileRequiresPremium
                )
            }
        }
    }

    fun setLuminousProfileEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            luminousProfileDao.setEnabled(id, enabled)
        }
    }

    fun deleteLuminousProfile(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            luminousProfileDao.delete(id)
        }
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
            _vaultDetail.value = null
            refreshStorageStats()
        }
    }

    fun resetLocalData() {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val mediaStore = NotificationMediaStore(app)
            val paths = dao.allThumbnailPaths()
            FollowUpScheduler.cancelAll(app)
            CriticalAlertScheduler.cancelAll(app)
            database.clearAllTables()
            paths.forEach(mediaStore::delete)
            mediaStore.cleanupOrphans(emptyList())
            settings.resetToDefaults()
            VaultSecurityManager(app).lock()
            _vaultDetail.value = null
            _vaultSearch.value = ""
            _vaultPackageFilter.value = null
            _vaultLimit.value = 100
            _settingsState.value = readSettings()
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
        quietStartMinutes = settings.quietStartMinutes,
        quietEndMinutes = settings.quietEndMinutes,
        circleColorHex = settings.circleColorHex,
        circleThickness = settings.circleThickness,
        circleGlow = settings.circleGlow,
        pulseSpeedMs = settings.pulseSpeedMs,
        vaultLockEnabled = settings.vaultLockEnabled,
        vaultLockTimeoutMinutes = settings.vaultLockTimeoutMinutes,
        sensitiveProtectionEnabled = settings.sensitiveProtectionEnabled,
        pausePingEnabled = settings.pausePingEnabled,
        pausePingCooldownSeconds = settings.pausePingCooldownSeconds,
        pausePingPerAppCooldowns = settings.pausePingPerAppCooldowns,
        criticalBypassQuietHours = settings.criticalBypassQuietHours
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
