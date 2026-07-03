package com.bote.app.sync

import com.bote.app.data.Apunte
import com.bote.app.data.ApunteBorrado
import com.bote.app.data.Asistente
import com.bote.app.data.BoteDao
import com.bote.app.data.Evento
import com.bote.app.data.EventoCompleto
import com.bote.app.data.Modo
import com.bote.app.data.Registro
import com.bote.app.data.Reparto
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max

/**
 * Sincronización entre dispositivos sin servidor: el evento completo viaja
 * como JSON (archivo o QR). Los UUID dan identidad estable a evento,
 * asistentes y apuntes; al importar se fusiona apunte a apunte (gana la
 * versión modificada más recientemente) y las lápidas evitan que
 * reaparezcan apuntes borrados.
 */
object EventoJson {

    private const val FORMATO = 2

    fun exportar(
        datos: EventoCompleto,
        borrados: List<ApunteBorrado>,
        registro: List<Registro> = emptyList()
    ): String {
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
        jEvento.put("modificadoMillis", evento.modificadoMillis)
        // El servidor del grupo viaja con el evento: quien lo importa se conecta solo
        jEvento.put("syncActivo", evento.syncActivo)
        jEvento.put("syncUrl", evento.syncUrl)
        jEvento.put("syncKey", evento.syncKey)
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
            j.put("modificadoMillis", a.modificadoMillis)
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

        val jBorrados = JSONArray()
        for (b in borrados) {
            val j = JSONObject()
            j.put("uuid", b.uuid)
            j.put("borradoMillis", b.borradoMillis)
            jBorrados.put(j)
        }
        raiz.put("borrados", jBorrados)

        val jRegistro = JSONArray()
        for (r in registro) {
            val j = JSONObject()
            j.put("uuid", r.uuid)
            j.put("tipo", r.tipo)
            j.put("texto", r.texto)
            j.put("millis", r.millis)
            jRegistro.put(j)
        }
        raiz.put("registro", jRegistro)

        return raiz.toString()
    }

    /**
     * Importa un evento. Si no existía se crea; si ya existía se fusiona:
     * los datos del evento y de cada apunte los gana la versión con la
     * modificación más reciente, los asistentes se unen por UUID (la marca
     * de liquidación nunca se pierde) y los apuntes con lápida se eliminan.
     * Devuelve el id local del evento.
     */
    suspend fun importar(dao: BoteDao, texto: String): Long {
        val raiz = JSONObject(texto)
        require(raiz.optString("app") == "bote") { "No es un evento de Bote" }
        val jEvento = raiz.getJSONObject("evento")
        val uuid = jEvento.getString("uuid")

        val previo = dao.eventoPorUuid(uuid)
        return if (previo == null) {
            importarNuevo(dao, raiz, jEvento)
        } else {
            fusionar(dao, previo.id, raiz, jEvento)
        }
    }

    // ── Evento que no existía en este dispositivo ─────────────────

    private suspend fun importarNuevo(dao: BoteDao, raiz: JSONObject, jEvento: JSONObject): Long {
        val eventoId = dao.insertarEvento(
            Evento(
                uuid = jEvento.getString("uuid"),
                titulo = jEvento.optString("titulo"),
                descripcion = jEvento.optString("descripcion"),
                fechaMillis = jEvento.optLong("fechaMillis"),
                ubicacion = jEvento.optString("ubicacion"),
                modo = jEvento.optString("modo", Modo.COLABORATIVO),
                soyCreador = false,
                miAsistenteId = 0,
                cerrado = jEvento.optBoolean("cerrado"),
                creadoMillis = jEvento.optLong("creadoMillis", System.currentTimeMillis()),
                modificadoMillis = jEvento.optLong("modificadoMillis"),
                syncActivo = jEvento.optBoolean("syncActivo"),
                syncUrl = jEvento.optString("syncUrl"),
                syncKey = jEvento.optString("syncKey")
            )
        )
        val idPorUuid = mutableMapOf<String, Long>()
        val jAsistentes = raiz.getJSONArray("asistentes")
        for (i in 0 until jAsistentes.length()) {
            val j = jAsistentes.getJSONObject(i)
            idPorUuid[j.getString("uuid")] = dao.insertarAsistente(asistenteDeJson(j, eventoId))
        }
        val borrados = uuidsBorrados(raiz)
        for ((bUuid, millis) in borrados) {
            dao.insertarBorrado(ApunteBorrado(eventoId, bUuid, millis))
        }
        val jApuntes = raiz.getJSONArray("apuntes")
        for (i in 0 until jApuntes.length()) {
            val j = jApuntes.getJSONObject(i)
            if (borrados.containsKey(j.getString("uuid"))) continue
            insertarApunteDeJson(dao, j, eventoId, idPorUuid)
        }
        unirRegistro(dao, raiz, eventoId, emptySet())
        return eventoId
    }

