package com.bote.app.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.util.UUID

/** Modos de una ficha de evento. */
object Modo {
    const val RESTRINGIDO = "RESTRINGIDO"
    const val COLABORATIVO = "COLABORATIVO"
}

/**
 * Ficha de evento: el bote común de un grupo. Los UUID dan identidad
 * estable a eventos y asistentes para poder sincronizar entre dispositivos
 * mediante exportación/importación.
 */
@Entity(tableName = "eventos")
data class Evento(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val titulo: String = "",
    val descripcion: String = "",
    val fechaMillis: Long = 0,
    val ubicacion: String = "",
    val fotoPath: String = "",
    val modo: String = Modo.COLABORATIVO,
    /** True si el evento se creó en este dispositivo (yo soy el admin). */
    val soyCreador: Boolean = true,
    /** Qué asistente soy yo en este evento (0 = sin decidir). */
    val miAsistenteId: Long = 0,
    /** Candado echado: cuentas finales generadas, no se admiten más apuntes. */
    val cerrado: Boolean = false,
    val creadoMillis: Long = System.currentTimeMillis(),
    /** Última modificación de los datos del evento; decide quién gana al fusionar. */
    val modificadoMillis: Long = System.currentTimeMillis(),
    /** Servidor de sincronización propio de este evento (grupo). Viaja con
     *  el evento al compartirlo, así quien lo importa queda conectado solo. */
    val syncActivo: Boolean = false,
    val syncUrl: String = "",
    val syncKey: String = ""
) {
    val esRestringido: Boolean get() = modo == Modo.RESTRINGIDO
    val sincronizable: Boolean
        get() = syncActivo && syncUrl.isNotBlank() && syncKey.isNotBlank()
}

@Entity(
    tableName = "asistentes",
    foreignKeys = [ForeignKey(
        entity = Evento::class,
        parentColumns = ["id"],
        childColumns = ["eventoId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("eventoId")]
)
data class Asistente(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventoId: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val nombre: String = "",
    val telefono: String = "",
    val email: String = "",
    val esCreador: Boolean = false,
    /** Ha pagado su parte de la cuenta final. */
    val liquidado: Boolean = false,
    val liquidadoMillis: Long = 0
)

/**
 * Apunte: un bien o servicio que ha pagado una única persona.
 * Los importes van en céntimos para evitar errores de coma flotante.
 */
@Entity(
    tableName = "apuntes",
    foreignKeys = [ForeignKey(
        entity = Evento::class,
        parentColumns = ["id"],
        childColumns = ["eventoId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("eventoId")]
)
data class Apunte(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventoId: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val concepto: String = "",
    val pagadorId: Long = 0,
    val presupuestadoCents: Long? = null,
    val gastadoCents: Long = 0,
    /** Obligatorio para cerrar el apunte (y para poder cerrar la cuenta). */
    val pagadoCents: Long? = null,
    /** True: a partes iguales; false: porcentajes personalizados. */
    val repartoIgualitario: Boolean = true,
    /** Nombre de la CategoriaApunte (restaurante, copas, regalo…). */
    val categoria: String = "OTROS",
    /** Foto del tique o recibo (ruta local; no viaja en la sincronización). */
    val fotoPath: String = "",
    val fechaMillis: Long = System.currentTimeMillis(),
    /** Última modificación; al fusionar dos versiones gana la más reciente. */
    val modificadoMillis: Long = System.currentTimeMillis()
) {
    val estaCerrado: Boolean get() = pagadoCents != null
    /** Importe que cuenta para el reparto: lo pagado si existe, si no lo gastado. */
    val importeEfectivo: Long get() = pagadoCents ?: gastadoCents
}

/** Porcentaje (en puntos básicos sobre 10000) que asume cada asistente de un apunte. */
@Entity(
    tableName = "repartos",
    primaryKeys = ["apunteId", "asistenteId"],
    foreignKeys = [ForeignKey(
        entity = Apunte::class,
        parentColumns = ["id"],
        childColumns = ["apunteId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("apunteId"), Index("asistenteId")]
)
data class Reparto(
    val apunteId: Long,
    val asistenteId: Long,
    val puntosBasicos: Int
)

/**
 * Lápida de un apunte borrado: evita que la fusión al importar
 * resucite apuntes que alguien eliminó a propósito.
 */
@Entity(
    tableName = "apuntes_borrados",
    primaryKeys = ["eventoId", "uuid"],
    foreignKeys = [ForeignKey(
        entity = Evento::class,
        parentColumns = ["id"],
        childColumns = ["eventoId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("eventoId")]
)
data class ApunteBorrado(
    val eventoId: Long,
    val uuid: String,
    val borradoMillis: Long = System.currentTimeMillis()
)

/** Tipos de entrada del registro de actividad de un evento. */
object TipoRegistro {
    const val EVENTO = "evento"
    const val ASISTENTE = "asistente"
    const val ASISTENTE_FUERA = "asistente_fuera"
    const val APUNTE = "apunte"
    const val APUNTE_BORRADO = "apunte_borrado"
    const val PAGO = "pago"
    const val CANDADO = "candado"
    const val SYNC = "sync"
}

/**
 * Entrada del registro de actividad: quién se une, qué se apunta, qué se
 * paga… El UUID permite unir los registros de varios dispositivos al
 * sincronizar sin duplicar entradas.
 */
@Entity(
    tableName = "registro",
    foreignKeys = [ForeignKey(
        entity = Evento::class,
        parentColumns = ["id"],
        childColumns = ["eventoId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("eventoId")]
)
data class Registro(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventoId: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val tipo: String = "",
    val texto: String = "",
    val millis: Long = System.currentTimeMillis()
)

data class ApunteConRepartos(
    @Embedded val apunte: Apunte,
    @Relation(parentColumn = "id", entityColumn = "apunteId")
    val repartos: List<Reparto>
)

data class EventoCompleto(
    @Embedded val evento: Evento,
    @Relation(parentColumn = "id", entityColumn = "eventoId")
    val asistentes: List<Asistente>,
    @Relation(entity = Apunte::class, parentColumn = "id", entityColumn = "eventoId")
    val apuntes: List<ApunteConRepartos>
) {
    val todosLiquidados: Boolean
        get() = asistentes.isNotEmpty() && asistentes.all { it.liquidado }

    /** Saldado: cuenta cerrada y todo el mundo ha pagado su parte. */
    val saldado: Boolean get() = evento.cerrado && todosLiquidados

    val totalGastadoCents: Long get() = apuntes.sumOf { it.apunte.importeEfectivo }

    fun miAsistente(): Asistente? = asistentes.firstOrNull { it.id == evento.miAsistenteId }
}
