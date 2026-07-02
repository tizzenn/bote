package com.bote.app.calendario

import android.content.Intent
import android.provider.CalendarContract
import com.bote.app.data.Evento

/**
 * Añade el evento al calendario de Android mediante la pantalla estándar
 * de nuevo evento (ACTION_INSERT): no requiere permisos y el usuario elige
 * el calendario de destino.
 */
object Calendario {

    private const val UNA_HORA_MILLIS = 60 * 60 * 1000L

    fun intentInsertar(evento: Evento, tituloPorDefecto: String): Intent =
        Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(
                CalendarContract.Events.TITLE,
                evento.titulo.ifBlank { tituloPorDefecto }
            )
            if (evento.descripcion.isNotBlank()) {
                putExtra(CalendarContract.Events.DESCRIPTION, evento.descripcion)
            }
            if (evento.ubicacion.isNotBlank()) {
                putExtra(CalendarContract.Events.EVENT_LOCATION, evento.ubicacion)
            }
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, evento.fechaMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, evento.fechaMillis + UNA_HORA_MILLIS)
        }
}
