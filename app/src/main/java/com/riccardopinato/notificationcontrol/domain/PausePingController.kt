package com.riccardopinato.notificationcontrol.domain

import com.riccardopinato.notificationcontrol.data.AppSettings
import java.util.concurrent.ConcurrentHashMap

class PausePingController(
    private val enabledProvider: () -> Boolean,
    private val cooldownSecondsProvider: () -> Int,
    private val premiumProvider: () -> Boolean = { false },
    private val perAppCooldownProvider: () -> Map<String, Int> = { emptyMap() }
) {
    constructor(settings: AppSettings) : this(
        enabledProvider = { settings.pausePingEnabled },
        cooldownSecondsProvider = { settings.pausePingCooldownSeconds },
        premiumProvider = { settings.isPremium },
        perAppCooldownProvider = { settings.pausePingPerAppCooldowns }
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

        val configuredSeconds = if (premiumProvider()) {
            perAppCooldownProvider()[packageName]
                ?: cooldownSecondsProvider()
        } else {
            cooldownSecondsProvider()
        }

        if (configuredSeconds <= 0) {
            lastAlertAt[packageName] = now
            return false
        }

        val previous = lastAlertAt.put(packageName, now)
        val cooldownMs = configuredSeconds.coerceIn(1, 300) * 1_000L
        return previous != null && now - previous < cooldownMs
    }
}
