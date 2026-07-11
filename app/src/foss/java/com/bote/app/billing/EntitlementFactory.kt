package com.bote.app.billing

import android.content.Context

/** Sabor F-Droid: todo gratis, sin librerías de Google. */
object EntitlementFactory {
    fun crear(context: Context): EntitlementProvider = FossEntitlement
}

private object FossEntitlement : EntitlementProvider {
    override fun syncDesbloqueada(): Boolean = true
}
