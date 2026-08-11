package ru.murasya.prn.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.murasya.prn.data.Intake
import ru.murasya.prn.data.Med
import ru.murasya.prn.data.PrnDatabase
import ru.murasya.prn.domain.Alert
import ru.murasya.prn.domain.MedState
import ru.murasya.prn.domain.alerts
import ru.murasya.prn.domain.medStates
import ru.murasya.prn.notify.refreshReminders

/** How often the screen re-reads the clock so "in 2 h" and "due now" stay honest. */
private const val TICK_MS = 20_000L
private const val KEEP_ALIVE_MS = 5_000L

/** One row of the log: the intake and the medication it belongs to. */
data class LogEntry(
    val intake: Intake,
    val med: Med,
)

data class EditorState(
    val mode: EditorMode,
    val draft: MedDraft,
)

data class PrnUiState(
    val now: Long = 0,
    val states: List<MedState> = emptyList(),
    val alerts: List<Alert> = emptyList(),
    val entries: List<LogEntry> = emptyList(),
    val ready: Boolean = false,
)

class PrnViewModel(
    private val app: Application,
) : AndroidViewModel(app) {
    private val dao = PrnDatabase.get(app).dao()

    private val ticks =
        flow {
            while (true) {
                emit(System.currentTimeMillis())
                delay(TICK_MS)
            }
        }

    val state: StateFlow<PrnUiState> =
        combine(dao.medsFlow(), dao.intakesFlow(), ticks) { meds, intakes, now ->
            compose(meds, intakes, now)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(KEEP_ALIVE_MS), PrnUiState())

    private val editorState = MutableStateFlow<EditorState?>(null)
    val editor: StateFlow<EditorState?> = editorState.asStateFlow()

    private val undoable = MutableStateFlow<Intake?>(null)
    val undoableIntake: StateFlow<Intake?> = undoable.asStateFlow()

    fun openCreate(color: Int) {
        val now = System.currentTimeMillis()
        editorState.value = EditorState(EditorMode.CREATE, MedDraft(colorArgb = color, takenAt = now))
    }

    fun openTake(med: Med) {
        val now = System.currentTimeMillis()
        editorState.value = EditorState(EditorMode.TAKE, draftOf(med, null, now))
    }

    fun openEdit(med: Med, intake: Intake?) {
        val now = System.currentTimeMillis()
        editorState.value = EditorState(EditorMode.EDIT, draftOf(med, intake, now))
    }

    /** Re-arms alarms and re-syncs the shade; cheap, idempotent, and run every time we come back. */
    fun refresh() {
        viewModelScope.launch { refreshReminders(app) }
    }

    fun updateDraft(draft: MedDraft) {
        editorState.value = editorState.value?.copy(draft = draft)
    }

    fun closeEditor() {
        editorState.value = null
    }

    /** Saves the medication and, unless we are only editing, records that a dose was just taken. */
    fun commit(mode: EditorMode, draft: MedDraft) =
        edit {
            val med = draft.toMed()
            val id =
                if (med.id == 0L) {
                    dao.insertMed(med)
                } else {
                    dao.updateMed(med)
                    med.id
                }
            if (mode == EditorMode.EDIT) {
                updateIntake(draft, id)
            } else {
                logIntake(id, draft.takenMg, draft.takenAt)
            }
        }

    /** One-tap logging from an alert card. The undo hook is what makes a mis-tap harmless. */
    fun take(medId: Long) =
        edit {
            val med = dao.med(medId) ?: return@edit
            val at = System.currentTimeMillis()
            val id = logIntake(med.id, med.doseMg, at)
            undoable.value = Intake(id = id, medId = med.id, takenAt = at, doseMg = med.doseMg)
        }

    fun undoTake() =
        edit {
            val intake = undoable.value ?: return@edit
            undoable.value = null
            dao.deleteIntake(intake.id)
            dao.refundStock(intake.medId, intake.doseMg)
        }

    fun forgetUndo() {
        undoable.value = null
    }

    fun deleteMed(medId: Long) =
        edit {
            dao.med(medId)?.let { dao.deleteMed(it) }
        }

    /** Removing a single mistaken entry puts its own milligrams back in the tin. */
    fun deleteIntake(intakeId: Long, medId: Long) =
        edit {
            val taken = dao.intake(intakeId)?.doseMg
            dao.deleteIntake(intakeId)
            if (taken != null) dao.refundStock(medId, taken)
        }

    private suspend fun logIntake(medId: Long, doseMg: Double, at: Long): Long {
        val id = dao.insertIntake(Intake(medId = medId, takenAt = at, doseMg = doseMg))
        dao.spendStock(medId, doseMg)
        return id
    }

    /**
     * Stock is milligrams now, so correcting a dose has to move the tin by the difference — leaving
     * it alone would let the number drift every time a half or a double got fixed after the fact.
     */
    private suspend fun updateIntake(draft: MedDraft, medId: Long) {
        if (draft.intakeId == 0L) return
        val was = dao.intake(draft.intakeId)?.doseMg
        val dose = draft.takenMg
        dao.updateIntake(Intake(id = draft.intakeId, medId = medId, takenAt = draft.takenAt, doseMg = dose))
        if (was != null && was != dose) {
            dao.refundStock(medId, was)
            dao.spendStock(medId, dose)
        }
    }

    private fun edit(block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
            refreshReminders(app)
        }
    }

    private fun compose(meds: List<Med>, intakes: List<Intake>, now: Long): PrnUiState {
        val states = medStates(meds, intakes, now, ZoneId.systemDefault())
        val byId = meds.associateBy { it.id }
        val entries = intakes.mapNotNull { intake -> byId[intake.medId]?.let { med -> LogEntry(intake, med) } }
        return PrnUiState(now, states, alerts(states), entries, ready = true)
    }
}
