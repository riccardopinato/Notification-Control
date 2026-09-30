package com.riccardopinato.notificationcontrol.luminous

data class LuminousAlertStyle(
    val colorHex: String,
    val thickness: Float,
    val glow: Float,
    val pulseSpeedMs: Long,
    val displayDurationMs: Long
)
