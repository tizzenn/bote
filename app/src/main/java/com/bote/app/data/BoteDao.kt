package com.bote.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
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

    /** Reemplaza el reparto completo de un apunte. */
    @Transaction
    suspend fun guardarRepartos(apunteId: Long, repartos: List<Reparto>) {
        eliminarRepartosDeApunte(apunteId)
        insertarRepartos(repartos)
    }
}
