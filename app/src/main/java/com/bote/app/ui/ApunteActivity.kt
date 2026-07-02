package com.bote.app.ui

import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.bote.app.BaseActivity
import com.bote.app.R
import com.bote.app.data.AppDatabase
import com.bote.app.data.Apunte
import com.bote.app.data.ApunteBorrado
import com.bote.app.data.ApunteConRepartos
import com.bote.app.data.Asistente
import com.bote.app.data.Calculadora
import com.bote.app.data.CategoriaApunte
import com.bote.app.data.Dinero
import com.bote.app.data.EventoCompleto
import com.bote.app.data.Reparto
import com.bote.app.databinding.ActivityApunteBinding
import com.bote.app.databinding.ItemRepartoBinding
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.roundToInt

class ApunteActivity : BaseActivity() {

    companion object {
        const val EXTRA_EVENTO_ID = "evento_id"
        const val EXTRA_APUNTE_ID = "apunte_id"
    }

    /** Estado de la fila de reparto de un asistente (fader + pin). */
    private class FilaReparto(
        val asistente: Asistente,
        val vista: ItemRepartoBinding,
        var participa: Boolean = true,
        var pct: Double = 0.0,
        var fijado: Boolean = false
    )

    private lateinit var binding: ActivityApunteBinding
    private var eventoId: Long = 0
    private var apunteId: Long = 0
    private var datos: EventoCompleto? = null
    private var original: ApunteConRepartos? = null

    private var filas = listOf<FilaReparto>()
    private var modoIgual = true
    private var actualizando = false
    private var pagadorId: Long = 0
    private var categoria = CategoriaApunte.OTROS
    private var apunteUuid: String = UUID.randomUUID().toString()
    private var fotoRecibo: String = ""

    private val elegirFotoRecibo = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val copia = FotoUtil.copiarFoto(this, uri, "recibo_$apunteUuid")
            if (copia != null) {
                fotoRecibo = copia
                mostrarRecibo()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityApunteBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        eventoId = intent.getLongExtra(EXTRA_EVENTO_ID, 0)
        apunteId = intent.getLongExtra(EXTRA_APUNTE_ID, 0)
        supportActionBar?.title = getString(
            if (apunteId == 0L) R.string.titulo_nuevo_apunte else R.string.titulo_editar_apunte
        )

        binding.toggleReparto.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val nuevoIgual = checkedId == R.id.btnIgual
            if (nuevoIgual == modoIgual) {
                refrescar()
                return@addOnButtonCheckedListener
            }
            modoIgual = nuevoIgual
            if (!modoIgual) {
                // Arranque del modo porcentual: se parte del reparto igualitario
                val n = filas.count { it.participa }
                filas.forEach {
                    it.pct = if (it.participa && n > 0) 100.0 / n else 0.0
                    it.fijado = false
                }
            }
            refrescar()
        }
        binding.btnGuardar.setOnClickListener { guardar() }
        binding.btnFotoRecibo.setOnClickListener {
            elegirFotoRecibo.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
        binding.btnQuitarFoto.setOnClickListener {
            fotoRecibo = ""
            mostrarRecibo()
        }
        binding.fotoRecibo.setOnClickListener { verReciboGrande() }

        pintarCategorias()
        cargar()
    }

    private fun mostrarRecibo() {
        val hay = FotoUtil.cargar(binding.fotoRecibo, fotoRecibo)
        binding.fotoRecibo.visibility = if (hay) View.VISIBLE else View.GONE
        binding.btnQuitarFoto.visibility = if (hay) View.VISIBLE else View.GONE
    }

    private fun verReciboGrande() {
        if (fotoRecibo.isBlank()) return
        val imagen = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        if (!FotoUtil.cargar(imagen, fotoRecibo)) return
        MaterialAlertDialogBuilder(this)
            .setView(imagen)
            .setPositiveButton(R.string.accion_cancelar, null)
            .show()
    }

    private fun cargar() {
        lifecycleScope.launch {
            val dao = AppDatabase.get(this@ApunteActivity).dao()
            val completo = dao.eventoCompleto(eventoId) ?: run { finish(); return@launch }
            datos = completo
            if (apunteId != 0L) {
                original = dao.apunteConRepartos(apunteId)
            }
            val apunte = original?.apunte

            if (apunte != null) {
                binding.campoConcepto.setText(apunte.concepto)
                binding.campoGastado.setText(Dinero.aTexto(apunte.gastadoCents))
                if (apunte.presupuestadoCents != null) {
                    binding.campoPresupuestado.setText(Dinero.aTexto(apunte.presupuestadoCents))
                }
                if (apunte.pagadoCents != null) {
                    binding.campoPagado.setText(Dinero.aTexto(apunte.pagadoCents))
                }
                categoria = CategoriaApunte.fromNombre(apunte.categoria)
                modoIgual = apunte.repartoIgualitario
                pagadorId = apunte.pagadorId
                apunteUuid = apunte.uuid
                fotoRecibo = apunte.fotoPath
                mostrarRecibo()
            } else {
                pagadorId = completo.evento.miAsistenteId.takeIf { id ->
                    completo.asistentes.any { it.id == id }
                } ?: completo.asistentes.firstOrNull()?.id ?: 0
            }

            marcarCategoria()
            pintarPagador(completo)
            montarReparto(completo)
            binding.toggleReparto.check(if (modoIgual) R.id.btnIgual else R.id.btnPorcentual)
            refrescar()
        }
    }

