package com.riccardopinato.notificationcontrol.billing

object BillingProducts {
    const val PREMIUM_SUBSCRIPTION = "notification_control_premium"
    const val PREMIUM_MONTHLY_BASE_PLAN = "monthly"
    const val PREMIUM_ANNUAL_BASE_PLAN = "annual"

    // Kept for backwards compatibility. The v1 commercial default is subscription-first;
    // a lifetime offer is shown only if it is explicitly configured in Play.
    const val PREMIUM_LIFETIME = "notification_control_premium_lifetime"

    fun basePlanRank(basePlanId: String): Int = when (basePlanId) {
        PREMIUM_MONTHLY_BASE_PLAN -> 0
        PREMIUM_ANNUAL_BASE_PLAN -> 1
        else -> 10
    }
}
