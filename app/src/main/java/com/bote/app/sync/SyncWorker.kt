package com.bote.app.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bote.app.config.Ajustes
import com.bote.app.data.AppDatabase

/**
 * Sincronización periódica en segundo plano. Recorre los eventos con servidor
 * y los sincroniza, saltándose los ya saldados (terminados, no cambian). Es
 * una sync completa: el ahorro real de datos llegará con los deltas.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!Ajustes.autoSyncActivo(applicationContext)) return Result.success()
        val dao = AppDatabase.get(applicationContext).dao()
        var huboFalloRed = false
        for (evento in dao.todosEventos()) {
            if (!evento.sincronizable) continue
            // Los eventos ya saldados no cambian: no gastamos red en ellos.
            val completo = dao.eventoCompleto(evento.id)
            if (completo != null && completo.saldado) continue
            val res = try {
                SyncRemoto.sincronizar(dao, evento.id)
            } catch (e: Exception) {
                SyncRemoto.Resultado.SinRed
            }
            when (res) {
                is SyncRemoto.Resultado.Ok ->
                    Ajustes.guardarUltimaSync(
                        applicationContext, evento.uuid, System.currentTimeMillis()
                    )
                SyncRemoto.Resultado.SinRed -> huboFalloRed = true
                else -> { /* error del servidor/auth: no reintentar en bucle */ }
            }
        }
        // Si falló por red, deja que WorkManager reintente con backoff.
        return if (huboFalloRed) Result.retry() else Result.success()
    }
}
