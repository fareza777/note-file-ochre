package app.ochre.notes.billing

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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BillingManager(
    context: Context,
    private val appScope: CoroutineScope,
    private val persistOwned: (Boolean) -> Unit,
) : PurchasesUpdatedListener {

    private val appContext = context.applicationContext

    private val _owned = MutableStateFlow(false)
    val owned: StateFlow<Boolean> = _owned.asStateFlow()

    private val _price = MutableStateFlow(BillingConfig.FALLBACK_PRICE)
    val price: StateFlow<String> = _price.asStateFlow()

    private val messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events: SharedFlow<String> = messages.asSharedFlow()

    private var productDetails: ProductDetails? = null
    private var connecting = false

    private val client = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
        )
        .enableAutoServiceReconnection()
        .build()

    init {
        connect()
    }

    fun refresh() {
        if (client.isReady) {
            queryProduct()
            queryPurchases()
        } else {
            connect()
        }
    }

    fun purchase(activity: Activity) {
        if (!client.isReady) {
            messages.tryEmit("Google Play is not ready yet. Try again in a moment.")
            refresh()
            return
        }
        val details = productDetails
        if (details == null) {
            messages.tryEmit("Remove ads is not available yet. Please try again later.")
            queryProduct()
            return
        }
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .apply {
                details.oneTimePurchaseOfferDetails?.offerToken?.let { setOfferToken(it) }
            }
            .build()
        val result = client.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productParams))
                .build(),
        )
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            messages.tryEmit("Google Play could not start the purchase. Please try again.")
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (purchases != null) {
                    applyPurchases(purchases)
                    if (purchases.any {
                            it.products.contains(BillingConfig.REMOVE_ADS_PRODUCT_ID) &&
                                it.purchaseState == Purchase.PurchaseState.PENDING
                        }
                    ) {
                        messages.tryEmit("Purchase pending. Ads turn off once Google Play approves it.")
                    }
                }
            }

            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> queryPurchases()
            else -> messages.tryEmit("The purchase could not be completed. Please try again.")
        }
    }

    private fun connect() {
        if (connecting || client.isReady) return
        connecting = true
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                connecting = false
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProduct()
                    queryPurchases()
                }
            }

            override fun onBillingServiceDisconnected() {
                connecting = false
            }
        })
    }

    private fun queryProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(BillingConfig.REMOVE_ADS_PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            )
            .build()
        client.queryProductDetailsAsync(params) { result, queryResult ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
            val details = queryResult.productDetailsList.firstOrNull {
                it.productId == BillingConfig.REMOVE_ADS_PRODUCT_ID
            } ?: return@queryProductDetailsAsync
            productDetails = details
            details.oneTimePurchaseOfferDetails?.formattedPrice?.let { _price.value = it }
        }
    }

    private fun queryPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                applyPurchases(purchases)
            }
        }
    }

    private fun applyPurchases(purchases: List<Purchase>) {
        val matching = purchases.filter {
            it.products.contains(BillingConfig.REMOVE_ADS_PRODUCT_ID)
        }
        val owned = matching.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        if (_owned.value != owned) _owned.value = owned
        appScope.launch { persistOwned(owned) }
        matching
            .filter {
                it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged
            }
            .forEach { purchase ->
                client.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build(),
                ) { }
            }
    }
}
