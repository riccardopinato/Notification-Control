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
import com.riccardopinato.notificationcontrol.domain.MonitoredAppsPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class InstalledApp(val packageName: String, val label: String)
data class SettingsUiState(
    val onboardingCompleted: Boolean = false, val monitoredPackages: Set<String> = emptySet(), val isPremium: Boolean = false,
    val retentionDays: Int = 7, val flashEnabled: Boolean = true, val overlayEnabled: Boolean = false, val screenOffOnly: Boolean = true,
    val strobeSpeedMs: Long = 150L, val strobeCycles: Int = 5, val batteryGuardEnabled: Boolean = true, val batteryGuardThreshold: Int = 15,
    val quietHoursEnabled: Boolean = false, val circleColorHex: String = "#6750A4", val circleThickness: Float = 24f,
    val circleGlow: Float = 30f, val pulseSpeedMs: Long = 1_000L
)

class NotificationControlViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = AppSettings(application)
    private val dao = NotificationDatabase.get(application).notificationDao()
    private val _settingsState = MutableStateFlow(readSettings())
    val settingsState: StateFlow<SettingsUiState> = _settingsState.asStateFlow()
    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val installedApps: StateFlow<List<InstalledApp>> = _installedApps.asStateFlow()
    val notifications: StateFlow<List<NotificationEntity>> = dao.observeRecent().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val notificationCount: StateFlow<Int> = dao.observeCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    init { loadInstalledApps() }
    fun completeOnboarding() { settings.onboardingCompleted = true; refresh() }
    fun toggleMonitoredApp(packageName: String): Boolean {
        val result = MonitoredAppsPolicy.toggle(settings.monitoredPackages, packageName, settings.isPremium)
        if (!result.accepted) return false
        settings.monitoredPackages = result.packages; refresh(); return true
    }
    fun setFlashEnabled(value: Boolean) { settings.flashEnabled = value; refresh() }
    fun setOverlayEnabled(value: Boolean) { settings.overlayEnabled = value; refresh() }
    fun setScreenOffOnly(value: Boolean) { settings.screenOffOnly = value; refresh() }
    fun setBatteryGuardEnabled(value: Boolean) { settings.batteryGuardEnabled = value; refresh() }
    fun setQuietHoursEnabled(value: Boolean) { settings.quietHoursEnabled = value; refresh() }
    fun setStrobeCycles(value: Int) { settings.strobeCycles = value; refresh() }
    fun setStrobeSpeed(value: Long) { settings.strobeSpeedMs = value; refresh() }
    fun setCircleThickness(value: Float) { settings.circleThickness = value; refresh() }
    fun setCircleGlow(value: Float) { settings.circleGlow = value; refresh() }
    fun setProtected(key: String, value: Boolean) { viewModelScope.launch(Dispatchers.IO) { dao.setProtected(key, value) } }
    fun deleteAllVault() { viewModelScope.launch(Dispatchers.IO) {
        val mediaStore = NotificationMediaStore(getApplication()); dao.allThumbnailPaths().forEach(mediaStore::delete); dao.deleteAll()
    } }
    fun refresh() { _settingsState.value = readSettings() }
    private fun readSettings() = SettingsUiState(
        settings.onboardingCompleted, settings.monitoredPackages, settings.isPremium, settings.retentionDays, settings.flashEnabled,
        settings.overlayEnabled, settings.screenOffOnly, settings.strobeSpeedMs, settings.strobeCycles, settings.batteryGuardEnabled,
        settings.batteryGuardThreshold, settings.quietHoursEnabled, settings.circleColorHex, settings.circleThickness, settings.circleGlow, settings.pulseSpeedMs
    )
    private fun loadInstalledApps() { viewModelScope.launch(Dispatchers.IO) {
        val pm = getApplication<Application>().packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        _installedApps.value = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .distinctBy { it.packageName }.filterNot { it.packageName == getApplication<Application>().packageName }.sortedBy { it.label.lowercase() }
    } }
}
