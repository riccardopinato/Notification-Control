package com.riccardopinato.notificationcontrol.processing

import com.riccardopinato.notificationcontrol.capture.CapturedNotification
import com.riccardopinato.notificationcontrol.domain.PausePingController

class PausePingConsumer(
    private val controller: PausePingController
) : NotificationEventConsumer {
    override suspend fun consume(event: CapturedNotification, context: ProcessingContext) {
        if (context.mode != ProcessingMode.POSTED) return
        val decision = controller.evaluateAndReserve(
            packageName = event.packageName,
            critical = context.critical
        )
        context.suppressLuminous = decision.suppress
        decision.reservation?.let { reservation ->
            context.onVisualAlertEmitted = {
                controller.confirmVisualAlert(reservation)
            }
            context.onVisualAlertNotEmitted = {
                controller.cancelVisualAlert(reservation)
            }
        }
    }
}
