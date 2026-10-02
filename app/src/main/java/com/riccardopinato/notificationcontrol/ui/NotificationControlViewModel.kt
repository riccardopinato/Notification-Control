package com.riccardopinato.notificationcontrol.ui

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
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
import com.riccardopinato.notificationcontrol.data.MediaRescueEntity
import com.riccardopinato.notificationcontrol.data.MessageEntity
import com.riccardopinato.notificationcontrol.data.NotificationDatabase
import com.riccardopinato.notificationcontrol.data.NotificationEntity
import com.riccardopinato.notificationcontrol.data.NotificationRevisionEntity
import com.riccardopinato.notificationcontrol.data.PickupCodeEntity
import com.riccardopinato.notificationcontrol.data.QuietHoursBand
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
import com.riccardopinato.notificationcontrol.workers.MediaRecoveryScheduler
import com.riccardopinato.notificationcontrol.workers.RetentionWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class InstalledApp(val packageName: String, val label: String)

@Immutable
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

@Immutable
data class SettingsUiState(
    val onboardingCompleted: Boolean = false,
    val monitoredPackages: Set<String> = emptySet(),
    val whatsAppMediaTreeUri: String? = null,
    val isPremium: Boolean = false,
    val retentionDays: Int = 7,
    val retentionDaysPerApp: Map<String, Int> = emptyMap(),
    val vaultMaxBytes: Long = 100L * 1024L * 1024L,
    val flashEnabled: Boolean = false,
    val overlayEnabled: Boolean = false,
    val screenOffOnly: Boolean = true,
    val strobeSpeedMs: Long = 150L,
    val strobeCycles: Int = 5,
    val batteryGuardEnabled: Boolean = true,
    val batteryGuardThreshold: Int = 15,
    val quietHoursEnabled: Boolean = false,
    val quietStartMinutes: Int = 22 * 60,
    val quietEndMinutes: Int = 7 * 60,
    val additionalQuietHours: List<QuietHoursBand> = emptyList(),
    val quietHoursExceptionPackages: Set<String> = emptySet(),
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

@OptIn(FlowPreview::class)
class NotificationControlViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = AppSettings(application)
    private val billingManager by lazy {
        PlayBillingManager.get(getApplication<Application>())
    }
    private val database by lazy {
        NotificationDatabase.get(getApplication<Application>())
    }
    private val dao by lazy { database.notificationDao() }
    private val automationDao by lazy { database.automationDao() }
    private val automationRepository by lazy {
        AutomationRepository(getApplication(), settings, automationDao)
    }
    private val luminousProfileDao by lazy { database.luminousProfileDao() }
    private val luminousProfileRepository by lazy {
        LuminousProfileRepository(settings, luminousProfileDao)
    }
    private val storageRepository by lazy {
        StorageStatsRepository(getApplication<Application>())
    }

    @Volatile
    private var installedAppsLoading = false

    @Volatile
    private var runtimeMaintenanceStarted = false

    @Volatile
    private var billingObservationStarted = false

    val billingState: StateFlow<BillingUiState>
        get() = billingManager.state

    private val _settingsState = MutableStateFlow(readSettings())
    val settingsState: StateFlow<SettingsUiState> = _settingsState.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val installedApps: StateFlow<List<InstalledApp>> = _installedApps.asStateFlow()

    private val _vaultSearch = MutableStateFlow("")
    val vaultSearch: StateFlow<String> = _vaultSearch.asStateFlow()

    private val _vaultPackageFilter = MutableStateFlow<String?>(null)
    val vaultPackageFilter: StateFlow<String?> = _vaultPackageFilter.asStateFlow()

    private val _storageStats = MutableStateFlow(StorageStats())
    val storageStats: StateFlow<StorageStats> = _storageStats.asStateFlow()

    private val _vaultDetail = MutableStateFlow<VaultDetailUiState?>(null)
    val vaultDetail: StateFlow<VaultDetailUiState?> = _vaultDetail.asStateFlow()

    private val _events = MutableSharedFlow<NotificationControlUiEvent>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    val vaultPaging: Flow<PagingData<NotificationEntity>> =
        combine(
            _vaultSearch.debounce(120L).distinctUntilChanged(),
            _vaultPackageFilter
        ) { query, app ->
            query to app
        }.flatMapLatest { (query, app) ->
            val fts = VaultSearchQuery.toFtsQuery(query)
            Pager(
                config = PagingConfig(
                    pageSize = 50,
                    initialLoadSize = 100,
                    prefetchDistance = 12,
                    enablePlaceholders = false
                ),
                pagingSourceFactory = {
                    if (fts == null) {
                        dao.pagingFilteredByApp(app)
                    } else {
                        dao.pagingSearch(fts, app)
                    }
                }
            ).flow
        }.cachedIn(viewModelScope)

    val vaultAppFilters: StateFlow<List<VaultAppFilter>> by lazy {
        dao.observeAppFilters().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )
    }

    val notificationCount: StateFlow<Int> by lazy {
        dao.observeCount().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            0
        )
    }

    val mediaRescueItems: StateFlow<List<MediaRescueEntity>> by lazy {
        database.mediaRecoveryDao().observeRescue().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )
    }


    val rules: StateFlow<List<RuleWithActions>> by lazy {
        automationDao.observeRules().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )
    }

    val criticalAlerts: StateFlow<List<CriticalAlertEntity>> by lazy {
        automationDao.observeActiveCriticalAlerts().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )
    }

    val criticalPatterns: StateFlow<List<CriticalPatternEntity>> by lazy {
        automationDao.observeCriticalPatterns().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )
    }

    val followUps: StateFlow<List<FollowUpEntity>> by lazy {
        automationDao.observeActiveFollowUps().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )
    }

    val pickupCodes: StateFlow<List<PickupCodeEntity>> by lazy {
        automationDao.observePickupCodes().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )
    }

    val luminousProfiles: StateFlow<List<LuminousProfileEntity>> by lazy {
        luminousProfileDao.observeProfiles().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )
    }

    fun ensureRuntimeMaintenance() {
        if (runtimeMaintenanceStarted) return
        runtimeMaintenanceStarted = true
        viewModelScope.launch(Dispatchers.IO) {
            RetentionWorker.schedule(getApplication())
            automationRepository.cleanupPickupCodes()
        }
    }

    fun ensureBillingReady() {
        if (!billingObservationStarted) {
            billingObservationStarted = true
            viewModelScope.launch {
                billingManager.state.collect {
                    _settingsState.value = readSettings()
                }
            }
        }
        billingManager.refresh()
    }

    fun restorePurchases() {
        ensureBillingReady()
    }

    fun completeOnboarding() {
        settings.onboardingCompleted = true
        refresh()
        ensureRuntimeMaintenance()
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

    fun addQuietHoursBand(startMinutes: Int, endMinutes: Int) {
        if (!settings.isPremium) return
        val band = QuietHoursBand(
            startMinutes.coerceIn(0, 1439),
            endMinutes.coerceIn(0, 1439)
        )
        settings.additionalQuietHours =
            (settings.additionalQuietHours + band).distinct()
        refresh()
    }

    fun removeQuietHoursBand(startMinutes: Int, endMinutes: Int) {
        if (!settings.isPremium) return
        settings.additionalQuietHours =
            settings.additionalQuietHours.filterNot {
                it.startMinutes == startMinutes && it.endMinutes == endMinutes
            }
        refresh()
    }

    fun addQuietHoursException(packageName: String) {
        if (!settings.isPremium || packageName.isBlank()) return
        settings.quietHoursExceptionPackages =
            settings.quietHoursExceptionPackages + packageName
        refresh()
    }

    fun removeQuietHoursException(packageName: String) {
        if (!settings.isPremium) return
        settings.quietHoursExceptionPackages =
            settings.quietHoursExceptionPackages - packageName
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

    fun setRetentionDaysForApp(packageName: String, days: Int) {
        if (!settings.isPremium || packageName.isBlank()) return
        settings.retentionDaysPerApp =
            settings.retentionDaysPerApp.toMutableMap().apply {
                put(packageName, days)
            }
        refresh()
    }

    fun clearRetentionDaysForApp(packageName: String) {
        if (!settings.isPremium) return
        settings.retentionDaysPerApp =
            settings.retentionDaysPerApp.toMutableMap().apply {
                remove(packageName)
            }
        refresh()
    }

    fun setVaultMaxBytes(bytes: Long) {
        if (!settings.isPremium) return
        settings.vaultMaxBytes = bytes
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
        timeStartMinutes: Int?,
        timeEndMinutes: Int?,
        screenState: String,
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
                    timeStartMinutes,
                    timeEndMinutes,
                    screenState,
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
        timeStartMinutes: Int?,
        timeEndMinutes: Int?,
        screenState: String,
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
                timeStartMinutes = timeStartMinutes,
                timeEndMinutes = timeEndMinutes,
                screenState = screenState,
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

    fun setWhatsAppMediaTreeUri(uri: String?) {
        settings.whatsAppMediaTreeUri = uri
        refresh()
        MediaRecoveryScheduler.enqueue(getApplication())
    }

    fun deleteRescueMedia(item: MediaRescueEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            database.mediaRecoveryDao().deleteRescueBySourceKey(item.sourceKey)
            NotificationMediaStore(getApplication()).delete(item.localPath)
            refreshStorageStats()
        }
    }

    fun refresh() {
        _settingsState.value = readSettings()
    }

    fun refreshAll() {
        refresh()
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
        whatsAppMediaTreeUri = settings.whatsAppMediaTreeUri,
        isPremium = settings.isPremium,
        retentionDays = settings.retentionDays,
        retentionDaysPerApp = settings.retentionDaysPerApp,
        vaultMaxBytes = settings.vaultMaxBytes,
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
        additionalQuietHours = settings.additionalQuietHours,
        quietHoursExceptionPackages = settings.quietHoursExceptionPackages,
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

    fun ensureInstalledAppsLoaded(force: Boolean = false) {
        if (installedAppsLoading) return
        if (!force && _installedApps.value.isNotEmpty()) return

        installedAppsLoading = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val app = getApplication<Application>()
                val pm = app.packageManager
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                _installedApps.value =
                    pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
                        .asSequence()
                        .map {
                            InstalledApp(
                                it.activityInfo.packageName,
                                it.loadLabel(pm).toString()
                            )
                        }
                        .distinctBy { it.packageName }
                        .filterNot { it.packageName == app.packageName }
                        .sortedBy { it.label.lowercase() }
                        .toList()
            } finally {
                installedAppsLoading = false
            }
        }
    }
}
