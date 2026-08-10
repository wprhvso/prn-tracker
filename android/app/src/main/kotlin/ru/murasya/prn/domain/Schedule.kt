package ru.murasya.prn.domain

import java.time.Instant
import java.time.ZoneId
import ru.murasya.prn.data.Med

const val MINUTE_MS = 60_000L
const val HOUR_MS = 3_600_000L
const val DAY_MS = 86_400_000L
const val MINUTES_PER_HOUR = 60
const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR

/**
 * True when [at] falls inside the medication's local time-of-day window. A window is a half-open
 * interval `[start, end)` of minutes since midnight; a start past the end wraps over midnight, and
 * a missing or degenerate window is always open.
 */
fun inWindow(at: Long, startMinute: Int?, endMinute: Int?, zone: ZoneId): Boolean {
    if (startMinute == null || endMinute == null || startMinute == endMinute) return true
    val minute = minuteOfDay(at, zone)
    return if (startMinute < endMinute) {
        minute in startMinute until endMinute
    } else {
        minute >= startMinute || minute < endMinute
    }
}

/** [at] itself when the window is open then, otherwise the next moment it opens. */
fun alignToWindow(at: Long, startMinute: Int?, endMinute: Int?, zone: ZoneId): Long {
    if (inWindow(at, startMinute, endMinute, zone)) return at
    val start = startMinute ?: return at
    return nextTimeOfDay(at, start, zone)
}

fun minuteOfDay(at: Long, zone: ZoneId): Int {
    val local = Instant.ofEpochMilli(at).atZone(zone)
    return local.hour * MINUTES_PER_HOUR + local.minute
}

/** When the next dose becomes allowed, or null when the medication has no interval configured. */
fun nextDueAt(med: Med, lastTakenAt: Long?, zone: ZoneId): Long? {
    val hours = med.intervalHours ?: return null
    if (hours <= 0.0) return null
    val from = lastTakenAt ?: med.createdAt
    val due = from + (hours * HOUR_MS).toLong()
    return alignToWindow(due, med.windowStartMinute, med.windowEndMinute, zone)
}

private fun nextTimeOfDay(at: Long, minute: Int, zone: ZoneId): Long {
    val midnight =
        Instant
            .ofEpochMilli(at)
            .atZone(zone)
            .toLocalDate()
            .atStartOfDay(zone)
    val today = midnight.plusMinutes(minute.toLong())
    val next = if (today.toInstant().toEpochMilli() > at) today else today.plusDays(1)
    return next.toInstant().toEpochMilli()
}
