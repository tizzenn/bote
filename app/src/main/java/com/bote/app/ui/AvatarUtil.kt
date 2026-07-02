package com.bote.app.ui

import android.content.Context
import android.graphics.Color
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.bote.app.R
import kotlin.math.abs

/**
 * Avatar de asistente: círculo de color determinista (según el nombre)
 * con las iniciales encima. Es la opción óptima sin depender de fotos:
 * no ocupa almacenamiento, sobrevive a la sincronización y siempre se ve.
 */
object AvatarUtil {

    private val COLORES = intArrayOf(
        R.color.avatar_1, R.color.avatar_2, R.color.avatar_3, R.color.avatar_4,
        R.color.avatar_5, R.color.avatar_6, R.color.avatar_7, R.color.avatar_8
    )

    fun color(context: Context, nombre: String): Int {
        val indice = abs(nombre.hashCode()) % COLORES.size
        return ContextCompat.getColor(context, COLORES[indice])
    }

    fun iniciales(nombre: String): String {
        val palabras = nombre.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        return when {
            palabras.isEmpty() -> "?"
            palabras.size == 1 -> palabras[0].take(1).uppercase()
            else -> (palabras[0].take(1) + palabras[1].take(1)).uppercase()
        }
    }

    /** Convierte un TextView en un avatar circular con iniciales. */
    fun aplicar(textView: TextView, nombre: String) {
        val contexto = textView.context
        val fondo = ContextCompat.getDrawable(contexto, R.drawable.ic_circulo)!!
            .mutate()
        DrawableCompat.setTint(DrawableCompat.wrap(fondo), color(contexto, nombre))
        textView.background = fondo
        textView.text = iniciales(nombre)
        textView.setTextColor(Color.WHITE)
    }
}
