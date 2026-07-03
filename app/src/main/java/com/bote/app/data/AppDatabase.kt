package com.bote.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Evento::class, Asistente::class, Apunte::class, Reparto::class,
        ApunteBorrado::class, Registro::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun dao(): BoteDao

    companion object {
        @Volatile
        private var instancia: AppDatabase? = null

        /** v1.0 → v1.1: foto del tique, marcas de modificación y lápidas de borrado. */
        private val MIGRACION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE eventos ADD COLUMN modificadoMillis INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE apuntes ADD COLUMN fotoPath TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE apuntes ADD COLUMN modificadoMillis INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `apuntes_borrados` (" +
                        "`eventoId` INTEGER NOT NULL, `uuid` TEXT NOT NULL, " +
                        "`borradoMillis` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`eventoId`, `uuid`), " +
                        "FOREIGN KEY(`eventoId`) REFERENCES `eventos`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_apuntes_borrados_eventoId` " +
                        "ON `apuntes_borrados` (`eventoId`)"
                )
            }
        }

        /** v1.1 → v1.2: registro de actividad del evento. */
        private val MIGRACION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `registro` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`eventoId` INTEGER NOT NULL, `uuid` TEXT NOT NULL, " +
                        "`tipo` TEXT NOT NULL, `texto` TEXT NOT NULL, " +
                        "`millis` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`eventoId`) REFERENCES `eventos`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_registro_eventoId` " +
                        "ON `registro` (`eventoId`)"
                )
            }
        }

        /** v1.8 → v1.9: servidor de sincronización propio de cada evento. */
        private val MIGRACION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE eventos ADD COLUMN syncActivo INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE eventos ADD COLUMN syncUrl TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE eventos ADD COLUMN syncKey TEXT NOT NULL DEFAULT ''")
            }
        }

        fun get(context: Context): AppDatabase =
            instancia ?: synchronized(this) {
                instancia ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bote.db"
                ).addMigrations(MIGRACION_1_2, MIGRACION_2_3, MIGRACION_3_4)
                    .build().also { instancia = it }
            }
    }
}
