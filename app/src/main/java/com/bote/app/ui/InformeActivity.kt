package com.bote.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import android.widget.LinearLayout
import com.bote.app.BaseActivity
import com.bote.app.R
import com.bote.app.config.Ajustes
import com.bote.app.data.AppDatabase
import com.bote.app.data.Calculadora
import com.bote.app.data.CategoriaApunte
import com.bote.app.data.Dinero
import com.bote.app.data.EventoCompleto
import com.bote.app.data.Registro
import com.bote.app.data.TipoRegistro
import com.bote.app.databinding.ActivityInformeBinding
import com.bote.app.databinding.ItemBalanceFilaBinding
import com.bote.app.databinding.ItemCategoriaBarraBinding
import com.bote.app.databinding.ItemSaldoBinding
import com.bote.app.databinding.ItemTransferenciaBinding
import com.bote.app.notification.NotificationScheduler
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class InformeActivity : BaseActivity() {

    companion object {
        const val EXTRA_EVENTO_ID = "evento_id"
    }

    private lateinit var binding: ActivityInformeBinding
    private var eventoId: Long = 0
    private var datos: EventoCompleto? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInformeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        eventoId = intent.getLongExtra(EXTRA_EVENTO_ID, 0)
        binding.btnCompartirInforme.setOnClickListener { compartir() }
    }

    override fun onResume() {
        super.onResume()
        cargar()
    }

    private fun cargar() {
        lifecycleScope.launch {
            val completo = AppDatabase.get(this@InformeActivity).dao()
                .eventoCompleto(eventoId) ?: run { finish(); return@launch }
            datos = completo
            pintar(completo)
        }
    }

    private fun nombreDe(asistente: com.bote.app.data.Asistente): String =
        asistente.nombre.ifBlank { getString(R.string.asistente_sin_nombre) }

    private fun pintar(completo: EventoCompleto) {
        val evento = completo.evento
        val fecha = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(evento.fechaMillis))
        supportActionBar?.title = evento.titulo.ifBlank { fecha }

        binding.subtitulo.setText(
            if (evento.cerrado) R.string.informe_definitivo else R.string.informe_parcial
        )
        binding.total.text =
            getString(R.string.total_evento_fmt, Dinero.formatear(completo.totalGastadoCents))

        val saldos = Calculadora.saldos(completo)

        binding.listaSaldos.removeAllViews()
        for (saldo in saldos) {
            val fila = ItemSaldoBinding.inflate(layoutInflater, binding.listaSaldos, false)
            val nombre = nombreDe(saldo.asistente)
            AvatarUtil.aplicar(fila.avatar, nombre)
            fila.nombre.text = nombre
            fila.detalle.text =
                getString(R.string.pago_total_fmt, Dinero.formatear(saldo.pagadoCents)) +
                    " · " +
                    getString(R.string.corresponde_fmt, Dinero.formatear(saldo.correspondeCents))
            when {
                saldo.saldoCents > 0 -> {
                    fila.saldo.text =
                        getString(R.string.saldo_favor_fmt, Dinero.formatear(saldo.saldoCents))
                    fila.saldo.setTextColor(
                        ContextCompat.getColor(this, R.color.saldo_positivo)
                    )
                }
                saldo.saldoCents < 0 -> {
                    fila.saldo.text =
                        getString(R.string.saldo_contra_fmt, Dinero.formatear(-saldo.saldoCents))
                    fila.saldo.setTextColor(
                        ContextCompat.getColor(this, R.color.saldo_negativo)
                    )
                }
                else -> {
                    fila.saldo.setText(R.string.saldo_cero)
                    fila.saldo.setTextColor(
                        ContextCompat.getColor(this, R.color.text_secondary)
                    )
                }
            }

            // Cada uno marca su pago; el creador puede marcar el de cualquiera.
            val puedeMarcar = evento.cerrado &&
                (evento.soyCreador || saldo.asistente.id == evento.miAsistenteId)
            fila.liquidado.isChecked = saldo.asistente.liquidado
            fila.liquidado.isEnabled = puedeMarcar
            fila.liquidado.setOnCheckedChangeListener { _, marcado ->
                marcarLiquidado(saldo.asistente.id, marcado)
            }
            binding.listaSaldos.addView(fila.root)
        }

        pintarBalance(completo, saldos)
        pintarCategorias(completo)

        binding.listaTransferencias.removeAllViews()
        val transferencias = Calculadora.transferencias(saldos)
        if (transferencias.isEmpty()) {
            binding.listaTransferencias.addView(textoTransferencia(getString(R.string.sin_transferencias)))
        } else {
            val cobroActivo = Ajustes.cobroActivo(this)
            for (t in transferencias) {
                val fila = ItemTransferenciaBinding.inflate(
                    layoutInflater, binding.listaTransferencias, false
                )
                fila.texto.text = getString(
                    R.string.transferencia_fmt,
                    nombreDe(t.de), Dinero.formatear(t.cents), nombreDe(t.a)
                )
                // El botón de cobro solo aparece si está activado en Ajustes
                // y la transferencia viene hacia mí.
                if (cobroActivo && t.a.id == evento.miAsistenteId) {
                    fila.btnPedirPago.visibility = View.VISIBLE
                    fila.btnPedirPago.setOnClickListener { pedirPago(completo, t) }
                }
                binding.listaTransferencias.addView(fila.root)
            }
        }
    }

    /** Tabla estilo balance: pagó / le corresponde / saldo, con fila TOTAL y subtotales. */
    private fun pintarBalance(completo: EventoCompleto, saldos: List<Calculadora.Saldo>) {
        binding.tablaBalance.removeAllViews()

        fun fila(nombre: String, pagado: String, corresponde: String, saldo: String, negrita: Boolean) {
            val fila = ItemBalanceFilaBinding.inflate(layoutInflater, binding.tablaBalance, false)
            fila.colNombre.text = nombre
            fila.colPagado.text = pagado
            fila.colCorresponde.text = corresponde
            fila.colSaldo.text = saldo
            if (negrita) {
                fila.colNombre.setTypeface(null, android.graphics.Typeface.BOLD)
                fila.colPagado.setTypeface(null, android.graphics.Typeface.BOLD)
                fila.colCorresponde.setTypeface(null, android.graphics.Typeface.BOLD)
            }
            binding.tablaBalance.addView(fila.root)
        }

        fila(
            getString(R.string.col_asistente), getString(R.string.col_pagado),
            getString(R.string.col_corresponde), getString(R.string.col_saldo), true
        )
        for (saldo in saldos) {
            val signo = if (saldo.saldoCents > 0) "+" else ""
            fila(
                nombreDe(saldo.asistente),
                Dinero.formatear(saldo.pagadoCents),
                Dinero.formatear(saldo.correspondeCents),
                signo + Dinero.formatear(saldo.saldoCents),
                false
            )
        }
        fila(
            getString(R.string.fila_total),
            Dinero.formatear(saldos.sumOf { it.pagadoCents }),
            Dinero.formatear(saldos.sumOf { it.correspondeCents }),
            Dinero.formatear(saldos.sumOf { it.saldoCents }),
            true
        )

        // Subtotales del evento
        binding.subtotales.removeAllViews()
        val presupuestado = completo.apuntes.sumOf { it.apunte.presupuestadoCents ?: 0L }
        val gastado = completo.apuntes.sumOf { it.apunte.gastadoCents }
        val pagadoApuntes = completo.apuntes.sumOf { it.apunte.pagadoCents ?: 0L }
        val saldado = saldos.filter { it.asistente.liquidado }.sumOf { it.correspondeCents }
        val pendiente = saldos.filter { !it.asistente.liquidado }.sumOf { it.correspondeCents }
        val lineas = listOf(
            getString(R.string.sub_presupuestado, Dinero.formatear(presupuestado)),
            getString(R.string.sub_gastado, Dinero.formatear(gastado)),
            getString(R.string.sub_pagado, Dinero.formatear(pagadoApuntes)),
            getString(R.string.sub_saldado, Dinero.formatear(saldado)),
            getString(R.string.sub_pendiente, Dinero.formatear(pendiente))
        )
        for (linea in lineas) {
            binding.subtotales.addView(textoTransferencia(linea))
        }
    }

    /** Desglose del gasto por categoría con barras proporcionales. */
    private fun pintarCategorias(completo: EventoCompleto) {
        binding.listaCategorias.removeAllViews()
        val total = completo.totalGastadoCents
        if (total <= 0) return
        val porCategoria = completo.apuntes
            .groupBy { CategoriaApunte.fromNombre(it.apunte.categoria) }
            .mapValues { entrada -> entrada.value.sumOf { it.apunte.importeEfectivo } }
            .toList()
            .sortedByDescending { it.second }
        for ((categoria, importe) in porCategoria) {
            if (importe <= 0) continue
            val fila = ItemCategoriaBarraBinding.inflate(
                layoutInflater, binding.listaCategorias, false
            )
            fila.icono.setImageResource(categoria.iconoRes)
            fila.nombre.setText(categoria.nombreRes)
            fila.importe.text = Dinero.formatear(importe)
            val fraccion = importe.toFloat() / total.toFloat()
            (fila.barra.layoutParams as LinearLayout.LayoutParams).weight = fraccion
            (fila.resto.layoutParams as LinearLayout.LayoutParams).weight = 1f - fraccion
            binding.listaCategorias.addView(fila.root)
        }
    }

    /** Mensaje de cobro a partir de la plantilla editable de Ajustes. */
    private fun pedirPago(completo: EventoCompleto, t: Calculadora.Transferencia) {
        val fecha = DateFormat.getDateInstance(DateFormat.MEDIUM)
            .format(Date(completo.evento.fechaMillis))
        val mensaje = Ajustes.cobroPlantilla(this)
            .replace("{nombre}", nombreDe(t.de))
            .replace("{importe}", Dinero.formatear(t.cents))
            .replace("{evento}", completo.evento.titulo.ifBlank { fecha })
            .replace("{telefono}", t.a.telefono.ifBlank { "…" })
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, mensaje)
                },
                getString(R.string.pedir_pago)
            )
        )
    }

    private fun textoTransferencia(texto: String): TextView =
        TextView(this).apply {
            text = texto
            textSize = 15f
            setTextColor(ContextCompat.getColor(this@InformeActivity, R.color.text_primary))
            setPadding(0, 8, 0, 8)
        }

    private fun marcarLiquidado(asistenteId: Long, marcado: Boolean) {
        lifecycleScope.launch {
            val dao = AppDatabase.get(this@InformeActivity).dao()
            val completo = dao.eventoCompleto(eventoId) ?: return@launch
            val asistente = completo.asistentes.firstOrNull { it.id == asistenteId }
                ?: return@launch
            dao.actualizarAsistente(
                asistente.copy(
                    liquidado = marcado,
                    liquidadoMillis = if (marcado) System.currentTimeMillis() else 0
                )
            )
            dao.insertarRegistro(
                Registro(
                    eventoId = eventoId,
                    tipo = TipoRegistro.PAGO,
                    texto = getString(
                        if (marcado) R.string.reg_pago_marcado
                        else R.string.reg_pago_desmarcado,
                        nombreDe(asistente)
                    )
                )
            )
            val actualizado = dao.eventoCompleto(eventoId) ?: return@launch
            // Si todos han liquidado, la cuenta queda a cero: fuera recordatorios.
            NotificationScheduler.reprogramar(
                this@InformeActivity, actualizado.evento,
                pagosPendientes = actualizado.evento.cerrado && !actualizado.todosLiquidados
            )
            datos = actualizado
            pintar(actualizado)
        }
    }

    private fun compartir() {
        val completo = datos ?: return
        val fecha = DateFormat.getDateInstance(DateFormat.MEDIUM)
            .format(Date(completo.evento.fechaMillis))
        val saldos = Calculadora.saldos(completo)
        val transferencias = Calculadora.transferencias(saldos)
        val texto = buildString {
            appendLine(completo.evento.titulo.ifBlank { fecha })
            appendLine(
                getString(
                    if (completo.evento.cerrado) R.string.informe_definitivo
                    else R.string.informe_parcial
                )
            )
            appendLine(
                getString(R.string.total_evento_fmt, Dinero.formatear(completo.totalGastadoCents))
            )
            appendLine()
            for (saldo in saldos) {
                append("· ").append(nombreDe(saldo.asistente)).append(": ")
                append(getString(R.string.pago_total_fmt, Dinero.formatear(saldo.pagadoCents)))
                append(" · ")
                append(
                    getString(R.string.corresponde_fmt, Dinero.formatear(saldo.correspondeCents))
                )
                append(" · ")
                appendLine(
                    when {
                        saldo.saldoCents > 0 -> getString(
                            R.string.saldo_favor_fmt, Dinero.formatear(saldo.saldoCents)
                        )
                        saldo.saldoCents < 0 -> getString(
                            R.string.saldo_contra_fmt, Dinero.formatear(-saldo.saldoCents)
                        )
                        else -> getString(R.string.saldo_cero)
                    }
                )
            }
            appendLine()
            if (transferencias.isEmpty()) {
                appendLine(getString(R.string.sin_transferencias))
            } else {
                for (t in transferencias) {
                    appendLine(
                        getString(
                            R.string.transferencia_fmt,
                            nombreDe(t.de), Dinero.formatear(t.cents), nombreDe(t.a)
                        )
                    )
                }
            }
        }
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, texto)
                },
                getString(R.string.compartir_informe)
            )
        )
    }
}
