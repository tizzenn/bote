package com.bote.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bote.app.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Al reiniciar (o actualizar la app) se reprograman todas las alarmas. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val resultado = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.get(context).dao()
                for (evento in dao.todosEventos()) {
                    val datos = dao.eventoCompleto(evento.id) ?: continue
                    NotificationScheduler.reprogramar(
                        context, evento,
                        pagosPendientes = evento.cerrado && !datos.todosLiquidados
                    )
                }
            } finally {
                resultado.finish()
            }
        }
    }
}
