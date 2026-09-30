package com.riccardopinato.notificationcontrol.processing

import com.riccardopinato.notificationcontrol.capture.CapturedNotification
import com.riccardopinato.notificationcontrol.domain.RuleEngine

class RulesConsumer(
    private val ruleEngine: RuleEngine
) : NotificationEventConsumer {
    override suspend fun consume(event: CapturedNotification, context: ProcessingContext) {
        if (context.mode != ProcessingMode.POSTED) return
        val evaluation = ruleEngine.evaluate(event)
        context.forceFlash = evaluation.forceFlash
        context.forceOverlay = evaluation.forceOverlay
        context.critical = evaluation.critical
        context.followUpDelayMinutes = evaluation.followUpDelayMinutes
    }
}
