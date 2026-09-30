package com.riccardopinato.notificationcontrol.automation

import android.content.Context
import com.riccardopinato.notificationcontrol.capture.CapturedNotification
import com.riccardopinato.notificationcontrol.capture.NotificationFingerprint
import com.riccardopinato.notificationcontrol.data.AppSettings
import com.riccardopinato.notificationcontrol.data.AutomationDao
import com.riccardopinato.notificationcontrol.data.CriticalAlertEntity
import com.riccardopinato.notificationcontrol.data.CriticalPatternEntity
import com.riccardopinato.notificationcontrol.data.FollowUpEntity
import com.riccardopinato.notificationcontrol.data.NotificationEntity
import com.riccardopinato.notificationcontrol.data.PickupCodeEntity
import com.riccardopinato.notificationcontrol.data.RuleEntity
import com.riccardopinato.notificationcontrol.domain.CriticalPatternType
import com.riccardopinato.notificationcontrol.domain.PickupCodeCandidate
import com.riccardopinato.notificationcontrol.domain.ProductLimits

class AutomationRepository(
    private val context: Context,
    private val settings: AppSettings,
    private val dao: AutomationDao
) {
    suspend fun createRule(
        name: String,
        packageName: String?,
        senderQuery: String?,
        textQuery: String?,
        matchMode: String,
        actions: List<Pair<String, String?>>
    ): Boolean {
        if (actions.isEmpty()) return false
        if (!settings.isPremium && dao.ruleCount() >= ProductLimits.FREE_RULES) return false
        val rule = RuleEntity(
            name = name.trim().ifBlank { "Rule" },
            packageName = packageName?.takeIf { it.isNotBlank() },
            senderQuery = senderQuery?.trim()?.takeIf { it.isNotBlank() },
            textQuery = textQuery?.trim()?.takeIf { it.isNotBlank() },
            matchMode = if (matchMode == "ANY") "ANY" else "ALL"
        )
        dao.createRule(rule, actions)
        return true
    }

    suspend fun updateRule(
        id: Long,
        name: String,
        packageName: String?,
        senderQuery: String?,
        textQuery: String?,
        matchMode: String,
        actions: List<Pair<String, String?>>
    ): Boolean {
        if (id <= 0L || actions.isEmpty()) return false
        val current = dao.ruleById(id) ?: return false
        dao.replaceRule(
            current.copy(
                name = name.trim().ifBlank { "Rule" },
                packageName = packageName?.trim()?.takeIf { it.isNotBlank() },
                senderQuery = senderQuery?.trim()?.takeIf { it.isNotBlank() },
                textQuery = textQuery?.trim()?.takeIf { it.isNotBlank() },
                matchMode = if (matchMode == "ANY") "ANY" else "ALL"
            ),
            actions
        )
        return true
    }

    suspend fun registerCriticalAlert(event: CapturedNotification): Long? {
        if (!settings.isPremium) return null
        val now = System.currentTimeMillis()
        val eventKey = NotificationFingerprint.sha256(
            event.sbnKey + "\u0000" + event.postedAt
        )
        val entity = CriticalAlertEntity(
            eventKey = eventKey,
            sourcePackage = event.packageName,
            sourceLabel = event.appLabel,
            title = event.title?.takeIf { it.isNotBlank() },
            createdAt = now,
            updatedAt = now,
            status = "ACTIVE",
            escalationStep = 0,
            nextAt = now + 2L * 60L * 1000L
        )
        val id = dao.insertCriticalAlert(entity)
        if (id == -1L) return null
        CriticalAlertScheduler.schedule(context, id, entity.nextAt)
        return id
    }

    suspend fun handleCriticalAlert(id: Long) {
        dao.handleCriticalAlert(id, System.currentTimeMillis())
        CriticalAlertScheduler.cancel(context, id)
    }

    suspend fun addCriticalPattern(type: String, value: String): Boolean {
        val normalized = value.trim()
        if (normalized.isBlank()) return false
        if (!settings.isPremium) {
            val limit = when (type) {
                CriticalPatternType.APP -> ProductLimits.FREE_CRITICAL_APPS
                CriticalPatternType.SENDER -> ProductLimits.FREE_CRITICAL_CONTACTS
                CriticalPatternType.KEYWORD -> ProductLimits.FREE_CRITICAL_WORDS
                else -> 0
            }
            if (limit <= 0 || dao.criticalPatternCount(type) >= limit) return false
        }
        return dao.insertCriticalPattern(
            CriticalPatternEntity(type = type, value = normalized)
        ) != -1L
    }

    suspend fun createFollowUpFromEvent(
        event: CapturedNotification,
        delayMinutes: Int,
        repeatMinutes: Int? = null
    ): Long? {
        if (!canCreateFollowUp()) return null
        val now = System.currentTimeMillis()
        val entity = FollowUpEntity(
            notificationKey = event.sbnKey,
            sourcePackage = event.packageName,
            sourceLabel = event.appLabel,
            title = event.title?.takeIf { it.isNotBlank() } ?: event.appLabel,
            body = event.bigText?.takeIf { it.isNotBlank() } ?: event.text,
            dueAt = now + delayMinutes.coerceIn(1, 10_080) * 60_000L,
            repeatMinutes = repeatMinutes
        )
        val id = dao.insertFollowUp(entity)
        FollowUpScheduler.schedule(context, id, entity.dueAt)
        return id
    }

    suspend fun createFollowUpFromNotification(
        notification: NotificationEntity,
        delayMinutes: Int
    ): Long? {
        if (!canCreateFollowUp()) return null
        val now = System.currentTimeMillis()
        val entity = FollowUpEntity(
            notificationKey = notification.sbnKey,
            sourcePackage = notification.packageName,
            sourceLabel = notification.appLabel,
            title = notification.title?.takeIf { it.isNotBlank() } ?: notification.appLabel,
            body = notification.bigText?.takeIf { it.isNotBlank() } ?: notification.text,
            dueAt = now + delayMinutes.coerceIn(1, 10_080) * 60_000L
        )
        val id = dao.insertFollowUp(entity)
        FollowUpScheduler.schedule(context, id, entity.dueAt)
        return id
    }

    suspend fun completeFollowUp(id: Long) {
        dao.completeFollowUp(id, System.currentTimeMillis())
        FollowUpScheduler.cancel(context, id)
    }

    suspend fun snoozeFollowUp(id: Long, delayMinutes: Int) {
        val dueAt = System.currentTimeMillis() + delayMinutes.coerceIn(1, 10_080) * 60_000L
        dao.snoozeFollowUp(id, dueAt, System.currentTimeMillis())
        FollowUpScheduler.schedule(context, id, dueAt)
    }

    suspend fun scheduleFollowUp(
        id: Long,
        dueAt: Long,
        repeatMinutes: Int?
    ): Boolean {
        if (!settings.isPremium) return false
        val safeDueAt = dueAt.coerceAtLeast(System.currentTimeMillis() + 60_000L)
        val safeRepeat = repeatMinutes
            ?.takeIf { it > 0 }
            ?.coerceIn(15, 10_080)
        dao.updateFollowUpSchedule(
            id = id,
            dueAt = safeDueAt,
            repeatMinutes = safeRepeat,
            now = System.currentTimeMillis()
        )
        FollowUpScheduler.schedule(context, id, safeDueAt)
        return true
    }

    suspend fun storePickupCode(
        event: CapturedNotification,
        candidate: PickupCodeCandidate
    ): Boolean {
        if (!settings.isPremium) return false
        val now = System.currentTimeMillis()
        val inserted = dao.insertPickupCode(
            PickupCodeEntity(
                code = candidate.code,
                sourcePackage = event.packageName,
                sourceLabel = event.appLabel,
                notificationKey = event.sbnKey,
                contextText = candidate.context,
                createdAt = now,
                expiresAt = now + 24L * 60L * 60L * 1000L
            )
        )
        return inserted != -1L
    }

    suspend fun cleanupPickupCodes() {
        dao.cleanupPickupCodes(System.currentTimeMillis())
    }

    private suspend fun canCreateFollowUp(): Boolean =
        settings.isPremium || dao.activeFollowUpCount() < ProductLimits.FREE_ACTIVE_FOLLOW_UPS
}
