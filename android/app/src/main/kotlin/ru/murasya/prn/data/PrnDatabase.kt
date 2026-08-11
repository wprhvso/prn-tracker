package ru.murasya.prn.data

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL

private const val MED_COLUMNS =
    "`id`, `name`, `intervalHours`, `windowStartMinute`, `windowEndMinute`, " +
        "`doseMg`, `stockMg`, `color`, `createdAt`"

private const val CREATE_MED =
    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `intervalHours` REAL, " +
        "`windowStartMinute` INTEGER, `windowEndMinute` INTEGER, `doseMg` REAL NOT NULL, " +
        "`stockMg` REAL, `color` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL"

/**
 * Stock used to be a count of doses; it is milligrams now, so the old count is multiplied by the
 * dose it counted. A medication with no dose recorded keeps its bare number rather than collapsing
 * to zero, which would read as "run out" on the very next screen. A null count stays null: never
 * tracked is not the same as nothing left.
 */
private const val SELECT_MED_AS_V4 =
    "`id`, `name`, `intervalHours`, `windowStartMinute`, `windowEndMinute`, `doseMg`, " +
        "`dosesLeft` * (CASE WHEN `doseMg` > 0 THEN `doseMg` ELSE 1 END), `color`, `createdAt`"

private const val INTAKE_COLUMNS = "`id`, `medId`, `takenAt`, `doseMg`"

private const val CREATE_INTAKE =
    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `medId` INTEGER NOT NULL, " +
        "`takenAt` INTEGER NOT NULL, `doseMg` REAL NOT NULL"

/**
 * Rebuilds `med` in its current shape, and does it the long way round on purpose.
 *
 * SQLite cannot drop a column on the versions this app still supports, so the table has to be
 * rebuilt — and `intake` cascades onto `med`, which means dropping `med` while that reference
 * stands performs an implicit delete and takes the entire log with it. So `intake` is rebuilt
 * without its foreign key first, `med` is replaced while nothing points at it, and `intake` gets
 * its key and indices back at the end. That order is safe whether or not foreign keys are enforced.
 *
 * Every version this migration starts from — 1, 2 and 3 — has the old `dosesLeft` column and none
 * of them has `stockMg`, so one body serves them all: the columns that went away are simply never
 * named, whether they are the tolerance pair or the dose count.
 */
private suspend fun rebuildMed(connection: SQLiteConnection) {
    connection.execSQL("CREATE TABLE `_intake_freed` ($CREATE_INTAKE)")
    connection.execSQL("INSERT INTO `_intake_freed` ($INTAKE_COLUMNS) SELECT $INTAKE_COLUMNS FROM `intake`")
    connection.execSQL("DROP TABLE `intake`")

    connection.execSQL("CREATE TABLE `_med_slim` ($CREATE_MED)")
    connection.execSQL("INSERT INTO `_med_slim` ($MED_COLUMNS) SELECT $SELECT_MED_AS_V4 FROM `med`")
    connection.execSQL("DROP TABLE `med`")
    connection.execSQL("ALTER TABLE `_med_slim` RENAME TO `med`")

    connection.execSQL(
        "CREATE TABLE `intake` ($CREATE_INTAKE, FOREIGN KEY(`medId`) REFERENCES `med`(`id`) " +
            "ON UPDATE NO ACTION ON DELETE CASCADE )",
    )
    connection.execSQL(
        "INSERT INTO `intake` ($INTAKE_COLUMNS) SELECT $INTAKE_COLUMNS FROM `_intake_freed` " +
            "WHERE `medId` IN (SELECT `id` FROM `med`)",
    )
    connection.execSQL("DROP TABLE `_intake_freed`")
    connection.execSQL("CREATE INDEX `index_intake_medId` ON `intake` (`medId`)")
    connection.execSQL("CREATE INDEX `index_intake_takenAt` ON `intake` (`takenAt`)")
}

private fun rebuildFrom(version: Int): Migration =
    object : Migration(version, 4) {
        override suspend fun migrate(connection: SQLiteConnection) = rebuildMed(connection)
    }

@Database(entities = [Med::class, Intake::class], version = 4, exportSchema = false)
abstract class PrnDatabase : RoomDatabase() {
    abstract fun dao(): PrnDao

    companion object {
        private const val NAME = "prn.db"

        @Volatile
        private var instance: PrnDatabase? = null

        fun get(context: Context): PrnDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): PrnDatabase =
            Room
                .databaseBuilder<PrnDatabase>(context, NAME)
                .setDriver(AndroidSQLiteDriver())
                .addMigrations(rebuildFrom(1), rebuildFrom(2), rebuildFrom(3))
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
