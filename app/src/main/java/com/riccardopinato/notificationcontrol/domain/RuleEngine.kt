package com.riccardopinato.notificationcontrol.domain

import com.riccardopinato.notificationcontrol.capture.CapturedNotification
import com.riccardopinato.notificationcontrol.data.AutomationDao
import com.riccardopinato.notificationcontrol.data.RuleWithActions

data class RuleEvaluation(
    val forceFlash: Boolean = false,
    val forceOverlay: Boolean = false,
    val critical: Boolean = false,
    val followUpDelayMinutes: Int? = null
)

class RuleEngine(private val dao: AutomationDao) {
    suspend fun evaluate(event: CapturedNotification): RuleEvaluation {
        var result = RuleEvaluation()
        dao.enabledRules().forEach { rule ->
            if (!matches(rule, event)) return@forEach
            rule.actions.forEach { action ->
                result = when (action.actionType) {
                    RuleActionType.FLASH -> result.copy(forceFlash = true)
                    RuleActionType.OVERLAY -> result.copy(forceOverlay = true)
                    RuleActionType.CRITICAL -> result.copy(critical = true)
                    RuleActionType.FOLLOW_UP -> result.copy(
                        followUpDelayMinutes = action.actionValue?.toIntOrNull()?.coerceIn(1, 10_080) ?: 60
                    )
                    else -> result
                }
            }
        }
        return result
    }

    private fun matches(rule: RuleWithActions, event: CapturedNotification): Boolean {
        val conditions = buildList {
            rule.rule.packageName?.takeIf { it.isNotBlank() }?.let {
                add(event.packageName == it)
            }
            rule.rule.senderQuery?.takeIf { it.isNotBlank() }?.let { query ->
                val senderHaystack = buildString {
                    append(event.title.orEmpty())
                    event.messages.forEach { message ->
                        append(' ')
                        append(message.sender.orEmpty())
                    }
                }
                add(senderHaystack.contains(query, ignoreCase = true))
            }
            rule.rule.textQuery?.takeIf { it.isNotBlank() }?.let { query ->
                add(combinedText(event).contains(query, ignoreCase = true))
            }
        }
        if (conditions.isEmpty()) return false
        return if (rule.rule.matchMode == "ANY") conditions.any { it } else conditions.all { it }
    }

    private fun combinedText(event: CapturedNotification): String = buildString {
        append(event.title.orEmpty())
        append(' ')
        append(event.text.orEmpty())
        append(' ')
        append(event.bigText.orEmpty())
        event.messages.forEach {
            append(' ')
            append(it.text)
        }
    }
}
