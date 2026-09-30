package com.riccardopinato.notificationcontrol.domain

import com.riccardopinato.notificationcontrol.data.AppSettings
import java.util.concurrent.ConcurrentHashMap

class PausePingController(
    private val enabledProvider: () -> Boolean,
    private val cooldownSecondsProvider: () -> Int
) {
    constructor(settings: AppSettings) : this(
        enabledProvider = { settings.pausePingEnabled },
        cooldownSecondsProvider = { settings.pausePingCooldownSeconds }
    )

    private val lastAlertAt = ConcurrentHashMap<String, Long>()

    fun shouldSuppress(
        packageName: String,
        critical: Boolean,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        if (!enabledProvider() || critical) {
            lastAlertAt[packageName] = now
            return false
        }

        val previous = lastAlertAt.put(packageName, now)
        val cooldownMs = cooldownSecondsProvider().coerceIn(1, 300) * 1_000L
        return previous != null && now - previous < cooldownMs
    }
}
