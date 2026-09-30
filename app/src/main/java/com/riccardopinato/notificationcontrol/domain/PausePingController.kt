package com.riccardopinato.notificationcontrol.domain

import com.riccardopinato.notificationcontrol.data.AppSettings
import java.util.concurrent.ConcurrentHashMap

class PausePingController(private val settings: AppSettings) {
    private val lastAlertAt = ConcurrentHashMap<String, Long>()

    fun shouldSuppress(packageName: String, critical: Boolean, now: Long = System.currentTimeMillis()): Boolean {
        if (!settings.pausePingEnabled || critical) {
            lastAlertAt[packageName] = now
            return false
        }

        val previous = lastAlertAt.put(packageName, now)
        val cooldownMs = settings.pausePingCooldownSeconds.coerceIn(1, 300) * 1_000L
        return previous != null && now - previous < cooldownMs
    }
}
