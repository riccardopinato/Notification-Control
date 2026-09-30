package com.riccardopinato.notificationcontrol.core.retention

import android.content.Context

class RetentionPolicyStore(context: Context) {
    private val prefs = context.getSharedPreferences("notification_control_prefs", Context.MODE_PRIVATE)

    var premiumRetentionDays: Int
        get() = prefs.getInt(KEY_RETENTION_DAYS, 7).takeIf { it in ALLOWED_DAYS } ?: 7
        set(value) {
            require(value in ALLOWED_DAYS) { "Unsupported retention value: $value" }
            prefs.edit().putInt(KEY_RETENTION_DAYS, value).apply()
        }

    fun effectiveRetentionDays(premium: Boolean): Int =
        if (premium) premiumRetentionDays else FREE_RETENTION_DAYS

    companion object {
        const val FREE_RETENTION_DAYS = 7
        const val FOREVER = -1
        val ALLOWED_DAYS = setOf(1, 3, 7, 30, FOREVER)
        private const val KEY_RETENTION_DAYS = "premium_retention_days"
    }
}
