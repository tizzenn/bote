package com.bote.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.bote.app.R
import com.bote.app.data.AppDatabase
import com.bote.app.data.Registro
import com.bote.app.data.TipoRegistro
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Acción "He pagado mi parte" de la notificación de pagos pendientes:
 * marca mi liquidación sin abrir la app y retira la notificación.
 */
class PagoLiquidadoReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_EVENTO_ID = "evento_id"
        const val EXTRA_NOTIF_ID = "notif_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val eventoId = intent.getLongExtra(EXTRA_EVENTO_ID, 0)
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, 0)
        if (eventoId == 0L) return

        val resultado = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.get(context).dao()
                val completo = dao.eventoCompleto(eventoId) ?: return@launch
                val mi = completo.miAsistente() ?: return@launch
                if (!mi.liquidado) {
                    dao.actualizarAsistente(
                        mi.copy(
                            liquidado = true,
                            liquidadoMillis = System.currentTimeMillis()
                        )
                    )
                    val nombre = mi.nombre.ifBlank {
                        context.getString(R.string.asistente_sin_nombre)
                    }
                    dao.insertarRegistro(
                        Registro(
                            eventoId = eventoId,
                            tipo = TipoRegistro.PAGO,
                            texto = context.getString(R.string.reg_pago_marcado, nombre)
                        )
                    )
                }
                dao.eventoCompleto(eventoId)?.let { actualizado ->
                    NotificationScheduler.reprogramar(
                        context, actualizado.evento,
                        pagosPendientes = actualizado.evento.cerrado &&
                            !actualizado.todosLiquidados
                    )
                }
            } finally {
                if (notifId != 0) NotificationManagerCompat.from(context).cancel(notifId)
                resultado.finish()
            }
        }
    }
}
