package com.riccardopinato.notificationcontrol.processing

import com.riccardopinato.notificationcontrol.automation.AutomationRepository
import com.riccardopinato.notificationcontrol.capture.CapturedNotification
import com.riccardopinato.notificationcontrol.domain.CriticalMatcher

class CriticalConsumer(
    private val matcher: CriticalMatcher,
    private val automationRepository: AutomationRepository
) : NotificationEventConsumer {
    override suspend fun consume(
        event: CapturedNotification,
        context: ProcessingContext
    ) {
        if (context.mode != ProcessingMode.POSTED) return
        context.critical = context.critical || matcher.isCritical(event)
        if (context.critical) {
            automationRepository.registerCriticalAlert(event)
        }
    }
}
