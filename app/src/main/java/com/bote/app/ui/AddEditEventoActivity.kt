package com.bote.app.ui

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bote.app.BaseActivity
import com.bote.app.R
import com.bote.app.data.AppDatabase
import com.bote.app.data.Asistente
import com.bote.app.data.Calculadora
import com.bote.app.data.Evento
import com.bote.app.data.EventoCompleto
import com.bote.app.data.Modo
import com.bote.app.data.Registro
import com.bote.app.data.Reparto
import com.bote.app.data.TipoRegistro
import com.bote.app.databinding.ActivityAddEditEventoBinding
import com.bote.app.databinding.DialogAsistenteBinding
import com.bote.app.databinding.ItemAsistenteEditBinding
import com.bote.app.notification.NotificationScheduler
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.UUID

class AddEditEventoActivity : BaseActivity() {

    companion object {
        const val EXTRA_EVENTO_ID = "evento_id"
    }

    private lateinit var binding: ActivityAddEditEventoBinding
    private val formatoFecha: DateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM)

    private var eventoId: Long = 0
    private var datos: EventoCompleto? = null
    private var eventoUuid: String = UUID.randomUUID().toString()
    private var fechaMillis: Long = 0
    private var fotoPath: String = ""

    private val asistentes = mutableListOf<Asistente>()
    private val eliminados = mutableListOf<Asistente>()

    private val elegirFoto = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val copia = FotoUtil.copiarFoto(this, uri, eventoUuid)
            if (copia != null) {
                fotoPath = copia
                mostrarFoto()
            }
        }
    }

    private val pedirPermisoContactos = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido ->
        if (concedido) {
            elegirContacto.launch(null)
        } else {
            Toast.makeText(this, R.string.permiso_contactos, Toast.LENGTH_LONG).show()
        }
    }

    private val elegirContacto = registerForActivityResult(
        ActivityResultContracts.PickContact()
    ) { uri: Uri? ->
        if (uri != null) leerContacto(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddEditEventoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        eventoId = intent.getLongExtra(EXTRA_EVENTO_ID, 0)
        supportActionBar?.title = getString(
            if (eventoId == 0L) R.string.titulo_nuevo_evento else R.string.titulo_editar_evento
        )

        binding.btnFoto.setOnClickListener {
            elegirFoto.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
        binding.campoFecha.setOnClickListener { elegirFecha() }
        binding.toggleModo.addOnButtonCheckedListener { _, _, _ ->
            binding.textoModo.setText(
                if (binding.toggleModo.checkedButtonId == R.id.btnRestringido)
                    R.string.modo_restringido_desc
                else
                    R.string.modo_colaborativo_desc
            )
        }
        binding.btnManual.setOnClickListener { dialogoAsistente() }
        binding.btnContactos.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED
            ) {
                elegirContacto.launch(null)
            } else {
                pedirPermisoContactos.launch(Manifest.permission.READ_CONTACTS)
            }
        }
        binding.btnGuardar.setOnClickListener { guardar() }

        // Sugerencias de direcciones ya usadas + abrir en el mapa
        lifecycleScope.launch {
            val direcciones = AppDatabase.get(this@AddEditEventoActivity).dao().ubicaciones()
            if (direcciones.isNotEmpty()) {
                binding.campoUbicacion.setAdapter(
                    ArrayAdapter(
                        this@AddEditEventoActivity,
                        android.R.layout.simple_list_item_1, direcciones
                    )
                )
            }
        }
        binding.campoUbicacionLayout.setEndIconOnClickListener {
            val direccion = binding.campoUbicacion.text?.toString().orEmpty().trim()
            if (direccion.isNotBlank()) {
                val intento = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("geo:0,0?q=${Uri.encode(direccion)}")
                )
                if (intento.resolveActivity(packageManager) != null) {
                    startActivity(intento)
                }
            }
        }

        if (eventoId == 0L) {
            binding.toggleModo.check(R.id.btnColaborativo)
            fechaMillis = hoy()
            binding.campoFecha.setText(formatoFecha.format(Date(fechaMillis)))
            // El creador del evento entra automáticamente como asistente.
            asistentes.add(
                Asistente(nombre = getString(R.string.asistente_yo), esCreador = true)
            )
            pintarAsistentes()
            pintarAvatarEvento()
        } else {
            cargar()
        }
    }

    private fun hoy(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 12)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun cargar() {
        lifecycleScope.launch {
            val completo = AppDatabase.get(this@AddEditEventoActivity).dao()
                .eventoCompleto(eventoId) ?: run { finish(); return@launch }
            datos = completo
            val evento = completo.evento
            eventoUuid = evento.uuid
            fechaMillis = evento.fechaMillis
            fotoPath = evento.fotoPath

            binding.campoTitulo.setText(evento.titulo)
            binding.campoDescripcion.setText(evento.descripcion)
            binding.campoFecha.setText(formatoFecha.format(Date(fechaMillis)))
            binding.campoUbicacion.setText(evento.ubicacion)
            binding.toggleModo.check(
                if (evento.esRestringido) R.id.btnRestringido else R.id.btnColaborativo
            )
            asistentes.clear()
            asistentes.addAll(completo.asistentes)
            pintarAsistentes()
            mostrarFoto()
        }
    }

    private fun mostrarFoto() {
        if (FotoUtil.cargar(binding.fotoPreview, fotoPath)) {
            binding.fotoPreview.visibility = View.VISIBLE
            binding.avatarEvento.visibility = View.GONE
        } else {
            binding.fotoPreview.visibility = View.GONE
            binding.avatarEvento.visibility = View.VISIBLE
            pintarAvatarEvento()
        }
    }

    private fun pintarAvatarEvento() {
        val nombre = binding.campoTitulo.text?.toString().orEmpty()
        AvatarUtil.aplicar(binding.avatarEvento, nombre.ifBlank { "$" })
    }

    private fun elegirFecha() {
        val calendario = Calendar.getInstance()
        if (fechaMillis > 0) calendario.timeInMillis = fechaMillis
        DatePickerDialog(
            this,
            { _, anio, mes, dia ->
                val elegido = Calendar.getInstance().apply {
                    set(anio, mes, dia, 12, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                fechaMillis = elegido.timeInMillis
                binding.campoFecha.setText(formatoFecha.format(Date(fechaMillis)))
            },
            calendario.get(Calendar.YEAR),
            calendario.get(Calendar.MONTH),
            calendario.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    // ── Asistentes ────────────────────────────────────────────────

    private fun pintarAsistentes() {
        binding.listaAsistentes.removeAllViews()
        for (asistente in asistentes) {
            val fila = ItemAsistenteEditBinding.inflate(
                layoutInflater, binding.listaAsistentes, false
            )
            val nombre = asistente.nombre.ifBlank { getString(R.string.asistente_sin_nombre) }
            AvatarUtil.aplicar(fila.avatar, nombre)
            fila.nombre.text = if (asistente.esCreador)
                "$nombre · ${getString(R.string.asistente_admin)}" else nombre
            val contacto = listOf(asistente.telefono, asistente.email)
                .filter { it.isNotBlank() }.joinToString(" · ")
            fila.detalle.text = contacto
            fila.detalle.visibility = if (contacto.isBlank()) View.GONE else View.VISIBLE
            if (asistente.esCreador) {
                fila.btnQuitar.visibility = View.INVISIBLE
            } else {
                fila.btnQuitar.setOnClickListener {
                    asistentes.remove(asistente)
                    if (asistente.id != 0L) eliminados.add(asistente)
                    pintarAsistentes()
                }
            }
            // Tocar la fila permite editar los datos del asistente
            fila.root.setOnClickListener { dialogoAsistente(asistente) }
            binding.listaAsistentes.addView(fila.root)
        }
    }

    /** Alta de asistente o, si se pasa uno existente, edición de sus datos. */
    private fun dialogoAsistente(existente: Asistente? = null) {
        val vista = DialogAsistenteBinding.inflate(layoutInflater)
        if (existente != null) {
            vista.campoNombre.setText(existente.nombre)
            vista.campoTelefono.setText(existente.telefono)
            vista.campoEmail.setText(existente.email)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(if (existente == null) R.string.anadir_manual else R.string.accion_editar)
            .setView(vista.root)
            .setNegativeButton(R.string.accion_cancelar, null)
            .setPositiveButton(R.string.accion_guardar) { _, _ ->
                val nombre = vista.campoNombre.text?.toString().orEmpty().trim()
                val telefono = vista.campoTelefono.text?.toString().orEmpty().trim()
                val email = vista.campoEmail.text?.toString().orEmpty().trim()
                if (existente == null) {
                    agregarAsistente(
                        Asistente(nombre = nombre, telefono = telefono, email = email)
                    )
                } else {
                    val indice = asistentes.indexOf(existente)
                    if (indice >= 0) {
                        asistentes[indice] = existente.copy(
                            nombre = nombre, telefono = telefono, email = email
                        )
                        pintarAsistentes()
                    }
                }
            }
            .show()
    }

    /**
     * El asistente nuevo entra en los apuntes ya existentes con porcentaje 0
     * (visible pero sin asumir costes) y como uno más a partir de su inclusión.
     */
    private fun agregarAsistente(asistente: Asistente) {
        asistentes.add(asistente)
        pintarAsistentes()
    }

    private fun leerContacto(uri: Uri) {
        var nombre = ""
        var contactoId = ""
        contentResolver.query(
            uri,
            arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME),
            null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                contactoId = cursor.getString(0)
                nombre = cursor.getString(1).orEmpty()
            }
        }
        if (contactoId.isEmpty()) return

        var telefono = ""
        contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactoId), null
        )?.use { cursor ->
            if (cursor.moveToFirst()) telefono = cursor.getString(0).orEmpty()
        }

        var email = ""
        contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS),
            "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?",
            arrayOf(contactoId), null
        )?.use { cursor ->
            if (cursor.moveToFirst()) email = cursor.getString(0).orEmpty()
        }

        agregarAsistente(Asistente(nombre = nombre, telefono = telefono, email = email))
    }

    // ── Guardado ──────────────────────────────────────────────────

    private fun guardar() {
        if (fechaMillis == 0L) {
            Toast.makeText(this, R.string.error_fecha, Toast.LENGTH_SHORT).show()
            return
        }
        if (asistentes.isEmpty()) {
            Toast.makeText(this, R.string.error_sin_asistentes, Toast.LENGTH_SHORT).show()
            return
        }
        val modo = if (binding.toggleModo.checkedButtonId == R.id.btnRestringido)
            Modo.RESTRINGIDO else Modo.COLABORATIVO
        val titulo = binding.campoTitulo.text?.toString().orEmpty().trim()
        val descripcion = binding.campoDescripcion.text?.toString().orEmpty().trim()
        val ubicacion = binding.campoUbicacion.text?.toString().orEmpty().trim()

        lifecycleScope.launch {
            val dao = AppDatabase.get(this@AddEditEventoActivity).dao()

            // No se puede quitar a quien tiene apuntes pagados a su nombre.
            for (asistente in eliminados) {
                if (dao.apuntesQuePaga(asistente.id) > 0) {
                    Toast.makeText(
                        this@AddEditEventoActivity,
                        R.string.error_asistente_pagador, Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }
            }

            val guardadoId: Long
            if (eventoId == 0L) {
                guardadoId = dao.insertarEvento(
                    Evento(
                        uuid = eventoUuid,
                        titulo = titulo,
                        descripcion = descripcion,
                        fechaMillis = fechaMillis,
                        ubicacion = ubicacion,
                        fotoPath = fotoPath,
                        modo = modo,
                        soyCreador = true
                    )
                )
            } else {
                guardadoId = eventoId
                val original = datos?.evento ?: return@launch
                dao.actualizarEvento(
                    original.copy(
                        titulo = titulo,
                        descripcion = descripcion,
                        fechaMillis = fechaMillis,
                        ubicacion = ubicacion,
                        fotoPath = fotoPath,
                        modo = modo,
                        modificadoMillis = System.currentTimeMillis()
                    )
                )
                for (asistente in eliminados) {
                    dao.eliminarRepartosDeAsistente(asistente.id)
                    dao.eliminarAsistente(asistente)
                    dao.insertarRegistro(
                        Registro(
                            eventoId = guardadoId,
                            tipo = TipoRegistro.ASISTENTE_FUERA,
                            texto = getString(
                                R.string.reg_asistente_quitado,
                                asistente.nombre.ifBlank { getString(R.string.asistente_sin_nombre) }
                            )
                        )
                    )
                }
                dao.insertarRegistro(
                    Registro(
                        eventoId = guardadoId,
                        tipo = TipoRegistro.EVENTO,
                        texto = getString(R.string.reg_evento_editado)
                    )
                )
            }

            if (eventoId == 0L) {
                dao.insertarRegistro(
                    Registro(
                        eventoId = guardadoId,
                        tipo = TipoRegistro.EVENTO,
                        texto = getString(R.string.reg_evento_creado)
                    )
                )
            }

            var miAsistenteId = datos?.evento?.miAsistenteId ?: 0L
            val idsNuevos = mutableListOf<Long>()
            for (asistente in asistentes) {
                if (asistente.id == 0L) {
                    val id = dao.insertarAsistente(asistente.copy(eventoId = guardadoId))
                    if (asistente.esCreador) miAsistenteId = id
                    idsNuevos.add(id)
                    val nombre = asistente.nombre.ifBlank {
                        getString(R.string.asistente_sin_nombre)
                    }
                    dao.insertarRegistro(
                        Registro(
                            eventoId = guardadoId,
                            tipo = TipoRegistro.ASISTENTE,
                            texto = getString(R.string.reg_asistente_nuevo, nombre)
                        )
                    )
                } else {
                    dao.actualizarAsistente(asistente)
                }
            }
            val evento = dao.evento(guardadoId)
            if (evento != null && evento.miAsistenteId != miAsistenteId) {
                dao.actualizarEvento(evento.copy(miAsistenteId = miAsistenteId))
            }

            // Los asistentes nuevos entran con 0% en los apuntes ya existentes:
            // aparecen en el reparto pero solo asumen costes desde su inclusión.
            if (idsNuevos.isNotEmpty()) {
                val apuntesPrevios = datos?.apuntes.orEmpty()
                for (ac in apuntesPrevios) {
                    val yaEnReparto = ac.repartos.map { it.asistenteId }.toSet()
                    val faltantes = idsNuevos.filter { !yaEnReparto.contains(it) }
                    if (faltantes.isNotEmpty()) {
                        dao.insertarRepartos(
                            faltantes.map { Reparto(ac.apunte.id, it, 0) }
                        )
                    }
                }
            }

            dao.evento(guardadoId)?.let { guardado ->
                val completo = dao.eventoCompleto(guardadoId)
                NotificationScheduler.reprogramar(
                    this@AddEditEventoActivity, guardado,
                    pagosPendientes = guardado.cerrado && completo?.todosLiquidados == false
                )
            }
            finish()
        }
    }
}
