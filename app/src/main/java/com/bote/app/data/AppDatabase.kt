package com.bote.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Evento::class, Asistente::class, Apunte::class, Reparto::class, ApunteBorrado::class],
    version = 2,
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

        fun get(context: Context): AppDatabase =
            instancia ?: synchronized(this) {
                instancia ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bote.db"
                ).addMigrations(MIGRACION_1_2).build().also { instancia = it }
            }
    }
}
