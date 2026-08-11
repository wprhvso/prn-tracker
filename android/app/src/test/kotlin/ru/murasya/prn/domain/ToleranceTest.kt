package ru.murasya.prn.domain

import kotlin.math.exp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.murasya.prn.data.Intake
import ru.murasya.prn.data.Med

private const val DELTA = 1e-6
private const val NOW = 1_800_000_000_000L
private const val WINDOW_DAYS = 14.0

private fun med(toleranceDays: Double? = WINDOW_DAYS, doseMg: Double = 100.0) =
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
    fun contributionDecaysExponentially() {
        val whole = toleranceAt(NOW, med(), listOf(intake(14 * DAY_MS))) ?: 0.0
        val half = toleranceAt(NOW, med(), listOf(intake(7 * DAY_MS))) ?: 0.0
        assertEquals(exp(-1.0), whole, DELTA)
        assertEquals(exp(-0.5), half, DELTA)
    }

    @Test
    fun oldDosesFadeTowardsBaselineWithoutASuddenCliff() {
        val month = toleranceAt(NOW, med(), listOf(intake(30 * DAY_MS))) ?: 0.0
        assertTrue(month > 0.0)
        assertTrue(month < 0.15)
        assertEquals(0.0, toleranceAt(NOW, med(), listOf(intake(400 * DAY_MS))) ?: -1.0, DELTA)
    }

    @Test
    fun threeFreshDosesReadAsThreeFold() {
        val doses = listOf(intake(0, id = 1), intake(MINUTE_MS, id = 2), intake(2 * MINUTE_MS, id = 3))
        assertEquals(3.0, toleranceAt(NOW, med(), doses) ?: 0.0, 1e-3)
    }

    /** The property the whole formula exists for: dose N times per window and the number reads N. */
    @Test
    fun steadyDosingReadsTheFrequencyRatio() {
        val gap = (WINDOW_DAYS * DAY_MS / 4).toLong()
        val doses = (0 until 200).map { i -> intake(i * gap, id = i + 1L) }
        val peak = toleranceAt(NOW, med(), doses) ?: 0.0
        assertEquals(1.0 / (1.0 - exp(-0.25)), peak, 1e-4)
        val overOneCycle = (0 until 100).map { step -> toleranceAt(NOW + step * gap / 100, med(), doses) ?: 0.0 }
        assertEquals(4.0, overOneCycle.average(), 0.05)
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

    @Test
    fun daysToResetRoundTrips() {
        val doses = listOf(intake(0, id = 1), intake(MINUTE_MS, id = 2), intake(2 * MINUTE_MS, id = 3))
        val load = toleranceAt(NOW, med(), doses) ?: 0.0
        val days = daysToReset(load, med()) ?: 0.0
        val after = toleranceAt(NOW + (days * DAY_MS).toLong(), med(), doses) ?: 0.0
        assertEquals(1.0, after, 1e-3)
    }

    @Test
    fun baselineNeedsNoBreak() {
        assertEquals(0.0, daysToReset(0.4, med()) ?: -1.0, DELTA)
        assertEquals(0.0, daysToReset(1.0, med()) ?: -1.0, DELTA)
        assertNull(daysToReset(3.0, med(toleranceDays = null)))
    }
}
