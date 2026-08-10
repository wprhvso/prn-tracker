package ru.murasya.prn.data

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.AndroidSQLiteDriver

@Database(entities = [Med::class, Intake::class], version = 1, exportSchema = false)
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
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
