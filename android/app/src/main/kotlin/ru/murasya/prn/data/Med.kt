package ru.murasya.prn.data

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "med")
data class Med(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,

    val intervalHours: Double? = null,

    val windowStartMinute: Int? = null,

    val windowEndMinute: Int? = null,

    val doseMg: Double = 0.0,

    val stockMg: Double? = null,

    @ColumnInfo(name = "color")
    val colorArgb: Int,
    val createdAt: Long,
)
