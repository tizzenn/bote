package com.bote.app.billing

import android.content.Context
import com.bote.app.config.Ajustes

/** Sabor Google Play: la sync se desbloquea con la suscripción activa. */
object EntitlementFactory {
    fun crear(context: Context): EntitlementProvider = PlayEntitlement(context.applicationContext)
}

private class PlayEntitlement(private val context: Context) : EntitlementProvider {
    override fun syncDesbloqueada(): Boolean = Ajustes.suscripcionActiva(context)
}
