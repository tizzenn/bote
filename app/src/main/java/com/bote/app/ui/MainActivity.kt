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
import com.bote.app.sync.EventoJson
import com.bote.app.sync.SyncCodec
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch

class MainActivity : BaseActivity() {

    companion object {
        const val EXTRA_PAGO_CENTS = "pago_cents"
        const val EXTRA_PAGO_CONCEPTO = "pago_concepto"

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

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            pedirNotificaciones.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        pagoPendienteCents = intent.getLongExtra(EXTRA_PAGO_CENTS, 0)
        pagoPendienteConcepto = intent.getStringExtra(EXTRA_PAGO_CONCEPTO).orEmpty()

        lifecycleScope.launch {
            AppDatabase.get(this@MainActivity).dao().observarEventos().collect {
                eventos = it
                refiltrar()
                ofrecerPagoDetectado()
            }
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
