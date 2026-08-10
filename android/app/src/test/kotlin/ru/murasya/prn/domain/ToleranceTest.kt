package ru.murasya.prn.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.murasya.prn.data.Intake
import ru.murasya.prn.data.Med

private const val DELTA = 1e-6
private const val NOW = 1_800_000_000_000L

private fun med(toleranceDays: Double? = 14.0, doseMg: Double = 100.0) =
    Med(
        id = 1,
        name = "Test",
        doseMg = doseMg,
        dosesLeft = 10,
        toleranceDays = toleranceDays,
        colorArgb = 0,
        createdAt = 0,
    )

private fun intake(agoMs: Long, doseMg: Double = 100.0, id: Long = 1) =
    Intake(id = id, medId = 1, takenAt = NOW - agoMs, doseMg = doseMg)

class ToleranceTest {
    @Test
    fun noToleranceDaysMeansNoMultiplier() {
        assertNull(toleranceAt(NOW, med(toleranceDays = null), listOf(intake(0))))
        assertNull(toleranceAt(NOW, med(toleranceDays = 0.0), listOf(intake(0))))
    }

    @Test
    fun freshDoseCountsAsOne() {
        assertEquals(1.0, toleranceAt(NOW, med(), listOf(intake(0))) ?: 0.0, DELTA)
    }

    @Test
    fun contributionDecaysLinearly() {
        val half = 7 * DAY_MS
        assertEquals(0.5, toleranceAt(NOW, med(), listOf(intake(half))) ?: 0.0, DELTA)
    }

    @Test
    fun doseOlderThanTheWindowIsForgotten() {
        assertEquals(0.0, toleranceAt(NOW, med(), listOf(intake(14 * DAY_MS))) ?: 0.0, DELTA)
        assertEquals(0.0, toleranceAt(NOW, med(), listOf(intake(30 * DAY_MS))) ?: 0.0, DELTA)
    }

    @Test
    fun threeFreshDosesReadAsThreeFold() {
        val doses = listOf(intake(0, id = 1), intake(MINUTE_MS, id = 2), intake(2 * MINUTE_MS, id = 3))
        assertEquals(3.0, toleranceAt(NOW, med(), doses) ?: 0.0, 1e-3)
    }

    @Test
    fun doubleDoseWeighsDouble() {
        assertEquals(2.0, toleranceAt(NOW, med(), listOf(intake(0, doseMg = 200.0))) ?: 0.0, DELTA)
    }

    @Test
    fun otherMedicationsAreIgnored() {
        val alien = Intake(id = 9, medId = 2, takenAt = NOW, doseMg = 100.0)
        assertEquals(0.0, toleranceAt(NOW, med(), listOf(alien)) ?: 0.0, DELTA)
    }

    @Test
    fun historyMatchesTheDirectSumAtEveryIntake() {
        val doses = listOf(intake(0, id = 1), intake(7 * DAY_MS, id = 2), intake(20 * DAY_MS, id = 3))
        val history = toleranceHistory(listOf(med()), doses)
        assertEquals(3, history.size)
        doses.forEach { dose ->
            val direct = toleranceAt(dose.takenAt, med(), doses) ?: 0.0
            assertEquals(direct, history.getValue(dose.id), DELTA)
        }
    }

    @Test
    fun historySkipsMedicationsWithoutTolerance() {
        assertEquals(emptyMap<Long, Double>(), toleranceHistory(listOf(med(null)), listOf(intake(0))))
    }
}
