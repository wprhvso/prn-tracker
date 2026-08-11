package ru.murasya.prn.domain

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class FormatTest {
    @Before
    fun pinTheLocale() {
        Locale.setDefault(Locale.US)
    }

    @Test
    fun wholeNumbersLoseTheirDecimals() {
        assertEquals("500", formatNumber(500.0))
        assertEquals("0", formatNumber(0.0))
        assertEquals("2", formatNumber(2.004))
    }

    @Test
    fun fractionsSurviveInEnglishLocale() {
        assertEquals("2.5", formatNumber(2.5))
        assertEquals("0.25", formatNumber(0.25))
    }

    @Test
    fun minutesOfDayReadAsAClock() {
        assertEquals("00:00", formatMinuteOfDay(0))
        assertEquals("09:30", formatMinuteOfDay(570))
        assertEquals("23:59", formatMinuteOfDay(1439))
    }

    @Test
    fun durationsSplitIntoWholeUnits() {
        assertEquals(DurationParts(0, 0, 0), durationParts(0))
        assertEquals(DurationParts(0, 0, 45), durationParts(45 * MINUTE_MS))
        assertEquals(DurationParts(0, 2, 15), durationParts(2 * HOUR_MS + 15 * MINUTE_MS))
        assertEquals(DurationParts(3, 4, 5), durationParts(3 * DAY_MS + 4 * HOUR_MS + 5 * MINUTE_MS))
    }

    @Test
    fun durationsIgnoreDirection() {
        assertEquals(durationParts(90 * MINUTE_MS), durationParts(-90 * MINUTE_MS))
    }
}
