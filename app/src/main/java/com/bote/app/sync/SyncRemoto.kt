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

    /** Resultado de un ciclo de sincronización, para poder avisar al usuario. */
    sealed class Resultado {
        /** Sincronizó bien; [huboCambios] indica si llegaron datos remotos nuevos. */
        data class Ok(val huboCambios: Boolean) : Resultado()
        /** El evento no tiene servidor configurado (nada que sincronizar). */
        object SinServidor : Resultado()
        /** Sin conexión o el servidor no respondió (timeout, red caída). */
        object SinRed : Resultado()
        /** Clave/API key rechazada (401/403). */
        object ErrorAuth : Resultado()
        /** El servidor respondió con error (URL mala, falta el SQL, 5xx…). */
        object ErrorServidor : Resultado()

        val correcto: Boolean get() = this is Ok
    }

    /**
     * Ciclo completo para un evento: baja la copia remota (si existe) y la
     * fusiona, y después sube el resultado. Devuelve un [Resultado] tipado
     * para que la interfaz pueda repintarse y avisar de los fallos.
     */
    suspend fun sincronizar(dao: BoteDao, eventoId: Long): Resultado =
        withContext(Dispatchers.IO) {
            val evento = dao.evento(eventoId) ?: return@withContext Resultado.SinServidor
            if (!evento.sincronizable) return@withContext Resultado.SinServidor
            val url = evento.syncUrl.trim().trimEnd('/')
            val key = evento.syncKey.trim()
            val uuid = evento.uuid

            val huboCambios = when (val bajada = descargar(url, key, uuid)) {
                is Descarga.Datos -> {
                    EventoJson.importar(dao, bajada.json)
                    true
                }
                Descarga.Vacio -> false
                Descarga.Auth -> return@withContext Resultado.ErrorAuth
                Descarga.Servidor -> return@withContext Resultado.ErrorServidor
                Descarga.Red -> return@withContext Resultado.SinRed
            }

            val fusionado = dao.eventoCompleto(eventoId)
                ?: return@withContext Resultado.ErrorServidor
            val borrados = dao.borradosDeEvento(eventoId)
            val registro = dao.registroDeEvento(eventoId)
            when (subir(url, key, uuid, EventoJson.exportar(fusionado, borrados, registro))) {
                Subida.Ok -> Resultado.Ok(huboCambios)
                Subida.Auth -> Resultado.ErrorAuth
                Subida.Servidor -> Resultado.ErrorServidor
                Subida.Red -> Resultado.SinRed
            }
        }

    // ── REST contra PostgREST/Supabase ────────────────────────────

    private sealed class Descarga {
        data class Datos(val json: String) : Descarga()
        object Vacio : Descarga()
        object Auth : Descarga()
        object Servidor : Descarga()
        object Red : Descarga()
    }

    private enum class Subida { Ok, Auth, Servidor, Red }

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

    private fun descargar(url: String, key: String, uuid: String): Descarga {
        val conexion = abrir(
            url, key, "/rest/v1/eventos_sync?uuid=eq.$uuid&select=datos", "GET"
        )
        return try {
            when (conexion.responseCode) {
                200 -> {
                    val cuerpo = conexion.inputStream.use {
                        it.readBytes().toString(Charsets.UTF_8)
                    }
                    val lista = JSONArray(cuerpo)
                    if (lista.length() == 0) Descarga.Vacio
                    else Descarga.Datos(lista.getJSONObject(0).getJSONObject("datos").toString())
                }
                401, 403 -> Descarga.Auth
                else -> Descarga.Servidor
            }
        } catch (e: Exception) {
            Descarga.Red
        } finally {
            conexion.disconnect()
        }
    }

    private fun subir(url: String, key: String, uuid: String, json: String): Subida {
        val conexion = abrir(url, key, "/rest/v1/eventos_sync?on_conflict=uuid", "POST")
        conexion.setRequestProperty("Prefer", "resolution=merge-duplicates")
        conexion.doOutput = true
        return try {
            val fila = JSONObject()
            fila.put("uuid", uuid)
            fila.put("datos", JSONObject(json))
            val cuerpo = JSONArray().put(fila).toString()
            conexion.outputStream.use { it.write(cuerpo.toByteArray(Charsets.UTF_8)) }
            when (conexion.responseCode) {
                in 200..299 -> Subida.Ok
                401, 403 -> Subida.Auth
                else -> Subida.Servidor
            }
        } catch (e: Exception) {
            Subida.Red
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
