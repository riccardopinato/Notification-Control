package com.riccardopinato.notificationcontrol.billing

data class PremiumOffer(
    val key: String,
    val productId: String,
    val title: String,
    val formattedPrice: String,
    val planLabel: String,
    val isLifetime: Boolean
)

data class BillingUiState(
    val connected: Boolean = false,
    val loading: Boolean = true,
    val entitlement: EntitlementTier = EntitlementTier.FREE,
    val offers: List<PremiumOffer> = emptyList(),
    val statusMessage: String? = null
)