    // ── Fusión con el evento local existente ──────────────────────

    private suspend fun fusionar(dao: BoteDao, eventoId: Long, raiz: JSONObject, jEvento: JSONObject): Long {
        val local = dao.eventoCompleto(eventoId) ?: return eventoId

        // Datos del evento: gana la modificación más reciente
        val modImportado = jEvento.optLong("modificadoMillis")
        if (modImportado > local.evento.modificadoMillis) {
            dao.actualizarEvento(
                local.evento.copy(
                    titulo = jEvento.optString("titulo"),
                    descripcion = jEvento.optString("descripcion"),
                    fechaMillis = jEvento.optLong("fechaMillis"),
                    ubicacion = jEvento.optString("ubicacion"),
                    modo = jEvento.optString("modo", Modo.COLABORATIVO),
                    cerrado = jEvento.optBoolean("cerrado"),
                    modificadoMillis = modImportado,
                    syncActivo = jEvento.optBoolean("syncActivo", local.evento.syncActivo),
                    syncUrl = jEvento.optString("syncUrl", local.evento.syncUrl),
                    syncKey = jEvento.optString("syncKey", local.evento.syncKey)
                )
            )
        }

        // Asistentes: unión por UUID; la liquidación nunca retrocede
        val idPorUuid = mutableMapOf<String, Long>()
        val localesPorUuid = local.asistentes.associateBy { it.uuid }
        local.asistentes.forEach { idPorUuid[it.uuid] = it.id }
        val jAsistentes = raiz.getJSONArray("asistentes")
        for (i in 0 until jAsistentes.length()) {
            val j = jAsistentes.getJSONObject(i)
            val aUuid = j.getString("uuid")
            val existente = localesPorUuid[aUuid]
            if (existente == null) {
                idPorUuid[aUuid] = dao.insertarAsistente(asistenteDeJson(j, eventoId))
            } else {
                dao.actualizarAsistente(
                    existente.copy(
                        nombre = j.optString("nombre").ifBlank { existente.nombre },
                        telefono = j.optString("telefono").ifBlank { existente.telefono },
                        email = j.optString("email").ifBlank { existente.email },
                        liquidado = existente.liquidado || j.optBoolean("liquidado"),
                        liquidadoMillis = max(existente.liquidadoMillis, j.optLong("liquidadoMillis"))
                    )
                )
            }
        }

        // Lápidas: unión, y se aplican a los apuntes locales
        val borradosLocales = dao.borradosDeEvento(eventoId).associateBy { it.uuid }.toMutableMap()
        for ((bUuid, millis) in uuidsBorrados(raiz)) {
            if (!borradosLocales.containsKey(bUuid)) {
                val lapida = ApunteBorrado(eventoId, bUuid, millis)
                dao.insertarBorrado(lapida)
                borradosLocales[bUuid] = lapida
            }
        }
        val apuntesLocales = mutableMapOf<String, com.bote.app.data.ApunteConRepartos>()
        for (ac in local.apuntes) {
            if (borradosLocales.containsKey(ac.apunte.uuid)) {
                dao.eliminarApunte(ac.apunte.id)
            } else {
                apuntesLocales[ac.apunte.uuid] = ac
            }
        }

        // Apuntes: unión por UUID; en conflicto gana el modificado más reciente
        val jApuntes = raiz.getJSONArray("apuntes")
        for (i in 0 until jApuntes.length()) {
            val j = jApuntes.getJSONObject(i)
            val aUuid = j.getString("uuid")
            if (borradosLocales.containsKey(aUuid)) continue
            val existente = apuntesLocales[aUuid]
            if (existente == null) {
                insertarApunteDeJson(dao, j, eventoId, idPorUuid)
            } else if (j.optLong("modificadoMillis") > existente.apunte.modificadoMillis) {
                dao.actualizarApunte(
                    existente.apunte.copy(
                        concepto = j.optString("concepto"),
                        pagadorId = idPorUuid[j.optString("pagadorUuid")]
                            ?: existente.apunte.pagadorId,
                        presupuestadoCents = if (j.has("presupuestadoCents"))
                            j.getLong("presupuestadoCents") else null,
                        gastadoCents = j.optLong("gastadoCents"),
                        pagadoCents = if (j.has("pagadoCents")) j.getLong("pagadoCents") else null,
                        repartoIgualitario = j.optBoolean("repartoIgualitario", true),
                        categoria = j.optString("categoria", "OTROS"),
                        modificadoMillis = j.optLong("modificadoMillis")
                    )
                )
                dao.guardarRepartos(existente.apunte.id, repartosDeJson(j, existente.apunte.id, idPorUuid))
            }
        }

        val uuidsRegistro = dao.registroDeEvento(eventoId).map { it.uuid }.toSet()
        unirRegistro(dao, raiz, eventoId, uuidsRegistro)
        return eventoId
    }

