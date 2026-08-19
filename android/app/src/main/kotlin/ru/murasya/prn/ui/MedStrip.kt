package ru.murasya.prn.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.murasya.prn.R
import ru.murasya.prn.data.Med
import ru.murasya.prn.domain.MedState
import ru.murasya.prn.domain.formatNumber
import ru.murasya.prn.text.relativeDuration

private const val DUE_TINT = 0.22f
private const val CARD_WIDTH = 190

@Composable
fun MedStrip(
    states: List<MedState>,
    now: Long,
    from: Int,
    onTake: (Med) -> Unit,
    onEdit: (Med) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
    ) {
        itemsIndexed(states, key = { _, state -> state.med.id }) { index, state ->
            MedCard(state, now, onTake, onEdit, itemMotion(from + index))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MedCard(
    state: MedState,
    now: Long,
    onTake: (Med) -> Unit,
    onEdit: (Med) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val interactions = remember { MutableInteractionSource() }
    val accent = Color(state.med.colorArgb)
    val target = if (state.due) accent.copy(alpha = DUE_TINT) else MaterialTheme.colorScheme.surfaceContainer
    val container by animateColorAsState(target, animationSpec = effects(), label = "medCard")
    val card =
        modifier
            .width(CARD_WIDTH.dp)
            .pressSquish(interactions)
            .combinedClickable(
                interactionSource = interactions,
                indication = LocalIndication.current,
                onClickLabel = stringResource(R.string.editor_take),
                onLongClickLabel = stringResource(R.string.editor_edit),
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
    val idle = MaterialTheme.colorScheme.onSurfaceVariant
    val status by animateColorAsState(if (state.due) accent else idle, animationSpec = effects(), label = "medStatus")
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
        TickerText(text = statusText(state, now), style = MaterialTheme.typography.labelLarge, color = status)
        Text(
            text = metaText(state),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
        )
    }
}

@Composable
private fun statusText(state: MedState, now: Long): String {
    val context = LocalContext.current
    val dueAt = state.dueAt
    val last = state.lastTakenAt
    return when {
        dueAt == null && last != null -> relativeDuration(context, now, last)
        dueAt == null -> stringResource(R.string.status_free)
        state.due -> stringResource(R.string.status_ready)
        else -> relativeDuration(context, now, dueAt)
    }
}

@Composable
private fun metaText(state: MedState): String {
    val dose = stringResource(R.string.dose_mg, formatNumber(state.med.doseMg))
    val stock = state.med.stockMg ?: return dose
    return "$dose  ·  ${stringResource(R.string.stock_short, formatNumber(stock))}"
}
