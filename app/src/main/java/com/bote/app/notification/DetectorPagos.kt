package com.bote.app.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.bote.app.config.Ajustes
import com.bote.app.data.Dinero

/**
 * Detector de pagos opcional: escucha las notificaciones del sistema y,
 * cuando una parece un pago (palabras de compra + importe), ofrece apuntarlo
 * en Bote con el importe y el comercio precargados. Todo ocurre en el
 * dispositivo; no se lee nada si el ajuste está desactivado.
 */
class DetectorPagos : NotificationListenerService() {

    companion object {
        private var ultimoHash = 0
        private var ultimoMillis = 0L

        private val PALABRAS_PAGO = listOf(
            "compra", "pago", "has pagado", "cargo", "bizum",
            "purchase", "payment", "charged"
        )
        private val IMPORTE = Regex(
            """(\d{1,6}(?:[.,]\d{1,2})?)\s*(?:€|eur)|(?:€|eur)\s*(\d{1,6}(?:[.,]\d{1,2})?)""",
            RegexOption.IGNORE_CASE
        )
        private val COMERCIO = Regex("""\ben\s+([A-ZÁÉÍÓÚÑ0-9][\w\s.*ÁÉÍÓÚÑáéíóúñ-]{2,30})""")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!Ajustes.detectorActivo(this)) return
        if (sbn.packageName == packageName) return

        val extras = sbn.notification.extras
        val titulo = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val texto = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val ampliado = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val completo = "$titulo. ${if (ampliado.length > texto.length) ampliado else texto}"

        val minusculas = completo.lowercase()
        if (PALABRAS_PAGO.none { minusculas.contains(it) }) return

        val coincidencia = IMPORTE.find(completo) ?: return
        val crudo = coincidencia.groupValues[1].ifBlank { coincidencia.groupValues[2] }
        val cents = Dinero.parsear(crudo) ?: return
        if (cents <= 0) return

        // Evita duplicados cuando el banco reemite la misma notificación
        val hash = (sbn.packageName + completo).hashCode()
        val ahora = System.currentTimeMillis()
        if (hash == ultimoHash && ahora - ultimoMillis < 120_000) return
        ultimoHash = hash
        ultimoMillis = ahora

        val comercio = COMERCIO.find(completo)?.groupValues?.get(1)?.trim().orEmpty()
        NotificationHelper.notificarPagoDetectado(this, cents, comercio)
    }
}
