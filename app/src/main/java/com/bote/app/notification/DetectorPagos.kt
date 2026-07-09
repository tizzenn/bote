package com.bote.app.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.bote.app.config.Ajustes
import com.bote.app.data.AppDatabase
import com.bote.app.data.Calculadora
import com.bote.app.data.Dinero
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.Normalizer
import kotlin.math.abs

/**
 * Detector de pagos opcional: escucha las notificaciones del sistema. Dos usos,
 * ambos locales y opt-in:
 *  - Una compra (palabras de compra + importe) → ofrece apuntarla en Bote.
 *  - Un Bizum/transferencia SALIENTE → si cuadra con lo que debías en un evento
 *    cerrado, ofrece marcar tu pago como hecho.
 */
class DetectorPagos : NotificationListenerService() {

    companion object {
        private var ultimoHash = 0
        private var ultimoMillis = 0L
        private var ultimoHashLiq = 0
        private var ultimoMillisLiq = 0L

        private val PALABRAS_PAGO = listOf(
            "compra", "pago", "has pagado", "cargo", "bizum",
            "purchase", "payment", "charged"
        )
        /** Un envío saliente: Bizum/transferencia + verbo de envío. */
        private val ENVIO_SALIENTE = listOf(
            "enviado", "has enviado", "enviaste", "enviada", "envío", "envio", "sent"
        )
        private val IMPORTE = Regex(
            """(\d{1,6}(?:[.,]\d{1,2})?)\s*(?:€|eur)|(?:€|eur)\s*(\d{1,6}(?:[.,]\d{1,2})?)""",
            RegexOption.IGNORE_CASE
        )
        private val COMERCIO = Regex("""\ben\s+([A-ZÁÉÍÓÚÑ0-9][\w\s.*ÁÉÍÓÚÑáéíóúñ-]{2,30})""")
        /** Destinatario de un envío: lo que sigue a "a"/"para". */
        private val DESTINATARIO = Regex(
            """\b(?:a|para)\s+([\p{Lu}][\p{L} .'-]{1,40})"""
        )
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

        // 1) ¿Un Bizum/transferencia saliente? → intentar cuadrar una liquidación
        if (esEnvioSaliente(minusculas)) {
            manejarEnvioSaliente(completo)
            return
        }

        // 2) ¿Parece una compra? → ofrecer apuntarla
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

    private fun esEnvioSaliente(minusculas: String): Boolean =
        (minusculas.contains("bizum") || minusculas.contains("transferencia")) &&
            ENVIO_SALIENTE.any { minusculas.contains(it) }

    /**
     * Busca, entre los eventos CERRADOS no saldados, una transferencia pendiente
     * en la que YO (asistente deudor) debo pagar a un acreedor cuyo nombre ≈ el
     * destinatario y con importe ≈ el enviado. Si cuadra, ofrece marcar el pago.
     */
    private fun manejarEnvioSaliente(completo: String) {
        val coincidencia = IMPORTE.find(completo) ?: return
        val crudo = coincidencia.groupValues[1].ifBlank { coincidencia.groupValues[2] }
        val cents = Dinero.parsear(crudo) ?: return
        if (cents <= 0) return
        val destinatario = DESTINATARIO.find(completo)?.groupValues?.get(1)?.trim()
            ?.substringBefore(" por ")?.trim().orEmpty()

        val hash = ("liq" + completo).hashCode()
        val ahora = System.currentTimeMillis()
        if (hash == ultimoHashLiq && ahora - ultimoMillisLiq < 120_000) return
        ultimoHashLiq = hash
        ultimoMillisLiq = ahora

        val ctx = applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            val dao = AppDatabase.get(ctx).dao()
            val ids = mutableListOf<Long>()
            val etiquetas = mutableListOf<String>()
            for (ev in dao.todosEventos()) {
                if (!ev.cerrado || ev.miAsistenteId == 0L) continue
                val c = dao.eventoCompleto(ev.id) ?: continue
                val mi = c.miAsistente() ?: continue
                if (mi.liquidado) continue
                val transferencias = Calculadora.transferencias(Calculadora.saldos(c))
                val cuadra = transferencias.firstOrNull { t ->
                    t.de.id == ev.miAsistenteId &&
                        importeParecido(t.cents, cents) &&
                        (destinatario.isBlank() || nombresParecidos(t.a.nombre, destinatario))
                } ?: continue
                ids.add(ev.id)
                etiquetas.add(
                    ctx.getString(
                        com.bote.app.R.string.liquidacion_label_fmt,
                        cuadra.a.nombre.ifBlank { ctx.getString(com.bote.app.R.string.asistente_sin_nombre) },
                        ev.titulo.ifBlank { ctx.getString(com.bote.app.R.string.app_name) }
                    )
                )
            }
            if (ids.isNotEmpty()) {
                NotificationHelper.notificarLiquidacion(
                    ctx, cents, ids.toLongArray(), etiquetas.toTypedArray()
                )
            }
        }
    }

    /** Importes ≈ iguales (tolerancia de 1 céntimo por redondeos). */
    private fun importeParecido(a: Long, b: Long): Boolean = abs(a - b) <= 1

    private fun normaliza(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .trim()

    /** Nombres parecidos: uno contiene al otro o comparten alguna palabra. */
    private fun nombresParecidos(a: String, b: String): Boolean {
        val na = normaliza(a)
        val nb = normaliza(b)
        if (na.isBlank() || nb.isBlank()) return false
        if (na == nb || na.contains(nb) || nb.contains(na)) return true
        val ta = na.split(Regex("\\s+")).filter { it.length >= 3 }.toSet()
        val tb = nb.split(Regex("\\s+")).filter { it.length >= 3 }.toSet()
        return ta.intersect(tb).isNotEmpty()
    }
}
