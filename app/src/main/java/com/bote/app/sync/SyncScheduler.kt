package com.bote.app.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.bote.app.config.Ajustes
import java.util.concurrent.TimeUnit

/** Programa (o cancela) la sincronización periódica cada hora. */
object SyncScheduler {

    private const val TRABAJO = "sync_periodica"

    /** Llamar al arrancar la app y cada vez que cambien los ajustes de sync. */
    fun configurar(context: Context) {
        val workManager = WorkManager.getInstance(context)
        if (!Ajustes.autoSyncActivo(context)) {
            workManager.cancelUniqueWork(TRABAJO)
            return
        }
        val red = if (Ajustes.autoSyncSoloWifi(context))
            NetworkType.UNMETERED else NetworkType.CONNECTED
        val peticion = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(red).build()
            )
            .build()
        workManager.enqueueUniquePeriodicWork(
            TRABAJO, ExistingPeriodicWorkPolicy.UPDATE, peticion
        )
    }
}
