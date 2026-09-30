package com.riccardopinato.notificationcontrol.processing

import com.riccardopinato.notificationcontrol.automation.AutomationRepository
import com.riccardopinato.notificationcontrol.capture.CapturedNotification

class FollowUpConsumer(
    private val repository: AutomationRepository
) : NotificationEventConsumer {
    override suspend fun consume(event: CapturedNotification, context: ProcessingContext) {
        if (context.mode != ProcessingMode.POSTED) return
        val delay = context.followUpDelayMinutes ?: return
        repository.createFollowUpFromEvent(event, delay)
    }
}
