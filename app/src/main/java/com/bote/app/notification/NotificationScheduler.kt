package com.bote.app.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.bote.app.config.Ajustes
import com.bote.app.data.Evento
import java.util.Calendar

/**
 * Alarmas de recordatorio: una el día del evento (a las 10:00) y, cuando
 * la cuenta está cerrada con pagos pendientes, un recordatorio cada 3 días.
 */
object NotificationScheduler {

    const val TIPO_EVENTO = "evento"
    const val TIPO_PAGOS = "pagos"

    private const val TRES_DIAS_MILLIS = 3 * 24 * 60 * 60 * 1000L

    private fun pendiente(context: Context, evento: Evento, tipo: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_EVENTO_ID, evento.id)
            putExtra(ReminderReceiver.EXTRA_TIPO, tipo)
        }
        val requestCode = (evento.id * 2 + if (tipo == TIPO_EVENTO) 0 else 1).toInt()
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Reprograma (o cancela) las alarmas de un evento según su estado y los ajustes. */
    fun reprogramar(context: Context, evento: Evento, pagosPendientes: Boolean) {
        val alarmas = context.getSystemService(AlarmManager::class.java)

        // Recordatorio del día del evento
        val pendienteEvento = pendiente(context, evento, TIPO_EVENTO)
        alarmas.cancel(pendienteEvento)
        if (Ajustes.notifEvento(context) && !evento.cerrado) {
            val cuando = Calendar.getInstance().apply {
                timeInMillis = evento.fechaMillis
                set(Calendar.HOUR_OF_DAY, 10)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }.timeInMillis
            if (cuando > System.currentTimeMillis()) {
                alarmas.set(AlarmManager.RTC_WAKEUP, cuando, pendienteEvento)
            }
        }

        // Recordatorio periódico de pagos pendientes
        val pendientePagos = pendiente(context, evento, TIPO_PAGOS)
        alarmas.cancel(pendientePagos)
        if (Ajustes.notifPagos(context) && evento.cerrado && pagosPendientes) {
            alarmas.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + TRES_DIAS_MILLIS,
                TRES_DIAS_MILLIS,
                pendientePagos
            )
        }
    }

    fun cancelar(context: Context, evento: Evento) {
        val alarmas = context.getSystemService(AlarmManager::class.java)
        alarmas.cancel(pendiente(context, evento, TIPO_EVENTO))
        alarmas.cancel(pendiente(context, evento, TIPO_PAGOS))
    }
}
