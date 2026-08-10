package ru.murasya.prn.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.ZoneId
import ru.murasya.prn.R
import ru.murasya.prn.data.Med
import ru.murasya.prn.domain.formatMultiplier
import ru.murasya.prn.domain.formatNumber

/** Lines up the clock column so 09:05 and 14:22 share a left edge. */
private const val TABULAR = "tnum"

/** One day of the log, rendered as a single rounded slab so the list reads as grouped, not ragged. */
@Composable
fun DaySection(
    day: LocalDate,
    today: LocalDate,
    entries: List<LogEntry>,
    zone: ZoneId,
    onTake: (Med) -> Unit,
    onEdit: (LogEntry) -> Unit,
) {
    Column {
        Text(
            text = dayLabel(day, today),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, bottom = 6.dp),
        )
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            DayRows(entries, zone, onTake, onEdit)
        }
    }
}

@Composable
private fun DayRows(entries: List<LogEntry>, zone: ZoneId, onTake: (Med) -> Unit, onEdit: (LogEntry) -> Unit) {
    Column {
        entries.forEachIndexed { index, entry ->
            LogRow(entry, zone, index > 0, onTake, onEdit)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LogRow(
    entry: LogEntry,
    zone: ZoneId,
    divided: Boolean,
    onTake: (Med) -> Unit,
    onEdit: (LogEntry) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val row =
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClickLabel = stringResource(R.string.editor_take),
                onLongClickLabel = stringResource(R.string.editor_edit),
                onClick = { onTake(entry.med) },
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onEdit(entry)
                },
            ).padding(horizontal = 16.dp, vertical = 12.dp)
    Column {
        if (divided) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 54.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
        Row(modifier = row, verticalAlignment = Alignment.CenterVertically) {
            ColorBar(entry.med.colorArgb)
            Spacer(Modifier.width(14.dp))
            EntryText(entry, Modifier.weight(1f))
            Text(
                text = timeLabel(entry.intake.takenAt, zone),
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = TABULAR),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EntryText(entry: LogEntry, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = entry.med.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = entryDetail(entry),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun entryDetail(entry: LogEntry): String {
    val dose = stringResource(R.string.dose_mg, formatNumber(entry.intake.doseMg))
    val tolerance = entry.tolerance ?: return dose
    return "$dose  ·  ${stringResource(R.string.alert_tolerance, formatMultiplier(tolerance))}"
}

/**
 * A bar rather than a dot: down a column of rows the eye follows a vertical stripe far faster, and
 * the log is meant to be skimmed, not read.
 */
@Composable
private fun ColorBar(argb: Int) {
    Box(
        modifier =
            Modifier
                .size(width = 5.dp, height = 32.dp)
                .clip(CircleShape)
                .background(Color(argb)),
    )
}

/** The medication's colour, the one thing that lets the eye group the log at a glance. */
@Composable
fun Swatch(argb: Int, size: Dp = 26.dp) {
    Box(
        modifier =
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(Color(argb)),
    )
}
