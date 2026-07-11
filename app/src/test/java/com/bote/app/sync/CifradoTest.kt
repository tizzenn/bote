package com.bote.app.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CifradoTest {

    private val frase = "frase de prueba del grupo"

    @Test
    fun cifrarYDescifrarDevuelveElOriginal() {
        val texto = """{"evento":{"titulo":"Cañas viernes","total":1250}}"""
        val blob = Cifrado.cifrar(texto, frase)
        assertNotEquals(texto, blob)
        assertEquals(texto, Cifrado.descifrar(blob, frase))
    }

    @Test
    fun textoConAcentosYEmoji() {
        val texto = "añémé 🍺 €uros"
        assertEquals(texto, Cifrado.descifrar(Cifrado.cifrar(texto, frase), frase))
    }

    @Test
    fun fraseIncorrectaDevuelveNull() {
        val blob = Cifrado.cifrar("secreto", frase)
        assertNull(Cifrado.descifrar(blob, "otra frase"))
    }

    @Test
    fun blobManipuladoDevuelveNull() {
        val blob = Cifrado.cifrar("secreto", frase)
        // Cambiar el último carácter corrompe el tag GCM
        val roto = blob.dropLast(1) + if (blob.last() == 'A') 'B' else 'A'
        assertNull(Cifrado.descifrar(roto, frase))
    }

    @Test
    fun basuraDevuelveNullSinExplotar() {
        assertNull(Cifrado.descifrar("", frase))
        assertNull(Cifrado.descifrar("no-es-base64!!!", frase))
        assertNull(Cifrado.descifrar("QQ==", frase)) // demasiado corto
    }

    @Test
    fun cadaCifradoEsDistinto() {
        // Sal e IV aleatorios: el mismo texto nunca produce el mismo blob
        assertNotEquals(Cifrado.cifrar("hola", frase), Cifrado.cifrar("hola", frase))
    }
}
