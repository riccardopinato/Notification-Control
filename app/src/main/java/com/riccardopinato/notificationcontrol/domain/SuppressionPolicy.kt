package com.riccardopinato.notificationcontrol.domain

import android.content.Context
import android.os.PowerManager
import com.riccardopinato.notificationcontrol.data.AppSettings
import com.riccardopinato.notificationcontrol.hardware.DevicePostureMonitor

data class SuppressionDecision(
    val suppressed: Boolean,
    val reason: String? = null
)

class SuppressionPolicy(
    private val context: Context,
    private val settings: AppSettings,
    private val postureMonitor: DevicePostureMonitor
) {
    fun evaluate(critical: Boolean = false): SuppressionDecision {
        if (postureMonitor.isCoveredOrFaceDown()) {
            return SuppressionDecision(true, "covered_or_face_down")
        }

        val quiet = QuietHoursPolicy.isActive(
            settings.quietHoursEnabled,
            settings.quietStartMinutes,
            settings.quietEndMinutes,
            QuietHoursPolicy.nowMinutes()
        )
        val mayBypassQuiet =
            critical && settings.isPremium && settings.criticalBypassQuietHours
        if (quiet && !mayBypassQuiet) {
            return SuppressionDecision(true, "quiet_hours")
        }

        if (
            BatteryPolicy.shouldSuppress(
                context,
                settings.batteryGuardEnabled,
                settings.batteryGuardThreshold
            )
        ) {
            return SuppressionDecision(true, "battery_guard")
        }

        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (settings.screenOffOnly && power?.isInteractive == true) {
            return SuppressionDecision(true, "screen_interactive")
        }

        return SuppressionDecision(false)
    }
}
