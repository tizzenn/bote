package com.bote.app.sync

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object QrUtil {

    /**
     * Tope de caracteres para ofrecer QR. Deliberadamente bajo: solo eventos
     * pequeños. Un payload mayor daría un QR de versión alta (muy denso) que no
     * se lee fiablemente de pantalla a cámara; en ese caso se cae a archivo.
     * ~1200 caracteres ⇒ QR de versión ≈ 25 con corrección L (~117 módulos),
     * cómodo de escanear a 800 px (~6,6 px por módulo). Por encima, archivo.
     */
    const val MAX_CARACTERES = 1200

    fun generar(texto: String, tamano: Int): Bitmap {
        val hints = mapOf(
            EncodeHintType.MARGIN to 2,
            // Corrección baja (L): más capacidad y menor versión para el mismo
            // dato ⇒ módulos más grandes y lectura más fiable en pantalla.
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
            // El payload es Base64 (ASCII): ISO-8859-1 evita la cabecera ECI.
            EncodeHintType.CHARACTER_SET to "ISO-8859-1"
        )
        val matriz = QRCodeWriter().encode(
            texto, BarcodeFormat.QR_CODE, tamano, tamano, hints
        )
        val bitmap = Bitmap.createBitmap(tamano, tamano, Bitmap.Config.RGB_565)
        val fila = IntArray(tamano)
        for (y in 0 until tamano) {
            for (x in 0 until tamano) {
                fila[x] = if (matriz.get(x, y)) Color.BLACK else Color.WHITE
            }
            bitmap.setPixels(fila, 0, tamano, 0, y, tamano, 1)
        }
        return bitmap
    }
}
