package com.bote.app

import android.app.Application
import com.bote.app.config.Ajustes
import com.bote.app.notification.NotificationHelper
import com.bote.app.sync.SyncScheduler

class BoteApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Ajustes.aplicarTema(this)
        NotificationHelper.crearCanal(this)
        SyncScheduler.configurar(this)
    }
}
