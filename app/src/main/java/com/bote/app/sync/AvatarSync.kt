package com.bote.app.sync

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.bote.app.config.Ajustes
import com.bote.app.data.BoteDao
import com.bote.app.data.Evento
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Sincroniza SOLO el avatar del evento, reducido a 256×256 JPEG q70 (~20 KB),
 * a través de Supabase Storage (bucket público "avatares", objeto <uuid>.jpg).
 * La imagen no viaja en el JSON (solo su marca de tiempo): así no infla la BD
 * ni el egress de la sincronización normal. Recibos/PDF siguen siendo locales.
 *
 * Requiere un bucket "avatares" en el proyecto (ver README). Si no existe o
 * falla la red, no pasa nada: el evento sigue sincronizándose sin avatar.
 */
object AvatarSync {

    private const val BUCKET = "avatares"
    private const val LADO = 256
    private const val CALIDAD = 70

    /** Devuelve true si se ha descargado un avatar nuevo (para repintar). */
    suspend fun sincronizar(
        context: Context,
        dao: BoteDao,
        evento: Evento,
        url: String,
        key: String
    ): Boolean = withContext(Dispatchers.IO) {
        val uuid = evento.uuid
        val avatarMillis = evento.avatarMillis
        if (avatarMillis <= 0L) return@withContext false
        val base = url.trim().trimEnd('/')
        val clave = key.trim()
        if (base.isBlank() || clave.isBlank()) return@withContext false

        val imagenLocal = Ajustes.avatarImagenMillis(context, uuid)
        val subido = Ajustes.avatarSubidoMillis(context, uuid)

        // Tenemos la imagen del avatar actual y aún no está subida → subir
        if (imagenLocal == avatarMillis && subido < avatarMillis) {
            val bytes = reducir(evento.fotoPath) ?: return@withContext false
            if (subir(base, clave, uuid, bytes)) {
                Ajustes.guardarAvatarSubidoMillis(context, uuid, avatarMillis)
            }
            return@withContext false
        }

        // El avatar actual es más nuevo que la imagen que tenemos → bajar
        if (avatarMillis > imagenLocal) {
            val bytes = descargar(base, clave, uuid) ?: return@withContext false
            val destino = File(
                File(context.filesDir, "fotos").apply { mkdirs() }, "$uuid.jpg"
            )
            try {
                destino.writeBytes(bytes)
            } catch (e: Exception) {
                return@withContext false
            }
            val actual = dao.evento(evento.id) ?: return@withContext false
            if (actual.fotoPath != destino.absolutePath) {
                dao.actualizarEvento(actual.copy(fotoPath = destino.absolutePath))
            }
            Ajustes.guardarAvatarImagenMillis(context, uuid, avatarMillis)
            Ajustes.guardarAvatarSubidoMillis(context, uuid, max(subido, avatarMillis))
            return@withContext true
        }
        false
    }

    /** Decodifica la foto local y la reduce a un JPEG pequeño (256 px, q70). */
    private fun reducir(path: String): ByteArray? {
        if (path.isBlank() || !File(path).exists()) return null
        return try {
            val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, medidas)
            if (medidas.outWidth <= 0 || medidas.outHeight <= 0) return null
            var escala = 1
            while (medidas.outWidth / escala > LADO * 2 || medidas.outHeight / escala > LADO * 2) {
                escala *= 2
            }
            val bitmap = BitmapFactory.decodeFile(
                path, BitmapFactory.Options().apply { inSampleSize = escala }
            ) ?: return null
            val lado = max(bitmap.width, bitmap.height)
            val escalado = if (lado > LADO) {
                val factor = LADO.toFloat() / lado
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * factor).roundToInt().coerceAtLeast(1),
                    (bitmap.height * factor).roundToInt().coerceAtLeast(1),
                    true
                )
            } else bitmap
            val salida = ByteArrayOutputStream()
            escalado.compress(Bitmap.CompressFormat.JPEG, CALIDAD, salida)
            salida.toByteArray()
        } catch (e: Exception) {
            null
        }
    }

    private fun subir(base: String, key: String, uuid: String, bytes: ByteArray): Boolean {
        val conexion = URL("$base/storage/v1/object/$BUCKET/$uuid.jpg")
            .openConnection() as HttpURLConnection
        return try {
            conexion.requestMethod = "POST"
            conexion.connectTimeout = 10000
            conexion.readTimeout = 15000
            conexion.setRequestProperty("apikey", key)
            conexion.setRequestProperty("Authorization", "Bearer $key")
            conexion.setRequestProperty("Content-Type", "image/jpeg")
            conexion.setRequestProperty("x-upsert", "true")
            conexion.doOutput = true
            conexion.outputStream.use { it.write(bytes) }
            conexion.responseCode in 200..299
        } catch (e: Exception) {
            false
        } finally {
            conexion.disconnect()
        }
    }

    private fun descargar(base: String, key: String, uuid: String): ByteArray? {
        val conexion = URL("$base/storage/v1/object/public/$BUCKET/$uuid.jpg")
            .openConnection() as HttpURLConnection
        return try {
            conexion.requestMethod = "GET"
            conexion.connectTimeout = 10000
            conexion.readTimeout = 15000
            conexion.setRequestProperty("apikey", key)
            conexion.setRequestProperty("Authorization", "Bearer $key")
            if (conexion.responseCode != 200) return null
            conexion.inputStream.use { it.readBytes() }
        } catch (e: Exception) {
            null
        } finally {
            conexion.disconnect()
        }
    }
}
