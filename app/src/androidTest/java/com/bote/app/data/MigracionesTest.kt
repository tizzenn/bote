package com.bote.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Prueba la actualización real desde v2.5 (BD versión 4): construye a mano el
 * esquema v4, mete datos y abre con Room + migraciones. Al abrir sin
 * room_master_table, Room VALIDA el esquema final contra las entidades: si
 * las migraciones dejan una columna de más o de menos, este test revienta
 * igual que reventaría la app del usuario al actualizar.
 */
@RunWith(AndroidJUnit4::class)
class MigracionesTest {

    private val nombreBd = "test-migraciones.db"

    private fun crearBdV4(context: Context) {
        context.deleteDatabase(nombreBd)
        val ruta = context.getDatabasePath(nombreBd).apply { parentFile?.mkdirs() }
        val db = SQLiteDatabase.openOrCreateDatabase(ruta, null)
        db.execSQL(
            "CREATE TABLE `eventos` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`uuid` TEXT NOT NULL, `titulo` TEXT NOT NULL, `descripcion` TEXT NOT NULL, " +
                "`fechaMillis` INTEGER NOT NULL, `ubicacion` TEXT NOT NULL, " +
                "`fotoPath` TEXT NOT NULL, `modo` TEXT NOT NULL, `soyCreador` INTEGER NOT NULL, " +
                "`miAsistenteId` INTEGER NOT NULL, `cerrado` INTEGER NOT NULL, " +
                "`creadoMillis` INTEGER NOT NULL, `modificadoMillis` INTEGER NOT NULL, " +
                "`syncActivo` INTEGER NOT NULL, `syncUrl` TEXT NOT NULL, `syncKey` TEXT NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE `asistentes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`eventoId` INTEGER NOT NULL, `uuid` TEXT NOT NULL, `nombre` TEXT NOT NULL, " +
                "`telefono` TEXT NOT NULL, `email` TEXT NOT NULL, `esCreador` INTEGER NOT NULL, " +
                "`liquidado` INTEGER NOT NULL, `liquidadoMillis` INTEGER NOT NULL, " +
                "FOREIGN KEY(`eventoId`) REFERENCES `eventos`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        db.execSQL("CREATE INDEX `index_asistentes_eventoId` ON `asistentes` (`eventoId`)")
        db.execSQL(
            "CREATE TABLE `apuntes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`eventoId` INTEGER NOT NULL, `uuid` TEXT NOT NULL, `concepto` TEXT NOT NULL, " +
                "`pagadorId` INTEGER NOT NULL, `presupuestadoCents` INTEGER, " +
                "`gastadoCents` INTEGER NOT NULL, `pagadoCents` INTEGER, " +
                "`repartoIgualitario` INTEGER NOT NULL, `categoria` TEXT NOT NULL, " +
                "`fotoPath` TEXT NOT NULL, `fechaMillis` INTEGER NOT NULL, " +
                "`modificadoMillis` INTEGER NOT NULL, " +
                "FOREIGN KEY(`eventoId`) REFERENCES `eventos`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        db.execSQL("CREATE INDEX `index_apuntes_eventoId` ON `apuntes` (`eventoId`)")
        db.execSQL(
            "CREATE TABLE `repartos` (`apunteId` INTEGER NOT NULL, " +
                "`asistenteId` INTEGER NOT NULL, `puntosBasicos` INTEGER NOT NULL, " +
                "PRIMARY KEY(`apunteId`, `asistenteId`), " +
                "FOREIGN KEY(`apunteId`) REFERENCES `apuntes`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        db.execSQL("CREATE INDEX `index_repartos_apunteId` ON `repartos` (`apunteId`)")
        db.execSQL("CREATE INDEX `index_repartos_asistenteId` ON `repartos` (`asistenteId`)")
        db.execSQL(
            "CREATE TABLE `apuntes_borrados` (`eventoId` INTEGER NOT NULL, " +
                "`uuid` TEXT NOT NULL, `borradoMillis` INTEGER NOT NULL, " +
                "PRIMARY KEY(`eventoId`, `uuid`), " +
                "FOREIGN KEY(`eventoId`) REFERENCES `eventos`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        db.execSQL(
            "CREATE INDEX `index_apuntes_borrados_eventoId` ON `apuntes_borrados` (`eventoId`)"
        )
        db.execSQL(
            "CREATE TABLE `registro` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`eventoId` INTEGER NOT NULL, `uuid` TEXT NOT NULL, `tipo` TEXT NOT NULL, " +
                "`texto` TEXT NOT NULL, `millis` INTEGER NOT NULL, " +
                "FOREIGN KEY(`eventoId`) REFERENCES `eventos`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        db.execSQL("CREATE INDEX `index_registro_eventoId` ON `registro` (`eventoId`)")

        // Datos de una instalación v2.5 típica
        db.execSQL(
            "INSERT INTO eventos VALUES (1, 'uuid-evento', 'Finde', '', 1000, '', '', " +
                "'COLABORATIVO', 1, 1, 0, 1000, 1000, 0, '', '')"
        )
        db.execSQL(
            "INSERT INTO asistentes VALUES (1, 1, 'uuid-a1', 'Ana', '', '', 1, 0, 0)"
        )
        // Apunte con "pagado" distinto del gastado: el pagado era el efectivo
        db.execSQL(
            "INSERT INTO apuntes VALUES (1, 1, 'uuid-ap1', 'Cena', 1, NULL, 1200, 1500, " +
                "1, 'RESTAURANTE', '', 1000, 1000)"
        )
        // Apunte sin "pagado": vale el gastado
        db.execSQL(
            "INSERT INTO apuntes VALUES (2, 1, 'uuid-ap2', 'Hielo', 1, 300, 800, NULL, " +
                "1, 'SUPER', '', 1000, 1000)"
        )
        db.execSQL("INSERT INTO repartos VALUES (1, 1, 10000)")
        db.execSQL("INSERT INTO repartos VALUES (2, 1, 10000)")
        db.version = 4
        db.close()
    }

    @Test
    fun migraDesdeV4ValidaEsquemaYConservaImportes() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        crearBdV4(context)

        val room = Room.databaseBuilder(context, AppDatabase::class.java, nombreBd)
            .addMigrations(*AppDatabase.MIGRACIONES)
            .build()
        try {
            val completo = room.dao().eventoCompleto(1)
            assertNotNull(completo)
            completo!!

            // El gastado hereda el "pagado" (importe efectivo) si existía
            val porUuid = completo.apuntes.associateBy { it.apunte.uuid }
            assertEquals(1500L, porUuid.getValue("uuid-ap1").apunte.gastadoCents)
            assertEquals(800L, porUuid.getValue("uuid-ap2").apunte.gastadoCents)
            // El presupuestado nullable sobrevive
            assertEquals(300L, porUuid.getValue("uuid-ap2").apunte.presupuestadoCents)
            // La columna nueva del avatar arranca a 0
            assertEquals(0L, completo.evento.avatarMillis)
            // Asistentes y repartos intactos
            assertEquals(1, completo.asistentes.size)
            assertEquals(2, completo.apuntes.sumOf { it.repartos.size })
        } finally {
            room.close()
            context.deleteDatabase(nombreBd)
        }
    }
}
