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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InventoryUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val statusMessage: String? = null,
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

class InventoryViewModel(
    private val repository: InventoryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(InventoryUiState())
    val state: StateFlow<InventoryUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun selectSection(section: AppSection) {
        _state.update {
            it.copy(
                section = section,
                // Keep context state when switching away; clear editor noise optional
            )
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val snapshot = repository.loadSnapshot()
                _state.update { current ->
                    val selection =
                        when (val sel = current.selection) {
                            BrowseSelection.System -> BrowseSelection.System
                            is BrowseSelection.Project -> {
                                if (snapshot.projects.any { it.path == sel.path }) {
                                    sel
                                } else {
                                    BrowseSelection.System
                                }
                            }
                        }
                    val preview = current.preview
                    val refreshedPreview =
                        if (preview != null) {
                            try {
                                preview.copy(content = repository.readFile(preview.path))
                            } catch (_: Exception) {
                                null
                            }
                        } else {
                            null
                        }
                    current.copy(
                        loading = false,
                        snapshot = snapshot,
                        selection = selection,
                        error = null,
                        preview = refreshedPreview,
                        draftContent = refreshedPreview?.content ?: current.draftContent,
                        dirty = false,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        error = e.message ?: e::class.simpleName ?: "Unknown error",
                    )
                }
            }
        }
    }

    fun selectSystem() {
        _state.update {
            it.copy(
                selection = BrowseSelection.System,
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
            _state.update { it.copy(saving = true, statusMessage = null) }
            try {
                repository.writeFile(preview.path, draft)
                _state.update {
                    it.copy(
                        saving = false,
                        dirty = false,
                        preview = preview.copy(content = draft),
                        statusMessage = "Saved ${preview.title}",
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        saving = false,
                        statusMessage = "Save failed: ${e.message}",
                    )
                }
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
            _state.update { it.copy(deleteConfirmPath = null, deleteConfirmTitle = null, statusMessage = null) }
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
                        statusMessage = "Deleted",
                    )
                }
                refresh()
            } catch (e: Exception) {
                _state.update { it.copy(statusMessage = "Delete failed: ${e.message}") }
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
            _state.update { it.copy(createDialog = null, statusMessage = null) }
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
                refresh()
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
                _state.update { it.copy(statusMessage = "Created $name") }
            } catch (e: Exception) {
                _state.update { it.copy(statusMessage = "Create failed: ${e.message}") }
            }
        }
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

    fun clearStatus() {
        _state.update { it.copy(statusMessage = null) }
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
            _state.update { it.copy(statusMessage = null) }
            try {
                repository.launchTool(tool, projectPath)
                val where = projectPath?.let { " in ${it.substringAfterLast('/')}" }.orEmpty()
                _state.update { it.copy(statusMessage = "Opened ${tool.displayName}$where") }
            } catch (e: Exception) {
                _state.update {
                    it.copy(statusMessage = "Open ${tool.displayName} failed: ${e.message}")
                }
            }
        }
    }

    private fun parentPath(path: String): String? {
        val normalized = path.replace('\\', '/')
        val idx = normalized.lastIndexOf('/')
        return if (idx > 0) normalized.substring(0, idx) else null
    }
}
