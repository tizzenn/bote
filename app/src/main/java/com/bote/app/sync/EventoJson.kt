package com.bote.app.sync

import com.bote.app.data.Apunte
import com.bote.app.data.ApunteConRepartos
import com.bote.app.data.Asistente
import com.bote.app.data.BoteDao
import com.bote.app.data.Evento
import com.bote.app.data.EventoCompleto
import com.bote.app.data.Reparto
import org.json.JSONArray
import org.json.JSONObject

/**
 * Sincronización entre dispositivos sin servidor: el evento completo se
 * exporta a JSON y se comparte con los asistentes; al importarlo, los UUID
 * permiten casar evento y asistentes y fusionar el estado.
 */
object EventoJson {

    private const val FORMATO = 1

    fun exportar(datos: EventoCompleto): String {
        val evento = datos.evento
        val raiz = JSONObject()
        raiz.put("formato", FORMATO)
        raiz.put("app", "bote")

        val jEvento = JSONObject()
        jEvento.put("uuid", evento.uuid)
        jEvento.put("titulo", evento.titulo)
        jEvento.put("descripcion", evento.descripcion)
        jEvento.put("fechaMillis", evento.fechaMillis)
        jEvento.put("ubicacion", evento.ubicacion)
        jEvento.put("modo", evento.modo)
        jEvento.put("cerrado", evento.cerrado)
        jEvento.put("creadoMillis", evento.creadoMillis)
        raiz.put("evento", jEvento)

        val jAsistentes = JSONArray()
        for (a in datos.asistentes) {
            val j = JSONObject()
            j.put("uuid", a.uuid)
            j.put("nombre", a.nombre)
            j.put("telefono", a.telefono)
            j.put("email", a.email)
            j.put("esCreador", a.esCreador)
            j.put("liquidado", a.liquidado)
            j.put("liquidadoMillis", a.liquidadoMillis)
            jAsistentes.put(j)
        }
        raiz.put("asistentes", jAsistentes)

        val porId = datos.asistentes.associateBy { it.id }
        val jApuntes = JSONArray()
        for (ac in datos.apuntes) {
            val a = ac.apunte
            val j = JSONObject()
            j.put("uuid", a.uuid)
            j.put("concepto", a.concepto)
            j.put("pagadorUuid", porId[a.pagadorId]?.uuid ?: "")
            if (a.presupuestadoCents != null) j.put("presupuestadoCents", a.presupuestadoCents)
            j.put("gastadoCents", a.gastadoCents)
            if (a.pagadoCents != null) j.put("pagadoCents", a.pagadoCents)
            j.put("repartoIgualitario", a.repartoIgualitario)
            j.put("categoria", a.categoria)
            j.put("fechaMillis", a.fechaMillis)
            val jRepartos = JSONArray()
            for (r in ac.repartos) {
                val jr = JSONObject()
                jr.put("asistenteUuid", porId[r.asistenteId]?.uuid ?: "")
                jr.put("puntosBasicos", r.puntosBasicos)
                jRepartos.put(jr)
            }
            j.put("repartos", jRepartos)
            jApuntes.put(j)
        }
        raiz.put("apuntes", jApuntes)

        return raiz.toString(2)
    }

    /**
     * Importa (o actualiza) un evento desde JSON. Si ya existía, conserva
     * los datos locales de identidad (soy creador, quién soy yo) y fusiona
     * las marcas de liquidación. Devuelve el id local del evento.
     */
    suspend fun importar(dao: BoteDao, texto: String): Long {
        val raiz = JSONObject(texto)
        require(raiz.optString("app") == "bote") { "No es un evento de Bote" }
        val jEvento = raiz.getJSONObject("evento")
        val uuid = jEvento.getString("uuid")

        // Estado local previo (si el evento ya existía en este dispositivo)
        val previo = dao.eventoPorUuid(uuid)
        var soyCreador = false
        var miAsistenteUuid: String? = null
        val liquidadosLocales = mutableSetOf<String>()
        if (previo != null) {
            val completo = dao.eventoCompleto(previo.id)
            if (completo != null) {
                soyCreador = completo.evento.soyCreador
                miAsistenteUuid = completo.miAsistente()?.uuid
                completo.asistentes.filter { it.liquidado }.forEach {
                    liquidadosLocales.add(it.uuid)
                }
            }
            dao.eliminarEvento(previo.id)
        }

        val eventoId = dao.insertarEvento(
            Evento(
                uuid = uuid,
                titulo = jEvento.optString("titulo"),
                descripcion = jEvento.optString("descripcion"),
                fechaMillis = jEvento.optLong("fechaMillis"),
                ubicacion = jEvento.optString("ubicacion"),
                modo = jEvento.optString("modo", com.bote.app.data.Modo.COLABORATIVO),
                soyCreador = soyCreador,
                miAsistenteId = 0,
                cerrado = jEvento.optBoolean("cerrado"),
                creadoMillis = jEvento.optLong("creadoMillis", System.currentTimeMillis())
            )
        )

        val idPorUuid = mutableMapOf<String, Long>()
        var miAsistenteId = 0L
        val jAsistentes = raiz.getJSONArray("asistentes")
        for (i in 0 until jAsistentes.length()) {
            val j = jAsistentes.getJSONObject(i)
            val aUuid = j.getString("uuid")
            val id = dao.insertarAsistente(
                Asistente(
                    eventoId = eventoId,
                    uuid = aUuid,
                    nombre = j.optString("nombre"),
                    telefono = j.optString("telefono"),
                    email = j.optString("email"),
                    esCreador = j.optBoolean("esCreador"),
                    liquidado = j.optBoolean("liquidado") || liquidadosLocales.contains(aUuid),
                    liquidadoMillis = j.optLong("liquidadoMillis")
                )
            )
            idPorUuid[aUuid] = id
            if (aUuid == miAsistenteUuid) miAsistenteId = id
        }
        if (miAsistenteId != 0L) {
            val evento = dao.evento(eventoId)
            if (evento != null) dao.actualizarEvento(evento.copy(miAsistenteId = miAsistenteId))
        }

        val jApuntes = raiz.getJSONArray("apuntes")
        for (i in 0 until jApuntes.length()) {
            val j = jApuntes.getJSONObject(i)
            val apunteId = dao.insertarApunte(
                Apunte(
                    eventoId = eventoId,
                    uuid = j.getString("uuid"),
                    concepto = j.optString("concepto"),
                    pagadorId = idPorUuid[j.optString("pagadorUuid")] ?: 0L,
                    presupuestadoCents = if (j.has("presupuestadoCents")) j.getLong("presupuestadoCents") else null,
                    gastadoCents = j.optLong("gastadoCents"),
                    pagadoCents = if (j.has("pagadoCents")) j.getLong("pagadoCents") else null,
                    repartoIgualitario = j.optBoolean("repartoIgualitario", true),
                    categoria = j.optString("categoria", "OTROS"),
                    fechaMillis = j.optLong("fechaMillis", System.currentTimeMillis())
                )
            )
            val jRepartos = j.getJSONArray("repartos")
            val repartos = mutableListOf<Reparto>()
            for (k in 0 until jRepartos.length()) {
                val jr = jRepartos.getJSONObject(k)
                val asistenteId = idPorUuid[jr.optString("asistenteUuid")] ?: continue
                repartos.add(Reparto(apunteId, asistenteId, jr.optInt("puntosBasicos")))
            }
            if (repartos.isNotEmpty()) dao.insertarRepartos(repartos)
        }

        return eventoId
    }
}
