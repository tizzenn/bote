package com.bote.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DineroTest {

    @Test
    fun parseaEnterosYDecimales() {
        assertEquals(1200L, Dinero.parsear("12"))
        assertEquals(1250L, Dinero.parsear("12.5"))
        assertEquals(1250L, Dinero.parsear("12,50"))
        assertEquals(5L, Dinero.parsear("0.05"))
        assertEquals(0L, Dinero.parsear("0"))
    }

    @Test
    fun rechazaEntradasInvalidas() {
        assertNull(Dinero.parsear(""))
        assertNull(Dinero.parsear("   "))
        assertNull(Dinero.parsear("abc"))
        assertNull(Dinero.parsear("-5"))
        assertNull(Dinero.parsear("12,34,56"))
    }

    @Test
    fun aTextoEsEstableParaEditar() {
        assertEquals("12.50", Dinero.aTexto(1250))
        assertEquals("0.05", Dinero.aTexto(5))
        // Ida y vuelta sin pérdida
        assertEquals(1250L, Dinero.parsear(Dinero.aTexto(1250)))
    }
}
