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
import com.bote.app.databinding.ActivityMainBinding
import com.bote.app.sync.EventoJson
import kotlinx.coroutines.launch

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: EventoAdapter
    private var eventos: List<EventoCompleto> = emptyList()

    private val pedirNotificaciones =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val importarArchivo =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) importar(uri)
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

    private fun importar(uri: android.net.Uri) {
        lifecycleScope.launch {
            try {
                val texto = contentResolver.openInputStream(uri)?.use {
                    it.readBytes().toString(Charsets.UTF_8)
                } ?: throw IllegalStateException("Sin contenido")
                val dao = AppDatabase.get(this@MainActivity).dao()
                val eventoId = EventoJson.importar(dao, texto)
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
            importarArchivo.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
            true
        }
        R.id.accionAjustes -> {
            startActivity(Intent(this, SettingsActivity::class.java))
            true
        }
        else -> super.onOptionsItemSelected(item)
    }
}
