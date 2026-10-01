package com.riccardopinato.notificationcontrol.luminous

import android.graphics.Color
import com.riccardopinato.notificationcontrol.data.AppSettings
import com.riccardopinato.notificationcontrol.data.LuminousProfileDao
import com.riccardopinato.notificationcontrol.data.LuminousProfileEntity
import java.util.Locale

class LuminousProfileRepository(
    private val settings: AppSettings,
    private val dao: LuminousProfileDao
) {
    suspend fun create(
        name: String,
        packageName: String?,
        senderQuery: String?,
        colorHex: String,
        flashEnabled: Boolean,
        overlayEnabled: Boolean,
        strobeCycles: Int,
        strobeSpeedMs: Long,
        circleThickness: Float,
        circleGlow: Float,
        pulseSpeedMs: Long,
        displayDurationMs: Long
    ): Boolean {
        if (!settings.isPremium) return false

        val normalizedPackage = packageName?.trim()?.takeIf { it.isNotBlank() }
        val normalizedSender = senderQuery?.trim()?.takeIf { it.isNotBlank() }
        if (normalizedPackage == null && normalizedSender == null) return false
        if (!flashEnabled && !overlayEnabled) return false

        val normalizedColor = normalizeColor(colorHex) ?: return false

        dao.insert(
            LuminousProfileEntity(
                name = name.trim().ifBlank { "Luminous" },
                packageName = normalizedPackage,
                senderQuery = normalizedSender,
                colorHex = normalizedColor,
                flashEnabled = flashEnabled,
                overlayEnabled = overlayEnabled,
                strobeCycles = strobeCycles.coerceIn(1, 30),
                strobeSpeedMs = strobeSpeedMs.coerceIn(40L, 2_000L),
                circleThickness = circleThickness.coerceIn(8f, 60f),
                circleGlow = circleGlow.coerceIn(0f, 80f),
                pulseSpeedMs = pulseSpeedMs.coerceIn(250L, 3_000L),
                displayDurationMs = displayDurationMs.coerceIn(3_000L, 120_000L)
            )
        )
        return true
    }

    private fun normalizeColor(value: String): String? {
        val candidate = value.trim().uppercase(Locale.ROOT)
        return runCatching {
            Color.parseColor(candidate)
            candidate
        }.getOrNull()
    }
}
