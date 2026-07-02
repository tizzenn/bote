package com.bote.app.data

import java.text.NumberFormat
import kotlin.math.roundToLong

/** Formateo y lectura de importes; internamente todo va en céntimos. */
object Dinero {

    fun formatear(cents: Long): String =
        NumberFormat.getCurrencyInstance().format(cents / 100.0)

    /** Acepta "12", "12.5", "12,50"… Devuelve null si no es un importe. */
    fun parsear(texto: String): Long? {
        val limpio = texto.trim().replace(',', '.')
        if (limpio.isEmpty()) return null
        val valor = limpio.toDoubleOrNull() ?: return null
        if (valor < 0) return null
        return (valor * 100).roundToLong()
    }

    /** Valor editable para rellenar un campo de texto ("12.50"). */
    fun aTexto(cents: Long): String = String.format(java.util.Locale.US, "%.2f", cents / 100.0)
}
