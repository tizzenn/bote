package com.bote.app.sync

import android.util.Base64
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Codificación compacta del evento para que quepa en un código QR:
 * JSON → gzip → Base64, con un prefijo que identifica el formato.
 */
object SyncCodec {

    const val PREFIJO = "BOTE1:"

    fun comprimir(json: String): String {
        val salida = ByteArrayOutputStream()
        GZIPOutputStream(salida).use { it.write(json.toByteArray(Charsets.UTF_8)) }
        return PREFIJO + Base64.encodeToString(salida.toByteArray(), Base64.NO_WRAP)
    }

    /** Acepta tanto el formato comprimido del QR como el JSON plano del archivo. */
    fun decodificar(texto: String): String {
        val limpio = texto.trim()
        if (!limpio.startsWith(PREFIJO)) return limpio
        val bytes = Base64.decode(limpio.removePrefix(PREFIJO), Base64.NO_WRAP)
        return GZIPInputStream(bytes.inputStream()).use {
            it.readBytes().toString(Charsets.UTF_8)
        }
    }
}
