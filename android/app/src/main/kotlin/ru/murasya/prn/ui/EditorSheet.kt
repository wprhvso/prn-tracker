package ru.murasya.prn.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.ZoneId
import ru.murasya.prn.R
import ru.murasya.prn.domain.MedState

private const val DEFAULT_WINDOW_START = 9 * 60
private const val DEFAULT_WINDOW_END = 22 * 60

private enum class TimeTarget { NONE, TAKEN, WINDOW_START, WINDOW_END }

/**
 * The one place anything is created or changed. It opens blank from the plus button, pre-filled
 * from a tap, and pre-filled with the intake itself from a long press.
 *
 * The form is laid out to fit without scrolling: paired fields share a row, the hint under every
 * field is gone now that the labels say the same thing, and the colour sliders stay folded away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorSheet(
    editor: EditorState,
    medState: MedState?,
    now: Long,
    zone: ZoneId,
    onDraftChange: (MedDraft) -> Unit,
    onCommit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        EditorForm(editor, medState, now, zone, onDraftChange, onCommit, onDelete)
    }
}

@Composable
private fun EditorForm(
    editor: EditorState,
    medState: MedState?,
    now: Long,
    zone: ZoneId,
    onDraftChange: (MedDraft) -> Unit,
    onCommit: () -> Unit,
    onDelete: () -> Unit,
) {
    val draft = editor.draft
    var picking by remember { mutableStateOf(TimeTarget.NONE) }
    var confirmingDelete by remember { mutableStateOf(false) }
    Column(
        modifier =
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 20.dp)
                .navigationBarsPadding()
                .imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = titleOf(editor.mode), style = MaterialTheme.typography.titleLarge)
        EditorWarnings(medState, draft, editor.mode, now, zone)
        EditorFields(draft, zone, onDraftChange) { picking = it }
        EditorButtons(editor.mode, draft.valid, onCommit) { confirmingDelete = true }
    }
    TimePickers(draft, zone, now, picking, onDraftChange) { picking = TimeTarget.NONE }
    if (confirmingDelete) {
        DeleteDialog(draft, onConfirm = onDelete, onDismiss = { confirmingDelete = false })
    }
}

@Composable
private fun EditorFields(draft: MedDraft, zone: ZoneId, onChange: (MedDraft) -> Unit, onPick: (TimeTarget) -> Unit) {
    PlainField(
        label = stringResource(R.string.field_name),
        value = draft.name,
        onValueChange = { onChange(draft.copy(name = it)) },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NumberField(
            label = stringResource(R.string.field_dose),
            value = draft.doseMg,
            onValueChange = { onChange(draft.copy(doseMg = it)) },
            modifier = Modifier.weight(1f),
        )
        NumberField(
            label = stringResource(R.string.field_stock),
            value = draft.dosesLeft,
            onValueChange = { onChange(draft.copy(dosesLeft = it)) },
            modifier = Modifier.weight(1f),
            decimal = false,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NumberField(
            label = stringResource(R.string.field_interval),
            value = draft.intervalHours,
            onValueChange = { onChange(draft.copy(intervalHours = it)) },
            modifier = Modifier.weight(1f),
        )
        NumberField(
            label = stringResource(R.string.field_tolerance),
            value = draft.toleranceDays,
            onValueChange = { onChange(draft.copy(toleranceDays = it)) },
            modifier = Modifier.weight(1f),
        )
    }
    TimeAndWindowRow(draft, zone, onChange, onPick)
    if (draft.windowStartMinute != null && draft.windowEndMinute != null) WindowButtons(draft, onPick)
    ColorPicker(selected = draft.colorArgb, onSelect = { onChange(draft.copy(colorArgb = it)) })
}

/** When the dose was taken, and whether reminders keep to a window — one row carries both. */
@Composable
private fun TimeAndWindowRow(
    draft: MedDraft,
    zone: ZoneId,
    onChange: (MedDraft) -> Unit,
    onPick: (TimeTarget) -> Unit,
) {
    val windowed = draft.windowStartMinute != null && draft.windowEndMinute != null
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TimeButton(
            label = stringResource(R.string.field_taken_at),
            minuteOfDay = minuteOfDayOf(draft.takenAt, zone),
            onClick = { onPick(TimeTarget.TAKEN) },
        )
        Spacer(Modifier.weight(1f))
        Text(text = stringResource(R.string.field_window), style = MaterialTheme.typography.labelLarge)
        Switch(checked = windowed, onCheckedChange = { onChange(draft.withWindow(it)) })
    }
}

