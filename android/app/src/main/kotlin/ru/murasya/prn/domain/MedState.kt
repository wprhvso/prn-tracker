package ru.murasya.prn.domain

import java.time.ZoneId
import ru.murasya.prn.data.Intake
import ru.murasya.prn.data.Med

/** Stock worth fewer than this many planned doses raises the low stock warning. */
const val LOW_STOCK_DOSES = 3

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
    val remindAt: Long?,
)

/**
 * How a medication earns its place at the front of the screen.
 *
 * Nothing here is about the drug's importance — only about how soon it needs a decision. A dose
 * that can be taken this second outranks one that becomes allowed in a minute, which outranks a
 * drug with no schedule at all: that last group can never be late, so however recently it was
 * taken, it is history rather than news.
 */
private enum class Urgency { READY, READY_LATER, SCHEDULED, FREE }

enum class AlertKind {
    OUT_OF_STOCK,
    DUE,
    LOW_STOCK,
}

data class Alert(
    val kind: AlertKind,
    val state: MedState,
)

/** Every medication's standing, most urgent first — the order everything downstream inherits. */
fun medStates(meds: List<Med>, intakes: List<Intake>, now: Long, zone: ZoneId): List<MedState> {
    val lastTaken = intakes.groupBy { it.medId }.mapValues { (_, own) -> own.maxOf { it.takenAt } }
    return meds
        .map { med -> medState(med, lastTaken[med.id], now, zone) }
        .sortedWith(compareBy<MedState>({ urgencyOf(it) }, { waitOf(it) }, { it.med.name.lowercase() }))
}

/** Alerts keep the states' own order inside each kind, because [sortedBy] is a stable sort. */
fun alerts(states: List<MedState>): List<Alert> =
    states
        .flatMap { state -> alertKinds(state).map { kind -> Alert(kind, state) } }
        .sortedBy { it.kind.ordinal }

/**
 * The subset worth waking someone for. A dose that is allowed but outside its hours stays on
 * screen and off the notification shade.
 */
fun notifiableAlerts(states: List<MedState>): List<Alert> =
    alerts(states).filter { it.kind != AlertKind.DUE || it.state.windowOpen }

/** The earliest moment any medication needs the app woken up again. */
fun nextWakeAt(states: List<MedState>): Long? = states.mapNotNull { it.remindAt }.minOrNull()

private fun urgencyOf(state: MedState): Urgency =
    when {
        state.due && state.windowOpen -> Urgency.READY
        state.due -> Urgency.READY_LATER
        state.dueAt != null -> Urgency.SCHEDULED
        else -> Urgency.FREE
    }

/**
 * The tie-break inside one band, always "smaller is more urgent". Anything on a schedule sorts by
 * the moment it comes up, so the longest overdue leads the ready ones and the soonest leads the
 * waiting ones. Anything without a schedule sorts by its last dose, most recent first, which is
 * the only sense in which one of them can be more current than another.
 */
private fun waitOf(state: MedState): Long = state.dueAt ?: -(state.lastTakenAt ?: 0L)

private fun alertKinds(state: MedState): List<AlertKind> =
    buildList {
        if (state.due) add(AlertKind.DUE)
        addAll(stockKinds(state.med))
    }

/**
 * Stock is milligrams now, so "three left" means three planned doses' worth. A medication with no
 * planned dose has no way to say what three doses would be, so it only ever reports running out.
 */
private fun stockKinds(med: Med): List<AlertKind> {
    val stock = med.stockMg ?: return emptyList()
    if (stock <= 0.0) return listOf(AlertKind.OUT_OF_STOCK)
    val low = med.doseMg > 0.0 && stock < LOW_STOCK_DOSES * med.doseMg
    return if (low) listOf(AlertKind.LOW_STOCK) else emptyList()
}

private fun medState(med: Med, lastTakenAt: Long?, now: Long, zone: ZoneId): MedState {
    val dueAt = nextDueAt(med, lastTakenAt)
    return MedState(
        med = med,
        lastTakenAt = lastTakenAt,
        dueAt = dueAt,
        due = dueAt != null && dueAt <= now,
        windowOpen = inWindow(now, med.windowStartMinute, med.windowEndMinute, zone),
        remindAt = remindAt(med, dueAt, now, zone),
    )
}

private fun remindAt(med: Med, dueAt: Long?, now: Long, zone: ZoneId): Long? {
    if (dueAt == null) return null
    val candidate = if (dueAt > now) dueAt else nextNagAt(dueAt, now)
    return alignToWindow(candidate, med.windowStartMinute, med.windowEndMinute, zone)
}
