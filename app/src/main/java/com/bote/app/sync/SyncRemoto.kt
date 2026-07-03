package com.bote.app.sync

import com.bote.app.data.BoteDao
import com.bote.app.data.Evento
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Sincronización automática opcional contra una tabla `eventos_sync` de
 * Supabase (o cualquier PostgREST). Cada evento lleva su propio servidor
 * (grupo), así que un mismo usuario puede pertenecer a varios grupos en
 * servidores distintos. La fusión es siempre local (EventoJson.importar):
 * el servidor es un simple buzón.
 */
object SyncRemoto {

    /**
     * Ciclo completo para un evento: baja la copia remota (si existe) y la
     * fusiona, y después sube el resultado. Devuelve true si había datos
     * remotos (por si la interfaz quiere repintarse).
     */
    suspend fun sincronizar(dao: BoteDao, eventoId: Long): Boolean =
        withContext(Dispatchers.IO) {
            val evento = dao.evento(eventoId) ?: return@withContext false
            if (!evento.sincronizable) return@withContext false
            val url = evento.syncUrl.trim().trimEnd('/')
            val key = evento.syncKey.trim()
            val uuid = evento.uuid

            val remoto = descargar(url, key, uuid)
            if (remoto != null) {
                EventoJson.importar(dao, remoto)
            }

            val fusionado = dao.eventoCompleto(eventoId) ?: return@withContext false
            val borrados = dao.borradosDeEvento(eventoId)
            val registro = dao.registroDeEvento(eventoId)
            subir(url, key, uuid, EventoJson.exportar(fusionado, borrados, registro))
            remoto != null
        }

    // ── REST contra PostgREST/Supabase ────────────────────────────

    private fun abrir(url: String, key: String, ruta: String, metodo: String): HttpURLConnection {
        val conexion = URL(url + ruta).openConnection() as HttpURLConnection
        conexion.requestMethod = metodo
        conexion.connectTimeout = 10000
        conexion.readTimeout = 15000
        conexion.setRequestProperty("apikey", key)
        conexion.setRequestProperty("Authorization", "Bearer $key")
        conexion.setRequestProperty("Content-Type", "application/json")
        return conexion
    }

    private fun descargar(url: String, key: String, uuid: String): String? {
        val conexion = abrir(
            url, key, "/rest/v1/eventos_sync?uuid=eq.$uuid&select=datos", "GET"
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

    private fun subir(url: String, key: String, uuid: String, json: String) {
        val conexion = abrir(url, key, "/rest/v1/eventos_sync?on_conflict=uuid", "POST")
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

    /** Prueba de conexión desde el formulario (escribe y borra una fila de test). */
    suspend fun probar(url: String, key: String): Boolean = withContext(Dispatchers.IO) {
        val base = url.trim().trimEnd('/')
        val clave = key.trim()
        if (base.isBlank() || clave.isBlank()) return@withContext false
        try {
            val conexion = abrir(base, clave, "/rest/v1/eventos_sync?uuid=eq.__bote_test__&select=uuid", "GET")
            val ok = conexion.responseCode == 200
            conexion.disconnect()
            ok
        } catch (e: Exception) {
            false
        }
    }
}
