package ru.murasya.prn.domain

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.ln1p
import ru.murasya.prn.data.Intake
import ru.murasya.prn.data.Med

/** From this multiplier upwards the tolerance warning turns loud. */
const val TOLERANCE_WARN = 2.0

/** The load one full build-up is worth — the unit the multiplier counts. */
private const val BASELINE = 1.0

/** Past this age, measured in fade windows, a dose can no longer move the first decimal. */
private const val TOLERANCE_TAIL = 15.0

/** Halvings used to find the moment the load falls back to baseline. Sixty is far past exact. */
private const val RESET_STEPS = 60

/**
 * How much tolerance one medication has piled up, and what it would take to shed it.
 *
 * A dose does not land at full strength: it climbs over [Med.toleranceRiseDays] and fades over
 * [Med.toleranceDays], so its contribution is the difference of two exponentials. Both halves decay
 * on their own, which is why a whole history collapses into the two running sums held here — [slow]
 * and [fast] — rather than a list, and why abstaining always drains the number back down. That last
 * property is what lets tolerance stay off the notification shade: stop taking the drug and the
 * warning leaves by itself, so there is never anything to nag about.
 *
 * The curve is scaled so one dose, at the top of its own climb, reads exactly `x1`. That is what
 * the multiplier counts: `x2` means two full build-ups' worth of the drug is still on board. With
 * no rise time set the climb is instant and the whole thing collapses to the plain exponential
 * decay it has always been, so nothing changes for a medication that never fills the field in.
 */
data class Tolerance(
    private val fade: Double,
    private val rise: Double?,
    private val slow: Double,
    private val fast: Double,
) {
    /** The multiplier as it stands right now. */
    val level: Double get() = levelAfter(0.0)

    /** The highest it still climbs to if nothing more is taken — where the last dose lands. */
    val peak: Double get() = levelAfter(peakDelay())

    /** Days off the drug before the multiplier falls back to `x1`; zero when it is already there. */
    val resetDays: Double get() = resetDelay() / DAY_MS

    /** The same tolerance with [doses] more reference doses swallowed this very moment. */
    fun plus(doses: Double): Tolerance = withDose(0L, doses)

    /** The same tolerance [millis] later, with nothing taken in between. */
    internal fun after(millis: Long): Tolerance {
        val elapsed = millis.coerceAtLeast(0L).toDouble()
        val climb = rise ?: return copy(slow = slow * exp(-elapsed / fade))
        return copy(
            slow = slow * exp(-elapsed / fade),
            fast = fast * exp(-elapsed * fastRate(climb)),
        )
    }

    /** Folds in [doses] taken [age] milliseconds ago. A future-dated dose counts as just swallowed. */
    internal fun withDose(age: Long, doses: Double): Tolerance {
        val elapsed = age.coerceAtLeast(0L).toDouble()
        if (elapsed > TOLERANCE_TAIL * fade) return this
        val climb = rise ?: return copy(slow = slow + doses * exp(-elapsed / fade))
        return copy(
            slow = slow + doses * exp(-elapsed / fade),
            fast = fast + doses * exp(-elapsed * fastRate(climb)),
        )
    }

    /**
     * The scale that puts a single dose's peak at exactly `1.0`.
     *
     * A dose peaks `rise * ln(1 + fade / rise)` after it is taken — always sooner than the fade
     * window, however slow the climb — and the raw curve is worth less than one there, so the whole
     * thing is divided by that height.
     */
    private val gain: Double
        get() {
            val climb = rise ?: return 1.0
            return (1.0 + climb / fade) * exp(peakAge(climb) / fade)
        }

    private fun peakAge(climb: Double): Double = climb * ln1p(fade / climb)

    private fun fastRate(climb: Double): Double = 1.0 / fade + 1.0 / climb

    private fun levelAfter(millis: Double): Double {
        val climb = rise ?: return slow * exp(-millis / fade)
        return gain * (slow * exp(-millis / fade) - fast * exp(-millis * fastRate(climb)))
    }

    /** How long until the load stops climbing. Zero once every dose on board is past its own peak. */
    private fun peakDelay(): Double {
        val climb = rise ?: return 0.0
        if (slow <= 0.0 || fast <= 0.0) return 0.0
        return (climb * ln((1.0 + fade / climb) * fast / slow)).coerceAtLeast(0.0)
    }

    /**
     * Milliseconds off the drug before the load falls back to baseline.
     *
     * Without a rise the load only ever falls, so the answer is one logarithm. With one it may still
     * be climbing, and can pass baseline twice — once on the way up — so the search starts at the
     * top of the climb, past which a sum of two decaying exponentials is strictly falling again and
     * the last crossing is the only one left. The bracket closes because the load never exceeds its
     * own slow half, which reaches baseline at `fade * ln(gain * slow)`.
     */
    private fun resetDelay(): Double {
        if (slow <= 0.0) return 0.0
        if (rise == null) return if (slow <= BASELINE) 0.0 else fade * ln(slow / BASELINE)
        var low = peakDelay()
        if (levelAfter(low) <= BASELINE) return 0.0
        var high = (fade * ln(gain * slow / BASELINE)).coerceAtLeast(low)
        repeat(RESET_STEPS) {
            val mid = (low + high) / 2.0
            if (levelAfter(mid) > BASELINE) low = mid else high = mid
        }
        return high
    }
}

/** How much tolerance has piled up by [at], or null when this medication does not track any. */
fun toleranceAt(at: Long, med: Med, intakes: List<Intake>): Tolerance? {
    val empty = emptyTolerance(med) ?: return null
    val unit = doseUnit(med)
    return intakes
        .filter { it.medId == med.id }
        .fold(empty) { carried, intake -> carried.withDose(at - intake.takenAt, intake.doseMg / unit) }
}

/**
 * The multiplier each dose landed the user at, keyed by intake id, so the log can show how deep the
 * hole already was. Every contribution decays on its own, so walking the doses in order is one pass.
 */
fun toleranceHistory(meds: List<Med>, intakes: List<Intake>): Map<Long, Double> {
    val byMed = intakes.groupBy { it.medId }
    return meds.flatMap { med -> medToleranceHistory(med, byMed[med.id].orEmpty()) }.toMap()
}

private fun medToleranceHistory(med: Med, own: List<Intake>): List<Pair<Long, Double>> {
    var carried = emptyTolerance(med) ?: return emptyList()
    val unit = doseUnit(med)
    var previous: Long? = null
    return own.sortedBy { it.takenAt }.map { intake ->
        carried = carried.after(intake.takenAt - (previous ?: intake.takenAt)).plus(intake.doseMg / unit)
        previous = intake.takenAt
        intake.id to carried.peak
    }
}

private fun emptyTolerance(med: Med): Tolerance? {
    val fade = med.toleranceDays?.takeIf { it > 0.0 } ?: return null
    val rise = med.toleranceRiseDays?.takeIf { it > 0.0 }
    return Tolerance(fade * DAY_MS, rise?.times(DAY_MS), 0.0, 0.0)
}

private fun doseUnit(med: Med): Double = if (med.doseMg > 0.0) med.doseMg else 1.0
