package ru.murasya.prn.data

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/** A single logged intake. The main screen is a reverse-chronological list of these. */
@Entity(
    tableName = "intake",
    foreignKeys = [
        ForeignKey(
            entity = Med::class,
            parentColumns = ["id"],
            childColumns = ["medId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("medId"), Index("takenAt")],
)
data class Intake(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val medId: Long,
    val takenAt: Long,
    /** Dose actually taken, in milligrams. Kept per intake so history stays honest after edits. */
    val doseMg: Double,
)
