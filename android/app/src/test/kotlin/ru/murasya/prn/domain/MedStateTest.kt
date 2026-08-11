package ru.murasya.prn.domain

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.murasya.prn.data.Intake
import ru.murasya.prn.data.Med

private val ZONE: ZoneId = ZoneId.of("Europe/Moscow")

private fun moment(hour: Int, minute: Int = 0, day: Int = 10): Long =
    ZonedDateTime.of(2026, 8, day, hour, minute, 0, 0, ZONE).toInstant().toEpochMilli()

private fun med(
    id: Long = 1,
    intervalHours: Double? = 6.0,
    dosesLeft: Int? = 10,
    toleranceDays: Double? = null,
    windowStartMinute: Int? = null,
    windowEndMinute: Int? = null,
) = Med(
    id = id,
    name = "Med$id",
    intervalHours = intervalHours,
    windowStartMinute = windowStartMinute,
    windowEndMinute = windowEndMinute,
    doseMg = 100.0,
    dosesLeft = dosesLeft,
    toleranceDays = toleranceDays,
    colorArgb = 0,
    createdAt = moment(0),
)

private fun intake(medId: Long, at: Long, id: Long = at) = Intake(id = id, medId = medId, takenAt = at, doseMg = 100.0)

private fun single(subject: Med, intakes: List<Intake>, now: Long) =
    medStates(listOf(subject), intakes, now, ZONE).first()

class MedStateTest {
    @Test
    fun anIntervalThatHasNotElapsedIsNotDue() {
        val state = single(med(), listOf(intake(1, moment(10))), moment(12))
        assertFalse(state.due)
        assertEquals(moment(16), state.dueAt)
        assertEquals(moment(16), state.remindAt)
    }

    @Test
    fun anElapsedIntervalIsDueAndNagsOnAnEscalatingLoop() {
        val state = single(med(), listOf(intake(1, moment(10))), moment(16, 5))
        assertTrue(state.due)
        assertEquals(moment(16, 15), state.remindAt)
    }

    @Test
    fun nagsSpreadOutAndThenSettleToEveryHour() {
        val due = moment(16)
        assertEquals(due + 15 * MINUTE_MS, nextNagAt(due, due))
        assertEquals(due + 35 * MINUTE_MS, nextNagAt(due, due + 20 * MINUTE_MS))
        assertEquals(due + 65 * MINUTE_MS, nextNagAt(due, due + 35 * MINUTE_MS))
        assertEquals(due + 110 * MINUTE_MS, nextNagAt(due, due + 65 * MINUTE_MS))
        assertEquals(due + 170 * MINUTE_MS, nextNagAt(due, due + 110 * MINUTE_MS))
        assertEquals(due + 230 * MINUTE_MS, nextNagAt(due, due + 175 * MINUTE_MS))
    }

    @Test
    fun everyNagLandsInTheFuture() {
        val due = moment(16)
        (0..600L step 7).forEach { minutes ->
            assertTrue(nextNagAt(due, due + minutes * MINUTE_MS) > due + minutes * MINUTE_MS)
        }
    }

    /** Outside its hours a dose is still allowed — the app says so, the notification waits. */
    @Test
    fun outsideTheWindowTheDoseIsDueOnScreenButSilent() {
        val subject = med(windowStartMinute = 540, windowEndMinute = 1320)
        val state = single(subject, listOf(intake(1, moment(14))), moment(23))
        assertTrue(state.due)
        assertFalse(state.windowOpen)
        assertEquals(moment(9, day = 11), state.remindAt)
        assertEquals(listOf(AlertKind.DUE), alerts(listOf(state)).map { it.kind })
        assertEquals(emptyList<AlertKind>(), notifiableAlerts(listOf(state)).map { it.kind })
    }

    @Test
    fun insideTheWindowTheSameDoseIsNotifiable() {
        val subject = med(windowStartMinute = 540, windowEndMinute = 1320)
        val state = single(subject, listOf(intake(1, moment(14))), moment(21))
        assertTrue(state.windowOpen)
        assertEquals(listOf(AlertKind.DUE), notifiableAlerts(listOf(state)).map { it.kind })
    }

    @Test
    fun aMedicationWithoutAnIntervalNeverWakesTheApp() {
        val state = single(med(intervalHours = null), listOf(intake(1, moment(10))), moment(23))
        assertNull(state.dueAt)
        assertNull(state.remindAt)
        assertFalse(state.due)
        assertNull(nextWakeAt(listOf(state)))
    }

    @Test
    fun stockThresholdsRaiseExactlyOneStockAlert() {
        val now = moment(11)
        val plenty = medStates(listOf(med(id = 1, intervalHours = null, dosesLeft = 4)), emptyList(), now, ZONE)
        val low = medStates(listOf(med(id = 2, intervalHours = null, dosesLeft = 3)), emptyList(), now, ZONE)
        val empty = medStates(listOf(med(id = 3, intervalHours = null, dosesLeft = 0)), emptyList(), now, ZONE)
        val untracked = medStates(listOf(med(id = 4, intervalHours = null, dosesLeft = null)), emptyList(), now, ZONE)
        assertEquals(emptyList<AlertKind>(), alerts(plenty).map { it.kind })
        assertEquals(listOf(AlertKind.LOW_STOCK), alerts(low).map { it.kind })
        assertEquals(listOf(AlertKind.OUT_OF_STOCK), alerts(empty).map { it.kind })
        assertEquals(emptyList<AlertKind>(), alerts(untracked).map { it.kind })
    }

    /** Tolerance is a state, not an event: it must never be able to reach the notification shade. */
    @Test
    fun toleranceIsMeasuredButNeverAlerted() {
        val now = moment(12)
        val subject = med(intervalHours = null, toleranceDays = 14.0)
        val doses = listOf(intake(1, now, id = 1), intake(1, now, id = 2), intake(1, now, id = 3))
        val state = single(subject, doses, now)
        assertEquals(3.0, state.tolerance ?: 0.0, 1e-6)
        assertEquals(emptyList<AlertKind>(), alerts(listOf(state)).map { it.kind })
    }

    @Test
    fun theEarliestReminderWinsTheAlarmSlot() {
        val now = moment(12)
        val soon = med(id = 1, intervalHours = 1.0)
        val later = med(id = 2, intervalHours = 8.0)
        val states = medStates(listOf(soon, later), listOf(intake(1, now), intake(2, now)), now, ZONE)
        assertEquals(now + HOUR_MS, nextWakeAt(states))
    }

    @Test
    fun alertsComeOutInSeverityOrder() {
        val states =
            medStates(
                listOf(med(id = 1, dosesLeft = 0)),
                listOf(intake(1, moment(10))),
                moment(20),
                ZONE,
            )
        assertEquals(listOf(AlertKind.OUT_OF_STOCK, AlertKind.DUE), alerts(states).map { it.kind })
    }
}
