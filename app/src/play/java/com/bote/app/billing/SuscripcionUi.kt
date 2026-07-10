package com.bote.app.billing

import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bote.app.R
import com.bote.app.config.Ajustes
import com.google.android.material.button.MaterialButton

/** Panel de suscripción del sabor play: estado + botón suscribir/gestionar. */
object SuscripcionUi {

    fun montar(activity: AppCompatActivity, contenedor: ViewGroup) {
        contenedor.removeAllViews()
        contenedor.visibility = View.VISIBLE

        val titulo = TextView(activity).apply {
            setText(R.string.seccion_suscripcion)
            setTextColor(ContextCompat.getColor(activity, R.color.text_secondary))
            textSize = 13f
            setPadding(0, 0, 0, 8)
        }
        val estado = TextView(activity).apply {
            setTextColor(ContextCompat.getColor(activity, R.color.text_primary))
            textSize = 14f
            setPadding(0, 0, 0, 8)
        }
        val boton = MaterialButton(activity)
        contenedor.addView(titulo)
        contenedor.addView(estado)
        contenedor.addView(
            boton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        fun refrescar(activa: Boolean) {
            estado.setText(
                if (activa) R.string.suscripcion_activa_desc else R.string.suscripcion_oferta
            )
            boton.setText(
                if (activa) R.string.suscripcion_gestionar else R.string.suscripcion_boton
            )
        }

        val manager = BillingManager(activity.applicationContext) { activa ->
            activity.runOnUiThread { refrescar(activa) }
        }
        refrescar(Ajustes.suscripcionActiva(activity))
        boton.setOnClickListener {
            if (Ajustes.suscripcionActiva(activity)) {
                abrirGestion(activity)
            } else if (!manager.comprar(activity)) {
                Toast.makeText(
                    activity, R.string.suscripcion_no_disponible, Toast.LENGTH_SHORT
                ).show()
            }
        }
        manager.conectar()
    }

    private fun abrirGestion(activity: AppCompatActivity) {
        val uri = Uri.parse(
            "https://play.google.com/store/account/subscriptions" +
                "?sku=${BillingManager.PRODUCTO}&package=${activity.packageName}"
        )
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: Exception) {
            Toast.makeText(activity, R.string.suscripcion_no_disponible, Toast.LENGTH_SHORT).show()
        }
    }
}
