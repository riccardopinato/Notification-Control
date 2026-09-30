package com.riccardopinato.notificationcontrol.processing

import com.riccardopinato.notificationcontrol.capture.CapturedNotification
import com.riccardopinato.notificationcontrol.domain.CriticalMatcher

class CriticalConsumer(
    private val matcher: CriticalMatcher
) : NotificationEventConsumer {
    override suspend fun consume(event: CapturedNotification, context: ProcessingContext) {
        if (context.mode != ProcessingMode.POSTED) return
        context.critical = context.critical || matcher.isCritical(event)
    }
}