    /** Une el registro de actividad de ambos dispositivos sin duplicar entradas. */
    private suspend fun unirRegistro(
        dao: BoteDao,
        raiz: JSONObject,
        eventoId: Long,
        existentes: Set<String>
    ) {
        val jRegistro = raiz.optJSONArray("registro") ?: return
        for (i in 0 until jRegistro.length()) {
            val j = jRegistro.getJSONObject(i)
            val uuid = j.getString("uuid")
            if (existentes.contains(uuid)) continue
            dao.insertarRegistro(
                Registro(
                    eventoId = eventoId,
                    uuid = uuid,
                    tipo = j.optString("tipo"),
                    texto = j.optString("texto"),
                    millis = j.optLong("millis", System.currentTimeMillis())
                )
            )
        }
    }

    // ── Piezas comunes ────────────────────────────────────────────

    private fun asistenteDeJson(j: JSONObject, eventoId: Long): Asistente =
        Asistente(
            eventoId = eventoId,
            uuid = j.getString("uuid"),
            nombre = j.optString("nombre"),
            telefono = j.optString("telefono"),
            email = j.optString("email"),
            esCreador = j.optBoolean("esCreador"),
            liquidado = j.optBoolean("liquidado"),
            liquidadoMillis = j.optLong("liquidadoMillis")
        )

    private suspend fun insertarApunteDeJson(
        dao: BoteDao,
        j: JSONObject,
        eventoId: Long,
        idPorUuid: Map<String, Long>
    ) {
        val apunteId = dao.insertarApunte(
            Apunte(
                eventoId = eventoId,
                uuid = j.getString("uuid"),
                concepto = j.optString("concepto"),
                pagadorId = idPorUuid[j.optString("pagadorUuid")] ?: 0L,
                presupuestadoCents = if (j.has("presupuestadoCents"))
                    j.getLong("presupuestadoCents") else null,
                gastadoCents = j.optLong("gastadoCents"),
                pagadoCents = if (j.has("pagadoCents")) j.getLong("pagadoCents") else null,
                repartoIgualitario = j.optBoolean("repartoIgualitario", true),
                categoria = j.optString("categoria", "OTROS"),
                fechaMillis = j.optLong("fechaMillis", System.currentTimeMillis()),
                modificadoMillis = j.optLong("modificadoMillis")
            )
        )
        val repartos = repartosDeJson(j, apunteId, idPorUuid)
        if (repartos.isNotEmpty()) dao.insertarRepartos(repartos)
    }

    private fun repartosDeJson(
        j: JSONObject,
        apunteId: Long,
        idPorUuid: Map<String, Long>
    ): List<Reparto> {
        val jRepartos = j.optJSONArray("repartos") ?: return emptyList()
        val repartos = mutableListOf<Reparto>()
        for (k in 0 until jRepartos.length()) {
            val jr = jRepartos.getJSONObject(k)
            val asistenteId = idPorUuid[jr.optString("asistenteUuid")] ?: continue
            repartos.add(Reparto(apunteId, asistenteId, jr.optInt("puntosBasicos")))
        }
        return repartos
    }

    private fun uuidsBorrados(raiz: JSONObject): Map<String, Long> {
        val jBorrados = raiz.optJSONArray("borrados") ?: return emptyMap()
        val resultado = mutableMapOf<String, Long>()
        for (i in 0 until jBorrados.length()) {
            val j = jBorrados.getJSONObject(i)
            resultado[j.getString("uuid")] =
                j.optLong("borradoMillis", System.currentTimeMillis())
        }
        return resultado
    }
}
