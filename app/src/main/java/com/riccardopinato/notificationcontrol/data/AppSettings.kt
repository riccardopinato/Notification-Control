package com.riccardopinato.notificationcontrol.data

import android.content.Context
import android.content.SharedPreferences

class AppSettings(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("notification_control_prefs", Context.MODE_PRIVATE)

    var isPremiumUnlocked: Boolean
        get() = prefs.getBoolean("pref_premium_unlocked", false)
        set(value) = prefs.edit().putBoolean("pref_premium_unlocked", value).apply()

    var onboardingCompleted: Boolean
        get() = prefs.getBoolean("pref_onboarding_completed", false)
        set(value) = prefs.edit().putBoolean("pref_onboarding_completed", value).apply()

    var flashEnabled: Boolean
        get() = prefs.getBoolean("flash_enabled", true)
        set(value) = prefs.edit().putBoolean("flash_enabled", value).apply()

    var overlayEnabled: Boolean
        get() = prefs.getBoolean("overlay_enabled", true)
        set(value) = prefs.edit().putBoolean("overlay_enabled", value).apply()

    var flashOnNotification: Boolean
        get() = prefs.getBoolean("flash_on_notification", true)
        set(value) = prefs.edit().putBoolean("flash_on_notification", value).apply()

    var strobeSpeedMs: Long
        get() = prefs.getLong("strobe_speed_ms", 150L)
        set(value) = prefs.edit().putLong("strobe_speed_ms", value).apply()

    var strobeCycles: Int
        get() = prefs.getInt("strobe_cycles", 5)
        set(value) = prefs.edit().putInt("strobe_cycles", value).apply()

    var batteryGuardEnabled: Boolean
        get() = prefs.getBoolean("battery_guard_enabled", true)
        set(value) = prefs.edit().putBoolean("battery_guard_enabled", value).apply()

    var batteryGuardThreshold: Int
        get() = prefs.getInt("battery_guard_threshold", 15)
        set(value) = prefs.edit().putInt("battery_guard_threshold", value).apply()

    var screenOffOnly: Boolean
        get() = prefs.getBoolean("screen_off_only", true)
        set(value) = prefs.edit().putBoolean("screen_off_only", value).apply()

    var circleColorHex: String
        get() = prefs.getString("circle_color_hex", "#38BDF8") ?: "#38BDF8"
        set(value) = prefs.edit().putString("circle_color_hex", value).apply()

    var circleThickness: Float
        get() = prefs.getFloat("pref_circle_thickness", 24f)
        set(value) = prefs.edit().putFloat("pref_circle_thickness", value).apply()

    var circleGlow: Float
        get() = prefs.getFloat("pref_circle_glow", 30f)
        set(value) = prefs.edit().putFloat("pref_circle_glow", value).apply()

    var pulseSpeed: Float
        get() = prefs.getFloat("pref_pulse_speed", 1000f)
        set(value) = prefs.edit().putFloat("pref_pulse_speed", value).apply()
}
