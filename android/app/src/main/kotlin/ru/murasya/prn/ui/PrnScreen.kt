package ru.murasya.prn.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import java.time.ZoneId
import ru.murasya.prn.R
import ru.murasya.prn.data.Med

private const val FAB_CLEARANCE = 88
private const val FAB_PRESS_SCALE = 0.92f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrnScreen(viewModel: PrnViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val editor by viewModel.editor.collectAsStateWithLifecycle()
    val permissions = rememberPermissionState()
    val zone = remember { ZoneId.systemDefault() }
    val snackbar = remember { SnackbarHostState() }
    val list = rememberLazyListState()

    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.refresh() }
    UndoBar(state, viewModel, snackbar)

    CompositionLocalProvider(LocalEntranceWindow provides rememberEntranceWindow(state.ready)) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = { PrnTopBar(list) },
            snackbarHost = { SnackbarHost(snackbar) },
            floatingActionButton = {
                AddButton(inviting = state.ready && state.states.isEmpty()) {
                    viewModel.openCreate(nextColor(state.usedColors()))
                }
            },
        ) { padding ->
            LogList(
                state = state,
                permissions = permissions,
                list = list,
                padding = padding,
                zone = zone,
                onTake = { med -> viewModel.take(med.id) },
                onOpen = viewModel::openTake,
                onEditEntry = { entry -> viewModel.openEdit(entry.med, entry.intake) },
                onEditMed = { med -> viewModel.openEdit(med, null) },
            )
        }
    }

    Editor(editor, state, zone, viewModel)
}

@Composable
private fun UndoBar(state: PrnUiState, viewModel: PrnViewModel, host: SnackbarHostState) {
    val logged by viewModel.undoableIntake.collectAsStateWithLifecycle()
    val intake = logged ?: return
    val name =
        state.states
            .firstOrNull { it.med.id == intake.medId }
            ?.med
            ?.name ?: return
    val message = stringResource(R.string.snack_taken, name)
    val undo = stringResource(R.string.action_undo)
    LaunchedEffect(intake.id) {
        val result = host.showSnackbar(message, undo, duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) viewModel.undoTake() else viewModel.forgetUndo()
    }
}

@Composable
private fun Editor(editor: EditorState?, state: PrnUiState, zone: ZoneId, viewModel: PrnViewModel) {
    if (editor == null) return
    val medState = state.states.firstOrNull { it.med.id == editor.draft.medId }
    EditorSheet(
        editor = editor,
        medState = medState,
        now = state.now,
        zone = zone,
        onDraftChange = viewModel::updateDraft,
        onCommit = {
            viewModel.commit(editor.mode, editor.draft)
            viewModel.closeEditor()
        },
        onDelete = {
            val draft = editor.draft
            if (draft.intakeId != 0L) {
                viewModel.deleteIntake(draft.intakeId, draft.medId)
            } else {
                viewModel.deleteMed(draft.medId)
            }
            viewModel.closeEditor()
        },
        onDismiss = viewModel::closeEditor,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrnTopBar(list: LazyListState) {
    val lifted by remember(list) { derivedStateOf { list.canScrollBackward } }
    val scheme = MaterialTheme.colorScheme
    val container by
        animateColorAsState(
            targetValue = if (lifted) scheme.surfaceContainer else scheme.surface,
            animationSpec = effects(),
            label = "topBarContainer",
        )
    TopAppBar(
        title = { Text(stringResource(R.string.app_name)) },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = container),
    )
}

@Composable
private fun AddButton(inviting: Boolean, onClick: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    FloatingActionButton(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        interactionSource = interactions,
        modifier = Modifier.entrance().pressSquish(interactions, FAB_PRESS_SCALE),
    ) {
        AddIcon(inviting)
    }
}

@Composable
private fun AddIcon(inviting: Boolean) {
    Icon(
        painter = painterResource(R.drawable.ic_add),
        contentDescription = stringResource(R.string.action_add),
        modifier = Modifier.breathing(inviting),
    )
}

@Composable
private fun LogList(
    state: PrnUiState,
    permissions: PermissionState,
    list: LazyListState,
    padding: PaddingValues,
    zone: ZoneId,
    onTake: (Med) -> Unit,
    onOpen: (Med) -> Unit,
    onEditEntry: (LogEntry) -> Unit,
    onEditMed: (Med) -> Unit,
) {
    val today = remember(state.now) { localDate(state.now, zone) }
    val days = remember(state.entries) { state.entries.groupBy { localDate(it.intake.takenAt, zone) } }
    LazyColumn(
        state = list,
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + FAB_CLEARANCE.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        var next = permissionItems(permissions, 0)
        next = alertItems(state, onTake, next)
        next = stripItem(state, onOpen, onEditMed, next)
        next = emptyItem(state, next)
        daySections(days, today, zone, onOpen, onEditEntry, next)
    }
}

private fun LazyListScope.permissionItems(permissions: PermissionState, from: Int): Int {
    var next = from
    if (!permissions.notifications) {
        val at = next++
        item(key = "perm-notifications") { NotificationBanner(permissions, itemMotion(at)) }
    }
    if (!permissions.exactAlarms) {
        val at = next++
        item(key = "perm-alarms") { ExactAlarmBanner(permissions, itemMotion(at)) }
    }
    return next
}

private fun LazyListScope.alertItems(state: PrnUiState, onTake: (Med) -> Unit, from: Int): Int {
    itemsIndexed(state.alerts, key = { _, alert -> "alert-${alert.state.med.id}-${alert.kind}" }) { index, alert ->
        AlertCard(alert, state.now, onTake, itemMotion(from + index))
    }
    return from + state.alerts.size
}

private fun LazyListScope.stripItem(
    state: PrnUiState,
    onOpen: (Med) -> Unit,
    onEditMed: (Med) -> Unit,
    from: Int,
): Int {
    if (state.states.isEmpty()) return from
    item(key = "strip") { MedStrip(state.states, state.now, from, onOpen, onEditMed, itemPlacement()) }
    return from + state.states.size
}

private fun LazyListScope.emptyItem(state: PrnUiState, from: Int): Int {
    if (!state.ready || state.entries.isNotEmpty()) return from
    item(key = "empty") { EmptyState(itemMotion(from)) }
    return from + 1
}

private fun LazyListScope.daySections(
    days: Map<LocalDate, List<LogEntry>>,
    today: LocalDate,
    zone: ZoneId,
    onTake: (Med) -> Unit,
    onEdit: (LogEntry) -> Unit,
    from: Int,
) {
    days.entries.forEachIndexed { index, (day, entries) ->
        item(key = "day-$day") {
            DaySection(day, today, entries, zone, onTake, onEdit, itemMotion(from + index))
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(top = 72.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.log_empty_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.log_empty_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private fun PrnUiState.usedColors(): List<Int> = states.map { it.med.colorArgb }
