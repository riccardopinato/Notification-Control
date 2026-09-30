package com.riccardopinato.notificationcontrol.processing

import com.riccardopinato.notificationcontrol.capture.CapturedNotification
import com.riccardopinato.notificationcontrol.data.AppSettings
import com.riccardopinato.notificationcontrol.domain.SuppressionPolicy
import com.riccardopinato.notificationcontrol.hardware.FlashCoordinator
import com.riccardopinato.notificationcontrol.ui.overlay.LuminousCircleOverlay

class LuminousConsumer(
    private val settings: AppSettings,
    private val suppressionPolicy: SuppressionPolicy,
    private val flash: FlashCoordinator,
    private val overlay: LuminousCircleOverlay
) : NotificationEventConsumer {
    override suspend fun consume(event: CapturedNotification, context: ProcessingContext) {
        if (context.mode != ProcessingMode.POSTED) return

        val flashEnabled = settings.flashEnabled || context.forceFlash
        val overlayEnabled = settings.overlayEnabled || context.forceOverlay
        if (!flashEnabled && !overlayEnabled) return
        if (context.suppressLuminous && !context.critical) return
        if (suppressionPolicy.evaluate(context.critical).suppressed) return

        if (flashEnabled) {
            val cycles = if (context.critical) maxOf(8, settings.strobeCycles) else settings.strobeCycles
            val speed = if (context.critical) minOf(100L, settings.strobeSpeedMs) else settings.strobeSpeedMs
            flash.startStrobe(cycles, speed, speed)
        }
        if (overlayEnabled) overlay.show(event.appLabel)
    }
}
