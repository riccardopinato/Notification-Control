package com.riccardopinato.notificationcontrol.domain

import com.riccardopinato.notificationcontrol.data.AppSettings
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

data class VisualAlertReservation internal constructor(
    internal val id: Long,
    internal val packageName: String,
    internal val timestamp: Long,
    internal val budgetReserved: Boolean
)

data class PausePingDecision(
    val suppress: Boolean,
    val reservation: VisualAlertReservation? = null
)

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
            if (normalized.isNotEmpty()) put(packageName, ArrayDeque(normalized))
        }
    }
    private val packageLocks = ConcurrentHashMap<String, Any>()
    private val pendingReservations = ConcurrentHashMap<Long, VisualAlertReservation>()
    private val nextReservationId = AtomicLong(0L)

    fun evaluateAndReserve(
        packageName: String,
        critical: Boolean,
        now: Long = System.currentTimeMillis()
    ): PausePingDecision {
        if (!enabledProvider() || critical) return PausePingDecision(false)

        val premium = premiumProvider()
        val configuredSeconds = if (premium) {
            perAppCooldownProvider()[packageName] ?: cooldownSecondsProvider()
        } else {
            cooldownSecondsProvider()
        }

        if (configuredSeconds <= 0) {
            lastAlertAt.remove(packageName)
            if (allowedAlertTimes.remove(packageName) != null) persistBudgetHistory()
            clearPendingForPackage(packageName)
            return PausePingDecision(false)
        }

        val lock = packageLocks.computeIfAbsent(packageName) { Any() }
        synchronized(lock) {
            prunePendingReservations(packageName, now)

            val previous = lastAlertAt[packageName]
            val confirmed = when {
                previous == null -> null
                now < previous -> {
                    lastAlertAt.remove(packageName, previous)
                    null
                }
                else -> previous
            }
            val pendingLatest = pendingReservations.values
                .asSequence()
                .filter { it.packageName == packageName }
                .maxOfOrNull { it.timestamp }
            val cooldownAnchor = listOfNotNull(confirmed, pendingLatest).maxOrNull()
            val cooldownMs =
                configuredSeconds.coerceIn(1, MAX_COOLDOWN_SECONDS) * 1_000L
            if (
                cooldownAnchor != null &&
                now - cooldownAnchor in 0 until cooldownMs
            ) {
                return PausePingDecision(true)
            }

            val budgetReserved = premium && budgetEnabledProvider()
            if (budgetReserved) {
                val history = allowedAlertTimes.computeIfAbsent(packageName) {
                    ArrayDeque()
                }
                val changed = synchronized(history) {
                    pruneHistory(history, now, budgetWindowMs())
                }
                if (changed) persistBudgetHistory()

                val maxAlerts =
                    budgetMaxAlertsProvider().coerceIn(1, MAX_BUDGET_ALERTS)
                val pendingBudgetCount = pendingReservations.values.count {
                    it.packageName == packageName && it.budgetReserved
                }
                if (history.size + pendingBudgetCount >= maxAlerts) {
                    return PausePingDecision(true)
                }
            }

            val reservation = VisualAlertReservation(
                id = nextReservationId.incrementAndGet(),
                packageName = packageName,
                timestamp = now,
                budgetReserved = budgetReserved
            )
            pendingReservations[reservation.id] = reservation
            return PausePingDecision(false, reservation)
        }
    }

    fun confirmVisualAlert(reservation: VisualAlertReservation) {
        val lock = packageLocks.computeIfAbsent(reservation.packageName) { Any() }
        synchronized(lock) {
            if (pendingReservations.remove(reservation.id) == null) return

            lastAlertAt.merge(
                reservation.packageName,
                reservation.timestamp,
                ::maxOf
            )
            if (reservation.budgetReserved) {
                val history = allowedAlertTimes.computeIfAbsent(
                    reservation.packageName
                ) { ArrayDeque() }
                synchronized(history) {
                    pruneHistory(history, reservation.timestamp, budgetWindowMs())
                    history.addLast(reservation.timestamp)
                    while (history.size > MAX_BUDGET_ALERTS) history.removeFirst()
                }
                persistBudgetHistory()
            }
        }
    }

    fun cancelVisualAlert(reservation: VisualAlertReservation) {
        val lock = packageLocks.computeIfAbsent(reservation.packageName) { Any() }
        synchronized(lock) {
            pendingReservations.remove(reservation.id)
        }
    }

    private fun clearPendingForPackage(packageName: String) {
        pendingReservations.entries.removeIf {
            it.value.packageName == packageName
        }
    }

    private fun prunePendingReservations(packageName: String, now: Long) {
        val cutoff = now - RESERVATION_TIMEOUT_MS
        pendingReservations.entries.removeIf { entry ->
            val reservation = entry.value
            reservation.packageName == packageName &&
                (reservation.timestamp < cutoff || reservation.timestamp > now)
        }
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
                if (timestamps.isNotEmpty()) put(packageName, timestamps)
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
        private const val RESERVATION_TIMEOUT_MS = 10_000L
    }
}
