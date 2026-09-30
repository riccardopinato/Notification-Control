package com.riccardopinato.notificationcontrol.processing

import com.riccardopinato.notificationcontrol.capture.CapturedNotification
import com.riccardopinato.notificationcontrol.data.AppSettings
import com.riccardopinato.notificationcontrol.domain.SuppressionPolicy
import com.riccardopinato.notificationcontrol.hardware.FlashCoordinator
import com.riccardopinato.notificationcontrol.luminous.LuminousAlertStyle
import com.riccardopinato.notificationcontrol.luminous.LuminousProfileResolver
import com.riccardopinato.notificationcontrol.ui.overlay.LuminousCircleOverlay

class LuminousConsumer(
    private val settings: AppSettings,
    private val suppressionPolicy: SuppressionPolicy,
    private val flash: FlashCoordinator,
    private val overlay: LuminousCircleOverlay,
    private val profileResolver: LuminousProfileResolver
) : NotificationEventConsumer {
    override suspend fun consume(
        event: CapturedNotification,
        context: ProcessingContext
    ) {
        if (context.mode != ProcessingMode.POSTED) return

        val profile = profileResolver.resolve(event)
        val flashEnabled =
            context.forceFlash || (profile?.flashEnabled ?: settings.flashEnabled)
        val overlayEnabled =
            context.forceOverlay || (profile?.overlayEnabled ?: settings.overlayEnabled)

        if (!flashEnabled && !overlayEnabled) return
        if (context.suppressLuminous && !context.critical) return
        if (suppressionPolicy.evaluate(context.critical).suppressed) return

        val baseCycles = profile?.strobeCycles ?: settings.strobeCycles
        val baseSpeed = profile?.strobeSpeedMs ?: settings.strobeSpeedMs
        val cycles = if (context.critical) maxOf(8, baseCycles) else baseCycles
        val speed = if (context.critical) minOf(100L, baseSpeed) else baseSpeed

        if (flashEnabled) {
            flash.startStrobe(cycles, speed, speed)
        }

        if (overlayEnabled) {
            val color = if (context.critical) {
                CRITICAL_COLOR
            } else {
                profile?.colorHex ?: settings.circleColorHex
            }
            overlay.show(
                label = event.appLabel,
                style = LuminousAlertStyle(
                    colorHex = color,
                    thickness = settings.circleThickness,
                    glow = if (context.critical) {
                        maxOf(settings.circleGlow, 42f)
                    } else {
                        settings.circleGlow
                    },
                    pulseSpeedMs = if (context.critical) {
                        minOf(settings.pulseSpeedMs, 650L)
                    } else {
                        settings.pulseSpeedMs
                    }
                )
            )
        }
    }

    companion object {
        private const val CRITICAL_COLOR = "#FF453A"
    }
}
