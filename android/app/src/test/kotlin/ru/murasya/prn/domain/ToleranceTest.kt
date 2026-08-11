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
private const val FADE_DAYS = 14.0
private const val RISE_DAYS = 1.0

private fun med(toleranceDays: Double? = FADE_DAYS, toleranceRiseDays: Double? = null, doseMg: Double = 100.0) =
    Med(
        id = 1,
        name = "Test",
        doseMg = doseMg,
        dosesLeft = 10,
        toleranceDays = toleranceDays,
        toleranceRiseDays = toleranceRiseDays,
        colorArgb = 0,
        createdAt = 0,
    )

private fun intake(agoMs: Long, doseMg: Double = 100.0, id: Long = 1) =
    Intake(id = id, medId = 1, takenAt = NOW - agoMs, doseMg = doseMg)

private fun levelAt(at: Long, subject: Med, doses: List<Intake>): Double = toleranceAt(at, subject, doses)?.level ?: 0.0

class ToleranceTest {
    @Test
    fun noToleranceDaysMeansNoMultiplier() {
        assertNull(toleranceAt(NOW, med(toleranceDays = null), listOf(intake(0))))
        assertNull(toleranceAt(NOW, med(toleranceDays = 0.0), listOf(intake(0))))
    }

    @Test
    fun freshDoseCountsAsOne() {
        assertEquals(1.0, levelAt(NOW, med(), listOf(intake(0))), DELTA)
    }

    @Test
    fun contributionDecaysExponentially() {
        assertEquals(exp(-1.0), levelAt(NOW, med(), listOf(intake(14 * DAY_MS))), DELTA)
        assertEquals(exp(-0.5), levelAt(NOW, med(), listOf(intake(7 * DAY_MS))), DELTA)
    }

    @Test
    fun oldDosesFadeTowardsBaselineWithoutASuddenCliff() {
        val month = levelAt(NOW, med(), listOf(intake(30 * DAY_MS)))
        assertTrue(month > 0.0)
        assertTrue(month < 0.15)
        assertEquals(0.0, levelAt(NOW, med(), listOf(intake(400 * DAY_MS))), DELTA)
    }

    @Test
    fun threeFreshDosesReadAsThreeFold() {
        val doses = listOf(intake(0, id = 1), intake(MINUTE_MS, id = 2), intake(2 * MINUTE_MS, id = 3))
        assertEquals(3.0, levelAt(NOW, med(), doses), 1e-3)
    }

    /** The property the whole formula exists for: dose N times per window and the number reads N. */
    @Test
    fun steadyDosingReadsTheFrequencyRatio() {
        val gap = (FADE_DAYS * DAY_MS / 4).toLong()
        val doses = (0 until 200).map { i -> intake(i * gap, id = i + 1L) }
        assertEquals(1.0 / (1.0 - exp(-0.25)), levelAt(NOW, med(), doses), 1e-4)
        val overOneCycle = (0 until 100).map { step -> levelAt(NOW + step * gap / 100, med(), doses) }
        assertEquals(4.0, overOneCycle.average(), 0.05)
    }

    @Test
    fun doubleDoseWeighsDouble() {
        assertEquals(2.0, levelAt(NOW, med(), listOf(intake(0, doseMg = 200.0))), DELTA)
    }

    @Test
    fun otherMedicationsAreIgnored() {
        val alien = Intake(id = 9, medId = 2, takenAt = NOW, doseMg = 100.0)
        assertEquals(0.0, levelAt(NOW, med(), listOf(alien)), DELTA)
    }

    /**
     * The invariant the whole scaling exists for, and the one the multiplier's meaning rests on:
     * one dose, at the top of its own climb, is worth exactly one — however slowly it climbs.
     */
    @Test
    fun oneDoseAlwaysPeaksAtExactlyOne() {
        listOf(0.1, 1.0, 3.0, 14.0, 40.0).forEach { rise ->
            val subject = med(toleranceRiseDays = rise)
            val dose = listOf(intake(0))
            val highest = (0..4000).maxOf { hours -> levelAt(NOW + hours * HOUR_MS, subject, dose) }
            assertEquals("rise of $rise days", 1.0, highest, 1e-3)
        }
    }

    @Test
    fun aRisingDoseStartsAtNothingAndPeaksLater() {
        val tolerance = requireNotNull(toleranceAt(NOW, med(toleranceRiseDays = RISE_DAYS), listOf(intake(0))))
        assertEquals(0.0, tolerance.level, DELTA)
        assertEquals(1.0, tolerance.peak, DELTA)
    }

