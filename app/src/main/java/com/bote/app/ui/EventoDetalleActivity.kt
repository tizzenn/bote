package com.bote.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.bote.app.BaseActivity
import com.bote.app.R
import com.bote.app.calendario.Calendario
import com.bote.app.config.Ajustes
import com.bote.app.data.AppDatabase
import com.bote.app.data.CategoriaApunte
import com.bote.app.data.Dinero
import com.bote.app.data.EventoCompleto
import com.bote.app.data.Registro
import com.bote.app.data.TipoRegistro
import com.bote.app.databinding.ActivityEventoDetalleBinding
import com.bote.app.databinding.ItemApunteBinding
import com.bote.app.databinding.ItemAsistenteMiniBinding
import com.bote.app.databinding.DialogQrBinding
import com.bote.app.notification.NotificationHelper
import com.bote.app.notification.NotificationScheduler
import com.bote.app.sync.EventoJson
import com.bote.app.sync.QrUtil
import com.bote.app.sync.SyncCodec
import com.bote.app.sync.SyncRemoto
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

class EventoDetalleActivity : BaseActivity() {

    companion object {
        const val EXTRA_EVENTO_ID = "evento_id"
    }

    private lateinit var binding: ActivityEventoDetalleBinding
    private val formatoFecha: DateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM)
    private var eventoId: Long = 0
    private var datos: EventoCompleto? = null
    private var identidadPreguntada = false
    private var sincronizando = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEventoDetalleBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        eventoId = intent.getLongExtra(EXTRA_EVENTO_ID, 0)

        binding.btnCalendario.setOnClickListener {
            datos?.let {
                startActivity(
                    Calendario.intentInsertar(
                        it.evento, formatoFecha.format(Date(it.evento.fechaMillis))
                    )
                )
            }
        }
        binding.btnInforme.setOnClickListener {
            startActivity(
                Intent(this, InformeActivity::class.java)
                    .putExtra(InformeActivity.EXTRA_EVENTO_ID, eventoId)
            )
        }
        binding.btnCompartir.setOnClickListener { compartirEvento() }
        binding.btnCandado.setOnClickListener { alternarCandado() }
        binding.fabApunte.setOnClickListener {
            startActivity(
                Intent(this, ApunteActivity::class.java)
                    .putExtra(ApunteActivity.EXTRA_EVENTO_ID, eventoId)
            )
        }
    }

    override fun onResume() {
        super.onResume()
        cargar()
        sincronizarNube()
    }

    /** Sincronización automática con la nube, si este evento tiene servidor. */
    private fun sincronizarNube() {
        if (sincronizando) return
        sincronizando = true
        lifecycleScope.launch {
            try {
                val dao = AppDatabase.get(this@EventoDetalleActivity).dao()
                val habiaRemoto = SyncRemoto.sincronizar(dao, eventoId)
                if (habiaRemoto) cargar()
            } catch (e: Exception) {
                // sin red o servidor caído: la app sigue en local
            } finally {
                sincronizando = false
            }
        }
    }

    private fun puedeEditar(datos: EventoCompleto): Boolean =
        datos.evento.soyCreador || !datos.evento.esRestringido

    private fun cargar() {
        lifecycleScope.launch {
            val completo = AppDatabase.get(this@EventoDetalleActivity).dao()
                .eventoCompleto(eventoId) ?: run { finish(); return@launch }
            datos = completo
            pintar(completo)
            invalidateOptionsMenu()
            preguntarIdentidad(completo)
        }
    }

    private fun pintar(completo: EventoCompleto) {
        val evento = completo.evento
        val fecha = formatoFecha.format(Date(evento.fechaMillis))

        supportActionBar?.title = evento.titulo.ifBlank { fecha }
        binding.titulo.text = evento.titulo.ifBlank { fecha }
        binding.fechaLugar.text =
            if (evento.ubicacion.isBlank()) fecha else "$fecha · ${evento.ubicacion}"
        binding.descripcion.text = evento.descripcion
        binding.descripcion.visibility =
            if (evento.descripcion.isBlank()) View.GONE else View.VISIBLE
        binding.foto.visibility =
            if (FotoUtil.cargar(binding.foto, evento.fotoPath)) View.VISIBLE else View.GONE

        val modoTexto = getString(
            if (evento.esRestringido) R.string.modo_restringido else R.string.modo_colaborativo
        )
        val estadoTexto = when {
            completo.saldado -> getString(R.string.evento_saldado)
            evento.cerrado -> getString(R.string.evento_cerrado)
            else -> getString(R.string.total_fmt, Dinero.formatear(completo.totalGastadoCents))
        }
        binding.estado.text = "$modoTexto · $estadoTexto"
        binding.estado.setTextColor(
            ContextCompat.getColor(
                this,
                when {
                    completo.saldado -> R.color.saldo_positivo
                    evento.cerrado -> R.color.saldo_negativo
                    else -> R.color.text_secondary
                }
            )
        )

        // Totales de la ficha: presupuestado, gastado y saldado
        val presupuestado = completo.apuntes.sumOf { it.apunte.presupuestadoCents ?: 0L }
        val saldos = com.bote.app.data.Calculadora.saldos(completo)
        val saldado = saldos.filter { it.asistente.liquidado }.sumOf { it.correspondeCents }
        binding.totales.text = listOf(
            getString(R.string.presupuestado_fmt, Dinero.formatear(presupuestado)),
            getString(R.string.gastado_fmt, Dinero.formatear(
                completo.apuntes.sumOf { it.apunte.gastadoCents }
            )),
            getString(R.string.saldado_fmt, Dinero.formatear(saldado))
        ).joinToString(" · ")

        // Candado: solo el creador cierra o reabre la cuenta
        if (evento.soyCreador) {
            binding.btnCandado.visibility = View.VISIBLE
            binding.btnCandado.setText(
                if (evento.cerrado) R.string.reabrir_cuenta else R.string.cerrar_cuenta
            )
            binding.btnCandado.setIconResource(
                if (evento.cerrado) R.drawable.ic_candado_abierto else R.drawable.ic_candado
            )
        } else {
            binding.btnCandado.visibility = View.GONE
        }

        binding.fabApunte.visibility =
            if (puedeEditar(completo) && !evento.cerrado) View.VISIBLE else View.GONE

        // Asistentes
        binding.listaAsistentes.removeAllViews()
        for (asistente in completo.asistentes) {
            val fila = ItemAsistenteMiniBinding.inflate(
                layoutInflater, binding.listaAsistentes, false
            )
            val nombre = asistente.nombre.ifBlank { getString(R.string.asistente_sin_nombre) }
            AvatarUtil.aplicar(fila.avatar, nombre)
            fila.nombre.text = buildString {
                append(nombre)
                if (asistente.esCreador) append(" · ").append(getString(R.string.asistente_admin))
                if (asistente.id == evento.miAsistenteId && !asistente.esCreador) {
                    append(" · ").append(getString(R.string.asistente_yo))
                }
            }
            fila.check.visibility = if (asistente.liquidado) View.VISIBLE else View.GONE
            binding.listaAsistentes.addView(fila.root)
        }

        // Apuntes
        binding.listaApuntes.removeAllViews()
        binding.sinApuntes.visibility =
            if (completo.apuntes.isEmpty()) View.VISIBLE else View.GONE
        val porId = completo.asistentes.associateBy { it.id }
        for (ac in completo.apuntes.sortedByDescending { it.apunte.fechaMillis }) {
            val apunte = ac.apunte
            val fila = ItemApunteBinding.inflate(layoutInflater, binding.listaApuntes, false)
            val categoria = CategoriaApunte.fromNombre(apunte.categoria)
            fila.icono.setImageResource(categoria.iconoRes)
            fila.concepto.text = apunte.concepto.ifBlank { getString(categoria.nombreRes) }
            val pagador = porId[apunte.pagadorId]?.nombre
                ?.ifBlank { getString(R.string.asistente_sin_nombre) }
                ?: getString(R.string.asistente_sin_nombre)
            fila.pagador.text = getString(R.string.paga_fmt, pagador)
            fila.importes.text = buildString {
                append(getString(R.string.gastado_fmt, Dinero.formatear(apunte.gastadoCents)))
                if (apunte.pagadoCents != null) {
                    append(" · ")
                    append(getString(R.string.pagado_fmt, Dinero.formatear(apunte.pagadoCents)))
                }
                if (apunte.presupuestadoCents != null) {
                    append(" · ")
                    append(
                        getString(
                            R.string.presupuestado_fmt,
                            Dinero.formatear(apunte.presupuestadoCents)
                        )
                    )
                }
            }
            fila.estado.setText(
                if (apunte.estaCerrado) R.string.apunte_cerrado else R.string.apunte_abierto
            )
            fila.estado.setTextColor(
                ContextCompat.getColor(
                    this,
                    if (apunte.estaCerrado) R.color.saldo_positivo else R.color.text_secondary
                )
            )
            fila.indicadorRecibo.visibility =
                if (apunte.fotoPath.isNotBlank()) View.VISIBLE else View.GONE
            if (puedeEditar(completo) && !evento.cerrado) {
                fila.root.setOnClickListener {
                    startActivity(
                        Intent(this, ApunteActivity::class.java)
                            .putExtra(ApunteActivity.EXTRA_EVENTO_ID, eventoId)
                            .putExtra(ApunteActivity.EXTRA_APUNTE_ID, apunte.id)
                    )
                }
            }
            binding.listaApuntes.addView(fila.root)
        }
    }

    /** En eventos importados hay que decir quién eres para el "deja de verse al pagar". */
    private fun preguntarIdentidad(completo: EventoCompleto) {
        if (identidadPreguntada) return
        if (completo.evento.soyCreador || completo.evento.miAsistenteId != 0L) return
        if (completo.asistentes.isEmpty()) return
        identidadPreguntada = true
        val nombres = completo.asistentes.map {
            it.nombre.ifBlank { getString(R.string.asistente_sin_nombre) }
        }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.importar_quien)
            .setItems(nombres) { _, indice ->
                lifecycleScope.launch {
                    val dao = AppDatabase.get(this@EventoDetalleActivity).dao()
                    dao.actualizarEvento(
                        completo.evento.copy(miAsistenteId = completo.asistentes[indice].id)
                    )
                    cargar()
                }
            }
            .show()
    }

    private fun alternarCandado() {
        val completo = datos ?: return
        if (!completo.evento.cerrado) {
            if (completo.apuntes.any { !it.apunte.estaCerrado }) {
                Toast.makeText(this, R.string.error_cerrar_apuntes_abiertos, Toast.LENGTH_LONG)
                    .show()
                return
            }
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.cerrar_cuenta)
                .setMessage(R.string.confirmar_cerrar_cuenta)
                .setNegativeButton(R.string.accion_cancelar, null)
                .setPositiveButton(R.string.cerrar_cuenta) { _, _ -> cerrarCuenta() }
                .show()
        } else {
            lifecycleScope.launch {
                val dao = AppDatabase.get(this@EventoDetalleActivity).dao()
                dao.actualizarEvento(
                    completo.evento.copy(
                        cerrado = false,
                        modificadoMillis = System.currentTimeMillis()
                    )
                )
                dao.insertarRegistro(
                    Registro(
                        eventoId = eventoId,
                        tipo = TipoRegistro.CANDADO,
                        texto = getString(R.string.reg_cuenta_reabierta)
                    )
                )
                dao.evento(eventoId)?.let {
                    NotificationScheduler.reprogramar(this@EventoDetalleActivity, it, false)
                }
                cargar()
            }
        }
    }

    private fun cerrarCuenta() {
        val completo = datos ?: return
        lifecycleScope.launch {
            val dao = AppDatabase.get(this@EventoDetalleActivity).dao()
            dao.actualizarEvento(
                completo.evento.copy(
                    cerrado = true,
                    modificadoMillis = System.currentTimeMillis()
                )
            )
            dao.insertarRegistro(
                Registro(
                    eventoId = eventoId,
                    tipo = TipoRegistro.CANDADO,
                    texto = getString(R.string.reg_cuenta_cerrada)
                )
            )
            val cerrado = dao.evento(eventoId) ?: return@launch
            val actualizado = dao.eventoCompleto(eventoId)
            NotificationScheduler.reprogramar(
                this@EventoDetalleActivity, cerrado,
                pagosPendientes = actualizado?.todosLiquidados == false
            )
            if (Ajustes.notifCierre(this@EventoDetalleActivity)) {
                val nombre = cerrado.titulo.ifBlank { getString(R.string.app_name) }
                NotificationHelper.notificar(
                    this@EventoDetalleActivity,
                    (eventoId * 2 + 1).toInt(),
                    getString(R.string.notif_cierre_titulo, nombre),
                    getString(R.string.notif_cierre_texto),
                    eventoId
                )
            }
            // Informe definitivo al echar el candado
            startActivity(
                Intent(this@EventoDetalleActivity, InformeActivity::class.java)
                    .putExtra(InformeActivity.EXTRA_EVENTO_ID, eventoId)
            )
        }
    }

    private fun compartirEvento() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.compartir_titulo)
            .setMessage(R.string.compartir_ayuda)
            .setNeutralButton(R.string.accion_cancelar, null)
            .setNegativeButton(R.string.compartir_archivo) { _, _ -> compartirArchivo() }
            .setPositiveButton(R.string.compartir_qr) { _, _ -> mostrarQr() }
            .show()
    }

    private fun compartirArchivo() {
        val completo = datos ?: return
        lifecycleScope.launch {
            val dao = AppDatabase.get(this@EventoDetalleActivity).dao()
            val borrados = dao.borradosDeEvento(eventoId)
            val registro = dao.registroDeEvento(eventoId)
            val uri = withContext(Dispatchers.IO) {
                val directorio = File(cacheDir, "compartir").apply { mkdirs() }
                val nombre = completo.evento.titulo.ifBlank { "evento" }
                    .replace(Regex("[^A-Za-z0-9_-]"), "_")
                val archivo = File(directorio, "bote-$nombre.json")
                archivo.writeText(EventoJson.exportar(completo, borrados, registro))
                FileProvider.getUriForFile(
                    this@EventoDetalleActivity,
                    "$packageName.fileprovider",
                    archivo
                )
            }
            val asunto = getString(
                R.string.sync_asunto,
                completo.evento.titulo.ifBlank {
                    formatoFecha.format(Date(completo.evento.fechaMillis))
                }
            )
            val intento = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, asunto)
                putExtra(Intent.EXTRA_TEXT, getString(R.string.sync_explicacion))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intento, asunto))
        }
    }

    /** El evento entero cabe en un QR si no es muy grande; si no, archivo. */
    private fun mostrarQr() {
        val completo = datos ?: return
        lifecycleScope.launch {
            val dao = AppDatabase.get(this@EventoDetalleActivity).dao()
            val borrados = dao.borradosDeEvento(eventoId)
            val carga = withContext(Dispatchers.IO) {
                SyncCodec.comprimir(EventoJson.exportar(completo, borrados))
            }
            if (carga.length > QrUtil.MAX_CARACTERES) {
                Toast.makeText(
                    this@EventoDetalleActivity,
                    R.string.qr_demasiado_grande, Toast.LENGTH_LONG
                ).show()
                compartirArchivo()
                return@launch
            }
            val bitmap = withContext(Dispatchers.IO) { QrUtil.generar(carga, 800) }
            val vista = DialogQrBinding.inflate(layoutInflater)
            vista.imagenQr.setImageBitmap(bitmap)
            MaterialAlertDialogBuilder(this@EventoDetalleActivity)
                .setTitle(R.string.compartir_qr)
                .setView(vista.root)
                .setPositiveButton(R.string.accion_cancelar, null)
                .show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_detalle, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val completo = datos
        menu.findItem(R.id.accionEditar)?.isVisible =
            completo != null && puedeEditar(completo) && !completo.evento.cerrado
        menu.findItem(R.id.accionEliminar)?.isVisible =
            completo?.evento?.soyCreador == true
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.accionEditar -> {
            startActivity(
                Intent(this, AddEditEventoActivity::class.java)
                    .putExtra(AddEditEventoActivity.EXTRA_EVENTO_ID, eventoId)
            )
            true
        }
        R.id.accionRegistro -> {
            startActivity(
                Intent(this, RegistroActivity::class.java)
                    .putExtra(RegistroActivity.EXTRA_EVENTO_ID, eventoId)
            )
            true
        }
        R.id.accionEliminar -> {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.accion_eliminar)
                .setMessage(R.string.confirmar_eliminar_evento)
                .setNegativeButton(R.string.accion_cancelar, null)
                .setPositiveButton(R.string.accion_eliminar) { _, _ ->
                    lifecycleScope.launch {
                        datos?.evento?.let {
                            NotificationScheduler.cancelar(this@EventoDetalleActivity, it)
                        }
                        AppDatabase.get(this@EventoDetalleActivity).dao()
                            .eliminarEvento(eventoId)
                        finish()
                    }
                }
                .show()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }
}
