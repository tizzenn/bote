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

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: EventoAdapter
    private var eventos: List<EventoCompleto> = emptyList()

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

        lifecycleScope.launch {
            AppDatabase.get(this@MainActivity).dao().observarEventos().collect {
                eventos = it
                refiltrar()
            }
        }
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
        adapter.actualizar(filtrados)
        binding.vacio.visibility = if (filtrados.isEmpty()) android.view.View.VISIBLE
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
}
