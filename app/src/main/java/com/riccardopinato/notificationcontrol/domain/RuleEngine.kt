package com.riccardopinato.notificationcontrol.domain

import com.riccardopinato.notificationcontrol.capture.CapturedNotification
import com.riccardopinato.notificationcontrol.data.AutomationDao

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
            if (!RuleMatcher.matches(rule, event)) return@forEach

            rule.actions.forEach { action ->
                result = when (action.actionType) {
                    RuleActionType.FLASH -> result.copy(forceFlash = true)
                    RuleActionType.OVERLAY -> result.copy(forceOverlay = true)
                    RuleActionType.CRITICAL -> result.copy(critical = true)
                    RuleActionType.FOLLOW_UP -> result.copy(
                        followUpDelayMinutes =
                            action.actionValue
                                ?.toIntOrNull()
                                ?.coerceIn(1, 10_080)
                                ?: 60
                    )
                    else -> result
                }
            }
        }

        return result
    }
}
