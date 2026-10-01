package com.riccardopinato.notificationcontrol.domain

import com.riccardopinato.notificationcontrol.capture.CapturedNotification
import com.riccardopinato.notificationcontrol.data.AutomationDao
import com.riccardopinato.notificationcontrol.data.RuleEntity

data class RuleEvaluation(
    val forceFlash: Boolean = false,
    val forceOverlay: Boolean = false,
    val critical: Boolean = false,
    val followUpDelayMinutes: Int? = null
)

class RuleEngine(
    private val dao: AutomationDao,
    private val premiumProvider: () -> Boolean = { true }
) {
    suspend fun evaluate(
        event: CapturedNotification,
        runtime: RuleRuntimeState
    ): RuleEvaluation {
        var result = RuleEvaluation()
        val premium = premiumProvider()
        val enabled = dao.enabledRules()
        val eligible = if (premium) {
            enabled
        } else {
            enabled.take(ProductLimits.FREE_RULES)
        }

        eligible.forEach { rule ->
            if (!premium && rule.rule.usesPremiumConditions()) return@forEach
            if (!RuleMatcher.matches(rule, event, runtime)) return@forEach

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

    private fun RuleEntity.usesPremiumConditions(): Boolean =
        timeStartMinutes != null ||
            timeEndMinutes != null ||
            screenState != RuleScreenState.ANY
}
