package com.riccardopinato.notificationcontrol.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class PlayBillingManager private constructor(context: Context) : PurchasesUpdatedListener {
    private val appContext = context.applicationContext
    private val store = EntitlementStore(appContext)
    private val connecting = AtomicBoolean(false)
    private val productLaunchData = mutableMapOf<String, LaunchData>()

    private val _state = MutableStateFlow(
        BillingUiState(
            connected = false,
            loading = true,
            entitlement = store.effectiveTier()
        )
    )
    val state: StateFlow<BillingUiState> = _state.asStateFlow()

    private val client = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .build()

    fun isPremiumCached(): Boolean = store.effectiveTier().isPremium

    fun connect() {
        if (client.isReady) {
            refresh()
            return
        }
        if (!connecting.compareAndSet(false, true)) return

        client.startConnection(
            object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    connecting.set(false)
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        _state.value = _state.value.copy(
                            connected = true,
                            loading = true,
                            entitlement = store.effectiveTier(),
                            statusMessage = null
                        )
                        refresh()
                    } else {
                        _state.value = _state.value.copy(
                            connected = false,
                            loading = false,
                            entitlement = store.effectiveTier(),
                            statusMessage = result.debugMessage
                        )
                    }
                }

                override fun onBillingServiceDisconnected() {
                    connecting.set(false)
                    _state.value = _state.value.copy(
                        connected = false,
                        loading = false,
                        entitlement = store.effectiveTier()
                    )
                }
            }
        )
    }

    fun refresh() {
        if (!client.isReady) {
            connect()
            return
        }
        _state.value = _state.value.copy(loading = true, connected = true)
        refreshPurchases()
        refreshProductDetails()
    }

    fun launchPurchase(activity: Activity, offerKey: String): BillingResult? {
        val launchData = synchronized(productLaunchData) {
            productLaunchData[offerKey]
        } ?: return null

        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(launchData.productDetails)
            .setOfferToken(launchData.offerToken)
            .build()

        return client.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productParams))
                .build()
        )
    }

    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: MutableList<Purchase>?
    ) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            purchases.orEmpty().forEach(::acknowledgeIfNeeded)
            refreshPurchases()
        } else if (
            billingResult.responseCode != BillingClient.BillingResponseCode.USER_CANCELED
        ) {
            _state.value = _state.value.copy(statusMessage = billingResult.debugMessage)
        }
    }

    private fun refreshPurchases() {
        val remaining = AtomicInteger(2)
        var subscriptions: List<Purchase> = emptyList()
        var inApp: List<Purchase> = emptyList()
        val successful = AtomicInteger(0)

        fun finishIfReady() {
            if (remaining.decrementAndGet() != 0) return

            if (successful.get() == 2) {
                val purchasedInApp = inApp.filter(::isCompletedPurchase)
                val purchasedSubs = subscriptions.filter(::isCompletedPurchase)

                purchasedInApp.forEach(::acknowledgeIfNeeded)
                purchasedSubs.forEach(::acknowledgeIfNeeded)

                val tier = when {
                    purchasedInApp.any {
                        BillingProducts.PREMIUM_LIFETIME in it.products
                    } -> EntitlementTier.PREMIUM_LIFETIME

                    purchasedSubs.any {
                        BillingProducts.PREMIUM_SUBSCRIPTION in it.products
                    } -> EntitlementTier.PREMIUM_SUBSCRIPTION

                    else -> EntitlementTier.FREE
                }
                store.updateVerified(tier)
                _state.value = _state.value.copy(
                    connected = true,
                    loading = false,
                    entitlement = tier,
                    statusMessage = null
                )
            } else {
                _state.value = _state.value.copy(
                    connected = client.isReady,
                    loading = false,
                    entitlement = store.effectiveTier()
                )
            }
        }

        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                subscriptions = purchases
                successful.incrementAndGet()
            }
            finishIfReady()
        }

        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                inApp = purchases
                successful.incrementAndGet()
            }
            finishIfReady()
        }
    }

    private fun refreshProductDetails() {
        val productQueries = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(BillingProducts.PREMIUM_SUBSCRIPTION)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(BillingProducts.PREMIUM_LIFETIME)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )

        client.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder()
                .setProductList(productQueries)
                .build()
        ) { result, detailsResult ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                _state.value = _state.value.copy(statusMessage = result.debugMessage)
                return@queryProductDetailsAsync
            }

            val offers = mutableListOf<PremiumOffer>()
            val launchData = mutableMapOf<String, LaunchData>()

            detailsResult.productDetailsList.forEach { details ->
                when (details.productType) {
                    BillingClient.ProductType.SUBS -> {
                        details.subscriptionOfferDetails.orEmpty().forEach { offer ->
                            val price = offer.pricingPhases.pricingPhaseList.lastOrNull()
                                ?: return@forEach
                            val key = listOf(
                                details.productId,
                                offer.basePlanId,
                                offer.offerId.orEmpty()
                            ).joinToString(":")
                            offers += PremiumOffer(
                                key = key,
                                productId = details.productId,
                                title = details.name,
                                formattedPrice = price.formattedPrice,
                                planLabel = offer.basePlanId,
                                isLifetime = false
                            )
                            launchData[key] = LaunchData(details, offer.offerToken)
                        }
                    }

                    BillingClient.ProductType.INAPP -> {
                        val oneTimeOffers =
                            details.oneTimePurchaseOfferDetailsList.orEmpty()
                                .ifEmpty {
                                    listOfNotNull(details.oneTimePurchaseOfferDetails)
                                }
                        oneTimeOffers.forEach { offer ->
                            val key = listOf(
                                details.productId,
                                offer.purchaseOptionId,
                                offer.offerId.orEmpty()
                            ).joinToString(":")
                            offers += PremiumOffer(
                                key = key,
                                productId = details.productId,
                                title = details.name,
                                formattedPrice = offer.formattedPrice,
                                planLabel = offer.purchaseOptionId,
                                isLifetime = true
                            )
                            launchData[key] = LaunchData(details, offer.offerToken)
                        }
                    }
                }
            }

            synchronized(productLaunchData) {
                productLaunchData.clear()
                productLaunchData.putAll(launchData)
            }
            _state.value = _state.value.copy(
                offers = offers.sortedWith(
                    compareBy<PremiumOffer> { it.isLifetime }
                        .thenBy { it.planLabel }
                )
            )
        }
    }

    private fun isCompletedPurchase(purchase: Purchase): Boolean =
        purchase.purchaseState == Purchase.PurchaseState.PURCHASED

    private fun acknowledgeIfNeeded(purchase: Purchase) {
        if (!isCompletedPurchase(purchase) || purchase.isAcknowledged) return
        client.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
        ) { }
    }

    private data class LaunchData(
        val productDetails: ProductDetails,
        val offerToken: String
    )

    companion object {
        @Volatile
        private var instance: PlayBillingManager? = null

        fun get(context: Context): PlayBillingManager =
            instance ?: synchronized(this) {
                instance ?: PlayBillingManager(context).also { instance = it }
            }
    }
}
