package com.inspiredandroid.orcaeye.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inspiredandroid.orcaeye.data.InventoryRepository
import com.inspiredandroid.orcaeye.model.AppSection
import com.inspiredandroid.orcaeye.model.AppSnapshot
import com.inspiredandroid.orcaeye.model.BrowseSelection
import com.inspiredandroid.orcaeye.model.CreateKind
import com.inspiredandroid.orcaeye.model.CreateRequest
import com.inspiredandroid.orcaeye.model.FilePreview
import com.inspiredandroid.orcaeye.model.MemoryItem
import com.inspiredandroid.orcaeye.model.ProjectInventory
import com.inspiredandroid.orcaeye.model.SkillItem
import com.inspiredandroid.orcaeye.model.SkillOrigin
import com.inspiredandroid.orcaeye.model.ToolKind
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InventoryUiState(
    val loading: Boolean = true,
    /** Project path discovery still running after system snapshot is shown. */
    val projectsLoading: Boolean = false,
    /** Full scan of the selected project (skills/memories) in progress. */
    val projectDetailsLoading: Boolean = false,
    val error: String? = null,
    val section: AppSection = AppSection.Context,
    val snapshot: AppSnapshot? = null,
    val selection: BrowseSelection = BrowseSelection.System,
    val preview: FilePreview? = null,
    val draftContent: String = "",
    val previewLoading: Boolean = false,
    val saving: Boolean = false,
    val dirty: Boolean = false,
    val createDialog: CreateRequest? = null,
    val deleteConfirmPath: String? = null,
    val deleteConfirmTitle: String? = null,
) {
    val projects: List<ProjectInventory>
        get() = snapshot?.projects.orEmpty()

    val selectedProject: ProjectInventory?
        get() {
            val sel = selection as? BrowseSelection.Project ?: return null
            return snapshot?.projects?.firstOrNull { it.path == sel.path }
        }

    val installedTools: List<ToolKind>
        get() =
            snapshot
                ?.tools
                ?.filter { it.installed }
                ?.map { it.kind }
                .orEmpty()
                .ifEmpty { ToolKind.entries }
}

/**
 * Context (inventory) screen callbacks, bundled so [BrowserScreen] takes one parameter
 * instead of a dozen — same pattern as [LoopsActions]. Defaults are no-ops so previews
 * and screenshot tests can pass an empty instance.
 */
class ContextActions(
    val onRefresh: () -> Unit = {},
    val onSelectSystem: () -> Unit = {},
    val onSelectProject: (String) -> Unit = {},
    val onOpenSkill: (SkillItem) -> Unit = {},
    val onOpenMemory: (MemoryItem) -> Unit = {},
    val onOpenFile: (path: String, title: String) -> Unit = { _, _ -> },
    val onDraftChange: (String) -> Unit = {},
    val onSave: () -> Unit = {},
    val onClosePreview: () -> Unit = {},
    val onShowCreate: (CreateKind, String?, List<ToolKind>) -> Unit = { _, _, _ -> },
    val onDismissCreate: () -> Unit = {},
    val onCreate: (name: String, description: String, tool: ToolKind) -> Unit = { _, _, _ -> },
    val onRequestDeleteFromEditor: () -> Unit = {},
    val onCancelDelete: () -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onOpenTool: (ToolKind, String?) -> Unit = { _, _ -> },
)

