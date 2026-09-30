package com.riccardopinato.notificationcontrol.core.apps

import android.content.Context

class MonitoredAppsStore(context: Context) {
    private val prefs = context.getSharedPreferences("notification_control_prefs", Context.MODE_PRIVATE)

    val monitoredPackages: Set<String>
        get() = prefs.getStringSet(KEY_PACKAGES, emptySet())?.toSet().orEmpty()

    fun setMonitored(packageName: String, enabled: Boolean, premium: Boolean): Boolean {
        val current = monitoredPackages.toMutableSet()
        if (enabled) {
            if (!premium && packageName !in current && current.size >= FREE_LIMIT) return false
            current += packageName
        } else {
            current -= packageName
        }
        prefs.edit().putStringSet(KEY_PACKAGES, current).apply()
        return true
    }

    fun isMonitored(packageName: String): Boolean = packageName in monitoredPackages

    companion object {
        const val FREE_LIMIT = 3
        private const val KEY_PACKAGES = "monitored_packages"
    }
}
