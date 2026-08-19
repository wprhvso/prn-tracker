package ru.murasya.prn.data

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

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

    val doseMg: Double,
)