@Composable
private fun WindowButtons(draft: MedDraft, onPick: (TimeTarget) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        TimeButton(
            label = stringResource(R.string.window_from),
            minuteOfDay = draft.windowStartMinute ?: DEFAULT_WINDOW_START,
            onClick = { onPick(TimeTarget.WINDOW_START) },
            modifier = Modifier.weight(1f),
        )
        TimeButton(
            label = stringResource(R.string.window_to),
            minuteOfDay = draft.windowEndMinute ?: DEFAULT_WINDOW_END,
            onClick = { onPick(TimeTarget.WINDOW_END) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun EditorButtons(mode: EditorMode, valid: Boolean, onCommit: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (mode == EditorMode.EDIT) {
            TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete)) }
        }
        Button(onClick = onCommit, enabled = valid, modifier = Modifier.weight(1f)) {
            Text(stringResource(commitLabelOf(mode)))
        }
    }
}

@Composable
private fun TimePickers(
    draft: MedDraft,
    zone: ZoneId,
    now: Long,
    target: TimeTarget,
    onChange: (MedDraft) -> Unit,
    onClose: () -> Unit,
) {
    if (target == TimeTarget.NONE) return
    val initial =
        when (target) {
            TimeTarget.TAKEN -> minuteOfDayOf(draft.takenAt, zone)
            TimeTarget.WINDOW_START -> draft.windowStartMinute ?: DEFAULT_WINDOW_START
            else -> draft.windowEndMinute ?: DEFAULT_WINDOW_END
        }
    PrnTimePickerDialog(
        title = stringResource(labelOf(target)),
        initialMinuteOfDay = initial,
        onDismiss = onClose,
        onConfirm = { minute ->
            onChange(draft.withPickedTime(target, minute, zone, now))
            onClose()
        },
    )
}

/**
 * Long-pressing a log row offers to delete that one entry; long-pressing a medication card offers
 * to delete the medication. The gesture already said which one the user meant.
 */
@Composable
private fun DeleteDialog(draft: MedDraft, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val entry = draft.intakeId != 0L
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (entry) {
                    stringResource(R.string.delete_entry_title)
                } else {
                    stringResource(R.string.delete_title, draft.name)
                },
            )
        },
        text = { Text(stringResource(if (entry) R.string.delete_entry_text else R.string.delete_text)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private fun MedDraft.withWindow(on: Boolean): MedDraft =
    if (on) {
        copy(
            windowStartMinute = windowStartMinute ?: DEFAULT_WINDOW_START,
            windowEndMinute = windowEndMinute ?: DEFAULT_WINDOW_END,
        )
    } else {
        copy(windowStartMinute = null, windowEndMinute = null)
    }

private fun MedDraft.withPickedTime(target: TimeTarget, minute: Int, zone: ZoneId, now: Long): MedDraft =
    when (target) {
        TimeTarget.TAKEN -> copy(takenAt = withTimeOfDay(takenAt, minute, zone, now))
        TimeTarget.WINDOW_START -> copy(windowStartMinute = minute)
        TimeTarget.WINDOW_END -> copy(windowEndMinute = minute)
        TimeTarget.NONE -> this
    }

@Composable
private fun titleOf(mode: EditorMode): String =
    when (mode) {
        EditorMode.CREATE -> stringResource(R.string.editor_new)
        EditorMode.TAKE -> stringResource(R.string.editor_take)
        EditorMode.EDIT -> stringResource(R.string.editor_edit)
    }

private fun labelOf(target: TimeTarget): Int =
    when (target) {
        TimeTarget.WINDOW_START -> R.string.window_from
        TimeTarget.WINDOW_END -> R.string.window_to
        else -> R.string.field_taken_at
    }

private fun commitLabelOf(mode: EditorMode): Int =
    when (mode) {
        EditorMode.CREATE -> R.string.action_add
        EditorMode.TAKE -> R.string.action_take
        EditorMode.EDIT -> R.string.action_save
    }
