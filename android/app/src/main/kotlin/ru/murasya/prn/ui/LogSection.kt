package ru.murasya.prn.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import ru.murasya.prn.domain.formatNumber

private const val TABULAR = "tnum"

private const val BAR_WIDTH = 5
private const val BAR_HEIGHT = 32
private const val BAR_STRETCH = 1.22f

@Composable
fun DaySection(
    day: LocalDate,
    today: LocalDate,
    entries: List<LogEntry>,
    zone: ZoneId,
    onTake: (Med) -> Unit,
    onEdit: (LogEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
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
    Column(modifier = Modifier.animateContentSize(sizing())) {
        entries.forEachIndexed { index, entry ->
            key(entry.intake.id) { LogRow(entry, zone, index > 0, onTake, onEdit) }
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
    val interactions = remember { MutableInteractionSource() }
    val row =
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = interactions,
                indication = LocalIndication.current,
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
            ColorBar(entry.med.colorArgb, interactions)
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
private fun entryDetail(entry: LogEntry): String = stringResource(R.string.dose_mg, formatNumber(entry.intake.doseMg))

@Composable
private fun ColorBar(argb: Int, source: InteractionSource) {
    val pressed by source.collectIsPressedAsState()
    val color by animateColorAsState(Color(argb), animationSpec = effects(), label = "barColor")
    val stretch by
        animateFloatAsState(
            targetValue = if (pressed) BAR_STRETCH else 1f,
            animationSpec = spatial(),
            label = "barStretch",
        )
    Box(
        modifier =
            Modifier
                .size(width = BAR_WIDTH.dp, height = BAR_HEIGHT.dp)
                .graphicsLayer { scaleY = stretch }
                .clip(CircleShape)
                .background(color),
    )
}

@Composable
fun Swatch(argb: Int, size: Dp = 26.dp) {
    val color by animateColorAsState(Color(argb), animationSpec = effects(), label = "swatch")
    Box(
        modifier =
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(color),
    )
}