class InventoryViewModel(
    private val repository: InventoryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(InventoryUiState())
    val state: StateFlow<InventoryUiState> = _state.asStateFlow()

    private var refreshJob: Job? = null
    private var projectLoadJob: Job? = null

    init {
        refresh()
    }

    fun selectSection(section: AppSection) {
        _state.update {
            it.copy(
                section = section,
            )
        }
    }

    /**
     * Progressive refresh:
     * 1. System inventory (tools + global skills/memories) → first paint
     * 2. Lightweight project discovery → sidebar
     * 3. If a project is selected, full-scan it on demand
     */
    fun refresh() {
        refreshJob?.cancel()
        projectLoadJob?.cancel()
        refreshJob =
            viewModelScope.launch {
                _state.update {
                    it.copy(
                        loading = true,
                        projectsLoading = true,
                        projectDetailsLoading = false,
                        error = null,
                    )
                }
                try {
                    val system = repository.loadSystemSnapshot()
                    _state.update { current ->
                        // Paint System immediately; keep previous project rows until discovery finishes.
                        current.copy(
                            loading = false,
                            snapshot =
                            system.copy(
                                projects = current.snapshot?.projects.orEmpty(),
                                unlinkedMemories = current.snapshot?.unlinkedMemories.orEmpty(),
                            ),
                            error = null,
                        )
                    }

                    val discovered = repository.discoverProjects()
                    _state.update { current ->
                        val snap = current.snapshot ?: system
                        val selection =
                            when (val sel = current.selection) {
                                BrowseSelection.System -> BrowseSelection.System
                                is BrowseSelection.Project -> {
                                    if (discovered.projects.any { it.path == sel.path }) {
                                        sel
                                    } else {
                                        BrowseSelection.System
                                    }
                                }
                            }
                        current.copy(
                            projectsLoading = false,
                            snapshot =
                            snap.copy(
                                projects = discovered.projects,
                                unlinkedMemories = discovered.unlinkedMemories,
                                warnings =
                                (snap.warnings + discovered.warnings).distinct(),
                            ),
                            selection = selection,
                        )
                    }

                    // Refresh open preview content if any
                    val preview = _state.value.preview
                    if (preview != null) {
                        try {
                            val content = repository.readFile(preview.path)
                            _state.update {
                                it.copy(
                                    preview = preview.copy(content = content),
                                    draftContent = content,
                                    dirty = false,
                                )
                            }
                        } catch (_: Exception) {
                            _state.update {
                                it.copy(preview = null, draftContent = "", dirty = false)
                            }
                        }
                    }

                    val selectedPath = (_state.value.selection as? BrowseSelection.Project)?.path
                    if (selectedPath != null) {
                        ensureProjectDetailsLoaded(selectedPath)
                    }
                } catch (e: Exception) {
                    _state.update {
                        it.copy(
                            loading = false,
                            projectsLoading = false,
                            projectDetailsLoading = false,
                            error = e.message ?: e::class.simpleName ?: "Unknown error",
                        )
                    }
                }
            }
    }

    fun selectSystem() {
        projectLoadJob?.cancel()
        _state.update {
            it.copy(
                selection = BrowseSelection.System,
                projectDetailsLoading = false,
                preview = null,
                draftContent = "",
                dirty = false,
            )
        }
    }

    fun selectProject(path: String) {
        _state.update {
            it.copy(
                selection = BrowseSelection.Project(path),
                preview = null,
                draftContent = "",
                dirty = false,
            )
        }
        ensureProjectDetailsLoaded(path)
    }

    private fun ensureProjectDetailsLoaded(path: String) {
        val existing = _state.value.snapshot?.projects?.firstOrNull { it.path == path }
        if (existing?.detailsLoaded == true) {
            _state.update { it.copy(projectDetailsLoading = false) }
            return
        }
        projectLoadJob?.cancel()
        projectLoadJob =
            viewModelScope.launch {
                _state.update { it.copy(projectDetailsLoading = true) }
                try {
                    val full = repository.loadProject(path)
                    _state.update { current ->
                        // Drop result if the user navigated away.
                        val stillSelected =
                            (current.selection as? BrowseSelection.Project)?.path == path
                        if (!stillSelected) {
                            return@update current.copy(projectDetailsLoading = false)
                        }
                        val snap = current.snapshot
                        if (snap == null) {
                            return@update current.copy(projectDetailsLoading = false)
                        }
                        if (full == null) {
                            return@update current.copy(
                                projectDetailsLoading = false,
                                selection = BrowseSelection.System,
                            )
                        }
                        val projects =
                            snap.projects.map { p ->
                                if (p.path == path || p.path == full.path) full else p
                            }.let { list ->
                                if (list.none { it.path == full.path }) list + full else list
                            }.sortedBy { it.name.lowercase() }
                        current.copy(
                            projectDetailsLoading = false,
                            snapshot = snap.copy(projects = projects),
                            selection = BrowseSelection.Project(full.path),
                        )
                    }
                } catch (_: Exception) {
                    _state.update { it.copy(projectDetailsLoading = false) }
                }
            }
    }

    fun openSkill(skill: SkillItem) {
        val path = skill.skillMdPath ?: return
        openFile(
            path = path,
            title = skill.name,
            deletePath = skill.path,
            canDelete = skill.origin != SkillOrigin.Bundled,
            isSkill = true,
        )
    }

    fun openMemory(memory: MemoryItem) {
        openFile(
            path = memory.path,
            title = memory.title,
            deletePath = memory.path,
            canDelete = true,
            isSkill = false,
        )
    }

    fun openFile(
        path: String,
        title: String,
        deletePath: String? = path,
        canDelete: Boolean = true,
        isSkill: Boolean = false,
        readOnly: Boolean = false,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(previewLoading = true) }
            val content = repository.readFile(path)
            _state.update {
                it.copy(
                    previewLoading = false,
                    preview =
                    FilePreview(
                        path = path,
                        title = title,
                        content = content,
                        deletePath = deletePath,
                        canDelete = canDelete,
                        isSkill = isSkill,
                        readOnly = readOnly,
                    ),
                    draftContent = content,
                    dirty = false,
                )
            }
        }
    }

    fun updateDraft(content: String) {
        _state.update { it.copy(draftContent = content, dirty = content != it.preview?.content) }
    }

    fun saveDraft() {
        val preview = _state.value.preview ?: return
        val draft = _state.value.draftContent
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            try {
                repository.writeFile(preview.path, draft)
                _state.update {
                    it.copy(
                        saving = false,
                        dirty = false,
                        preview = preview.copy(content = draft),
                    )
                }
            } catch (_: Exception) {
                _state.update { it.copy(saving = false) }
            }
        }
    }

    fun requestDeleteFromEditor() {
        val preview = _state.value.preview ?: return
        if (!preview.canDelete) return
        val path = preview.deletePath ?: preview.path
        _state.update {
            it.copy(
                deleteConfirmPath = path,
                deleteConfirmTitle = preview.title,
            )
        }
    }

    fun cancelDelete() {
        _state.update { it.copy(deleteConfirmPath = null, deleteConfirmTitle = null) }
    }

    fun confirmDelete() {
        val path = _state.value.deleteConfirmPath ?: return
        viewModelScope.launch {
            _state.update { it.copy(deleteConfirmPath = null, deleteConfirmTitle = null) }
            try {
                repository.deleteEntry(path)
                val closedPreview =
                    _state.value.preview?.let { preview ->
                        val del = preview.deletePath ?: preview.path
                        if (del == path || preview.path == path) null else preview
                    }
                _state.update {
                    it.copy(
                        preview = closedPreview,
                        draftContent = closedPreview?.content.orEmpty(),
                        dirty = false,
                    )
                }
                // Prefer reloading only the selected project when possible.
                val projectPath = (_state.value.selection as? BrowseSelection.Project)?.path
                if (projectPath != null) {
                    invalidateAndReloadProject(projectPath)
                } else {
                    refresh()
                }
            } catch (_: Exception) {
                // Ignore; user can retry via refresh.
            }
        }
    }

    fun showCreate(
        kind: CreateKind,
        projectPath: String?,
        availableTools: List<ToolKind>,
    ) {
        val tools = availableTools.ifEmpty { ToolKind.entries }
        _state.update {
            it.copy(
                createDialog =
                CreateRequest(
                    kind = kind,
                    projectPath = projectPath,
                    availableTools = tools,
                ),
            )
        }
    }

    fun dismissCreate() {
        _state.update { it.copy(createDialog = null) }
    }

    fun create(
        name: String,
        description: String,
        tool: ToolKind,
    ) {
        val req = _state.value.createDialog ?: return
        viewModelScope.launch {
            _state.update { it.copy(createDialog = null) }
            try {
                val path =
                    when (req.kind) {
                        CreateKind.Skill ->
                            repository.createSkill(
                                name = name,
                                tool = tool,
                                projectPath = req.projectPath,
                                description = description,
                            )
                        CreateKind.Memory ->
                            repository.createMemory(
                                name = name,
                                tool = tool,
                                projectPath = req.projectPath,
                                content = "",
                            )
                    }
                if (req.projectPath != null) {
                    invalidateAndReloadProject(req.projectPath)
                } else {
                    // System skill/memory: reload system lists without waiting on projects.
                    reloadSystemOnly()
                }
                openFile(
                    path = path,
                    title = name.trim().ifBlank { path },
                    deletePath =
                    if (req.kind == CreateKind.Skill) {
                        parentPath(path) ?: path
                    } else {
                        path
                    },
                    canDelete = true,
                    isSkill = req.kind == CreateKind.Skill,
                )
            } catch (_: Exception) {
                // Ignore; user can retry.
            }
        }
    }

    private suspend fun reloadSystemOnly() {
        try {
            val system = repository.loadSystemSnapshot()
            _state.update { current ->
                val snap = current.snapshot
                current.copy(
                    snapshot =
                    system.copy(
                        projects = snap?.projects.orEmpty(),
                        unlinkedMemories = snap?.unlinkedMemories.orEmpty(),
                        warnings = (system.warnings + snap?.warnings.orEmpty()).distinct(),
                    ),
                )
            }
        } catch (_: Exception) {
            // Ignore; caller continues with existing snapshot.
        }
    }

    private suspend fun invalidateAndReloadProject(projectPath: String) {
        _state.update { current ->
            val snap = current.snapshot ?: return@update current
            current.copy(
                snapshot =
                snap.copy(
                    projects =
                    snap.projects.map { p ->
                        if (p.path == projectPath) {
                            p.copy(
                                detailsLoaded = false,
                                skills = emptyList(),
                                memories = emptyList(),
                            )
                        } else {
                            p
                        }
                    },
                ),
            )
        }
        ensureProjectDetailsLoaded(projectPath)
        // ensureProjectDetailsLoaded launches a job; wait by reloading directly for create/delete
        projectLoadJob?.join()
    }

    fun closePreview() {
        _state.update {
            it.copy(
                preview = null,
                draftContent = "",
                dirty = false,
                previewLoading = false,
            )
        }
    }

    /**
     * Launch Claude / Grok / OpenCode in a new terminal.
     * [projectPath] is used as the shell working directory when non-null.
     */
    fun openTool(
        tool: ToolKind,
        projectPath: String? = null,
    ) {
        viewModelScope.launch {
            try {
                repository.launchTool(tool, projectPath)
            } catch (_: Exception) {
                // Ignore; launch is fire-and-forget.
            }
        }
    }

    private fun parentPath(path: String): String? {
        val normalized = path.replace('\\', '/')
        val idx = normalized.lastIndexOf('/')
        return if (idx > 0) normalized.substring(0, idx) else null
    }
}
