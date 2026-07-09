package com.bote.app.data

/**
 * Cuentas del bote: cuánto ha pagado cada uno, cuánto le corresponde
 * y qué transferencias mínimas dejan el evento a cero.
 */
object Calculadora {

    data class Saldo(
        val asistente: Asistente,
        val pagadoCents: Long,
        val correspondeCents: Long
    ) {
        /** Positivo: recupera dinero; negativo: debe dinero. */
        val saldoCents: Long get() = pagadoCents - correspondeCents
    }

    data class Transferencia(val de: Asistente, val a: Asistente, val cents: Long)

    /** Reparto igualitario en puntos básicos que suma exactamente 10000. */
    fun puntosIguales(n: Int): List<Int> {
        if (n <= 0) return emptyList()
        val base = 10000 / n
        val resto = 10000 - base * n
        return List(n) { if (it < resto) base + 1 else base }
    }

    /**
     * Reparte el importe de un apunte entre sus participantes por el método
     * del resto mayor, de forma que las partes suman exactamente el importe.
     */
    fun partesDeApunte(apunte: ApunteConRepartos): Map<Long, Long> {
        val importe = apunte.apunte.gastadoCents
        val repartos = apunte.repartos.filter { it.puntosBasicos > 0 }
        if (importe == 0L || repartos.isEmpty()) return emptyMap()

        val base = mutableMapOf<Long, Long>()
        val restos = mutableListOf<Pair<Long, Long>>() // asistenteId → resto
        var asignado = 0L
        for (r in repartos) {
            val exacto = importe * r.puntosBasicos          // sobre 10000
            val parte = exacto / 10000
            base[r.asistenteId] = parte
            restos.add(r.asistenteId to exacto % 10000)
            asignado += parte
        }
        var faltan = importe - asignado
        for ((asistenteId, _) in restos.sortedByDescending { it.second }) {
            if (faltan <= 0) break
            base[asistenteId] = base.getValue(asistenteId) + 1
            faltan--
        }
        return base
    }

    fun saldos(datos: EventoCompleto): List<Saldo> {
        val pagado = mutableMapOf<Long, Long>()
        val corresponde = mutableMapOf<Long, Long>()
        for (apunte in datos.apuntes) {
            val importe = apunte.apunte.gastadoCents
            pagado[apunte.apunte.pagadorId] =
                (pagado[apunte.apunte.pagadorId] ?: 0L) + importe
            for ((asistenteId, parte) in partesDeApunte(apunte)) {
                corresponde[asistenteId] = (corresponde[asistenteId] ?: 0L) + parte
            }
        }
        return datos.asistentes.map {
            Saldo(it, pagado[it.id] ?: 0L, corresponde[it.id] ?: 0L)
        }
    }

    /**
     * Transferencias que saldan las cuentas: se van casando el mayor deudor
     * con el mayor acreedor hasta dejar todos los saldos a cero.
     */
    fun transferencias(saldos: List<Saldo>): List<Transferencia> {
        val deudores = saldos.filter { it.saldoCents < 0 }
            .map { it.asistente to -it.saldoCents }.toMutableList()
        val acreedores = saldos.filter { it.saldoCents > 0 }
            .map { it.asistente to it.saldoCents }.toMutableList()
        deudores.sortByDescending { it.second }
        acreedores.sortByDescending { it.second }

        val resultado = mutableListOf<Transferencia>()
        var i = 0
        var j = 0
        while (i < deudores.size && j < acreedores.size) {
            val (deudor, debe) = deudores[i]
            val (acreedor, cobra) = acreedores[j]
            val cents = minOf(debe, cobra)
            if (cents > 0) {
                resultado.add(Transferencia(deudor, acreedor, cents))
            }
            deudores[i] = deudor to (debe - cents)
            acreedores[j] = acreedor to (cobra - cents)
            if (deudores[i].second == 0L) i++
            if (acreedores[j].second == 0L) j++
        }
        return resultado
    }
}
