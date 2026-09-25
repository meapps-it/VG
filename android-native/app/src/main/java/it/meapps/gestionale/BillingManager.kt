package it.meapps.gestionale

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

class BillingManager(
    context: Context,
    private val onPremiumChanged: (Boolean) -> Unit,
    private val onPriceChanged: (String?) -> Unit,
    private val onMessage: (String) -> Unit
) : PurchasesUpdatedListener {

    companion object {
        const val PREMIUM_PRODUCT_ID = "premium_gestionale"
    }

    private val appContext = context.applicationContext
    private var premiumProduct: ProductDetails? = null

    private val billingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .enableAutoServiceReconnection()
        .build()

    fun start() {
        if (billingClient.isReady) {
            refresh()
            return
        }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    refresh()
                } else {
                    onMessage("Google Play Billing non disponibile: ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                // Auto-reconnection is enabled.
            }
        })
    }

    fun refresh() {
        if (!billingClient.isReady) return
        queryProduct()
        queryPurchases()
    }

    private fun queryProduct() {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PREMIUM_PRODUCT_ID)
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product))
            .build()

        billingClient.queryProductDetailsAsync(params) { result, detailsResult ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                premiumProduct = null
                onPriceChanged(null)
                return@queryProductDetailsAsync
            }

            premiumProduct = detailsResult.productDetailsList.firstOrNull()
            val price = premiumProduct
                ?.subscriptionOfferDetails
                ?.firstOrNull()
                ?.pricingPhases
                ?.pricingPhaseList
                ?.lastOrNull()
                ?.formattedPrice
            onPriceChanged(price)
        }
    }

    private fun queryPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                onPremiumChanged(false)
                return@queryPurchasesAsync
            }

            val premiumPurchases = purchases.filter {
                it.products.contains(PREMIUM_PRODUCT_ID) &&
                    it.purchaseState == Purchase.PurchaseState.PURCHASED
            }

            premiumPurchases.forEach(::acknowledgeIfNeeded)
            onPremiumChanged(premiumPurchases.isNotEmpty())
        }
    }

    fun launchPurchase(activity: Activity) {
        if (!billingClient.isReady) {
            onMessage("Google Play Billing non è ancora pronto")
            start()
            return
        }

        val details = premiumProduct
        if (details == null) {
            onMessage("Abbonamento Pro non ancora configurato su Google Play")
            queryProduct()
            return
        }

        val offer = details.subscriptionOfferDetails?.firstOrNull()
        if (offer == null) {
            onMessage("Piano annuale Pro non disponibile su Google Play")
            return
        }

        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(offer.offerToken)
            .build()

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()

        val result = billingClient.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            onMessage(result.debugMessage.ifBlank { "Impossibile avviare il pagamento" })
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val premiumPurchases = purchases.orEmpty().filter {
                    it.products.contains(PREMIUM_PRODUCT_ID) &&
                        it.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                premiumPurchases.forEach(::acknowledgeIfNeeded)
                onPremiumChanged(premiumPurchases.isNotEmpty())
                if (premiumPurchases.isNotEmpty()) onMessage("Versione Pro attivata")
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> queryPurchases()
            else -> onMessage(result.debugMessage.ifBlank { "Pagamento non completato" })
        }
    }

    private fun acknowledgeIfNeeded(purchase: Purchase) {
        if (purchase.isAcknowledged) return
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(params) { result ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                onPremiumChanged(true)
            }
        }
    }

    fun end() {
        if (billingClient.isReady) billingClient.endConnection()
    }
}
