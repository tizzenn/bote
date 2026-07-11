package com.bote.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bote.app.R
import com.bote.app.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_EVENTO_ID = "evento_id"
        const val EXTRA_TIPO = "tipo"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val eventoId = intent.getLongExtra(EXTRA_EVENTO_ID, 0)
        val tipo = intent.getStringExtra(EXTRA_TIPO) ?: return
        if (eventoId == 0L) return

        val resultado = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val datos = AppDatabase.get(context).dao().eventoCompleto(eventoId)
                    ?: return@launch
                val nombre = datos.evento.titulo.ifBlank {
                    context.getString(R.string.app_name)
                }
                when (tipo) {
                    NotificationScheduler.TIPO_EVENTO -> {
                        NotificationHelper.notificar(
                            context,
                            (eventoId * 2).toInt(),
                            context.getString(R.string.notif_evento_hoy_titulo, nombre),
                            context.getString(R.string.notif_evento_hoy_texto),
                            eventoId
                        )
                    }
                    NotificationScheduler.TIPO_PAGOS -> {
                        if (datos.saldado || !datos.evento.cerrado) {
                            // Ya no hay nada pendiente: se retira la alarma periódica.
                            NotificationScheduler.reprogramar(context, datos.evento, false)
                        } else {
                            NotificationHelper.notificarPagosPendientes(
                                context,
                                (eventoId * 2 + 1).toInt(),
                                context.getString(R.string.notif_pagos_titulo, nombre),
                                context.getString(R.string.notif_pagos_texto),
                                eventoId,
                                ofrecerMarcarPago = datos.miAsistente()?.liquidado == false
                            )
                        }
                    }
                }
            } finally {
                resultado.finish()
            }
        }
    }
}
