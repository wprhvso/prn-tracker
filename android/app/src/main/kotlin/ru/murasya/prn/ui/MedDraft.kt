package ru.murasya.prn.ui

import ru.murasya.prn.data.Intake
import ru.murasya.prn.data.Med
import ru.murasya.prn.domain.formatNumber

/** What the editor is doing, which decides the title and which buttons are on offer. */
enum class EditorMode { CREATE, TAKE, EDIT }

/**
 * The editor form as the user sees it: numbers stay strings while they are being typed, so a
 * half-written "1." never snaps back or gets rejected mid-keystroke.
 *
 * [doseMg] and [planMg] answer different questions. The plan belongs to the medication and is what
 * a dose is meant to be; [doseMg] belongs to this one intake and starts out as the plan, so taking
 * a half or a double is a matter of typing over one number rather than rewriting the medication.
 *
 * The window is a pair of plain minutes rather than an optional pair, because two equal times
 * already say "any hour" — which is what a fresh medication gets, and what let the switch go.
 */
data class MedDraft(
    val medId: Long = 0,
    val intakeId: Long = 0,
    val name: String = "",
    val doseMg: String = "",
    val planMg: String = "",
    val stockMg: String = "",
    val intervalHours: String = "",
    val windowStartMinute: Int = 0,
    val windowEndMinute: Int = 0,
    val colorArgb: Int = 0,
    val takenAt: Long = 0,
    val createdAt: Long = 0,
) {
    val valid: Boolean get() = name.isNotBlank()

    /** What this intake is actually worth, falling back to the plan when the field is left blank. */
    val takenMg: Double get() = doseMg.toPositiveDouble() ?: planMg.toPositiveDouble() ?: 0.0
}

fun draftOf(med: Med, intake: Intake?, now: Long): MedDraft =
    MedDraft(
        medId = med.id,
        intakeId = intake?.id ?: 0,
        name = med.name,
        doseMg = numberField(intake?.doseMg ?: med.doseMg),
        planMg = numberField(med.doseMg),
        stockMg = med.stockMg?.let(::formatNumber).orEmpty(),
        intervalHours = med.intervalHours?.let(::formatNumber).orEmpty(),
        windowStartMinute = med.windowStartMinute ?: 0,
        windowEndMinute = med.windowEndMinute ?: 0,
        colorArgb = med.colorArgb,
        takenAt = intake?.takenAt ?: now,
        createdAt = med.createdAt,
    )

fun MedDraft.toMed(): Med =
    Med(
        id = medId,
        name = name.trim(),
        intervalHours = intervalHours.toPositiveDouble(),
        windowStartMinute = windowStartMinute.takeIf { it != windowEndMinute },
        windowEndMinute = windowEndMinute.takeIf { it != windowStartMinute },
        doseMg = planMg.toPositiveDouble() ?: 0.0,
        stockMg = stockMg.toStock(),
        colorArgb = colorArgb,
        createdAt = if (createdAt > 0) createdAt else takenAt,
    )

/** Accepts both decimal separators, because half the world types a comma. */
fun String.toPositiveDouble(): Double? = trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }

/** Blank means "not counting stock"; zero is a real answer and has to survive as one. */
fun String.toStock(): Double? = trim().replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.0)

private fun numberField(value: Double): String = if (value > 0.0) formatNumber(value) else ""
