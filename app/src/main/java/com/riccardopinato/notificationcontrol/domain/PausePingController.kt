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
    private val budgetWindowMinutesProvider: () -> Int = { DEFAULT_BUDGET_WINDOW_MINUTES },
    private val budgetHistoryProvider: () -> Map<String, List<Long>> = { emptyMap() },
    private val budgetHistoryConsumer: (Map<String, List<Long>>) -> Unit = {}
) {
    constructor(settings: AppSettings) : this(
        enabledProvider = { settings.pausePingEnabled },
        cooldownSecondsProvider = { settings.pausePingCooldownSeconds },
        premiumProvider = { settings.isPremium },
        perAppCooldownProvider = { settings.pausePingPerAppCooldowns },
        budgetEnabledProvider = { settings.pausePingBudgetEnabled },
        budgetMaxAlertsProvider = { settings.pausePingBudgetMaxAlerts },
        budgetWindowMinutesProvider = { settings.pausePingBudgetWindowMinutes },
        budgetHistoryProvider = { settings.pausePingBudgetHistory },
        budgetHistoryConsumer = { settings.pausePingBudgetHistory = it }
    )

    private val lastAlertAt = ConcurrentHashMap<String, Long>()
    private val allowedAlertTimes = ConcurrentHashMap<String, ArrayDeque<Long>>().apply {
        budgetHistoryProvider().forEach { (packageName, timestamps) ->
            if (packageName.isBlank()) return@forEach
            val normalized = timestamps
                .filter { it > 0L }
                .distinct()
                .sorted()
                .takeLast(MAX_BUDGET_ALERTS)
            if (normalized.isNotEmpty()) {
                put(packageName, ArrayDeque(normalized))
            }
        }
    }

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
            if (allowedAlertTimes.remove(packageName) != null) {
                persistBudgetHistory()
            }
            return false
        }

        val previous = lastAlertAt.put(packageName, now)
        val cooldownMs = configuredSeconds.coerceIn(1, MAX_COOLDOWN_SECONDS) * 1_000L
        if (previous != null && now >= previous && now - previous < cooldownMs) {
            return true
        }

        if (!premium || !budgetEnabledProvider()) return false

        val maxAlerts = budgetMaxAlertsProvider().coerceIn(1, MAX_BUDGET_ALERTS)
        val windowMs = budgetWindowMs()
        val history = allowedAlertTimes.computeIfAbsent(packageName) { ArrayDeque() }
        val changed: Boolean
        val suppress: Boolean

        synchronized(history) {
            changed = pruneHistory(history, now, windowMs)
            suppress = history.size >= maxAlerts
        }
        if (changed) persistBudgetHistory()
        return suppress
    }

    fun recordVisualAlert(
        packageName: String,
        critical: Boolean,
        now: Long = System.currentTimeMillis()
    ) {
        if (critical || !enabledProvider() || !premiumProvider() || !budgetEnabledProvider()) {
            return
        }

        val configuredSeconds =
            perAppCooldownProvider()[packageName] ?: cooldownSecondsProvider()
        if (configuredSeconds <= 0) return

        val history = allowedAlertTimes.computeIfAbsent(packageName) { ArrayDeque() }
        val windowMs = budgetWindowMs()
        synchronized(history) {
            pruneHistory(history, now, windowMs)
            history.addLast(now)
            while (history.size > MAX_BUDGET_ALERTS) {
                history.removeFirst()
            }
        }
        persistBudgetHistory()
    }

    private fun budgetWindowMs(): Long =
        budgetWindowMinutesProvider()
            .coerceIn(1, MAX_BUDGET_WINDOW_MINUTES)
            .toLong() * 60_000L

    private fun pruneHistory(
        history: ArrayDeque<Long>,
        now: Long,
        windowMs: Long
    ): Boolean {
        var changed = false
        val cutoff = now - windowMs
        while (history.isNotEmpty() && history.first < cutoff) {
            history.removeFirst()
            changed = true
        }
        // Defensive handling for manual/system clock rollback.
        while (history.isNotEmpty() && history.last > now) {
            history.removeLast()
            changed = true
        }
        return changed
    }

    private fun persistBudgetHistory() {
        val snapshot = buildMap<String, List<Long>> {
            allowedAlertTimes.forEach { (packageName, history) ->
                val timestamps = synchronized(history) { history.toList() }
                if (timestamps.isNotEmpty()) {
                    put(packageName, timestamps)
                }
            }
        }
        budgetHistoryConsumer(snapshot)
    }

    companion object {
        const val DEFAULT_BUDGET_MAX_ALERTS = 3
        const val DEFAULT_BUDGET_WINDOW_MINUTES = 30
        const val MAX_BUDGET_ALERTS = 10
        const val MAX_BUDGET_WINDOW_MINUTES = 120
        private const val MAX_COOLDOWN_SECONDS = 300
    }
}
