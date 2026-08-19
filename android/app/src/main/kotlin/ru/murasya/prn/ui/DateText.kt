package ru.murasya.prn.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import ru.murasya.prn.R
import ru.murasya.prn.domain.minuteOfDay

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

fun localDate(at: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()

fun timeLabel(at: Long, zone: ZoneId): String = TIME_FORMAT.format(Instant.ofEpochMilli(at).atZone(zone))

fun minuteOfDayOf(at: Long, zone: ZoneId): Int = minuteOfDay(at, zone)

fun withTimeOfDay(at: Long, minuteOfDay: Int, zone: ZoneId, notAfter: Long): Long {
    val midnight =
        Instant
            .ofEpochMilli(at)
            .atZone(zone)
            .toLocalDate()
            .atStartOfDay(zone)
    val candidate = midnight.plusMinutes(minuteOfDay.toLong())
    val shifted = if (candidate.toInstant().toEpochMilli() > notAfter) candidate.minusDays(1) else candidate
    return shifted.toInstant().toEpochMilli()
}

@Composable
fun dayLabel(day: LocalDate, today: LocalDate): String {
    val absolute = remember(day) { DATE_FORMAT.format(day) }
    return when (day) {
        today -> stringResource(R.string.day_today)
        today.minusDays(1) -> stringResource(R.string.day_yesterday)
        else -> absolute
    }
}
