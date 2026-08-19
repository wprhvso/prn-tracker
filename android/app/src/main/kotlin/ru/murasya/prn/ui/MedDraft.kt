package ru.murasya.prn.ui

import ru.murasya.prn.data.Intake
import ru.murasya.prn.data.Med
import ru.murasya.prn.domain.formatNumber

enum class EditorMode { CREATE, TAKE, EDIT }

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

fun String.toPositiveDouble(): Double? = trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }

fun String.toStock(): Double? = trim().replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.0)

private fun numberField(value: Double): String = if (value > 0.0) formatNumber(value) else ""
