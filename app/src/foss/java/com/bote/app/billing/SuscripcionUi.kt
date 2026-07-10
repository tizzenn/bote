package com.bote.app.billing

import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity

/** En F-Droid no hay suscripción: todo está desbloqueado, panel oculto. */
object SuscripcionUi {
    fun montar(activity: AppCompatActivity, contenedor: ViewGroup) {
        contenedor.visibility = View.GONE
    }
}
