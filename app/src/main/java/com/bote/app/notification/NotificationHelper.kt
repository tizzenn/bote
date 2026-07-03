package com.bote.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bote.app.R
import com.bote.app.data.Dinero
import com.bote.app.ui.EventoDetalleActivity
import com.bote.app.ui.MainActivity

object NotificationHelper {

    const val CANAL_RECORDATORIOS = "recordatorios"

    fun crearCanal(context: Context) {
        val canal = NotificationChannel(
            CANAL_RECORDATORIOS,
            context.getString(R.string.notif_canal_nombre),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notif_canal_desc)
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(canal)
    }

    fun notificar(context: Context, notifId: Int, titulo: String, texto: String, eventoId: Long) {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= 33
        ) {
            return
        }
        val intent = Intent(context, EventoDetalleActivity::class.java).apply {
            putExtra(EventoDetalleActivity.EXTRA_EVENTO_ID, eventoId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendiente = PendingIntent.getActivity(
            context, notifId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notificacion = NotificationCompat.Builder(context, CANAL_RECORDATORIOS)
            .setSmallIcon(R.drawable.ic_dinero)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setContentIntent(pendiente)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(notifId, notificacion)
    }

    /** Ofrece apuntar un pago detectado en las notificaciones del banco. */
    fun notificarPagoDetectado(context: Context, cents: Long, comercio: String) {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= 33
        ) {
            return
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_PAGO_CENTS, cents)
            putExtra(MainActivity.EXTRA_PAGO_CONCEPTO, comercio)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val notifId = (System.currentTimeMillis() % 100000).toInt() + 50000
        val pendiente = PendingIntent.getActivity(
            context, notifId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val detalle = if (comercio.isBlank()) Dinero.formatear(cents)
        else "${Dinero.formatear(cents)} — $comercio"
        val notificacion = NotificationCompat.Builder(context, CANAL_RECORDATORIOS)
            .setSmallIcon(R.drawable.ic_dinero)
            .setContentTitle(context.getString(R.string.notif_pago_titulo))
            .setContentText(detalle)
            .setContentIntent(pendiente)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(notifId, notificacion)
    }
}
