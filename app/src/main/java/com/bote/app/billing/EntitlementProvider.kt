package com.bote.app.billing

import android.content.Context

/**
 * Decide qué funciones de pago están desbloqueadas. Es lo único que cambia
 * entre sabores: en `foss` siempre está todo desbloqueado; en `play` depende
 * de la suscripción (BillingClient). Cada sabor aporta su [EntitlementFactory].
 */
interface EntitlementProvider {
    /** ¿Está desbloqueada la sincronización en la nube? */
    fun syncDesbloqueada(): Boolean
}

/** Acceso cómodo desde el código común. */
object Entitlements {
    fun de(context: Context): EntitlementProvider = EntitlementFactory.crear(context)
}