    /** No rise time means the old curve exactly, so an existing medication never shifts under anyone. */
    @Test
    fun withoutARiseTimeTheCurveIsUnchanged() {
        val doses = listOf(intake(0, id = 1), intake(3 * DAY_MS, id = 2))
        val tolerance = requireNotNull(toleranceAt(NOW, med(), doses))
        assertEquals(1.0 + exp(-3.0 / FADE_DAYS), tolerance.level, DELTA)
        assertEquals(tolerance.level, tolerance.peak, DELTA)
    }

    @Test
    fun historyMatchesTheDirectSumAtEveryIntake() {
        val doses = listOf(intake(0, id = 1), intake(7 * DAY_MS, id = 2), intake(20 * DAY_MS, id = 3))
        val history = toleranceHistory(listOf(med()), doses)
        assertEquals(3, history.size)
        doses.forEach { dose ->
            val sofar = doses.filter { it.takenAt <= dose.takenAt }
            assertEquals(levelAt(dose.takenAt, med(), sofar), history.getValue(dose.id), DELTA)
        }
    }

    /** With a rise the log shows where each dose landed, which is a little after the dose itself. */
    @Test
    fun historyRecordsWhereEachDoseLanded() {
        val subject = med(toleranceRiseDays = RISE_DAYS)
        val doses = listOf(intake(0, id = 1), intake(7 * DAY_MS, id = 2))
        val history = toleranceHistory(listOf(subject), doses)
        assertEquals(2, history.size)
        doses.forEach { dose ->
            val sofar = doses.filter { it.takenAt <= dose.takenAt }
            assertEquals(
                requireNotNull(toleranceAt(dose.takenAt, subject, sofar)).peak,
                history.getValue(dose.id),
                DELTA,
            )
        }
    }

    @Test
    fun historySkipsMedicationsWithoutTolerance() {
        assertEquals(emptyMap<Long, Double>(), toleranceHistory(listOf(med(null)), listOf(intake(0))))
    }

    @Test
    fun daysToResetRoundTrips() {
        val doses = listOf(intake(0, id = 1), intake(MINUTE_MS, id = 2), intake(2 * MINUTE_MS, id = 3))
        val days = requireNotNull(toleranceAt(NOW, med(), doses)).resetDays
        assertEquals(1.0, levelAt(NOW + (days * DAY_MS).toLong(), med(), doses), 1e-3)
    }

    /**
     * With a rise the load can still be climbing, so it may cross baseline twice. The answer has to
     * be the last crossing — the moment it is over for good, not the moment on the way up.
     */
    @Test
    fun resetOutlastsALoadThatIsStillClimbing() {
        val subject = med(toleranceRiseDays = RISE_DAYS)
        val doses = (0 until 30).map { i -> intake(i * DAY_MS, id = i + 1L) }
        val days = requireNotNull(toleranceAt(NOW, subject, doses)).resetDays
        assertTrue(days > 0.0)
        assertEquals(1.0, levelAt(NOW + (days * DAY_MS).toLong(), subject, doses), 1e-3)
        assertTrue(levelAt(NOW + ((days + 1.0) * DAY_MS).toLong(), subject, doses) < 1.0)
    }

    @Test
    fun baselineNeedsNoBreak() {
        assertEquals(0.0, requireNotNull(toleranceAt(NOW, med(), listOf(intake(30 * DAY_MS)))).resetDays, DELTA)
        assertEquals(0.0, requireNotNull(toleranceAt(NOW, med(), emptyList())).resetDays, DELTA)
    }

    /** What the editor asks before a dose is swallowed: not where you are, but where this puts you. */
    @Test
    fun oneMoreDoseIsJudgedByWhereItLands() {
        val subject = med(toleranceRiseDays = RISE_DAYS)
        val carried = requireNotNull(toleranceAt(NOW, subject, listOf(intake(30 * DAY_MS))))
        val projected = carried.plus(1.0)
        assertEquals(carried.level, projected.level, DELTA)
        assertTrue(projected.peak > carried.level + 0.9)
    }

    /** A dose dated into the future is a typo, not a time machine: it counts as taken right now. */
    @Test
    fun futureDosesCountAsFresh() {
        assertEquals(1.0, levelAt(NOW, med(), listOf(intake(-3 * DAY_MS))), DELTA)
        assertEquals(0.0, levelAt(NOW, med(toleranceRiseDays = RISE_DAYS), listOf(intake(-3 * DAY_MS))), DELTA)
    }
}
