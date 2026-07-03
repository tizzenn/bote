package com.bote.app.sync

import android.content.Context
import com.bote.app.config.Ajustes
import com.bote.app.data.BoteDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Sincronización automática opcional contra una tabla `eventos_sync` de
 * Supabase (o cualquier PostgREST). El evento viaja como JSON completo y la
 * fusión es siempre local (EventoJson.importar), así que el servidor es un
 * simple buzón: da igual Supabase gratuito o un VPS propio con PostgREST.
 */
object SyncRemoto {

    fun activo(context: Context): Boolean =
        Ajustes.syncActivo(context) &&
            Ajustes.syncUrl(context).isNotBlank() &&
            Ajustes.syncKey(context).isNotBlank()

    /**
     * Ciclo completo para un evento: baja la copia remota (si existe) y la
     * fusiona, y después sube el resultado. Devuelve true si había datos
     * remotos (por si la interfaz quiere repintarse).
     */
    suspend fun sincronizar(context: Context, dao: BoteDao, eventoId: Long): Boolean =
        withContext(Dispatchers.IO) {
            val completo = dao.eventoCompleto(eventoId) ?: return@withContext false
            val uuid = completo.evento.uuid

            val remoto = descargar(context, uuid)
            if (remoto != null) {
                EventoJson.importar(dao, remoto)
            }

            val fusionado = dao.eventoCompleto(eventoId) ?: return@withContext false
            val borrados = dao.borradosDeEvento(eventoId)
            val registro = dao.registroDeEvento(eventoId)
            subir(context, uuid, EventoJson.exportar(fusionado, borrados, registro))
            remoto != null
        }

    // ── REST contra PostgREST/Supabase ────────────────────────────

    private fun base(context: Context): String =
        Ajustes.syncUrl(context).trim().trimEnd('/')

    private fun abrir(context: Context, ruta: String, metodo: String): HttpURLConnection {
        val conexion = URL(base(context) + ruta).openConnection() as HttpURLConnection
        val clave = Ajustes.syncKey(context).trim()
        conexion.requestMethod = metodo
        conexion.connectTimeout = 10000
        conexion.readTimeout = 15000
        conexion.setRequestProperty("apikey", clave)
        conexion.setRequestProperty("Authorization", "Bearer $clave")
        conexion.setRequestProperty("Content-Type", "application/json")
        return conexion
    }

    private fun descargar(context: Context, uuid: String): String? {
        val conexion = abrir(
            context, "/rest/v1/eventos_sync?uuid=eq.$uuid&select=datos", "GET"
        )
        return try {
            if (conexion.responseCode != 200) return null
            val cuerpo = conexion.inputStream.use {
                it.readBytes().toString(Charsets.UTF_8)
            }
            val lista = JSONArray(cuerpo)
            if (lista.length() == 0) null
            else lista.getJSONObject(0).getJSONObject("datos").toString()
        } catch (e: Exception) {
            null
        } finally {
            conexion.disconnect()
        }
    }

    private fun subir(context: Context, uuid: String, json: String) {
        val conexion = abrir(
            context, "/rest/v1/eventos_sync?on_conflict=uuid", "POST"
        )
        conexion.setRequestProperty("Prefer", "resolution=merge-duplicates")
        conexion.doOutput = true
        try {
            val fila = JSONObject()
            fila.put("uuid", uuid)
            fila.put("datos", JSONObject(json))
            val cuerpo = JSONArray().put(fila).toString()
            conexion.outputStream.use { it.write(cuerpo.toByteArray(Charsets.UTF_8)) }
            conexion.responseCode // fuerza el envío; los errores se ignoran (reintento en la próxima apertura)
        } catch (e: Exception) {
            // sin red o servidor caído: la app sigue siendo local-first
        } finally {
            conexion.disconnect()
        }
    }
}
