package com.bote.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.ImageView
import java.io.File

/** Copia y carga de la foto-avatar del evento (almacenada en filesDir/fotos). */
object FotoUtil {

    /** Copia la imagen elegida al almacenamiento interno y devuelve su ruta. */
    fun copiarFoto(context: Context, uri: Uri, nombre: String): String? {
        return try {
            val directorio = File(context.filesDir, "fotos").apply { mkdirs() }
            val destino = File(directorio, "$nombre.jpg")
            context.contentResolver.openInputStream(uri)?.use { entrada ->
                destino.outputStream().use { salida -> entrada.copyTo(salida) }
            } ?: return null
            destino.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    /** Decodifica la foto a un tamaño razonable y la pone en el ImageView. */
    fun cargar(imageView: ImageView, path: String): Boolean {
        if (path.isBlank() || !File(path).exists()) return false
        val bitmap = decodificar(path, 512) ?: return false
        imageView.setImageBitmap(bitmap)
        return true
    }

    private fun decodificar(path: String, maxLado: Int): Bitmap? {
        val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, medidas)
        var escala = 1
        while (medidas.outWidth / escala > maxLado * 2 || medidas.outHeight / escala > maxLado * 2) {
            escala *= 2
        }
        val opciones = BitmapFactory.Options().apply { inSampleSize = escala }
        return BitmapFactory.decodeFile(path, opciones)
    }
}
