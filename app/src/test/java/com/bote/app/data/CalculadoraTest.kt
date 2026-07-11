package com.bote.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculadoraTest {

    private fun asistente(id: Long, nombre: String = "A$id") =
        Asistente(id = id, eventoId = 1, nombre = nombre)

    private fun apunte(
        id: Long,
        pagador: Long,
        gastado: Long,
        repartos: List<Reparto>
    ) = ApunteConRepartos(
        Apunte(id = id, eventoId = 1, pagadorId = pagador, gastadoCents = gastado),
        repartos
    )

    private fun completo(
        asistentes: List<Asistente>,
        apuntes: List<ApunteConRepartos>
    ) = EventoCompleto(Evento(id = 1), asistentes, apuntes)

    // ── puntosIguales ─────────────────────────────────────────────

    @Test
    fun puntosIgualesSumanDiezMil() {
        for (n in 1..12) {
            val puntos = Calculadora.puntosIguales(n)
            assertEquals(n, puntos.size)
            assertEquals(10000, puntos.sum())
            // Diferencia máxima de 1 punto básico entre partes
            assertTrue(puntos.max() - puntos.min() <= 1)
        }
    }

    @Test
    fun puntosIgualesConCeroParticipantes() {
        assertTrue(Calculadora.puntosIguales(0).isEmpty())
    }

    // ── partesDeApunte ────────────────────────────────────────────

    @Test
    fun partesSumanExactamenteElImporte() {
        // 10,00 € entre 3: 3,34 + 3,33 + 3,33 (nunca se pierde un céntimo)
        val partes = Calculadora.partesDeApunte(
            apunte(
                1, pagador = 1, gastado = 1000,
                repartos = listOf(
                    Reparto(1, 1, 3334), Reparto(1, 2, 3333), Reparto(1, 3, 3333)
                )
            )
        )
        assertEquals(1000, partes.values.sum())
    }

    @Test
    fun parteConPorcentajeCeroNoParticipa() {
        // Incorporación tardía: figura con 0 puntos y no asume coste
        val partes = Calculadora.partesDeApunte(
            apunte(
                1, pagador = 1, gastado = 1000,
                repartos = listOf(Reparto(1, 1, 10000), Reparto(1, 2, 0))
            )
        )
        assertEquals(1000, partes[1L])
        assertTrue(2L !in partes)
    }

    // ── saldos ────────────────────────────────────────────────────

    @Test
    fun saldosCuadranACero() {
        val a = listOf(asistente(1), asistente(2), asistente(3))
        val datos = completo(
            a,
            listOf(
                apunte(
                    1, pagador = 1, gastado = 3000,
                    repartos = listOf(
                        Reparto(1, 1, 3334), Reparto(1, 2, 3333), Reparto(1, 3, 3333)
                    )
                ),
                apunte(
                    2, pagador = 2, gastado = 900,
                    repartos = listOf(
                        Reparto(2, 1, 3334), Reparto(2, 2, 3333), Reparto(2, 3, 3333)
                    )
                )
            )
        )
        val saldos = Calculadora.saldos(datos)
        assertEquals(0, saldos.sumOf { it.saldoCents })
        assertEquals(3900, saldos.sumOf { it.pagadoCents })
        assertEquals(3900, saldos.sumOf { it.correspondeCents })
    }

    // ── transferencias ────────────────────────────────────────────

    @Test
    fun transferenciasSaldanTodasLasDeudas() {
        val a = listOf(asistente(1), asistente(2), asistente(3))
        val datos = completo(
            a,
            listOf(
                apunte(
                    1, pagador = 1, gastado = 3000,
                    repartos = listOf(
                        Reparto(1, 1, 3334), Reparto(1, 2, 3333), Reparto(1, 3, 3333)
                    )
                )
            )
        )
        val saldos = Calculadora.saldos(datos)
        val transferencias = Calculadora.transferencias(saldos)

        // Lo transferido iguala exactamente lo que se debe
        val deudaTotal = saldos.filter { it.saldoCents < 0 }.sumOf { -it.saldoCents }
        assertEquals(deudaTotal, transferencias.sumOf { it.cents })
        // Nadie paga más de lo que debe ni cobra más de lo que le corresponde
        for (s in saldos.filter { it.saldoCents < 0 }) {
            assertEquals(
                -s.saldoCents,
                transferencias.filter { it.de.id == s.asistente.id }.sumOf { it.cents }
            )
        }
        for (s in saldos.filter { it.saldoCents > 0 }) {
            assertEquals(
                s.saldoCents,
                transferencias.filter { it.a.id == s.asistente.id }.sumOf { it.cents }
            )
        }
    }

    @Test
    fun sinDeudasNoHayTransferencias() {
        val a = listOf(asistente(1), asistente(2))
        val datos = completo(
            a,
            listOf(
                apunte(
                    1, pagador = 1, gastado = 500,
                    repartos = listOf(Reparto(1, 1, 5000), Reparto(1, 2, 5000))
                ),
                apunte(
                    2, pagador = 2, gastado = 500,
                    repartos = listOf(Reparto(2, 1, 5000), Reparto(2, 2, 5000))
                )
            )
        )
        assertTrue(Calculadora.transferencias(Calculadora.saldos(datos)).isEmpty())
    }
}
