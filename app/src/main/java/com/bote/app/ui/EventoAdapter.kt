package com.bote.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bote.app.R
import com.bote.app.data.Dinero
import com.bote.app.data.EventoCompleto
import com.bote.app.databinding.ItemEventoBinding
import java.text.DateFormat
import java.util.Date

class EventoAdapter(
    private val alPulsar: (EventoCompleto) -> Unit
) : RecyclerView.Adapter<EventoAdapter.Holder>() {

    private var datos: List<EventoCompleto> = emptyList()
    private val formatoFecha: DateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM)

    /** Actualiza con DiffUtil: sin parpadeo ni repintado completo de la lista. */
    fun actualizar(nuevos: List<EventoCompleto>) {
        val previos = datos
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = previos.size
            override fun getNewListSize() = nuevos.size
            override fun areItemsTheSame(vieja: Int, nueva: Int) =
                previos[vieja].evento.id == nuevos[nueva].evento.id
            override fun areContentsTheSame(vieja: Int, nueva: Int) =
                previos[vieja] == nuevos[nueva]
        })
        datos = nuevos
        diff.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemEventoBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = datos.size

    override fun onBindViewHolder(holder: Holder, position: Int) =
        holder.bind(datos[position])

    inner class Holder(private val binding: ItemEventoBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: EventoCompleto) {
            val contexto = binding.root.context
            val evento = item.evento
            val fecha = formatoFecha.format(Date(evento.fechaMillis))

            binding.titulo.text = evento.titulo.ifBlank { fecha }
            binding.subtitulo.text =
                if (evento.ubicacion.isBlank()) fecha else "$fecha · ${evento.ubicacion}"
            binding.detalle.text = contexto.getString(
                R.string.num_asistentes, item.asistentes.size
            ) + " · " + contexto.getString(
                R.string.total_fmt, Dinero.formatear(item.totalGastadoCents)
            )

            if (FotoUtil.cargar(binding.foto, evento.fotoPath)) {
                binding.foto.visibility = View.VISIBLE
                binding.avatarEvento.visibility = View.GONE
            } else {
                binding.foto.visibility = View.GONE
                binding.avatarEvento.visibility = View.VISIBLE
                AvatarUtil.aplicar(binding.avatarEvento, evento.titulo.ifBlank { fecha })
            }

            binding.iconoNube.visibility =
                if (evento.sincronizable) View.VISIBLE else View.GONE
            binding.iconoCandado.visibility =
                if (evento.cerrado) View.VISIBLE else View.GONE
            when {
                item.saldado -> {
                    binding.estado.text = contexto.getString(R.string.evento_saldado)
                    binding.estado.setTextColor(
                        ContextCompat.getColor(contexto, R.color.saldo_positivo)
                    )
                }
                evento.cerrado -> {
                    binding.estado.text = contexto.getString(R.string.pendiente_liquidar)
                    binding.estado.setTextColor(
                        ContextCompat.getColor(contexto, R.color.saldo_negativo)
                    )
                }
                else -> binding.estado.text = ""
            }

            binding.root.setOnClickListener { alPulsar(item) }
        }
    }
}
