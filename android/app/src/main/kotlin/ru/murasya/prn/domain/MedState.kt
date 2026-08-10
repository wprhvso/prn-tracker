package ru.murasya.prn.domain

import java.time.ZoneId
import ru.murasya.prn.data.Intake
import ru.murasya.prn.data.Med

/** Stock at or below this many doses raises the low stock warning. */
const val LOW_STOCK = 3

/**
 * Minutes past the due time at which an overdue medication nags again, then hourly forever.
 *
 * The floor of 15 minutes is not a taste call: `setExactAndAllowWhileIdle` refuses to fire more
 * than once per nine minutes per app, and Android's notification cooldown quietens anything that
 * repeats inside two minutes. A shorter loop would be *less* noticeable, not more.
 */
private val NAG_STEPS_MINUTES = longArrayOf(15, 35, 65, 110)
private const val NAG_TAIL_MINUTES = 60L

/** The next moment an already-overdue medication should nag. Always strictly after [now]. */
fun nextNagAt(dueAt: Long, now: Long): Long {
    val overdue = (now - dueAt) / MINUTE_MS
    val step = NAG_STEPS_MINUTES.firstOrNull { it > overdue }
    if (step != null) return dueAt + step * MINUTE_MS
    val last = NAG_STEPS_MINUTES.last()
    val extra = (overdue - last) / NAG_TAIL_MINUTES + 1
    return dueAt + (last + extra * NAG_TAIL_MINUTES) * MINUTE_MS
}

/** Everything the UI and the notifier need to know about one medication at one instant. */
data class MedState(
    val med: Med,
    val lastTakenAt: Long?,
    val dueAt: Long?,
    val due: Boolean,
    val tolerance: Double?,
    val remindAt: Long?,
)

enum class AlertKind {
    OUT_OF_STOCK,
    DUE,
    LOW_STOCK,
    TOLERANCE,
    ;

    /** Tolerance stays on screen only: pushing it would mean nagging someone who already stopped. */
    val notifies: Boolean get() = this != TOLERANCE
}

data class Alert(
    val kind: AlertKind,
    val state: MedState,
)

fun medStates(meds: List<Med>, intakes: List<Intake>, now: Long, zone: ZoneId): List<MedState> {
    val lastTaken = intakes.groupBy { it.medId }.mapValues { (_, own) -> own.maxOf { it.takenAt } }
    return meds.map { med -> medState(med, lastTaken[med.id], intakes, now, zone) }
}

fun alerts(states: List<MedState>): List<Alert> =
    states
        .flatMap { state -> alertKinds(state).map { kind -> Alert(kind, state) } }
        .sortedWith(compareBy({ it.kind.ordinal }, { it.state.med.name }))

/** The earliest moment any medication needs the app woken up again. */
fun nextWakeAt(states: List<MedState>): Long? = states.mapNotNull { it.remindAt }.minOrNull()

private fun alertKinds(state: MedState): List<AlertKind> =
    buildList {
        if (state.due) add(AlertKind.DUE)
        when {
            state.med.dosesLeft <= 0 -> add(AlertKind.OUT_OF_STOCK)
            state.med.dosesLeft <= LOW_STOCK -> add(AlertKind.LOW_STOCK)
        }
        val tolerance = state.tolerance
        if (tolerance != null && tolerance >= TOLERANCE_WARN) add(AlertKind.TOLERANCE)
    }

private fun medState(med: Med, lastTakenAt: Long?, intakes: List<Intake>, now: Long, zone: ZoneId): MedState {
    val dueAt = nextDueAt(med, lastTakenAt, zone)
    val open = inWindow(now, med.windowStartMinute, med.windowEndMinute, zone)
    return MedState(
        med = med,
        lastTakenAt = lastTakenAt,
        dueAt = dueAt,
        due = dueAt != null && dueAt <= now && open,
        tolerance = toleranceAt(now, med, intakes),
        remindAt = remindAt(med, dueAt, now, zone),
    )
}

private fun remindAt(med: Med, dueAt: Long?, now: Long, zone: ZoneId): Long? {
    if (dueAt == null) return null
    val candidate = if (dueAt > now) dueAt else nextNagAt(dueAt, now)
    return alignToWindow(candidate, med.windowStartMinute, med.windowEndMinute, zone)
}
