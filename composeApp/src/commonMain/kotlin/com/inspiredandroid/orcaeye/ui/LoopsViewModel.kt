package com.inspiredandroid.orcaeye.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inspiredandroid.orcaeye.data.CrontabParser
import com.inspiredandroid.orcaeye.data.CrontabRepository
import com.inspiredandroid.orcaeye.model.CronSchedule
import com.inspiredandroid.orcaeye.model.LoopJob
import com.inspiredandroid.orcaeye.model.LoopSnapshot
import com.inspiredandroid.orcaeye.model.LoopSource
import com.inspiredandroid.orcaeye.model.SchedulePreset
import com.inspiredandroid.orcaeye.model.ToolKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

data class LoopsUiState(
    val loading: Boolean = true,
    val snapshot: LoopSnapshot? = null,
    val statusMessage: String? = null,
    val editor: LoopEditorState? = null,
    val deleteConfirm: LoopJob? = null,
    /** Next firing time per enabled job id, computed on load so the UI stays a pure render. */
    val nextRuns: Map<String, LocalDateTime> = emptyMap(),
) {
    val jobs: List<LoopJob> get() = snapshot?.scheduled.orEmpty()
    val externalJobs: List<LoopJob> get() = snapshot?.external.orEmpty()
    val crontabAvailable: Boolean get() = snapshot?.crontabAvailable != false
}

/** The create/edit form. [expression] is the source of truth; [preset] is derived from it. */
data class LoopEditorState(
    val jobId: String? = null,
    val name: String = "",
    val tool: ToolKind = ToolKind.Grok,
    val projectPath: String? = null,
    val prompt: String = "",
    val preset: SchedulePreset = SchedulePreset.Daily(hour = 9, minute = 0),
    val expression: String = "0 9 * * *",
    val logPath: String = "",
    val extraFlags: String = "",
    val pathPrefix: String = "",
    val nextRuns: List<LocalDateTime> = emptyList(),
) {
    val schedule: CronSchedule? get() = CronSchedule.parse(expression)
    val expressionValid: Boolean get() = schedule != null
    val isNew: Boolean get() = jobId == null
    val canSave: Boolean get() = expressionValid && prompt.isNotBlank() && name.isNotBlank()
}

/**
 * The Loops screen's callbacks, bundled so [BrowserScreen] takes one parameter instead of a
 * dozen. Defaults are no-ops so previews and screenshot tests can pass an empty instance.
 */
class LoopsActions(
    val onRefresh: () -> Unit = {},
    val onCreate: () -> Unit = {},
    val onEdit: (LoopJob) -> Unit = {},
    val onEditorChange: (LoopEditorState) -> Unit = {},
    val onEditorSave: () -> Unit = {},
    val onEditorCancel: () -> Unit = {},
    val onToggleEnabled: (LoopJob) -> Unit = {},
    val onRunNow: (LoopJob) -> Unit = {},
    val onViewLog: (LoopJob) -> Unit = {},
    val onRequestDelete: (LoopJob) -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onCancelDelete: () -> Unit = {},
    val onClearStatus: () -> Unit = {},
)

