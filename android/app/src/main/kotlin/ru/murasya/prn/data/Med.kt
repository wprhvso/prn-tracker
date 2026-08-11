package ru.murasya.prn.data

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

/**
 * A medication the user tracks.
 *
 * Every optional field switches off the feature that depends on it: no [intervalHours] means no
 * "time for the next dose" reminder, no window means the reminder may fire at any hour, no
 * [stockMg] means stock is not being counted.
 */
@Entity(tableName = "med")
data class Med(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** Display name, also the notification title. */
    val name: String,
    /** How often the drug may be taken, in hours. Drives the "next dose" reminder. */
    val intervalHours: Double? = null,
    /** Start of the allowed time-of-day window, minutes since local midnight. */
    val windowStartMinute: Int? = null,
    /** End of the allowed window, minutes since local midnight. May be smaller than the start. */
    val windowEndMinute: Int? = null,
    /** The planned dose in milligrams — what a dose is meant to be when nothing says otherwise. */
    val doseMg: Double = 0.0,
    /** Milligrams left in stock, or null when stock is not being tracked. */
    val stockMg: Double? = null,
    /** Row accent in the log, packed ARGB. */
    @ColumnInfo(name = "color")
    val colorArgb: Int,
    val createdAt: Long,
)
