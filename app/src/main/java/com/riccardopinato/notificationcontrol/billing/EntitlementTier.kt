package com.riccardopinato.notificationcontrol.billing

enum class EntitlementTier {
    FREE,
    PREMIUM_SUBSCRIPTION,
    PREMIUM_LIFETIME;

    val isPremium: Boolean
        get() = this != FREE
}
