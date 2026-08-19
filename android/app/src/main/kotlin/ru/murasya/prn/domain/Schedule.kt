package ru.murasya.prn.domain

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import ru.murasya.prn.data.Med

const val MINUTE_MS = 60_000L
const val HOUR_MS = 3_600_000L
const val DAY_MS = 86_400_000L
const val MINUTES_PER_HOUR = 60
const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR

fun inWindow(at: Long, startMinute: Int?, endMinute: Int?, zone: ZoneId): Boolean {
    if (startMinute == null || endMinute == null || startMinute == endMinute) return true
    val minute = minuteOfDay(at, zone)
    return if (startMinute < endMinute) {
        minute in startMinute until endMinute
    } else {
        minute >= startMinute || minute < endMinute
    }
}

fun alignToWindow(at: Long, startMinute: Int?, endMinute: Int?, zone: ZoneId): Long {
    if (inWindow(at, startMinute, endMinute, zone)) return at
    val start = startMinute ?: return at
    return nextTimeOfDay(at, start, zone)
}

fun minuteOfDay(at: Long, zone: ZoneId): Int {
    val local = Instant.ofEpochMilli(at).atZone(zone)
    return local.hour * MINUTES_PER_HOUR + local.minute
}

fun nextDueAt(med: Med, lastTakenAt: Long?): Long? {
    val hours = med.intervalHours ?: return null
    if (hours <= 0.0) return null
    val from = lastTakenAt ?: med.createdAt
    return from + (hours * HOUR_MS).toLong()
}

private fun nextTimeOfDay(at: Long, minute: Int, zone: ZoneId): Long {
    val date = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
    val time = LocalTime.of(minute / MINUTES_PER_HOUR, minute % MINUTES_PER_HOUR)
    val today = date.atTime(time).atZone(zone)
    val next = if (today.toInstant().toEpochMilli() > at) today else date.plusDays(1).atTime(time).atZone(zone)
    return next.toInstant().toEpochMilli()
}
