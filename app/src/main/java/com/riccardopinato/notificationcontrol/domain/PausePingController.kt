package com.riccardopinato.notificationcontrol.domain

import com.riccardopinato.notificationcontrol.data.AppSettings
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

class PausePingController(
    private val enabledProvider: () -> Boolean,
    private val cooldownSecondsProvider: () -> Int,
    private val premiumProvider: () -> Boolean = { false },
    private val perAppCooldownProvider: () -> Map<String, Int> = { emptyMap() },
    private val budgetEnabledProvider: () -> Boolean = { false },
    private val budgetMaxAlertsProvider: () -> Int = { DEFAULT_BUDGET_MAX_ALERTS },
    private val budgetWindowMinutesProvider: () -> Int = { DEFAULT_BUDGET_WINDOW_MINUTES }
) {
    constructor(settings: AppSettings) : this(
        enabledProvider = { settings.pausePingEnabled },
        cooldownSecondsProvider = { settings.pausePingCooldownSeconds },
        premiumProvider = { settings.isPremium },
        perAppCooldownProvider = { settings.pausePingPerAppCooldowns },
        budgetEnabledProvider = { settings.pausePingBudgetEnabled },
        budgetMaxAlertsProvider = { settings.pausePingBudgetMaxAlerts },
        budgetWindowMinutesProvider = { settings.pausePingBudgetWindowMinutes }
    )

    private val lastAlertAt = ConcurrentHashMap<String, Long>()
    private val allowedAlertTimes = ConcurrentHashMap<String, ArrayDeque<Long>>()

    fun shouldSuppress(
        packageName: String,
        critical: Boolean,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        if (!enabledProvider()) return false
        if (critical) return false

        val premium = premiumProvider()
        val configuredSeconds = if (premium) {
            perAppCooldownProvider()[packageName]
                ?: cooldownSecondsProvider()
        } else {
            cooldownSecondsProvider()
        }

        // A Premium per-app value of 0 is an explicit Pausa Ping exemption.
        if (configuredSeconds <= 0) {
            lastAlertAt.remove(packageName)
            allowedAlertTimes.remove(packageName)
            return false
        }

        val previous = lastAlertAt.put(packageName, now)
        val cooldownMs = configuredSeconds.coerceIn(1, MAX_COOLDOWN_SECONDS) * 1_000L
        if (previous != null && now >= previous && now - previous < cooldownMs) {
            return true
        }

        if (!premium || !budgetEnabledProvider()) return false

        val maxAlerts = budgetMaxAlertsProvider().coerceIn(1, MAX_BUDGET_ALERTS)
        val windowMs =
            budgetWindowMinutesProvider()
                .coerceIn(1, MAX_BUDGET_WINDOW_MINUTES)
                .toLong() * 60_000L
        val cutoff = now - windowMs
        val history = allowedAlertTimes.computeIfAbsent(packageName) { ArrayDeque() }

        synchronized(history) {
            while (history.isNotEmpty() && history.first < cutoff) {
                history.removeFirst()
            }
            // Defensive handling for manual/system clock rollback.
            while (history.isNotEmpty() && history.last > now) {
                history.removeLast()
            }
            if (history.size >= maxAlerts) return true
            history.addLast(now)
        }
        return false
    }

    companion object {
        const val DEFAULT_BUDGET_MAX_ALERTS = 3
        const val DEFAULT_BUDGET_WINDOW_MINUTES = 30
        const val MAX_BUDGET_ALERTS = 10
        const val MAX_BUDGET_WINDOW_MINUTES = 120
        private const val MAX_COOLDOWN_SECONDS = 300
    }
}
