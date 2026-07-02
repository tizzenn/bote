package com.bote.app.ui

import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.bote.app.BaseActivity
import com.bote.app.R
import com.bote.app.config.Ajustes
import com.bote.app.config.PaletaColor
import com.bote.app.config.Tema
import com.bote.app.databinding.ActivitySettingsBinding
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

class SettingsActivity : BaseActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Tema
        when (Ajustes.tema(this)) {
            Tema.CLARO -> binding.radioBlanco.isChecked = true
            Tema.OSCURO -> binding.radioNegro.isChecked = true
            Tema.SISTEMA -> binding.radioSistema.isChecked = true
        }
        binding.grupoTema.setOnCheckedChangeListener { _, id ->
            val tema = when (id) {
                R.id.radioBlanco -> Tema.CLARO
                R.id.radioNegro -> Tema.OSCURO
                else -> Tema.SISTEMA
            }
            Ajustes.guardarTema(this, tema)
        }

        // Colores principal y de acento
        montarPaleta(binding.grupoPrimario, Ajustes.colorPrimario(this)) {
            Ajustes.guardarColorPrimario(this, it)
            recreate()
        }
        montarPaleta(binding.grupoAcento, Ajustes.colorAcento(this)) {
            Ajustes.guardarColorAcento(this, it)
            recreate()
        }

        // Notificaciones
        binding.switchEvento.isChecked = Ajustes.notifEvento(this)
        binding.switchEvento.setOnCheckedChangeListener { _, valor ->
            Ajustes.guardarNotifEvento(this, valor)
        }
        binding.switchCierre.isChecked = Ajustes.notifCierre(this)
        binding.switchCierre.setOnCheckedChangeListener { _, valor ->
            Ajustes.guardarNotifCierre(this, valor)
        }
        binding.switchPagos.isChecked = Ajustes.notifPagos(this)
        binding.switchPagos.setOnCheckedChangeListener { _, valor ->
            Ajustes.guardarNotifPagos(this, valor)
        }
    }

    private fun montarPaleta(
        grupo: ChipGroup,
        actual: PaletaColor,
        alElegir: (PaletaColor) -> Unit
    ) {
        grupo.removeAllViews()
        for (color in PaletaColor.entries) {
            val chip = layoutInflater.inflate(R.layout.item_chip_choice, grupo, false) as Chip
            chip.id = View.generateViewId()
            chip.text = getString(color.nombreRes)
            val circulo = ContextCompat.getDrawable(this, R.drawable.ic_circulo)!!.mutate()
            DrawableCompat.setTint(
                DrawableCompat.wrap(circulo),
                ContextCompat.getColor(this, color.colorRes)
            )
            chip.chipIcon = circulo
            chip.isChecked = color == actual
            chip.setOnCheckedChangeListener { _, marcado ->
                if (marcado && color != actual) alElegir(color)
            }
            grupo.addView(chip)
        }
    }
}
