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
import com.bote.app.ui.InformeActivity
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

    /**
     * Notificación de pagos pendientes con acciones directas: "Ver informe"
     * y, si yo aún debo mi parte, "He pagado" (marca sin abrir la app).
     */
    fun notificarPagosPendientes(
        context: Context,
        notifId: Int,
        titulo: String,
        texto: String,
        eventoId: Long,
        ofrecerMarcarPago: Boolean
    ) {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= 33
        ) {
            return
        }
        val abrirDetalle = PendingIntent.getActivity(
            context, notifId,
            Intent(context, EventoDetalleActivity::class.java).apply {
                putExtra(EventoDetalleActivity.EXTRA_EVENTO_ID, eventoId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val verInforme = PendingIntent.getActivity(
            context, notifId + 100000,
            Intent(context, InformeActivity::class.java).apply {
                putExtra(InformeActivity.EXTRA_EVENTO_ID, eventoId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notificacion = NotificationCompat.Builder(context, CANAL_RECORDATORIOS)
            .setSmallIcon(R.drawable.ic_dinero)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setContentIntent(abrirDetalle)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.accion_ver_informe), verInforme)
            .apply {
                if (ofrecerMarcarPago) {
                    val hePagado = PendingIntent.getBroadcast(
                        context, notifId + 200000,
                        Intent(context, PagoLiquidadoReceiver::class.java).apply {
                            putExtra(PagoLiquidadoReceiver.EXTRA_EVENTO_ID, eventoId)
                            putExtra(PagoLiquidadoReceiver.EXTRA_NOTIF_ID, notifId)
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    addAction(0, context.getString(R.string.accion_he_pagado), hePagado)
                }
            }
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

    /**
     * Ofrece marcar como hecha tu liquidación de un evento cerrado cuando una
     * notificación de Bizum/transferencia saliente cuadra con lo que debías.
     */
    fun notificarLiquidacion(
        context: Context,
        cents: Long,
        eventoIds: LongArray,
        etiquetas: Array<String>
    ) {
        if (eventoIds.isEmpty()) return
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= 33
        ) {
            return
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_LIQUIDA_EVENTOS, eventoIds)
            putExtra(MainActivity.EXTRA_LIQUIDA_ETIQUETAS, etiquetas)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val notifId = (System.currentTimeMillis() % 100000).toInt() + 60000
        val pendiente = PendingIntent.getActivity(
            context, notifId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val texto = if (eventoIds.size == 1) etiquetas.first()
        else context.getString(R.string.notif_liquidacion_varios, eventoIds.size)
        val notificacion = NotificationCompat.Builder(context, CANAL_RECORDATORIOS)
            .setSmallIcon(R.drawable.ic_dinero)
            .setContentTitle(context.getString(R.string.notif_liquidacion_titulo, Dinero.formatear(cents)))
            .setContentText(texto)
            .setContentIntent(pendiente)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(notifId, notificacion)
    }
}
