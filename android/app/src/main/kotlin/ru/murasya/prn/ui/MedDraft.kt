package ru.murasya.prn.ui

import ru.murasya.prn.data.Intake
import ru.murasya.prn.data.Med
import ru.murasya.prn.domain.formatNumber

/** What the editor is doing, which decides the title and which buttons are on offer. */
enum class EditorMode { CREATE, TAKE, EDIT }

/**
 * The editor form as the user sees it: numbers stay strings while they are being typed, so a
 * half-written "1." never snaps back or gets rejected mid-keystroke.
 */
data class MedDraft(
    val medId: Long = 0,
    val intakeId: Long = 0,
    val name: String = "",
    val doseMg: String = "",
    val dosesLeft: String = "",
    val intervalHours: String = "",
    val toleranceDays: String = "",
    val windowStartMinute: Int? = null,
    val windowEndMinute: Int? = null,
    val colorArgb: Int = 0,
    val takenAt: Long = 0,
    val createdAt: Long = 0,
) {
    val valid: Boolean get() = name.isNotBlank()
}

fun draftOf(med: Med, intake: Intake?, now: Long): MedDraft =
    MedDraft(
        medId = med.id,
        intakeId = intake?.id ?: 0,
        name = med.name,
        doseMg = numberField(intake?.doseMg ?: med.doseMg),
        dosesLeft = med.dosesLeft.toString(),
        intervalHours = med.intervalHours?.let(::formatNumber).orEmpty(),
        toleranceDays = med.toleranceDays?.let(::formatNumber).orEmpty(),
        windowStartMinute = med.windowStartMinute,
        windowEndMinute = med.windowEndMinute,
        colorArgb = med.colorArgb,
        takenAt = intake?.takenAt ?: now,
        createdAt = med.createdAt,
    )

fun MedDraft.toMed(): Med =
    Med(
        id = medId,
        name = name.trim(),
        intervalHours = intervalHours.toPositiveDouble(),
        windowStartMinute = windowStartMinute,
        windowEndMinute = windowEndMinute,
        doseMg = doseMg.toPositiveDouble() ?: 0.0,
        dosesLeft = dosesLeft.trim().toIntOrNull()?.coerceAtLeast(0) ?: 0,
        toleranceDays = toleranceDays.toPositiveDouble(),
        colorArgb = colorArgb,
        createdAt = if (createdAt > 0) createdAt else takenAt,
    )

/** Accepts both decimal separators, because half the world types a comma. */
fun String.toPositiveDouble(): Double? = trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }

private fun numberField(value: Double): String = if (value > 0.0) formatNumber(value) else ""
