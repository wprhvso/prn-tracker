package ru.murasya.prn.domain

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

private const val CENTS = 100.0

/** Splits a duration into whole days, hours and minutes; the sign is dropped. */
data class DurationParts(
    val days: Long,
    val hours: Long,
    val minutes: Long,
)

fun durationParts(millis: Long): DurationParts {
    val total = abs(millis) / MINUTE_MS
    return DurationParts(
        days = total / (MINUTES_PER_DAY),
        hours = total % MINUTES_PER_DAY / MINUTES_PER_HOUR,
        minutes = total % MINUTES_PER_HOUR,
    )
}

/** Trims a trailing `.0` so 500 mg reads as "500" while 2.5 mg keeps its half. */
fun formatNumber(value: Double): String {
    val rounded = (value * CENTS).roundToLong() / CENTS
    if (rounded == rounded.toLong().toDouble()) return rounded.toLong().toString()
    val text = String.format(Locale.getDefault(), "%.2f", rounded)
    return if (text.endsWith('0')) text.dropLast(1) else text
}

/** Minutes since midnight as `09:30`. */
fun formatMinuteOfDay(minute: Int): String =
    String.format(
        Locale.getDefault(),
        "%02d:%02d",
        minute / MINUTES_PER_HOUR,
        minute % MINUTES_PER_HOUR,
    )
