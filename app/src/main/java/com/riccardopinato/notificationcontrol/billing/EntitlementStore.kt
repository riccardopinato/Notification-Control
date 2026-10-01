package com.riccardopinato.notificationcontrol.billing

import android.content.Context
import androidx.core.content.edit
import com.riccardopinato.notificationcontrol.BuildConfig

class EntitlementStore(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun effectiveTier(now: Long = System.currentTimeMillis()): EntitlementTier {
        if (BuildConfig.QA_PREMIUM_UNLOCKED) {
            return EntitlementTier.PREMIUM_LIFETIME
        }
        val stored = storedTier()
        if (stored == EntitlementTier.PREMIUM_LIFETIME) return stored
        if (stored == EntitlementTier.PREMIUM_SUBSCRIPTION) {
            val verifiedAt = prefs.getLong(KEY_VERIFIED_AT, 0L)
            if (verifiedAt > 0L && now - verifiedAt <= SUBSCRIPTION_OFFLINE_GRACE_MS) {
                return stored
            }
        }
        return EntitlementTier.FREE
    }

    fun updateVerified(tier: EntitlementTier, now: Long = System.currentTimeMillis()) {
        if (BuildConfig.QA_PREMIUM_UNLOCKED) return
        prefs.edit {
            putString(KEY_TIER, tier.name)
            putLong(KEY_VERIFIED_AT, now)
        }
    }

    fun lastVerifiedAt(): Long = prefs.getLong(KEY_VERIFIED_AT, 0L)

    private fun storedTier(): EntitlementTier =
        runCatching {
            EntitlementTier.valueOf(
                prefs.getString(KEY_TIER, EntitlementTier.FREE.name)
                    ?: EntitlementTier.FREE.name
            )
        }.getOrDefault(EntitlementTier.FREE)

    companion object {
        private const val PREFS = "notification_control_entitlement"
        private const val KEY_TIER = "tier"
        private const val KEY_VERIFIED_AT = "last_verified_at"
        const val SUBSCRIPTION_OFFLINE_GRACE_MS = 72L * 60L * 60L * 1000L
    }
}
