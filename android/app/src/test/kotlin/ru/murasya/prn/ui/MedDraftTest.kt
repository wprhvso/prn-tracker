package ru.murasya.prn.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.murasya.prn.data.Intake
import ru.murasya.prn.data.Med

private const val NOW = 1_800_000_000_000L

private fun med(
    doseMg: Double = 100.0,
    stockMg: Double? = 4200.0,
    windowStartMinute: Int? = null,
    windowEndMinute: Int? = null,
) = Med(
    id = 1,
    name = "Test",
    intervalHours = 6.0,
    windowStartMinute = windowStartMinute,
    windowEndMinute = windowEndMinute,
    doseMg = doseMg,
    stockMg = stockMg,
    colorArgb = 0,
    createdAt = NOW,
)

class MedDraftTest {

    @Test
    fun equalWindowTimesMeanNoWindow() {
        val saved = draftOf(med(), null, NOW).copy(name = "Test").toMed()
        assertNull(saved.windowStartMinute)
        assertNull(saved.windowEndMinute)
    }

    @Test
    fun aMedicationWithoutAWindowOpensTheFormAtMidnight() {
        val draft = draftOf(med(), null, NOW)
        assertEquals(0, draft.windowStartMinute)
        assertEquals(0, draft.windowEndMinute)
    }

    @Test
    fun aRealWindowSurvivesTheRoundTrip() {
        val draft = draftOf(med(windowStartMinute = 540, windowEndMinute = 1320), null, NOW)
        assertEquals(540, draft.windowStartMinute)
        assertEquals(1320, draft.windowEndMinute)
        val saved = draft.toMed()
        assertEquals(540, saved.windowStartMinute)
        assertEquals(1320, saved.windowEndMinute)
    }

    @Test
    fun thePlanIsWhatTheMedicationKeeps() {
        val draft = draftOf(med(), null, NOW).copy(doseMg = "150")
        assertEquals(100.0, draft.toMed().doseMg, 1e-9)
        assertEquals(150.0, draft.takenMg, 1e-9)
    }

    @Test
    fun aBlankDoseFallsBackToThePlan() {
        assertEquals(100.0, draftOf(med(), null, NOW).copy(doseMg = "").takenMg, 1e-9)
    }

    @Test
    fun anIntakeOpensAtItsOwnDoseNotThePlan() {
        val intake = Intake(id = 7, medId = 1, takenAt = NOW, doseMg = 50.0)
        val draft = draftOf(med(), intake, NOW)
        assertEquals("50", draft.doseMg)
        assertEquals("100", draft.planMg)
        assertEquals(50.0, draft.takenMg, 1e-9)
    }

    @Test
    fun blankStockIsUntrackedAndZeroIsEmpty() {
        assertNull(draftOf(med(stockMg = null), null, NOW).toMed().stockMg)
        assertEquals(0.0, draftOf(med(), null, NOW).copy(stockMg = "0").toMed().stockMg)
        assertEquals(4200.0, draftOf(med(), null, NOW).toMed().stockMg)
    }

    @Test
    fun commasAreAcceptedAsDecimalSeparators() {
        assertEquals(2.5, draftOf(med(), null, NOW).copy(planMg = "2,5").toMed().doseMg, 1e-9)
        assertEquals(12.5, draftOf(med(), null, NOW).copy(stockMg = "12,5").toMed().stockMg)
    }
}
