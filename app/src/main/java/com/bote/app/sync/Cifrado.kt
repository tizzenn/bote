package com.bote.app.sync

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cifrado extremo a extremo del blob que se sube al servidor. La clave se
 * deriva de una frase en el dispositivo (PBKDF2) y nunca sale de él; el
 * servidor solo ve bytes opacos. AES-256-GCM (autenticado): si la frase es
 * incorrecta o el blob está manipulado, el descifrado falla y devuelve null.
 *
 * IMPORTANTE: la BD local sigue en claro; esto solo cifra lo que viaja. Un
 * fallo aquí impide sincronizar, pero nunca corrompe los datos locales.
 */
object Cifrado {

    private const val VERSION = 1
    private const val ITERACIONES = 120_000
    private const val BITS_CLAVE = 256
    private const val BYTES_SAL = 16
    private const val BYTES_IV = 12
    private const val BITS_TAG = 128

    private fun clave(frase: String, sal: ByteArray): SecretKeySpec {
        val fabrica = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(frase.toCharArray(), sal, ITERACIONES, BITS_CLAVE)
        return SecretKeySpec(fabrica.generateSecret(spec).encoded, "AES")
    }

    /** Cifra el texto y devuelve un Base64 con versión + sal + IV + criptograma. */
    fun cifrar(texto: String, frase: String): String {
        val aleatorio = SecureRandom()
        val sal = ByteArray(BYTES_SAL).also { aleatorio.nextBytes(it) }
        val iv = ByteArray(BYTES_IV).also { aleatorio.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, clave(frase, sal), GCMParameterSpec(BITS_TAG, iv))
        val cripto = cipher.doFinal(texto.toByteArray(Charsets.UTF_8))
        val todo = ByteArray(1 + sal.size + iv.size + cripto.size)
        todo[0] = VERSION.toByte()
        System.arraycopy(sal, 0, todo, 1, sal.size)
        System.arraycopy(iv, 0, todo, 1 + sal.size, iv.size)
        System.arraycopy(cripto, 0, todo, 1 + sal.size + iv.size, cripto.size)
        // java.util.Base64 (API 26+) y no android.util: así es testeable en JVM.
        return Base64.getEncoder().encodeToString(todo)
    }

    /** Descifra un blob de [cifrar]; devuelve null si la frase no cuadra. */
    fun descifrar(blob: String, frase: String): String? {
        return try {
            val todo = Base64.getDecoder().decode(blob)
            if (todo.isEmpty() || todo[0].toInt() != VERSION) return null
            val minimo = 1 + BYTES_SAL + BYTES_IV
            if (todo.size <= minimo) return null
            val sal = todo.copyOfRange(1, 1 + BYTES_SAL)
            val iv = todo.copyOfRange(1 + BYTES_SAL, minimo)
            val cripto = todo.copyOfRange(minimo, todo.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, clave(frase, sal), GCMParameterSpec(BITS_TAG, iv))
            String(cipher.doFinal(cripto), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }
}