    private fun pintarCategorias() {
        binding.grupoCategorias.removeAllViews()
        for (cat in CategoriaApunte.entries) {
            val chip = layoutInflater.inflate(
                R.layout.item_chip_choice, binding.grupoCategorias, false
            ) as Chip
            chip.id = View.generateViewId()
            chip.text = getString(cat.nombreRes)
            chip.setChipIconResource(cat.iconoRes)
            chip.tag = cat.name
            chip.setOnCheckedChangeListener { _, marcado ->
                if (marcado) categoria = cat
            }
            binding.grupoCategorias.addView(chip)
        }
        marcarCategoria()
    }

    private fun marcarCategoria() {
        for (i in 0 until binding.grupoCategorias.childCount) {
            val chip = binding.grupoCategorias.getChildAt(i) as Chip
            chip.isChecked = chip.tag == categoria.name
        }
    }

    private fun pintarPagador(completo: EventoCompleto) {
        val nombres = completo.asistentes.map {
            it.nombre.ifBlank { getString(R.string.asistente_sin_nombre) }
        }
        binding.campoPagador.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, nombres)
        )
        val indice = completo.asistentes.indexOfFirst { it.id == pagadorId }
            .coerceAtLeast(0)
        if (nombres.isNotEmpty()) {
            binding.campoPagador.setText(nombres[indice], false)
        }
        binding.campoPagador.setOnItemClickListener { _, _, posicion, _ ->
            pagadorId = completo.asistentes[posicion].id
        }
    }

    // ── Reparto con faders ────────────────────────────────────────

    private fun montarReparto(completo: EventoCompleto) {
        binding.listaReparto.removeAllViews()
        val repartosPrevios = original?.repartos?.associateBy { it.asistenteId }
        filas = completo.asistentes.map { asistente ->
            val vista = ItemRepartoBinding.inflate(
                layoutInflater, binding.listaReparto, false
            )
            val nombre = asistente.nombre.ifBlank { getString(R.string.asistente_sin_nombre) }
            AvatarUtil.aplicar(vista.avatar, nombre)
            vista.nombre.text = nombre
            binding.listaReparto.addView(vista.root)

            val previo = repartosPrevios?.get(asistente.id)
            val fila = FilaReparto(
                asistente = asistente,
                vista = vista,
                participa = if (repartosPrevios == null) true else previo != null,
                pct = (previo?.puntosBasicos ?: 0) / 100.0
            )

            vista.participa.setOnCheckedChangeListener { _, marcado ->
                if (actualizando) return@setOnCheckedChangeListener
                fila.participa = marcado
                if (!marcado) {
                    fila.pct = 0.0
                    fila.fijado = false
                }
                if (!modoIgual) reequilibrarLibres()
                refrescar()
            }
            vista.btnFijar.setOnClickListener {
                if (!fila.participa || modoIgual) return@setOnClickListener
                fila.fijado = !fila.fijado
                refrescar()
            }
            vista.fader.addOnChangeListener { _, valor, delUsuario ->
                if (!delUsuario || actualizando || modoIgual) return@addOnChangeListener
                moverFader(fila, valor.toDouble())
                refrescar()
            }
            fila
        }
    }

    /**
     * Mueve un fader: el porcentaje del resto se redistribuye
     * proporcionalmente entre los faders abiertos (sin fijar).
     */
    private fun moverFader(fila: FilaReparto, nuevo: Double) {
        if (!fila.participa || fila.fijado) return
        val fijadoOtros = filas
            .filter { it.participa && it.fijado && it !== fila }
            .sumOf { it.pct }
        fila.pct = nuevo.coerceIn(0.0, (100.0 - fijadoOtros).coerceAtLeast(0.0))

        val libres = filas.filter { it.participa && !it.fijado && it !== fila }
        val resto = (100.0 - fijadoOtros - fila.pct).coerceAtLeast(0.0)
        if (libres.isEmpty()) return
        val sumaLibres = libres.sumOf { it.pct }
        if (sumaLibres <= 0.0) {
            libres.forEach { it.pct = resto / libres.size }
        } else {
            libres.forEach { it.pct = it.pct * resto / sumaLibres }
        }
    }

    /** Reparte lo no fijado a partes iguales entre los faders abiertos. */
    private fun reequilibrarLibres() {
        val libres = filas.filter { it.participa && !it.fijado }
        val fijado = filas.filter { it.participa && it.fijado }.sumOf { it.pct }
        val resto = (100.0 - fijado).coerceAtLeast(0.0)
        libres.forEach { it.pct = if (libres.isEmpty()) 0.0 else resto / libres.size }
    }

    private fun refrescar() {
        actualizando = true
        val n = filas.count { it.participa }
        for (fila in filas) {
            val vista = fila.vista
            vista.participa.isChecked = fila.participa
            val pctMostrar = if (modoIgual) {
                if (fila.participa && n > 0) 100.0 / n else 0.0
            } else {
                fila.pct
            }
            vista.pct.text = getString(R.string.pct_fmt, pctMostrar.roundToInt())
            vista.fader.visibility = if (modoIgual) View.GONE else View.VISIBLE
            vista.btnFijar.visibility = if (modoIgual) View.GONE else View.VISIBLE
            vista.fader.isEnabled = fila.participa && !fila.fijado
            vista.fader.value = pctMostrar.coerceIn(0.0, 100.0).roundToInt().toFloat()
            vista.btnFijar.setImageResource(
                if (fila.fijado) R.drawable.ic_pin else R.drawable.ic_pin_off
            )
        }
        actualizando = false
    }

    // ── Guardado ──────────────────────────────────────────────────

    private fun guardar() {
        val gastado = Dinero.parsear(binding.campoGastado.text?.toString().orEmpty())
        if (gastado == null) {
            Toast.makeText(this, R.string.error_gastado, Toast.LENGTH_SHORT).show()
            return
        }
        val participantes = filas.filter { it.participa }
        if (participantes.isEmpty()) {
            Toast.makeText(this, R.string.error_participantes, Toast.LENGTH_SHORT).show()
            return
        }
        val presupuestado = Dinero.parsear(binding.campoPresupuestado.text?.toString().orEmpty())
        val pagado = Dinero.parsear(binding.campoPagado.text?.toString().orEmpty())
        val concepto = binding.campoConcepto.text?.toString().orEmpty().trim()

        // Puntos básicos que suman exactamente 10000
        val puntos: List<Int> = if (modoIgual) {
            Calculadora.puntosIguales(participantes.size)
        } else {
            val total = participantes.sumOf { it.pct }
            val crudos = if (total <= 0.0) {
                Calculadora.puntosIguales(participantes.size)
            } else {
                participantes.map { (it.pct * 10000.0 / total).roundToInt() }
            }
            val diferencia = 10000 - crudos.sum()
            val indiceMayor = crudos.indices.maxByOrNull { crudos[it] } ?: 0
            crudos.mapIndexed { i, pb ->
                if (i == indiceMayor) (pb + diferencia).coerceAtLeast(0) else pb
            }
        }

        lifecycleScope.launch {
            val dao = AppDatabase.get(this@ApunteActivity).dao()
            val id: Long
            val ahora = System.currentTimeMillis()
            if (apunteId == 0L) {
                id = dao.insertarApunte(
                    Apunte(
                        eventoId = eventoId,
                        uuid = apunteUuid,
                        concepto = concepto,
                        pagadorId = pagadorId,
                        presupuestadoCents = presupuestado,
                        gastadoCents = gastado,
                        pagadoCents = pagado,
                        repartoIgualitario = modoIgual,
                        categoria = categoria.name,
                        fotoPath = fotoRecibo,
                        modificadoMillis = ahora
                    )
                )
            } else {
                id = apunteId
                val previo = original?.apunte ?: return@launch
                dao.actualizarApunte(
                    previo.copy(
                        concepto = concepto,
                        pagadorId = pagadorId,
                        presupuestadoCents = presupuestado,
                        gastadoCents = gastado,
                        pagadoCents = pagado,
                        repartoIgualitario = modoIgual,
                        categoria = categoria.name,
                        fotoPath = fotoRecibo,
                        modificadoMillis = ahora
                    )
                )
            }
            dao.guardarRepartos(
                id,
                participantes.mapIndexed { i, fila ->
                    Reparto(id, fila.asistente.id, puntos[i])
                }
            )
            finish()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (apunteId != 0L) {
            menuInflater.inflate(R.menu.menu_apunte, menu)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.accionEliminar -> {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.accion_eliminar)
                .setNegativeButton(R.string.accion_cancelar, null)
                .setPositiveButton(R.string.accion_eliminar) { _, _ ->
                    lifecycleScope.launch {
                        val dao = AppDatabase.get(this@ApunteActivity).dao()
                        // Lápida: que la fusión al sincronizar no lo resucite
                        dao.insertarBorrado(ApunteBorrado(eventoId, apunteUuid))
                        dao.eliminarApunte(apunteId)
                        finish()
                    }
                }
                .show()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }
}
