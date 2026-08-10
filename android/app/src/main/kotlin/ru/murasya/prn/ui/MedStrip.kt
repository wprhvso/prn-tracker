package ru.murasya.prn.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.murasya.prn.R
import ru.murasya.prn.data.Med
import ru.murasya.prn.domain.MedState
import ru.murasya.prn.domain.formatMultiplier
import ru.murasya.prn.domain.formatNumber
import ru.murasya.prn.text.relativeDuration

private const val DUE_TINT = 0.22f

/** A compact always-on read of every medication: when the next dose is allowed, how deep the
 * tolerance is, how much is left. Tapping one is the fastest way to log another dose. */
@Composable
fun MedStrip(states: List<MedState>, now: Long, onTake: (Med) -> Unit, onEdit: (Med) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
    ) {
        items(states, key = { it.med.id }) { state -> MedCard(state, now, onTake, onEdit) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MedCard(state: MedState, now: Long, onTake: (Med) -> Unit, onEdit: (Med) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val accent = Color(state.med.colorArgb)
    val target = if (state.due) accent.copy(alpha = DUE_TINT) else MaterialTheme.colorScheme.surfaceContainer
    val container by animateColorAsState(target, animationSpec = calmEffects(), label = "medCard")
    val card =
        Modifier
            .width(190.dp)
            .combinedClickable(
                onClick = { onTake(state.med) },
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onEdit(state.med)
                },
            )
    Surface(shape = MaterialTheme.shapes.large, color = container, modifier = card) {
        MedCardBody(state, now, accent)
    }
}

@Composable
private fun MedCardBody(state: MedState, now: Long, accent: Color) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Swatch(state.med.colorArgb, 14.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = state.med.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = statusText(state, now),
            style = MaterialTheme.typography.labelLarge,
            color = if (state.due) accent else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = metaText(state),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
        )
    }
}

@Composable
private fun statusText(state: MedState, now: Long): String {
    val context = LocalContext.current
    val dueAt = state.dueAt ?: return stringResource(R.string.status_free)
    if (state.due) return stringResource(R.string.status_ready)
    return stringResource(R.string.status_next, relativeDuration(context, now, dueAt))
}

@Composable
private fun metaText(state: MedState): String {
    val parts = mutableListOf(stringResource(R.string.dose_mg, formatNumber(state.med.doseMg)))
    state.tolerance?.let { parts += stringResource(R.string.alert_tolerance, formatMultiplier(it)) }
    parts += pluralStringResource(R.plurals.doses_left, state.med.dosesLeft, state.med.dosesLeft)
    return parts.joinToString("  ·  ")
}
