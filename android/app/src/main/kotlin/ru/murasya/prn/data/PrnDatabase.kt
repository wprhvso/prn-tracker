package ru.murasya.prn.data

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL

@Database(entities = [Med::class, Intake::class], version = 2, exportSchema = false)
abstract class PrnDatabase : RoomDatabase() {
    abstract fun dao(): PrnDao

    companion object {
        private const val NAME = "prn.db"

        /**
         * Tolerance grew a second half: how fast it builds, not only how fast it fades. Written out
         * by hand rather than left to the destructive fallback, because by now there is a log worth
         * keeping. Null is the honest default — an existing medication never said how fast it rises,
         * and null is exactly what makes the tolerance behave as it always did.
         */
        private val ADD_TOLERANCE_RISE =
            object : Migration(1, 2) {
                override suspend fun migrate(connection: SQLiteConnection) {
                    connection.execSQL("ALTER TABLE med ADD COLUMN toleranceRiseDays REAL")
                }
            }

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
                .addMigrations(ADD_TOLERANCE_RISE)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
