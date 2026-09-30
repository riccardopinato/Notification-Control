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
    override suspend fun consume(event: CapturedNotification, mode: ProcessingMode) {
        if (mode != ProcessingMode.POSTED) return
        if (!settings.flashEnabled && !settings.overlayEnabled) return
        if (suppressionPolicy.evaluate().suppressed) return

        if (settings.flashEnabled) {
            flash.startStrobe(
                settings.strobeCycles,
                settings.strobeSpeedMs,
                settings.strobeSpeedMs
            )
        }
        if (settings.overlayEnabled) overlay.show(event.appLabel)
    }
}
