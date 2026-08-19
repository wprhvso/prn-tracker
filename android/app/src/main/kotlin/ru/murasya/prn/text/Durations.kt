package ru.murasya.prn.text

import android.content.Context
import ru.murasya.prn.R
import ru.murasya.prn.domain.durationParts

fun shortDuration(context: Context, millis: Long): String {
    val (days, hours, minutes) = durationParts(millis)
    return when {
        days > 0 -> context.getString(R.string.dur_days_hours, days, hours)
        hours > 0 -> context.getString(R.string.dur_hours_minutes, hours, minutes)
        minutes > 0 -> context.getString(R.string.dur_minutes, minutes)
        else -> context.getString(R.string.dur_now)
    }
}

fun relativeDuration(context: Context, from: Long, to: Long): String {
    val delta = to - from
    val text = shortDuration(context, delta)
    return when {
        delta > 0 -> context.getString(R.string.time_in, text)
        delta < 0 -> context.getString(R.string.time_ago, text)
        else -> text
    }
}
