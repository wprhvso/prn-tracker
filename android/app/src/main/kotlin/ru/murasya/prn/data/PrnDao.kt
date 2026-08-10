package ru.murasya.prn.data

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow

/**
 * The whole data access layer. The data set is tiny — a handful of medications and their intakes —
 * so everything is read wholesale and joined in Kotlin instead of in SQL.
 */
@Dao
interface PrnDao {
    @Query("SELECT * FROM med ORDER BY name COLLATE NOCASE")
    fun medsFlow(): Flow<List<Med>>

    @Query("SELECT * FROM intake ORDER BY takenAt DESC, id DESC")
    fun intakesFlow(): Flow<List<Intake>>

    @Query("SELECT * FROM med")
    suspend fun meds(): List<Med>

    @Query("SELECT * FROM intake ORDER BY takenAt DESC, id DESC")
    suspend fun intakes(): List<Intake>

    @Query("SELECT * FROM med WHERE id = :id")
    suspend fun med(id: Long): Med?

    @Insert
    suspend fun insertMed(med: Med): Long

    @Update
    suspend fun updateMed(med: Med)

    @Delete
    suspend fun deleteMed(med: Med)

    @Insert
    suspend fun insertIntake(intake: Intake): Long

    @Update
    suspend fun updateIntake(intake: Intake)

    @Query("DELETE FROM intake WHERE id = :id")
    suspend fun deleteIntake(id: Long)

    @Query("UPDATE med SET dosesLeft = MAX(dosesLeft - 1, 0) WHERE id = :id")
    suspend fun spendDose(id: Long)

    @Query("UPDATE med SET dosesLeft = dosesLeft + 1 WHERE id = :id")
    suspend fun refundDose(id: Long)
}
