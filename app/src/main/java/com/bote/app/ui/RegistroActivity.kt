package com.bote.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bote.app.BaseActivity
import com.bote.app.R
import com.bote.app.data.AppDatabase
import com.bote.app.data.Registro
import com.bote.app.data.TipoRegistro
import com.bote.app.databinding.ActivityRegistroBinding
import com.bote.app.databinding.ItemRegistroBinding
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Registro de actividad del evento: quién se une, qué se apunta, qué se paga… */
class RegistroActivity : BaseActivity() {

    companion object {
        const val EXTRA_EVENTO_ID = "evento_id"
    }

    private lateinit var binding: ActivityRegistroBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegistroBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val eventoId = intent.getLongExtra(EXTRA_EVENTO_ID, 0)
        binding.listaRegistro.layoutManager = LinearLayoutManager(this)

        lifecycleScope.launch {
            val entradas = AppDatabase.get(this@RegistroActivity).dao()
                .registroDeEvento(eventoId)
            binding.listaRegistro.adapter = RegistroAdapter(entradas)
            binding.vacio.visibility = if (entradas.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private class RegistroAdapter(
        private val entradas: List<Registro>
    ) : RecyclerView.Adapter<RegistroAdapter.Holder>() {

        private val formato = DateFormat.getDateTimeInstance(
            DateFormat.MEDIUM, DateFormat.SHORT
        )

        class Holder(val binding: ItemRegistroBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(ItemRegistroBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount(): Int = entradas.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val entrada = entradas[position]
            holder.binding.texto.text = entrada.texto
            holder.binding.fecha.text = formato.format(Date(entrada.millis))
            holder.binding.icono.setImageResource(
                when (entrada.tipo) {
                    TipoRegistro.ASISTENTE -> R.drawable.ic_persona_add
                    TipoRegistro.ASISTENTE_FUERA -> R.drawable.ic_eliminar
                    TipoRegistro.APUNTE -> R.drawable.ic_dinero
                    TipoRegistro.APUNTE_BORRADO -> R.drawable.ic_eliminar
                    TipoRegistro.PAGO -> R.drawable.ic_check_circulo
                    TipoRegistro.CANDADO -> R.drawable.ic_candado
                    TipoRegistro.SYNC -> R.drawable.ic_compartir
                    else -> R.drawable.ic_editar
                }
            )
        }
    }
}
