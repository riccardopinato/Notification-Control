package com.riccardopinato.notificationcontrol.processing

import com.riccardopinato.notificationcontrol.capture.CapturedNotification
import com.riccardopinato.notificationcontrol.data.AppSettings
import com.riccardopinato.notificationcontrol.domain.SuppressionPolicy
import com.riccardopinato.notificationcontrol.hardware.FlashCoordinator
import com.riccardopinato.notificationcontrol.luminous.LuminousAlertStyle
import com.riccardopinato.notificationcontrol.luminous.LuminousProfileResolver
import com.riccardopinato.notificationcontrol.ui.overlay.LuminousCircleOverlay
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

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

        fun cancelReservation() {
            val callback = context.onVisualAlertNotEmitted
            context.onVisualAlertEmitted = null
            context.onVisualAlertNotEmitted = null
            runCatching { callback?.invoke() }
        }

        if (!flashEnabled && !overlayEnabled) {
            cancelReservation()
            return
        }
        if (context.suppressLuminous && !context.critical) {
            cancelReservation()
            return
        }
        if (
            suppressionPolicy.evaluate(
                critical = context.critical,
                packageName = event.packageName
            ).suppressed
        ) {
            cancelReservation()
            return
        }

        val baseCycles = profile?.strobeCycles ?: settings.strobeCycles
        val baseSpeed = profile?.strobeSpeedMs ?: settings.strobeSpeedMs
        val cycles = if (context.critical) maxOf(8, baseCycles) else baseCycles
        val speed = if (context.critical) minOf(100L, baseSpeed) else baseSpeed

        val successCallback = context.onVisualAlertEmitted
        val failureCallback = context.onVisualAlertNotEmitted
        context.onVisualAlertEmitted = null
        context.onVisualAlertNotEmitted = null

        val terminal = AtomicBoolean(false)
        val pendingOutputs = AtomicInteger(
            (if (flashEnabled) 1 else 0) + (if (overlayEnabled) 1 else 0)
        )
        val onActualEmission = {
            if (terminal.compareAndSet(false, true)) {
                runCatching { successCallback?.invoke() }
            }
            Unit
        }
        val onNoEmission = {
            if (
                pendingOutputs.decrementAndGet() <= 0 &&
                terminal.compareAndSet(false, true)
            ) {
                runCatching { failureCallback?.invoke() }
            }
            Unit
        }

        if (flashEnabled) {
            flash.startStrobe(
                cycles = cycles,
                onMs = speed,
                offMs = speed,
                onFirstEmission = onActualEmission,
                onNoEmission = onNoEmission
            )
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
                ),
                onShown = onActualEmission,
                onNotShown = onNoEmission
            )
        }
    }

    companion object {
        private const val CRITICAL_COLOR = "#FF453A"
        private const val DEFAULT_DISPLAY_DURATION_MS = 15_000L
    }
}
