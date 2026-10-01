package com.riccardopinato.notificationcontrol.processing

import com.riccardopinato.notificationcontrol.capture.CapturedNotification
import com.riccardopinato.notificationcontrol.domain.RuleEngine
import com.riccardopinato.notificationcontrol.domain.RuleRuntimeStateProvider

class RulesConsumer(
    private val ruleEngine: RuleEngine,
    private val runtimeStateProvider: RuleRuntimeStateProvider
) : NotificationEventConsumer {
    override suspend fun consume(event: CapturedNotification, context: ProcessingContext) {
        if (context.mode != ProcessingMode.POSTED) return
        val evaluation = ruleEngine.evaluate(event, runtimeStateProvider.current())
        context.forceFlash = evaluation.forceFlash
        context.forceOverlay = evaluation.forceOverlay
        context.critical = evaluation.critical
        context.followUpDelayMinutes = evaluation.followUpDelayMinutes
    }
}
