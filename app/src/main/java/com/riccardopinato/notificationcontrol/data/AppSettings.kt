package com.riccardopinato.notificationcontrol.data

import android.content.Context
import androidx.core.content.edit
import com.riccardopinato.notificationcontrol.billing.EntitlementStore

data class QuietHoursBand(
    val startMinutes: Int,
    val endMinutes: Int
)

class AppSettings(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val entitlementStore by lazy { EntitlementStore(appContext) }

    var onboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING, false)
        set(value) = prefs.edit { putBoolean(KEY_ONBOARDING, value) }

    var monitoredPackages: Set<String>
        get() = prefs.getStringSet(KEY_MONITORED_PACKAGES, emptySet())?.toSet().orEmpty()
        set(value) = prefs.edit { putStringSet(KEY_MONITORED_PACKAGES, value.toSet()) }

    val isPremium: Boolean
        get() = entitlementStore.effectiveTier().isPremium

    var retentionDays: Int
        get() = prefs.getInt(KEY_RETENTION_DAYS, 7)
        set(value) = prefs.edit { putInt(KEY_RETENTION_DAYS, value) }

    var retentionDaysPerApp: Map<String, Int>
        get() = prefs.getStringSet(KEY_RETENTION_PER_APP, emptySet())
            .orEmpty()
            .mapNotNull { entry ->
                val separator = entry.lastIndexOf('=')
                if (separator <= 0 || separator >= entry.lastIndex) {
                    null
                } else {
                    val packageName = entry.substring(0, separator)
                    val days = entry.substring(separator + 1).toIntOrNull()
                    days?.let { packageName to normalizeRetentionDays(it) }
                }
            }
            .toMap()
        set(value) = prefs.edit {
            putStringSet(
                KEY_RETENTION_PER_APP,
                value
                    .filterKeys(String::isNotBlank)
                    .map { (packageName, days) ->
                        packageName + "=" + normalizeRetentionDays(days)
                    }
                    .toSet()
            )
        }

    var vaultMaxBytes: Long
        get() = prefs.getLong(KEY_VAULT_MAX_BYTES, 100L * 1024L * 1024L)
        set(value) = prefs.edit {
            putLong(KEY_VAULT_MAX_BYTES, value.coerceAtLeast(10L * 1024L * 1024L))
        }

    var flashEnabled: Boolean
        get() = prefs.getBoolean(KEY_FLASH_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_FLASH_ENABLED, value) }

    var overlayEnabled: Boolean
        get() = prefs.getBoolean(KEY_OVERLAY_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_OVERLAY_ENABLED, value) }

    var batteryGuardEnabled: Boolean
        get() = prefs.getBoolean(KEY_BATTERY_GUARD, true)
        set(value) = prefs.edit { putBoolean(KEY_BATTERY_GUARD, value) }

    var batteryGuardThreshold: Int
        get() = prefs.getInt(KEY_BATTERY_THRESHOLD, 15)
        set(value) = prefs.edit { putInt(KEY_BATTERY_THRESHOLD, value.coerceIn(5, 50)) }

    var quietHoursEnabled: Boolean
        get() = prefs.getBoolean(KEY_QUIET_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_QUIET_ENABLED, value) }

    var quietStartMinutes: Int
        get() = prefs.getInt(KEY_QUIET_START, 22 * 60)
        set(value) = prefs.edit { putInt(KEY_QUIET_START, value.coerceIn(0, 1439)) }

    var quietEndMinutes: Int
        get() = prefs.getInt(KEY_QUIET_END, 7 * 60)
        set(value) = prefs.edit { putInt(KEY_QUIET_END, value.coerceIn(0, 1439)) }

    var additionalQuietHours: List<QuietHoursBand>
        get() = prefs.getStringSet(KEY_QUIET_ADDITIONAL, emptySet())
            .orEmpty()
            .mapNotNull { encoded ->
                val parts = encoded.split(':')
                if (parts.size != 2) return@mapNotNull null
                val start = parts[0].toIntOrNull() ?: return@mapNotNull null
                val end = parts[1].toIntOrNull() ?: return@mapNotNull null
                QuietHoursBand(
                    start.coerceIn(0, 1439),
                    end.coerceIn(0, 1439)
                )
            }
            .distinct()
            .sortedWith(compareBy({ it.startMinutes }, { it.endMinutes }))
        set(value) = prefs.edit {
            putStringSet(
                KEY_QUIET_ADDITIONAL,
                value.map {
                    it.startMinutes.coerceIn(0, 1439).toString() +
                        ":" +
                        it.endMinutes.coerceIn(0, 1439)
                }.toSet()
            )
        }

    var quietHoursExceptionPackages: Set<String>
        get() = prefs.getStringSet(KEY_QUIET_EXCEPTIONS, emptySet())
            ?.toSet()
            .orEmpty()
        set(value) = prefs.edit {
            putStringSet(KEY_QUIET_EXCEPTIONS, value.filter(String::isNotBlank).toSet())
        }

    var screenOffOnly: Boolean
        get() = prefs.getBoolean(KEY_SCREEN_OFF_ONLY, true)
        set(value) = prefs.edit { putBoolean(KEY_SCREEN_OFF_ONLY, value) }

    var strobeSpeedMs: Long
        get() = prefs.getLong(KEY_STROBE_SPEED, 150L)
        set(value) = prefs.edit { putLong(KEY_STROBE_SPEED, value.coerceIn(20L, 5_000L)) }

    var strobeCycles: Int
        get() = prefs.getInt(KEY_STROBE_CYCLES, 5)
        set(value) = prefs.edit { putInt(KEY_STROBE_CYCLES, value.coerceIn(1, 30)) }

    var circleColorHex: String
        get() = prefs.getString(KEY_CIRCLE_COLOR, "#6750A4") ?: "#6750A4"
        set(value) = prefs.edit { putString(KEY_CIRCLE_COLOR, value) }

    var circleThickness: Float
        get() = prefs.getFloat(KEY_CIRCLE_THICKNESS, 24f)
        set(value) = prefs.edit { putFloat(KEY_CIRCLE_THICKNESS, value.coerceIn(8f, 60f)) }

    var circleGlow: Float
        get() = prefs.getFloat(KEY_CIRCLE_GLOW, 30f)
        set(value) = prefs.edit { putFloat(KEY_CIRCLE_GLOW, value.coerceIn(0f, 80f)) }

    var pulseSpeedMs: Long
        get() = prefs.getLong(KEY_PULSE_SPEED, 1_000L)
        set(value) = prefs.edit { putLong(KEY_PULSE_SPEED, value.coerceIn(250L, 3_000L)) }

    var vaultLockEnabled: Boolean
        get() = prefs.getBoolean(KEY_VAULT_LOCK_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_VAULT_LOCK_ENABLED, value) }

    var vaultLockTimeoutMinutes: Int
        get() = prefs.getInt(KEY_VAULT_LOCK_TIMEOUT, 5)
        set(value) = prefs.edit { putInt(KEY_VAULT_LOCK_TIMEOUT, value.coerceIn(1, 60)) }

    var sensitiveProtectionEnabled: Boolean
        get() = prefs.getBoolean(KEY_SENSITIVE_PROTECTION, true)
        set(value) = prefs.edit { putBoolean(KEY_SENSITIVE_PROTECTION, value) }

    var pausePingEnabled: Boolean
        get() = prefs.getBoolean(KEY_PAUSE_PING_ENABLED, true)
        set(value) = prefs.edit { putBoolean(KEY_PAUSE_PING_ENABLED, value) }

    var pausePingCooldownSeconds: Int
        get() = prefs.getInt(KEY_PAUSE_PING_COOLDOWN, 20)
        set(value) = prefs.edit { putInt(KEY_PAUSE_PING_COOLDOWN, value.coerceIn(1, 300)) }

    var pausePingPerAppCooldowns: Map<String, Int>
        get() = prefs.getStringSet(KEY_PAUSE_PING_PER_APP, emptySet())
            .orEmpty()
            .mapNotNull { entry ->
                val separator = entry.lastIndexOf('=')
                if (separator <= 0 || separator >= entry.lastIndex) {
                    null
                } else {
                    val packageName = entry.substring(0, separator)
                    val seconds = entry.substring(separator + 1).toIntOrNull()
                    seconds?.let { packageName to it.coerceIn(0, 300) }
                }
            }
            .toMap()
        set(value) = prefs.edit {
            putStringSet(
                KEY_PAUSE_PING_PER_APP,
                value.map { (packageName, seconds) ->
                    packageName + "=" + seconds.coerceIn(0, 300)
                }.toSet()
            )
        }

    var criticalBypassQuietHours: Boolean
        get() = prefs.getBoolean(KEY_CRITICAL_BYPASS_QUIET, true)
        set(value) = prefs.edit { putBoolean(KEY_CRITICAL_BYPASS_QUIET, value) }

    private fun normalizeRetentionDays(days: Int): Int =
        if (days == Int.MAX_VALUE) {
            Int.MAX_VALUE
        } else {
            days.coerceIn(1, 3650)
        }

    fun resetToDefaults() {
        prefs.edit { clear() }
    }

    companion object {
        private const val PREFS = "notification_control_settings"
        private const val KEY_ONBOARDING = "onboarding_completed"
        private const val KEY_MONITORED_PACKAGES = "monitored_packages"
        private const val KEY_RETENTION_DAYS = "retention_days"
        private const val KEY_RETENTION_PER_APP = "retention_days_per_app"
        private const val KEY_VAULT_MAX_BYTES = "vault_max_bytes"
        private const val KEY_BATTERY_GUARD = "battery_guard_enabled"
        private const val KEY_BATTERY_THRESHOLD = "battery_guard_threshold"
        private const val KEY_QUIET_ENABLED = "quiet_hours_enabled"
        private const val KEY_QUIET_START = "quiet_start_minutes"
        private const val KEY_QUIET_END = "quiet_end_minutes"
        private const val KEY_QUIET_ADDITIONAL = "quiet_additional_bands"
        private const val KEY_QUIET_EXCEPTIONS = "quiet_exception_packages"
        private const val KEY_FLASH_ENABLED = "flash_enabled"
        private const val KEY_OVERLAY_ENABLED = "overlay_enabled"
        private const val KEY_SCREEN_OFF_ONLY = "screen_off_only"
        private const val KEY_STROBE_SPEED = "strobe_speed_ms"
        private const val KEY_STROBE_CYCLES = "strobe_cycles"
        private const val KEY_CIRCLE_COLOR = "circle_color_hex"
        private const val KEY_CIRCLE_THICKNESS = "circle_thickness"
        private const val KEY_CIRCLE_GLOW = "circle_glow"
        private const val KEY_PULSE_SPEED = "pulse_speed_ms"
        private const val KEY_VAULT_LOCK_ENABLED = "vault_lock_enabled"
        private const val KEY_VAULT_LOCK_TIMEOUT = "vault_lock_timeout_minutes"
        private const val KEY_SENSITIVE_PROTECTION = "sensitive_protection_enabled"
        private const val KEY_PAUSE_PING_ENABLED = "pause_ping_enabled"
        private const val KEY_PAUSE_PING_COOLDOWN = "pause_ping_cooldown_seconds"
        private const val KEY_PAUSE_PING_PER_APP = "pause_ping_per_app_cooldowns"
        private const val KEY_CRITICAL_BYPASS_QUIET = "critical_bypass_quiet_hours"
    }
}
