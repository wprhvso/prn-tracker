package ru.murasya.prn.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrnScreen(viewModel: PrnViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val editor by viewModel.editor.collectAsStateWithLifecycle()
    val permissions = rememberPermissionState()
    val zone = remember { ZoneId.systemDefault() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbar = remember { SnackbarHostState() }

    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.refresh() }
    UndoBar(state, viewModel, snackbar)

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { PrnTopBar(scrollBehavior) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = { AddButton { viewModel.openCreate(nextColor(state.usedColors())) } },
    ) { padding ->
        LogList(
            state = state,
            permissions = permissions,
            padding = padding,
            zone = zone,
            onTake = { med -> viewModel.take(med.id) },
            onOpen = viewModel::openTake,
            onEditEntry = { entry -> viewModel.openEdit(entry.med, entry.intake) },
            onEditMed = { med -> viewModel.openEdit(med, null) },
        )
    }

    Editor(editor, state, zone, viewModel)
}

/** A dose logged in one tap deserves to be un-logged in one tap. */
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
private fun PrnTopBar(scrollBehavior: TopAppBarScrollBehavior) {
    LargeTopAppBar(
        title = { Text(stringResource(R.string.app_name)) },
        scrollBehavior = scrollBehavior,
    )
}

@Composable
private fun AddButton(onClick: () -> Unit) {
    FloatingActionButton(onClick = onClick, shape = MaterialTheme.shapes.large) {
        Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.action_add))
    }
}

@Composable
private fun LogList(
    state: PrnUiState,
    permissions: PermissionState,
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
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 104.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        permissionItems(permissions)
        alertItems(state, onTake)
        stripItem(state, onOpen, onEditMed)
        emptyItem(state)
        daySections(days, today, zone, onOpen, onEditEntry)
    }
}

private fun LazyListScope.permissionItems(permissions: PermissionState) {
    if (!permissions.notifications) item(key = "perm-notifications") { NotificationBanner(permissions) }
    if (!permissions.exactAlarms) item(key = "perm-alarms") { ExactAlarmBanner(permissions) }
}

private fun LazyListScope.alertItems(state: PrnUiState, onTake: (Med) -> Unit) {
    items(state.alerts, key = { "alert-${it.state.med.id}-${it.kind}" }) { alert ->
        AlertCard(alert, state.now, onTake)
    }
}

private fun LazyListScope.stripItem(state: PrnUiState, onOpen: (Med) -> Unit, onEditMed: (Med) -> Unit) {
    if (state.states.isEmpty()) return
    item(key = "strip") { MedStrip(state.states, state.now, onOpen, onEditMed) }
}

private fun LazyListScope.emptyItem(state: PrnUiState) {
    if (!state.ready || state.entries.isNotEmpty()) return
    item(key = "empty") { EmptyState() }
}

private fun LazyListScope.daySections(
    days: Map<LocalDate, List<LogEntry>>,
    today: LocalDate,
    zone: ZoneId,
    onTake: (Med) -> Unit,
    onEdit: (LogEntry) -> Unit,
) {
    days.forEach { (day, entries) ->
        item(key = "day-$day") { DaySection(day, today, entries, zone, onTake, onEdit) }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 72.dp, start = 24.dp, end = 24.dp),
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
