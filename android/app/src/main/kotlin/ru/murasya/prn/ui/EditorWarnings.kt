package ru.murasya.prn.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.ZoneId
import ru.murasya.prn.R
import ru.murasya.prn.domain.DAY_MS
import ru.murasya.prn.domain.MedState
import ru.murasya.prn.domain.TOLERANCE_WARN
import ru.murasya.prn.domain.daysToReset
import ru.murasya.prn.domain.formatMinuteOfDay
import ru.murasya.prn.domain.formatMultiplier
import ru.murasya.prn.domain.inWindow
import ru.murasya.prn.text.relativeDuration
import ru.murasya.prn.text.shortDuration

/**
 * The warnings live here rather than in the notification shade on purpose: they only matter at the
 * moment someone is about to take a dose, and pushing them would mean nagging a person who has
 * already stopped.
 */
@Composable
fun EditorWarnings(state: MedState?, draft: MedDraft, mode: EditorMode, now: Long, zone: ZoneId) {
    val warnings =
        listOfNotNull(
            toleranceWarning(state, draft, mode),
            earlyWarning(state, now),
            windowWarning(draft, now, zone),
            stockWarning(draft),
        )
    if (warnings.isEmpty()) return
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        WarningLines(warnings)
    }
}

@Composable
private fun WarningLines(warnings: List<String>) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        warnings.forEach { warning -> WarningLine(warning) }
    }
}

@Composable
private fun WarningLine(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(painterResource(R.drawable.ic_warning), contentDescription = null, modifier = Modifier.size(20.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Shows where this dose *lands*, not where the user already is: at the moment of deciding, the
 * number that matters is the one they are about to create.
 */
@Composable
private fun toleranceWarning(state: MedState?, draft: MedDraft, mode: EditorMode): String? {
    val context = LocalContext.current
    val med = state?.med ?: return null
    val carried = state.tolerance ?: return null
    val adding = if (mode == EditorMode.EDIT) 0.0 else doseWeight(draft, med.doseMg)
    val projected = carried + adding
    if (projected < TOLERANCE_WARN) return null
    val reset = daysToReset(projected, med) ?: return null
    val off = shortDuration(context, (reset * DAY_MS).toLong())
    val shown = formatMultiplier(projected)
    return if (adding > 0.0) {
        stringResource(R.string.warn_tolerance_after, shown, off)
    } else {
        stringResource(R.string.warn_tolerance, shown, off)
    }
}

/** How many reference doses this one is worth, so a double dose warns like two. */
private fun doseWeight(draft: MedDraft, reference: Double): Double {
    val unit = if (reference > 0.0) reference else 1.0
    return (draft.doseMg.toPositiveDouble() ?: reference) / unit
}

@Composable
private fun earlyWarning(state: MedState?, now: Long): String? {
    val context = LocalContext.current
    val dueAt = state?.dueAt ?: return null
    if (dueAt <= now) return null
    return stringResource(R.string.warn_early, relativeDuration(context, now, dueAt))
}

@Composable
private fun windowWarning(draft: MedDraft, now: Long, zone: ZoneId): String? {
    val start = draft.windowStartMinute ?: return null
    val end = draft.windowEndMinute ?: return null
    if (inWindow(now, start, end, zone)) return null
    val range = stringResource(R.string.window_range, formatMinuteOfDay(start), formatMinuteOfDay(end))
    return stringResource(R.string.warn_window, range)
}

@Composable
private fun stockWarning(draft: MedDraft): String? {
    val left = draft.dosesLeft.trim().toIntOrNull() ?: return null
    return if (left <= 0) stringResource(R.string.warn_stock) else null
}
