package com.bote.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BoteDao {

    // ── Eventos ───────────────────────────────────────────────────

    @Transaction
    @Query("SELECT * FROM eventos ORDER BY fechaMillis DESC")
    fun observarEventos(): Flow<List<EventoCompleto>>

    @Transaction
    @Query("SELECT * FROM eventos WHERE id = :id")
    suspend fun eventoCompleto(id: Long): EventoCompleto?

    @Query("SELECT * FROM eventos WHERE id = :id")
    suspend fun evento(id: Long): Evento?

    @Query("SELECT * FROM eventos WHERE uuid = :uuid")
    suspend fun eventoPorUuid(uuid: String): Evento?

    @Query("SELECT * FROM eventos")
    suspend fun todosEventos(): List<Evento>

    /** Direcciones ya usadas, para sugerirlas al crear eventos nuevos. */
    @Query("SELECT DISTINCT ubicacion FROM eventos WHERE ubicacion != '' ORDER BY ubicacion")
    suspend fun ubicaciones(): List<String>

    @Insert
    suspend fun insertarEvento(evento: Evento): Long

    @Update
    suspend fun actualizarEvento(evento: Evento)

    @Query("DELETE FROM eventos WHERE id = :id")
    suspend fun eliminarEvento(id: Long)

    // ── Asistentes ────────────────────────────────────────────────

    @Insert
    suspend fun insertarAsistente(asistente: Asistente): Long

    @Update
    suspend fun actualizarAsistente(asistente: Asistente)

    @Delete
    suspend fun eliminarAsistente(asistente: Asistente)

    @Query("SELECT COUNT(*) FROM apuntes WHERE pagadorId = :asistenteId")
    suspend fun apuntesQuePaga(asistenteId: Long): Int

    @Query("DELETE FROM repartos WHERE asistenteId = :asistenteId")
    suspend fun eliminarRepartosDeAsistente(asistenteId: Long)

    // ── Apuntes y repartos ────────────────────────────────────────

    @Transaction
    @Query("SELECT * FROM apuntes WHERE id = :id")
    suspend fun apunteConRepartos(id: Long): ApunteConRepartos?

    @Insert
    suspend fun insertarApunte(apunte: Apunte): Long

    @Update
    suspend fun actualizarApunte(apunte: Apunte)

    @Query("DELETE FROM apuntes WHERE id = :id")
    suspend fun eliminarApunte(id: Long)

    @Insert
    suspend fun insertarRepartos(repartos: List<Reparto>)

    @Query("DELETE FROM repartos WHERE apunteId = :apunteId")
    suspend fun eliminarRepartosDeApunte(apunteId: Long)

    // ── Lápidas de apuntes borrados (para la fusión al sincronizar) ──

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarBorrado(borrado: ApunteBorrado)

    @Query("SELECT * FROM apuntes_borrados WHERE eventoId = :eventoId")
    suspend fun borradosDeEvento(eventoId: Long): List<ApunteBorrado>

    // ── Registro de actividad ─────────────────────────────────────

    @Insert
    suspend fun insertarRegistro(registro: Registro)

    @Query("SELECT * FROM registro WHERE eventoId = :eventoId ORDER BY millis DESC")
    suspend fun registroDeEvento(eventoId: Long): List<Registro>

    /** Reemplaza el reparto completo de un apunte. */
    @Transaction
    suspend fun guardarRepartos(apunteId: Long, repartos: List<Reparto>) {
        eliminarRepartosDeApunte(apunteId)
        insertarRepartos(repartos)
    }
}
