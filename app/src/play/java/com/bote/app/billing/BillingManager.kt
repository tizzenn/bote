package com.bote.app.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.bote.app.config.Ajustes

/**
 * Suscripción anual que desbloquea la sync en el sabor play. Guarda el estado
 * en [Ajustes.suscripcionActiva], que es lo que consulta EntitlementProvider.
 */
class BillingManager(
    private val context: Context,
    private val alCambiar: (Boolean) -> Unit
) : PurchasesUpdatedListener {

    companion object {
        const val PRODUCTO = "bote_sync_anual"
    }

    private var detalles: ProductDetails? = null

    private val cliente = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    fun conectar() {
        cliente.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(resultado: BillingResult) {
                if (resultado.responseCode == BillingClient.BillingResponseCode.OK) {
                    consultarProducto()
                    consultarCompras()
                }
            }
            override fun onBillingServiceDisconnected() { /* se reintenta al reabrir */ }
        })
    }

    private fun consultarProducto() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCTO)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            ).build()
        cliente.queryProductDetailsAsync(params) { resultado, lista ->
            if (resultado.responseCode == BillingClient.BillingResponseCode.OK) {
                detalles = lista.firstOrNull()
            }
        }
    }

    private fun consultarCompras() {
        cliente.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        ) { _, compras ->
            aplicar(compras)
        }
    }

    fun comprar(activity: Activity): Boolean {
        val d = detalles ?: return false
        val oferta = d.subscriptionOfferDetails?.firstOrNull() ?: return false
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(d)
                        .setOfferToken(oferta.offerToken)
                        .build()
                )
            ).build()
        cliente.launchBillingFlow(activity, params)
        return true
    }

    override fun onPurchasesUpdated(resultado: BillingResult, compras: MutableList<Purchase>?) {
        if (resultado.responseCode == BillingClient.BillingResponseCode.OK && compras != null) {
            aplicar(compras)
        }
    }

    private fun aplicar(compras: List<Purchase>) {
        val activa = compras.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        Ajustes.guardarSuscripcionActiva(context, activa)
        compras.forEach { reconocer(it) }
        alCambiar(activa)
    }

    private fun reconocer(compra: Purchase) {
        if (compra.purchaseState == Purchase.PurchaseState.PURCHASED && !compra.isAcknowledged) {
            cliente.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(compra.purchaseToken)
                    .build()
            ) { /* reconocido */ }
        }
    }

    fun cerrar() {
        runCatching { cliente.endConnection() }
    }
}