class LoopsViewModel(
    private val repository: CrontabRepository,
    private val now: () -> LocalDateTime = { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()) },
) : ViewModel() {
    private val _state = MutableStateFlow(LoopsUiState())
    val state: StateFlow<LoopsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            try {
                refreshNow()
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        statusMessage = "Could not read the crontab: ${e.message}",
                    )
                }
            }
        }
    }

    fun startCreate(defaultProjectPath: String? = null) {
        val tool = ToolKind.Grok
        val editor =
            LoopEditorState(
                tool = tool,
                projectPath = defaultProjectPath,
                extraFlags = CrontabParser.defaultFlags(tool),
                pathPrefix = repository.defaultPathPrefix(),
            ).withDerivedName()
        _state.update { it.copy(editor = editor.withNextRuns()) }
    }

    fun startEdit(job: LoopJob) {
        if (!job.editable) return
        val editor =
            LoopEditorState(
                jobId = job.id,
                name = job.name,
                tool = job.tool ?: ToolKind.Grok,
                projectPath = job.workingDirectory,
                prompt = job.prompt,
                preset = SchedulePreset.from(job.schedule),
                expression = job.schedule.expression,
                logPath = job.logPath.orEmpty(),
                extraFlags = job.extraFlags,
                pathPrefix = job.pathPrefix.orEmpty(),
            )
        _state.update { it.copy(editor = editor.withNextRuns()) }
    }

    /**
     * Applies a form change, keeping the derived bits in step: switching CLI swaps the default
     * flags, changing prompt or project renames the job (until the user renames it by hand),
     * and any schedule change recomputes the next runs.
     */
    fun updateEditor(next: LoopEditorState) {
        val current = _state.value.editor ?: return
        var updated = next

        if (next.tool != current.tool && current.extraFlags == CrontabParser.defaultFlags(current.tool)) {
            updated = updated.copy(extraFlags = CrontabParser.defaultFlags(next.tool))
        }
        // The preset controls and the raw expression are two views of one value; whichever the
        // user touched wins, and the other is re-derived.
        if (next.preset != current.preset && next.expression == current.expression) {
            next.preset.toSchedule()?.let { updated = updated.copy(expression = it.expression) }
        }
        updated.schedule?.let { updated = updated.copy(preset = SchedulePreset.from(it)) }

        if (updated.name.isBlank() || updated.name == current.derivedName()) {
            updated = updated.withDerivedName()
        }
        if (updated.logPath.isBlank() || updated.logPath == repository.defaultLogPathFor(current.name)) {
            updated = updated.copy(logPath = repository.defaultLogPathFor(updated.name))
        }

        _state.update { it.copy(editor = updated.withNextRuns()) }
    }

    fun cancelEditor() {
        _state.update { it.copy(editor = null) }
    }

    fun saveEditor() {
        val editor = _state.value.editor ?: return
        val schedule = editor.schedule ?: return
        val existing = _state.value.snapshot?.jobs?.firstOrNull { it.id == editor.jobId }

        val job =
            (existing ?: LoopJob(id = "", name = "", source = LoopSource.Managed, enabled = true, schedule = schedule))
                .copy(
                    name = CrontabParser.slug(editor.name),
                    schedule = schedule,
                    tool = editor.tool,
                    workingDirectory = editor.projectPath,
                    prompt = editor.prompt.trim(),
                    extraFlags = editor.extraFlags.trim(),
                    pathPrefix = editor.pathPrefix.takeIf { it.isNotBlank() },
                    logPath = editor.logPath.takeIf { it.isNotBlank() },
                ).asManaged()

        _state.update { it.copy(editor = null) }
        viewModelScope.launch { persist(job, "Saved ${job.name}") }
    }

    fun toggleEnabled(job: LoopJob) {
        if (!job.editable) return
        val updated = job.copy(enabled = !job.enabled).asManaged()
        viewModelScope.launch {
            persist(updated, if (updated.enabled) "Enabled ${updated.name}" else "Disabled ${updated.name}")
        }
    }

    fun runNow(job: LoopJob) {
        viewModelScope.launch {
            try {
                repository.runNow(job)
                _state.update { it.copy(statusMessage = "Running ${job.name}…") }
            } catch (e: Exception) {
                _state.update { it.copy(statusMessage = "Run failed: ${e.message}") }
            }
        }
    }

    fun requestDelete(job: LoopJob) {
        if (!job.editable) return
        _state.update { it.copy(deleteConfirm = job) }
    }

    fun cancelDelete() {
        _state.update { it.copy(deleteConfirm = null) }
    }

    fun confirmDelete() {
        val job = _state.value.deleteConfirm ?: return
        _state.update { it.copy(deleteConfirm = null) }
        viewModelScope.launch {
            try {
                repository.deleteJob(job.id)
                _state.update { it.copy(statusMessage = "Deleted ${job.name}") }
                refreshNow()
            } catch (e: Exception) {
                _state.update { it.copy(statusMessage = "Delete failed: ${e.message}") }
            }
        }
    }

    fun clearStatus() {
        _state.update { it.copy(statusMessage = null) }
    }

    private suspend fun persist(
        job: LoopJob,
        successMessage: String,
    ) {
        try {
            repository.saveJob(job)
            _state.update { it.copy(statusMessage = successMessage) }
            refreshNow()
        } catch (e: Exception) {
            _state.update { it.copy(statusMessage = "Save failed: ${e.message}") }
        }
    }

    private suspend fun refreshNow() {
        val snapshot = repository.loadJobs()
        val at = now()
        val nextRuns =
            snapshot.jobs
                .filter { it.enabled && it.source != LoopSource.External }
                .mapNotNull { job -> job.schedule.nextRuns(at, count = 1).firstOrNull()?.let { job.id to it } }
                .toMap()
        _state.update { it.copy(loading = false, snapshot = snapshot, nextRuns = nextRuns) }
    }

    private fun LoopEditorState.withNextRuns(): LoopEditorState = copy(nextRuns = schedule?.nextRuns(now(), count = 3).orEmpty())

    private fun LoopEditorState.derivedName(): String = CrontabParser.deriveName(prompt, projectPath)

    private fun LoopEditorState.withDerivedName(): LoopEditorState = copy(name = derivedName())
}
