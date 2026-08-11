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

/**
 * Everything the UI and the notifier need to know about one medication at one instant.
 *
 * Two clocks live here on purpose. [dueAt] is a fact about the drug — the moment the next dose
 * becomes allowed — and is never bent by the allowed hours. [remindAt] is a fact about the user's
 * day: the first moment we are permitted to speak. A dose that becomes allowed at 03:00 reads as
 * ready the second you open the app, and still waits until the window opens before it makes noise.
 */
data class MedState(
    val med: Med,
    val lastTakenAt: Long?,
    val dueAt: Long?,
    val due: Boolean,
    val windowOpen: Boolean,
    val tolerance: Double?,
    val remindAt: Long?,
)

enum class AlertKind {
    OUT_OF_STOCK,
    DUE,
    LOW_STOCK,
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

/**
 * The subset worth waking someone for. A dose that is allowed but outside its hours stays on
 * screen and off the notification shade; tolerance never gets here at all, because it is a state
 * rather than an event and pushing it would mean nagging somebody who already stopped.
 */
fun notifiableAlerts(states: List<MedState>): List<Alert> =
    alerts(states).filter { it.kind != AlertKind.DUE || it.state.windowOpen }

/** The earliest moment any medication needs the app woken up again. */
fun nextWakeAt(states: List<MedState>): Long? = states.mapNotNull { it.remindAt }.minOrNull()

private fun alertKinds(state: MedState): List<AlertKind> =
    buildList {
        if (state.due) add(AlertKind.DUE)
        when (state.med.dosesLeft) {
            null -> Unit
            0 -> add(AlertKind.OUT_OF_STOCK)
            in 1..LOW_STOCK -> add(AlertKind.LOW_STOCK)
            else -> Unit
        }
    }

private fun medState(med: Med, lastTakenAt: Long?, intakes: List<Intake>, now: Long, zone: ZoneId): MedState {
    val dueAt = nextDueAt(med, lastTakenAt)
    return MedState(
        med = med,
        lastTakenAt = lastTakenAt,
        dueAt = dueAt,
        due = dueAt != null && dueAt <= now,
        windowOpen = inWindow(now, med.windowStartMinute, med.windowEndMinute, zone),
        tolerance = toleranceAt(now, med, intakes),
        remindAt = remindAt(med, dueAt, now, zone),
    )
}

private fun remindAt(med: Med, dueAt: Long?, now: Long, zone: ZoneId): Long? {
    if (dueAt == null) return null
    val candidate = if (dueAt > now) dueAt else nextNagAt(dueAt, now)
    return alignToWindow(candidate, med.windowStartMinute, med.windowEndMinute, zone)
}
