package com.bote.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bote.app.BaseActivity
import com.bote.app.R
import com.bote.app.config.Ajustes
import com.bote.app.data.AppDatabase
import com.bote.app.data.EventoCompleto
import com.bote.app.data.Registro
import com.bote.app.data.TipoRegistro
import com.bote.app.databinding.ActivityMainBinding
import com.bote.app.notification.NotificationScheduler
import com.bote.app.sync.EventoJson
import com.bote.app.sync.SyncCodec
import com.bote.app.sync.SyncRemoto
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch

class MainActivity : BaseActivity() {

    companion object {
        const val EXTRA_PAGO_CENTS = "pago_cents"
        const val EXTRA_PAGO_CONCEPTO = "pago_concepto"
        const val EXTRA_LIQUIDA_EVENTOS = "liquida_eventos"
        const val EXTRA_LIQUIDA_ETIQUETAS = "liquida_etiquetas"

        /** Clave persistida → etiqueta; el orden de la lista es el del menú. */
        val ORDENES = listOf(
            "FECHA" to R.string.orden_fecha,
            "CREACION" to R.string.orden_creacion,
            "ASISTENTES" to R.string.orden_asistentes,
            "CAROS" to R.string.orden_caros
        )

        /** Aplica el criterio de orden a una lista ya filtrada. */
        fun ordenar(
            lista: List<EventoCompleto>, clave: String
        ): List<EventoCompleto> = when (clave) {
            "CREACION" -> lista.sortedByDescending { it.evento.creadoMillis }
            "ASISTENTES" -> lista.sortedByDescending { it.asistentes.size }
            "CAROS" -> lista.sortedByDescending { it.totalGastadoCents }
            else -> lista.sortedByDescending { it.evento.fechaMillis }
        }
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: EventoAdapter
    private var eventos: List<EventoCompleto> = emptyList()

    /** Pago detectado en notificaciones, pendiente de elegir evento. */
    private var pagoPendienteCents: Long = 0
    private var pagoPendienteConcepto: String = ""

    /** Liquidación detectada (Bizum saliente) pendiente de confirmar. */
    private var liquidaEventos: LongArray = LongArray(0)
    private var liquidaEtiquetas: Array<String> = emptyArray()

    private var sincronizandoTodos = false

    private val pedirNotificaciones =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val importarArchivo =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) importarDesdeArchivo(uri)
        }

    private val escanearQr = registerForActivityResult(ScanContract()) { resultado ->
        val contenido = resultado.contents
        if (contenido != null) importarTexto(contenido)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        adapter = EventoAdapter { datos ->
            startActivity(
                Intent(this, EventoDetalleActivity::class.java)
                    .putExtra(EventoDetalleActivity.EXTRA_EVENTO_ID, datos.evento.id)
            )
        }
        binding.listaEventos.layoutManager = LinearLayoutManager(this)
        binding.listaEventos.adapter = adapter

        binding.fabNuevo.setOnClickListener {
            startActivity(Intent(this, AddEditEventoActivity::class.java))
        }

        binding.grupoFiltros.setOnCheckedStateChangeListener { _, _ -> refiltrar() }

        binding.refrescar.setOnRefreshListener { sincronizarTodos(desdeGesto = true) }

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            pedirNotificaciones.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        pagoPendienteCents = intent.getLongExtra(EXTRA_PAGO_CENTS, 0)
        pagoPendienteConcepto = intent.getStringExtra(EXTRA_PAGO_CONCEPTO).orEmpty()
        liquidaEventos = intent.getLongArrayExtra(EXTRA_LIQUIDA_EVENTOS) ?: LongArray(0)
        liquidaEtiquetas = intent.getStringArrayExtra(EXTRA_LIQUIDA_ETIQUETAS) ?: emptyArray()

        lifecycleScope.launch {
            migrarNombreCreador()
            AppDatabase.get(this@MainActivity).dao().observarEventos().collect {
                eventos = it
                refiltrar()
                ofrecerPagoDetectado()
                ofrecerLiquidacion()
            }
        }
    }

    /**
     * Migración suave del nombre del creador: los eventos antiguos guardaban el
     * literal "Yo"/"Me"; si ya hay un nombre configurado, se renombra el creador.
     */
    private suspend fun migrarNombreCreador() {
        val nombre = Ajustes.nombreUsuario(this)
        if (nombre.isBlank()) return
        val dao = AppDatabase.get(this).dao()
        val literales = setOf("Yo", "Me", getString(R.string.asistente_yo))
        for (evento in dao.todosEventos()) {
            if (!evento.soyCreador) continue
            val completo = dao.eventoCompleto(evento.id) ?: continue
            val creador = completo.asistentes.firstOrNull { it.esCreador } ?: continue
            if (creador.nombre in literales && creador.nombre != nombre) {
                dao.actualizarAsistente(creador.copy(nombre = nombre))
            }
        }
    }

    /** Con una liquidación detectada, confirma marcarla (o elige entre varias). */
    private fun ofrecerLiquidacion() {
        if (liquidaEventos.isEmpty()) return
        val ids = liquidaEventos
        val etiquetas = liquidaEtiquetas
        liquidaEventos = LongArray(0)
        liquidaEtiquetas = emptyArray()

        if (ids.size == 1) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.liquidacion_confirmar_titulo)
                .setMessage(etiquetas.firstOrNull().orEmpty())
                .setNegativeButton(R.string.accion_cancelar, null)
                .setPositiveButton(R.string.accion_guardar) { _, _ -> marcarLiquidacion(ids[0]) }
                .show()
        } else {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.liquidacion_elegir)
                .setItems(etiquetas) { _, indice -> marcarLiquidacion(ids[indice]) }
                .setNegativeButton(R.string.accion_cancelar, null)
                .show()
        }
    }

    private fun marcarLiquidacion(eventoId: Long) {
        lifecycleScope.launch {
            val dao = AppDatabase.get(this@MainActivity).dao()
            val completo = dao.eventoCompleto(eventoId) ?: return@launch
            val mi = completo.miAsistente() ?: return@launch
            if (mi.liquidado) return@launch
            dao.actualizarAsistente(
                mi.copy(liquidado = true, liquidadoMillis = System.currentTimeMillis())
            )
            val nombre = mi.nombre.ifBlank { getString(R.string.asistente_sin_nombre) }
            dao.insertarRegistro(
                Registro(
                    eventoId = eventoId,
                    tipo = TipoRegistro.PAGO,
                    texto = getString(R.string.reg_pago_marcado, nombre)
                )
            )
            val actualizado = dao.eventoCompleto(eventoId) ?: return@launch
            NotificationScheduler.reprogramar(
                this@MainActivity, actualizado.evento,
                pagosPendientes = actualizado.evento.cerrado && !actualizado.todosLiquidados
            )
        }
    }

    /** Con un pago detectado pendiente, pregunta en qué evento apuntarlo. */
    private fun ofrecerPagoDetectado() {
        if (pagoPendienteCents <= 0) return
        val cents = pagoPendienteCents
        val concepto = pagoPendienteConcepto
        pagoPendienteCents = 0

        val candidatos = eventos.filter { datos ->
            !datos.evento.cerrado &&
                (datos.evento.soyCreador || !datos.evento.esRestringido)
        }
        if (candidatos.isEmpty()) return
        val nombres = candidatos.map { datos ->
            datos.evento.titulo.ifBlank {
                java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
                    .format(java.util.Date(datos.evento.fechaMillis))
            }
        }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.elegir_evento)
            .setItems(nombres) { _, indice ->
                startActivity(
                    Intent(this, ApunteActivity::class.java)
                        .putExtra(ApunteActivity.EXTRA_EVENTO_ID, candidatos[indice].evento.id)
                        .putExtra(ApunteActivity.EXTRA_GASTADO_PREVIO, cents)
                        .putExtra(ApunteActivity.EXTRA_CONCEPTO_PREVIO, concepto)
                )
            }
            .setNegativeButton(R.string.accion_cancelar, null)
            .show()
    }

    /**
     * Visibilidad del listado general: cuando pagas tu parte, el evento
     * desaparece de "Activos"; solo el creador lo sigue viendo hasta que
     * todos liquiden y la cuenta quede a cero (entonces pasa a "Saldados").
     */
    private fun refiltrar() {
        val filtrados = when (binding.grupoFiltros.checkedChipId) {
            R.id.chipSaldados -> eventos.filter { it.saldado }
            R.id.chipTodos -> eventos
            else -> eventos.filter { datos ->
                !datos.saldado &&
                    (datos.evento.soyCreador || datos.miAsistente()?.liquidado != true)
            }
        }
        val ordenados = ordenar(filtrados, Ajustes.ordenEventos(this))
        adapter.actualizar(ordenados)
        binding.vacio.visibility = if (ordenados.isEmpty()) android.view.View.VISIBLE
        else android.view.View.GONE
    }

    /**
     * Sincroniza todos los eventos con servidor y avisa del resumen. Se lanza
     * desde el botón del menú o al deslizar para refrescar. La lista se repinta
     * sola por el Flow al fusionar los cambios.
     */
    private fun sincronizarTodos(desdeGesto: Boolean) {
        if (sincronizandoTodos) {
            if (desdeGesto) binding.refrescar.isRefreshing = false
            return
        }
        val sincronizables = eventos.filter { it.evento.sincronizable }
        if (sincronizables.isEmpty()) {
            binding.refrescar.isRefreshing = false
            Toast.makeText(this, R.string.sync_nada, Toast.LENGTH_SHORT).show()
            return
        }
        sincronizandoTodos = true
        if (!desdeGesto) binding.refrescar.isRefreshing = true
        lifecycleScope.launch {
            val dao = AppDatabase.get(this@MainActivity).dao()
            var ok = 0
            var fallo = 0
            for (ec in sincronizables) {
                val res = try {
                    SyncRemoto.sincronizar(applicationContext, dao, ec.evento.id)
                } catch (e: Exception) {
                    SyncRemoto.Resultado.SinRed
                }
                if (res is SyncRemoto.Resultado.Ok) {
                    ok++
                    Ajustes.guardarUltimaSync(
                        this@MainActivity, ec.evento.uuid, System.currentTimeMillis()
                    )
                } else {
                    fallo++
                }
            }
            binding.refrescar.isRefreshing = false
            sincronizandoTodos = false
            val mensaje = if (fallo == 0) getString(R.string.sync_ok)
            else getString(R.string.sync_resumen, ok, fallo)
            Toast.makeText(this@MainActivity, mensaje, Toast.LENGTH_LONG).show()
        }
    }

    private fun elegirOrigenImportacion() {
        val opciones = arrayOf(
            getString(R.string.compartir_archivo),
            getString(R.string.escanear_qr)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.importar_desde)
            .setItems(opciones) { _, indice ->
                if (indice == 0) {
                    importarArchivo.launch(
                        arrayOf("application/json", "application/octet-stream", "text/plain")
                    )
                } else {
                    escanearQr.launch(
                        ScanOptions()
                            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            .setBeepEnabled(false)
                            .setPrompt(getString(R.string.escanear_qr))
                    )
                }
            }
            .show()
    }

    private fun importarDesdeArchivo(uri: android.net.Uri) {
        lifecycleScope.launch {
            val texto = try {
                contentResolver.openInputStream(uri)?.use {
                    it.readBytes().toString(Charsets.UTF_8)
                }
            } catch (e: Exception) {
                null
            }
            if (texto == null) {
                Toast.makeText(this@MainActivity, R.string.importar_error, Toast.LENGTH_LONG).show()
            } else {
                importarTexto(texto)
            }
        }
    }

    private fun importarTexto(texto: String) {
        lifecycleScope.launch {
            try {
                val json = SyncCodec.decodificar(texto)
                val dao = AppDatabase.get(this@MainActivity).dao()
                val eventoId = EventoJson.importar(dao, json)
                dao.insertarRegistro(
                    Registro(
                        eventoId = eventoId,
                        tipo = TipoRegistro.SYNC,
                        texto = getString(R.string.reg_importado)
                    )
                )
                Toast.makeText(this@MainActivity, R.string.importar_ok, Toast.LENGTH_SHORT).show()
                startActivity(
                    Intent(this@MainActivity, EventoDetalleActivity::class.java)
                        .putExtra(EventoDetalleActivity.EXTRA_EVENTO_ID, eventoId)
                )
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, R.string.importar_error, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.accionOrdenar -> {
            elegirOrden()
            true
        }
        R.id.accionSincronizar -> {
            sincronizarTodos(desdeGesto = false)
            true
        }
        R.id.accionImportar -> {
            elegirOrigenImportacion()
            true
        }
        R.id.accionAjustes -> {
            startActivity(Intent(this, SettingsActivity::class.java))
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun elegirOrden() {
        val etiquetas = ORDENES.map { getString(it.second) }.toTypedArray()
        val actual = ORDENES.indexOfFirst { it.first == Ajustes.ordenEventos(this) }
            .coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.orden_titulo)
            .setSingleChoiceItems(etiquetas, actual) { dialogo, indice ->
                Ajustes.guardarOrdenEventos(this, ORDENES[indice].first)
                refiltrar()
                dialogo.dismiss()
            }
            .setNegativeButton(R.string.accion_cancelar, null)
            .show()
    }
}
