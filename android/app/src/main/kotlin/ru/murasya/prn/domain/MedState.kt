package ru.murasya.prn.domain

import java.time.ZoneId
import ru.murasya.prn.data.Intake
import ru.murasya.prn.data.Med

const val LOW_STOCK_DOSES = 3

private val NAG_STEPS_MINUTES = longArrayOf(15, 35, 65, 110)
private const val NAG_TAIL_MINUTES = 60L

fun nextNagAt(dueAt: Long, now: Long): Long {
    val overdue = (now - dueAt) / MINUTE_MS
    val step = NAG_STEPS_MINUTES.firstOrNull { it > overdue }
    if (step != null) return dueAt + step * MINUTE_MS
    val last = NAG_STEPS_MINUTES.last()
    val extra = (overdue - last) / NAG_TAIL_MINUTES + 1
    return dueAt + (last + extra * NAG_TAIL_MINUTES) * MINUTE_MS
}

data class MedState(
    val med: Med,
    val lastTakenAt: Long?,
    val dueAt: Long?,
    val due: Boolean,
    val windowOpen: Boolean,
    val remindAt: Long?,
)

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

fun medStates(meds: List<Med>, intakes: List<Intake>, now: Long, zone: ZoneId): List<MedState> {
    val lastTaken = intakes.groupBy { it.medId }.mapValues { (_, own) -> own.maxOf { it.takenAt } }
    return meds
        .map { med -> medState(med, lastTaken[med.id], now, zone) }
        .sortedWith(compareBy<MedState>({ urgencyOf(it) }, { waitOf(it) }, { it.med.name.lowercase() }))
}

fun alerts(states: List<MedState>): List<Alert> =
    states
        .flatMap { state -> alertKinds(state).map { kind -> Alert(kind, state) } }
        .sortedBy { it.kind.ordinal }

fun notifiableAlerts(states: List<MedState>): List<Alert> =
    alerts(states).filter { it.kind != AlertKind.DUE || it.state.windowOpen }

fun nextWakeAt(states: List<MedState>): Long? = states.mapNotNull { it.remindAt }.minOrNull()

private fun urgencyOf(state: MedState): Urgency =
    when {
        state.due && state.windowOpen -> Urgency.READY
        state.due -> Urgency.READY_LATER
        state.dueAt != null -> Urgency.SCHEDULED
        else -> Urgency.FREE
    }

private fun waitOf(state: MedState): Long = state.dueAt ?: -(state.lastTakenAt ?: 0L)

private fun alertKinds(state: MedState): List<AlertKind> =
    buildList {
        if (state.due) add(AlertKind.DUE)
        addAll(stockKinds(state.med))
    }

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
