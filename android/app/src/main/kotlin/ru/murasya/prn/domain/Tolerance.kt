package ru.murasya.prn.domain

import ru.murasya.prn.data.Intake
import ru.murasya.prn.data.Med

/** From this multiplier upwards the tolerance warning turns loud. */
const val TOLERANCE_WARN = 2.0

/**
 * How much tolerance has piled up by [at], counted in "doses worth".
 *
 * One dose contributes `1.0` the moment it is swallowed and fades linearly to zero over
 * [Med.toleranceDays]; a dose twice the usual size counts double. The sum of those contributions is
 * the multiplier shown as `x2.4` — at `x1` exactly one fresh dose is on board, at `x3` three of
 * them, which is what "exceeded threefold" means. Because every contribution decays on its own,
 * abstaining drains the number back to zero and the warning disappears without anyone nagging.
 */
fun toleranceAt(at: Long, med: Med, intakes: List<Intake>): Double? {
    val window = toleranceWindow(med) ?: return null
    val unit = doseUnit(med)
    return intakes
        .filter { it.medId == med.id }
        .sumOf { intake ->
            val age = at - intake.takenAt
            if (age < 0L || age >= window) 0.0 else intake.doseMg / unit * (1.0 - age / window)
        }
}

/**
 * The multiplier as it stood at the moment of every intake, keyed by intake id, so the log can show
 * how deep the hole already was each time. Only intakes inside the decay window matter, which keeps
 * this linear in practice however long the log grows.
 */
fun toleranceHistory(meds: List<Med>, intakes: List<Intake>): Map<Long, Double> {
    val byMed = intakes.groupBy { it.medId }
    return meds.flatMap { med -> medToleranceHistory(med, byMed[med.id].orEmpty()) }.toMap()
}

private fun medToleranceHistory(med: Med, own: List<Intake>): List<Pair<Long, Double>> {
    val window = toleranceWindow(med) ?: return emptyList()
    val unit = doseUnit(med)
    val ordered = own.sortedBy { it.takenAt }
    var oldest = 0
    return ordered.mapIndexed { index, intake ->
        while (intake.takenAt - ordered[oldest].takenAt >= window) oldest++
        val carried =
            (oldest..index).sumOf { past ->
                val age = intake.takenAt - ordered[past].takenAt
                ordered[past].doseMg / unit * (1.0 - age / window)
            }
        intake.id to carried
    }
}

private fun toleranceWindow(med: Med): Double? {
    val days = med.toleranceDays ?: return null
    return if (days > 0.0) days * DAY_MS else null
}

private fun doseUnit(med: Med): Double = if (med.doseMg > 0.0) med.doseMg else 1.0
