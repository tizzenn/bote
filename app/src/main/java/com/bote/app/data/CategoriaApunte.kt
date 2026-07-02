package com.bote.app.data

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.bote.app.R

/** Categoría de un apunte, con su icono Material elegible en el formulario. */
enum class CategoriaApunte(
    @StringRes val nombreRes: Int,
    @DrawableRes val iconoRes: Int
) {
    RESTAURANTE(R.string.cat_restaurante, R.drawable.ic_cat_restaurante),
    COPAS(R.string.cat_copas, R.drawable.ic_cat_copas),
    REGALO(R.string.cat_regalo, R.drawable.ic_cat_regalo),
    SUPER(R.string.cat_super, R.drawable.ic_cat_super),
    ALOJAMIENTO(R.string.cat_alojamiento, R.drawable.ic_cat_alojamiento),
    COMBUSTIBLE(R.string.cat_combustible, R.drawable.ic_cat_combustible),
    SUSTANCIAS(R.string.cat_sustancias, R.drawable.ic_cat_sustancias),
    CASH(R.string.cat_cash, R.drawable.ic_cat_cash),
    OTROS(R.string.cat_otros, R.drawable.ic_cat_otros);

    companion object {
        fun fromNombre(nombre: String?): CategoriaApunte =
            entries.firstOrNull { it.name == nombre } ?: OTROS
    }
}
