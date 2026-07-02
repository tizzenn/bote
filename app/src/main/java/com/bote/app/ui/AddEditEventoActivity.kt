package com.bote.app.ui

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.View
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

    /** UUID de asistentes nuevos que asumen los gastos ya apuntados. */
    private val solidarios = mutableSetOf<String>()

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
                    solidarios.remove(asistente.uuid)
                    if (asistente.id != 0L) eliminados.add(asistente)
                    pintarAsistentes()
                }
            }
            binding.listaAsistentes.addView(fila.root)
        }
    }

    private fun dialogoAsistente() {
        val vista = DialogAsistenteBinding.inflate(layoutInflater)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.anadir_manual)
            .setView(vista.root)
            .setNegativeButton(R.string.accion_cancelar, null)
            .setPositiveButton(R.string.accion_guardar) { _, _ ->
                agregarAsistente(
                    Asistente(
                        nombre = vista.campoNombre.text?.toString().orEmpty().trim(),
                        telefono = vista.campoTelefono.text?.toString().orEmpty().trim(),
                        email = vista.campoEmail.text?.toString().orEmpty().trim()
                    )
                )
            }
            .show()
    }

    /**
     * Si el evento ya tiene apuntes, se pregunta si el nuevo asistente asume
     * los costes desde el principio (solidario) o empieza limpio.
     */
    private fun agregarAsistente(asistente: Asistente) {
        asistentes.add(asistente)
        pintarAsistentes()
        val hayApuntes = datos?.apuntes?.isNotEmpty() == true
        if (hayApuntes) {
            val nombre = asistente.nombre.ifBlank { getString(R.string.asistente_sin_nombre) }
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_gastos_titulo)
                .setMessage(getString(R.string.dialog_gastos_msg, nombre))
                .setPositiveButton(R.string.gastos_solidario) { _, _ ->
                    solidarios.add(asistente.uuid)
                }
                .setNegativeButton(R.string.gastos_limpio, null)
                .show()
        }
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
            val idsSolidarios = mutableListOf<Long>()
            for (asistente in asistentes) {
                if (asistente.id == 0L) {
                    val id = dao.insertarAsistente(asistente.copy(eventoId = guardadoId))
                    if (asistente.esCreador) miAsistenteId = id
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
                    if (solidarios.contains(asistente.uuid)) {
                        idsSolidarios.add(id)
                        dao.insertarRegistro(
                            Registro(
                                eventoId = guardadoId,
                                tipo = TipoRegistro.ASISTENTE,
                                texto = getString(R.string.reg_asistente_solidario, nombre)
                            )
                        )
                    }
                } else {
                    dao.actualizarAsistente(asistente)
                }
            }
            val evento = dao.evento(guardadoId)
            if (evento != null && evento.miAsistenteId != miAsistenteId) {
                dao.actualizarEvento(evento.copy(miAsistenteId = miAsistenteId))
            }

            // Los solidarios entran a partes iguales en los apuntes igualitarios previos.
            if (idsSolidarios.isNotEmpty()) {
                val completo = dao.eventoCompleto(guardadoId)
                if (completo != null) {
                    for (ac in completo.apuntes) {
                        if (!ac.apunte.repartoIgualitario) continue
                        val participantes =
                            (ac.repartos.map { it.asistenteId } + idsSolidarios).distinct()
                        val puntos = Calculadora.puntosIguales(participantes.size)
                        dao.guardarRepartos(
                            ac.apunte.id,
                            participantes.mapIndexed { i, asistenteId ->
                                Reparto(ac.apunte.id, asistenteId, puntos[i])
                            }
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
