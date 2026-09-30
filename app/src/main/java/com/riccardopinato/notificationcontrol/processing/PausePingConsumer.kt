package com.riccardopinato.notificationcontrol.processing

import com.riccardopinato.notificationcontrol.capture.CapturedNotification
import com.riccardopinato.notificationcontrol.domain.PausePingController

class PausePingConsumer(
    private val controller: PausePingController
) : NotificationEventConsumer {
    override suspend fun consume(event: CapturedNotification, context: ProcessingContext) {
        if (context.mode != ProcessingMode.POSTED) return
        context.suppressLuminous = controller.shouldSuppress(
            packageName = event.packageName,
            critical = context.critical
        )
    }
}
