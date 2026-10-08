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
        if (
            suppressionPolicy.evaluate(
                critical = context.critical,
                packageName = event.packageName
            ).suppressed
        ) return

        val baseCycles = profile?.strobeCycles ?: settings.strobeCycles
        val baseSpeed = profile?.strobeSpeedMs ?: settings.strobeSpeedMs
        val cycles = if (context.critical) maxOf(8, baseCycles) else baseCycles
        val speed = if (context.critical) minOf(100L, baseSpeed) else baseSpeed

        var emitted = false
        try {
            if (flashEnabled) {
                flash.startStrobe(cycles, speed, speed)
                emitted = true
            }

            if (overlayEnabled) {
                val color = if (context.critical) {
                    CRITICAL_COLOR
                } else {
                    profile?.colorHex ?: settings.circleColorHex
                }
                val thickness = profile?.circleThickness ?: settings.circleThickness
                val glow = profile?.circleGlow ?: settings.circleGlow
                val pulse = profile?.pulseSpeedMs ?: settings.pulseSpeedMs
                val duration = profile?.displayDurationMs ?: DEFAULT_DISPLAY_DURATION_MS

                overlay.show(
                    label = event.appLabel,
                    style = LuminousAlertStyle(
                        colorHex = color,
                        thickness = thickness,
                        glow = if (context.critical) maxOf(glow, 42f) else glow,
                        pulseSpeedMs = if (context.critical) minOf(pulse, 650L) else pulse,
                        displayDurationMs =
                            if (context.critical) maxOf(duration, 12_000L) else duration
                    )
                )
                emitted = true
            }
        } finally {
            if (emitted) {
                context.onVisualAlertEmitted?.let { callback ->
                    runCatching(callback)
                }
                context.onVisualAlertEmitted = null
            }
        }
    }

    companion object {
        private const val CRITICAL_COLOR = "#FF453A"
        private const val DEFAULT_DISPLAY_DURATION_MS = 15_000L
    }
}
