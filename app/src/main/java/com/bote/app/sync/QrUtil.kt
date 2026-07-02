package com.bote.app.sync

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

object QrUtil {

    /** Capacidad práctica de un QR en modo byte (versión 40, corrección L). */
    const val MAX_CARACTERES = 2900

    fun generar(texto: String, tamano: Int): Bitmap {
        val matriz = QRCodeWriter().encode(
            texto, BarcodeFormat.QR_CODE, tamano, tamano,
            mapOf(EncodeHintType.MARGIN to 1)
        )
        val bitmap = Bitmap.createBitmap(tamano, tamano, Bitmap.Config.RGB_565)
        for (x in 0 until tamano) {
            for (y in 0 until tamano) {
                bitmap.setPixel(x, y, if (matriz.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }
}
