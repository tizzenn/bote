package com.bote.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale

/**
 * Adjuntos locales de un evento (fotos y PDFs). Se copian a
 * filesDir/adjuntos/<eventoUuid>/ y se abren con el visor del sistema vía
 * FileProvider. No se sincronizan: viven solo en este dispositivo.
 */
object AdjuntoUtil {

    /** A partir de aquí se avisa de que el archivo es grande. */
    const val MAX_BYTES = 10L * 1024 * 1024

    fun directorio(context: Context, eventoUuid: String): File =
        File(context.filesDir, "adjuntos/$eventoUuid")

    fun listar(context: Context, eventoUuid: String): List<File> =
        directorio(context, eventoUuid).listFiles()
            ?.filter { it.isFile }
            ?.sortedBy { it.name.lowercase(Locale.getDefault()) }
            ?: emptyList()

    /** Tamaño del documento elegido, o -1 si no se puede saber. */
    fun tamano(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val indice = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (indice >= 0 && cursor.moveToFirst() && !cursor.isNull(indice)) {
                    cursor.getLong(indice)
                } else -1L
            } ?: -1L
        } catch (e: Exception) {
            -1L
        }
    }

    /** Nombre visible del documento elegido, o uno genérico si no lo da. */
    fun nombreVisible(context: Context, uri: Uri): String {
        val nombre = try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val indice = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (indice >= 0 && cursor.moveToFirst()) cursor.getString(indice) else null
            }
        } catch (e: Exception) {
            null
        }
        return nombre?.takeIf { it.isNotBlank() } ?: "adjunto"
    }

    /**
     * Copia el documento al almacenamiento privado del evento. Conserva el
     * nombre visible (saneado) y evita colisiones. Devuelve el File o null.
     */
    fun copiar(context: Context, uri: Uri, eventoUuid: String): File? {
        return try {
            val dir = directorio(context, eventoUuid).apply { mkdirs() }
            val base = sanear(nombreVisible(context, uri))
            var destino = File(dir, base)
            var n = 1
            while (destino.exists()) {
                val punto = base.lastIndexOf('.')
                destino = if (punto > 0)
                    File(dir, base.substring(0, punto) + "_" + n + base.substring(punto))
                else File(dir, base + "_" + n)
                n++
            }
            context.contentResolver.openInputStream(uri)?.use { entrada ->
                destino.outputStream().use { salida -> entrada.copyTo(salida) }
            } ?: return null
            destino
        } catch (e: Exception) {
            null
        }
    }

    /** Abre el adjunto con el visor del sistema. Devuelve false si no hay app. */
    fun abrir(context: Context, archivo: File): Boolean {
        return try {
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", archivo
            )
            val intento = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, tipoMime(archivo))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intento.resolveActivity(context.packageManager) != null) {
                context.startActivity(intento)
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }

    fun eliminar(archivo: File) {
        runCatching { archivo.delete() }
    }

    /** Borra todos los adjuntos de un evento (al eliminarlo). */
    fun eliminarTodos(context: Context, eventoUuid: String) {
        runCatching { directorio(context, eventoUuid).deleteRecursively() }
    }

    fun esImagen(archivo: File): Boolean =
        tipoMime(archivo).startsWith("image/")

    private fun tipoMime(archivo: File): String {
        val ext = archivo.extension.lowercase(Locale.getDefault())
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: "application/octet-stream"
    }

    private fun sanear(nombre: String): String =
        nombre.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80).ifBlank { "adjunto" }

    /** Formatea un tamaño en bytes de forma legible (KB/MB). */
    fun formatearTamano(bytes: Long): String {
        if (bytes < 0) return ""
        val kb = 1024.0
        val mb = kb * 1024
        return when {
            bytes >= mb -> String.format(Locale.getDefault(), "%.1f MB", bytes / mb)
            bytes >= kb -> String.format(Locale.getDefault(), "%.0f KB", bytes / kb)
            else -> "$bytes B"
        }
    }
}
