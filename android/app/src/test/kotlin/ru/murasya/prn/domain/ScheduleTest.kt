package ru.murasya.prn.domain

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.murasya.prn.data.Med

private val ZONE: ZoneId = ZoneId.of("Europe/Moscow")

private fun at(hour: Int, minute: Int = 0, day: Int = 10): Long =
    ZonedDateTime.of(2026, 8, day, hour, minute, 0, 0, ZONE).toInstant().toEpochMilli()

private fun med(intervalHours: Double? = null, windowStartMinute: Int? = null, windowEndMinute: Int? = null) =
    Med(
        id = 1,
        name = "Test",
        intervalHours = intervalHours,
        windowStartMinute = windowStartMinute,
        windowEndMinute = windowEndMinute,
        doseMg = 100.0,
        stockMg = 1000.0,
        colorArgb = 0,
        createdAt = at(0),
    )

class ScheduleTest {
    @Test
    fun anAbsentWindowIsAlwaysOpen() {
        assertTrue(inWindow(at(3), null, null, ZONE))
        assertTrue(inWindow(at(3), 540, null, ZONE))
        assertTrue(inWindow(at(3), 540, 540, ZONE))
    }

    @Test
    fun plainWindowIsHalfOpen() {
        assertTrue(inWindow(at(9), 540, 1320, ZONE))
        assertTrue(inWindow(at(21, 59), 540, 1320, ZONE))
        assertFalse(inWindow(at(22), 540, 1320, ZONE))
        assertFalse(inWindow(at(8, 59), 540, 1320, ZONE))
    }

    @Test
    fun windowWrapsPastMidnight() {
        assertTrue(inWindow(at(23), 1320, 360, ZONE))
        assertTrue(inWindow(at(2), 1320, 360, ZONE))
        assertFalse(inWindow(at(12), 1320, 360, ZONE))
    }

    @Test
    fun timeInsideTheWindowIsKept() {
        assertEquals(at(10), alignToWindow(at(10), 540, 1320, ZONE))
    }

    @Test
    fun nightTimeIsDeferredToTheNextOpening() {
        assertEquals(at(9), alignToWindow(at(3), 540, 1320, ZONE))
    }

    @Test
    fun lateEveningIsDeferredToTheNextMorning() {
        assertEquals(at(9, day = 11), alignToWindow(at(23), 540, 1320, ZONE))
    }

    /** Adding minutes to midnight would open a 09:00 window at 10:00 on the day the clocks move. */
    @Test
    fun springForwardDoesNotDragTheWindow() {
        val berlin = ZoneId.of("Europe/Berlin")
        val night = ZonedDateTime.of(2026, 3, 29, 0, 30, 0, 0, berlin).toInstant().toEpochMilli()
        val morning = ZonedDateTime.of(2026, 3, 29, 9, 0, 0, 0, berlin).toInstant().toEpochMilli()
        assertEquals(morning, alignToWindow(night, 540, 1320, berlin))
    }

    @Test
    fun noIntervalMeansNoDueDate() {
        assertNull(nextDueAt(med(), at(10)))
        assertNull(nextDueAt(med(intervalHours = 0.0), at(10)))
    }

    @Test
    fun dueDateIsTheIntervalAfterTheLastDose() {
        assertEquals(at(16), nextDueAt(med(intervalHours = 6.0), at(10)))
    }

    /** Eligibility is a fact about the drug: the allowed hours may delay the reminder, never this. */
    @Test
    fun theAllowedHoursDoNotMoveTheDueDate() {
        val subject = med(intervalHours = 6.0, windowStartMinute = 540, windowEndMinute = 1320)
        assertEquals(at(3, day = 11), nextDueAt(subject, at(21)))
    }

    @Test
    fun neverTakenFallsBackToTheCreationTime() {
        assertEquals(at(6), nextDueAt(med(intervalHours = 6.0), null))
    }

    @Test
    fun fractionalIntervalsWork() {
        assertEquals(at(10, 30), nextDueAt(med(intervalHours = 0.5), at(10)))
    }
}
