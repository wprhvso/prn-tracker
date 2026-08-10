package ru.murasya.prn.domain

import kotlin.math.exp
import kotlin.math.ln
import ru.murasya.prn.data.Intake
import ru.murasya.prn.data.Med

/** From this multiplier upwards the tolerance warning turns loud. */
const val TOLERANCE_WARN = 2.0

/** Past this age, measured in tolerance windows, a dose can no longer move the first decimal. */
private const val TOLERANCE_TAIL = 15.0

/**
 * How much tolerance has piled up by [at], counted in doses per tolerance window.
 *
 * A dose contributes `1.0` the moment it is swallowed and decays exponentially with a time constant
 * of [Med.toleranceDays]; a dose twice the usual size counts double. The exponential is the only
 * smooth curve where a single fresh dose reads exactly `x1` *and* a steady habit of N doses per
 * window averages exactly `xN` — a linear ramp, for instance, quietly halves the number, so a
 * once-a-day user of a fortnight-tolerance drug would see `x7` where the honest answer is `x14`.
 *
 * Because every contribution decays on its own, abstaining drains the number back to baseline,
 * which is why tolerance never has to nag: stop taking the drug and the warning leaves by itself.
 */
fun toleranceAt(at: Long, med: Med, intakes: List<Intake>): Double? {
    val window = toleranceWindow(med) ?: return null
    val unit = doseUnit(med)
    return intakes
        .filter { it.medId == med.id }
        .sumOf { intake ->
            val age = (at - intake.takenAt) / window
            if (age < 0.0 || age > TOLERANCE_TAIL) 0.0 else intake.doseMg / unit * exp(-age)
        }
}

/** Days off the drug that would bring [load] back to baseline — the promise the warning makes. */
fun daysToReset(load: Double, med: Med): Double? {
    val days = med.toleranceDays?.takeIf { it > 0.0 } ?: return null
    return if (load <= 1.0) 0.0 else days * ln(load)
}

/**
 * The multiplier as it stood at the moment of every intake, keyed by intake id, so the log can show
 * how deep the hole already was each time. Exponential decay is memoryless, so this is one pass.
 */
fun toleranceHistory(meds: List<Med>, intakes: List<Intake>): Map<Long, Double> {
    val byMed = intakes.groupBy { it.medId }
    return meds.flatMap { med -> medToleranceHistory(med, byMed[med.id].orEmpty()) }.toMap()
}

private fun medToleranceHistory(med: Med, own: List<Intake>): List<Pair<Long, Double>> {
    val window = toleranceWindow(med) ?: return emptyList()
    val unit = doseUnit(med)
    var load = 0.0
    var previous: Long? = null
    return own.sortedBy { it.takenAt }.map { intake ->
        previous?.let { load *= exp(-(intake.takenAt - it) / window) }
        load += intake.doseMg / unit
        previous = intake.takenAt
        intake.id to load
    }
}

private fun toleranceWindow(med: Med): Double? {
    val days = med.toleranceDays ?: return null
    return if (days > 0.0) days * DAY_MS else null
}

private fun doseUnit(med: Med): Double = if (med.doseMg > 0.0) med.doseMg else 1.0
