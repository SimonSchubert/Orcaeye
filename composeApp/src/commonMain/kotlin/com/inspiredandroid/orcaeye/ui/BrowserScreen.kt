package com.inspiredandroid.orcaeye.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inspiredandroid.orcaeye.model.AgentFileItem
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
import com.inspiredandroid.orcaeye.ui.icons.ToolIcon

@Composable
fun BrowserScreen(
    state: InventoryUiState,
    loopsState: LoopsUiState = LoopsUiState(loading = false),
    contextActions: ContextActions = ContextActions(),
    loopsActions: LoopsActions = LoopsActions(),
    onSelectSection: (AppSection) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        SectionHeader(
            section = state.section,
            onSelectSection = onSelectSection,
        )
        when (state.section) {
            AppSection.Context ->
                ContextBody(
                    state = state,
                    actions = contextActions,
                )
            AppSection.Loops ->
                LoopsBody(
                    state = state,
                    loopsState = loopsState,
                    loopsActions = loopsActions,
                    contextActions = contextActions,
                )
        }
    }

    state.createDialog?.let { req ->
        CreateDialog(
            request = req,
            onDismiss = contextActions.onDismissCreate,
            onConfirm = contextActions.onCreate,
        )
    }
    if (state.deleteConfirmPath != null) {
        AlertDialog(
            onDismissRequest = contextActions.onCancelDelete,
            title = { Text("Delete?") },
            text = {
                Text("Delete \"${state.deleteConfirmTitle}\"? This cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = contextActions.onConfirmDelete,
                    modifier = Modifier.hoverHand(),
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = contextActions.onCancelDelete,
                    modifier = Modifier.hoverHand(),
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun SectionHeader(
    section: AppSection,
    onSelectSection: (AppSection) -> Unit,
) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionTab(
            label = "Context",
            selected = section == AppSection.Context,
            onClick = { onSelectSection(AppSection.Context) },
        )
        SectionTab(
            label = "Loops",
            selected = section == AppSection.Loops,
            onClick = { onSelectSection(AppSection.Loops) },
        )
    }
}

@Composable
private fun SectionTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg =
        if (selected) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    val fg =
        if (selected) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = fg,
        modifier =
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .hoverClickable(
                selected = selected,
                selectedColor = bg,
                hoverColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                cornerRadius = 20.dp,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun ContextBody(
    state: InventoryUiState,
    actions: ContextActions,
) {
    when {
        state.loading && state.snapshot == null -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        state.error != null && state.snapshot == null -> {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("Error: ${state.error}", color = MaterialTheme.colorScheme.error)
            }
        }
        else -> {
            Row(Modifier.fillMaxSize()) {
                Sidebar(
                    state = state,
                    actions = actions,
                    modifier =
                    Modifier
                        .width(280.dp)
                        .fillMaxHeight(),
                )
                MainPane(
                    state = state,
                    actions = actions,
                    modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.background),
                )
                if (state.preview != null || state.previewLoading) {
                    EditorPane(
                        preview = state.preview,
                        draft = state.draftContent,
                        loading = state.previewLoading,
                        saving = state.saving,
                        dirty = state.dirty,
                        actions = actions,
                        modifier =
                        Modifier
                            .widthIn(min = 360.dp, max = 560.dp)
                            .weight(1.1f)
                            .fillMaxHeight(),
                    )
                }
            }
        }
    }
}

/**
 * Loops reuses the Context editor pane on the right so "View log" opens a log file the same
 * way a skill or memory opens.
 */
@Composable
private fun LoopsBody(
    state: InventoryUiState,
    loopsState: LoopsUiState,
    loopsActions: LoopsActions,
    contextActions: ContextActions,
) {
    Row(Modifier.fillMaxSize()) {
        LoopsScreen(
            state = loopsState,
            projects = state.projects,
            systemSkills = state.snapshot?.systemSkills.orEmpty(),
            installedTools = state.installedTools,
            actions = loopsActions,
            modifier =
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.background),
        )
        if (state.preview != null || state.previewLoading) {
            EditorPane(
                preview = state.preview,
                draft = state.draftContent,
                loading = state.previewLoading,
                saving = state.saving,
                dirty = state.dirty,
                actions = contextActions,
                modifier =
                Modifier
                    .widthIn(min = 360.dp, max = 560.dp)
                    .weight(1.1f)
                    .fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun Sidebar(
    state: InventoryUiState,
    actions: ContextActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        NavRow(
            label = "System",
            selected = state.selection is BrowseSelection.System,
            subtitle =
            state.snapshot?.let {
                "${it.systemSkills.size} skills · ${it.systemMemories.size} memories"
            },
            onClick = actions.onSelectSystem,
        )
        Row(
            modifier =
            Modifier.padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Projects",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.projectsLoading) {
                CircularProgressIndicator(
                    modifier =
                    Modifier
                        .height(12.dp)
                        .width(12.dp),
                    strokeWidth = 1.5.dp,
                )
            }
        }
        ScrollableLazyColumn(modifier = Modifier.weight(1f)) {
            if (state.projectsLoading && state.projects.isEmpty()) {
                item {
                    Text(
                        "Scanning…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
            items(state.projects, key = { it.path }) { project ->
                NavRow(
                    label = project.name,
                    selected =
                    (state.selection as? BrowseSelection.Project)?.path == project.path,
                    subtitle = null,
                    onClick = { actions.onSelectProject(project.path) },
                )
            }
        }
        state.snapshot?.warnings?.takeIf { it.isNotEmpty() }?.let { warnings ->
            Text(
                text = "${warnings.size} warning(s)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

@Composable
private fun NavRow(
    label: String,
    selected: Boolean,
    subtitle: String?,
    onClick: () -> Unit,
) {
    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .hoverClickable(
                selected = selected,
                selectedColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                cornerRadius = 0.dp,
                onClick = onClick,
            ).padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MainPane(
    state: InventoryUiState,
    actions: ContextActions,
    modifier: Modifier = Modifier,
) {
    val snapshot = state.snapshot
    if (snapshot == null) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("No data")
        }
        return
    }
    when (state.selection) {
        BrowseSelection.System ->
            SystemContent(
                snapshot = snapshot,
                loading = state.loading,
                actions = actions,
                modifier = modifier,
            )
        is BrowseSelection.Project -> {
            val project = state.selectedProject
            if (project == null) {
                Box(modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                    if (state.projectsLoading || state.projectDetailsLoading) {
                        CircularProgressIndicator()
                    } else {
                        Text("Project not found")
                    }
                }
            } else {
                ProjectContent(
                    project = project,
                    loading = state.loading || state.projectDetailsLoading || !project.detailsLoaded,
                    actions = actions,
                    installedTools = state.installedTools,
                    modifier = modifier,
                )
            }
        }
    }
}

@Composable
private fun SystemContent(
    snapshot: AppSnapshot,
    loading: Boolean,
    actions: ContextActions,
    modifier: Modifier = Modifier,
) {
    ScrollableLazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            DetailToolbar(
                title = "System",
                subtitle = "Skills, memories, and config across installed tools",
                loading = loading,
                onRefresh = actions.onRefresh,
            )
        }
        ToolKind.entries.forEach { kind ->
            val install = snapshot.tools.firstOrNull { it.kind == kind }
            if (install?.installed != true) {
                item {
                    SectionCard(
                        title = kind.displayName,
                        subtitle = "Not installed",
                        tool = kind,
                    ) {
                        Text("No home directory or binary found.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                return@forEach
            }
            val skills = snapshot.systemSkills.filter { it.tool == kind }
            val memories = snapshot.systemMemories.filter { it.tool == kind }
            val agents = snapshot.systemAgentFiles.filter { it.tool == kind }
            val unlinked =
                if (kind == ToolKind.Grok) {
                    snapshot.unlinkedMemories.filter { it.tool == kind }
                } else {
                    emptyList()
                }
            item {
                SectionCard(
                    title = kind.displayName,
                    subtitle = install.homeDir.orEmpty(),
                    tool = kind,
                    onTitleClick = { actions.onOpenTool(kind, null) },
                ) {
                    // User/Bundled labels are the skills section chrome; Add sits on the User row.
                    SkillOriginGroups(
                        skills = skills,
                        onOpen = actions.onOpenSkill,
                        onAddUser = { actions.onShowCreate(CreateKind.Skill, null, listOf(kind)) },
                    )
                    SectionHeaderWithAdd(
                        title = "Memories (${memories.size})",
                        onAdd = {
                            actions.onShowCreate(CreateKind.Memory, null, listOf(kind))
                        },
                    )
                    if (memories.isEmpty()) {
                        EmptyHint("No global memories")
                    } else {
                        memories.forEach { mem ->
                            MemoryRow(mem, actions.onOpenMemory)
                        }
                    }
                    if (unlinked.isNotEmpty()) {
                        SubHeader("Unlinked project memories (${unlinked.size})")
                        unlinked.forEach { mem ->
                            MemoryRow(mem, actions.onOpenMemory)
                        }
                    }
                    SubHeader("Config / agents (${agents.size})")
                    if (agents.isEmpty()) {
                        EmptyHint("None")
                    } else {
                        agents.forEach { file ->
                            AgentRow(file, actions.onOpenFile)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectContent(
    project: ProjectInventory,
    loading: Boolean,
    actions: ContextActions,
    installedTools: List<ToolKind>,
    modifier: Modifier = Modifier,
) {
    val toolsForCreate =
        (project.toolsPresent.toList() + installedTools)
            .distinct()
            .sortedBy { it.ordinal }
            .ifEmpty { ToolKind.entries }

    ScrollableLazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            DetailToolbar(
                title = project.name,
                subtitle = project.path,
                loading = loading,
                onRefresh = actions.onRefresh,
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                project.toolsPresent.sortedBy { it.ordinal }.forEach { kind ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = RoundedCornerShape(12.dp),
                        modifier =
                        Modifier.hoverClickable(
                            cornerRadius = 12.dp,
                            onClick = { actions.onOpenTool(kind, project.path) },
                        ),
                    ) {
                        Text(
                            kind.displayName,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
        if (!project.detailsLoaded || loading) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(18.dp).width(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(
                        if (project.detailsLoaded) "Refreshing…" else "Loading project details…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            SectionCard(title = "Agent files", subtitle = "${project.agentFiles.size}") {
                if (project.agentFiles.isEmpty()) {
                    EmptyHint(
                        if (!project.detailsLoaded) "…" else "No AGENTS.md / CLAUDE.md",
                    )
                } else {
                    project.agentFiles.forEach { AgentRow(it, actions.onOpenFile) }
                }
            }
        }
        item {
            PlainCard {
                SectionHeaderWithAdd(
                    title =
                    if (project.detailsLoaded) {
                        "Skills (${project.skills.size})"
                    } else {
                        "Skills"
                    },
                    onAdd = {
                        actions.onShowCreate(CreateKind.Skill, project.path, toolsForCreate)
                    },
                )
                if (project.skills.isEmpty()) {
                    EmptyHint(if (!project.detailsLoaded) "…" else "No project skills")
                } else {
                    project.skills.forEach { SkillRow(it, actions.onOpenSkill) }
                }
            }
        }
        item {
            PlainCard {
                SectionHeaderWithAdd(
                    title =
                    if (project.detailsLoaded) {
                        "Memories (${project.memories.size})"
                    } else {
                        "Memories"
                    },
                    onAdd = {
                        actions.onShowCreate(CreateKind.Memory, project.path, toolsForCreate)
                    },
                )
                if (project.memories.isEmpty()) {
                    EmptyHint(if (!project.detailsLoaded) "…" else "No project memories found")
                } else {
                    project.memories.forEach { MemoryRow(it, actions.onOpenMemory) }
                }
            }
        }
    }
}

/**
 * System skills split by origin. Always shows a **User** row (with Add) so tools
 * that only have user skills (Claude, OpenCode) still get a clear section label.
 * **Bundled** appears when present (Grok).
 */
@Composable
private fun SkillOriginGroups(
    skills: List<SkillItem>,
    onOpen: (SkillItem) -> Unit,
    onAddUser: () -> Unit,
) {
    val user = skills.filter { it.origin == SkillOrigin.User }
    val bundled = skills.filter { it.origin == SkillOrigin.Bundled }
    val other = skills.filter { it.origin != SkillOrigin.User && it.origin != SkillOrigin.Bundled }

    SectionHeaderWithAdd(
        title = "User (${user.size})",
        onAdd = onAddUser,
    )
    if (user.isEmpty() && bundled.isEmpty() && other.isEmpty()) {
        EmptyHint("No system skills")
    } else {
        user.forEach { SkillRow(it, onOpen, muted = false) }
    }
    if (bundled.isNotEmpty()) {
        SubHeader("Bundled (${bundled.size})")
        bundled.forEach { SkillRow(it, onOpen, muted = true) }
    }
    other.forEach { SkillRow(it, onOpen, muted = false) }
}

@Composable
private fun SkillRow(
    skill: SkillItem,
    onOpen: (SkillItem) -> Unit,
    muted: Boolean = skill.origin == SkillOrigin.Bundled,
) {
    val titleColor =
        if (muted) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    val secondaryColor =
        if (muted) {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .hoverClickable(onClick = { onOpen(skill) })
            .padding(vertical = 6.dp, horizontal = 4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            ToolIcon(tool = skill.tool, size = 14.dp)
            Text(
                skill.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (muted) FontWeight.Normal else FontWeight.Medium,
                color = titleColor,
            )
            OriginBadge(skill.origin)
        }
        skill.description?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = secondaryColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            skill.symlinkTarget?.let { "→ $it" } ?: skill.path,
            style = MaterialTheme.typography.labelSmall,
            color = secondaryColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MemoryRow(
    memory: MemoryItem,
    onOpen: (MemoryItem) -> Unit,
) {
    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .hoverClickable(onClick = { onOpen(memory) })
            .padding(vertical = 6.dp, horizontal = 4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            ToolIcon(tool = memory.tool, size = 14.dp)
            Text(memory.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
        Text(
            memory.path,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AgentRow(
    file: AgentFileItem,
    onOpenFile: (path: String, title: String) -> Unit,
) {
    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .hoverClickable(onClick = { onOpenFile(file.path, file.name) })
            .padding(vertical = 6.dp, horizontal = 4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            file.tool?.let { ToolIcon(tool = it, size = 14.dp) }
            Text(file.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
        Text(
            file.path,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun OriginBadge(origin: SkillOrigin) {
    val label =
        when (origin) {
            SkillOrigin.User -> "user"
            SkillOrigin.Bundled -> "bundled"
            SkillOrigin.Project -> "project"
        }
    if (origin == SkillOrigin.Bundled) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(8.dp),
            border =
            BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
            ),
        ) {
            Text(
                label,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
            )
        }
    } else {
        Badge(label)
    }
}

@Composable
private fun EditorPane(
    preview: FilePreview?,
    draft: String,
    loading: Boolean,
    saving: Boolean,
    dirty: Boolean,
    actions: ContextActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    preview?.title ?: "Editor",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                preview?.path?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (preview?.readOnly == true) {
                Text(
                    "read-only",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp),
                )
            } else {
                if (preview?.canDelete == true) {
                    TextButton(
                        onClick = actions.onRequestDeleteFromEditor,
                        enabled = !loading && !saving,
                        modifier = Modifier.hoverHand(),
                    ) {
                        Text("Delete")
                    }
                }
                TextButton(
                    onClick = actions.onSave,
                    enabled = dirty && !loading && !saving && preview != null,
                    modifier = Modifier.hoverHand(),
                ) {
                    Text(if (saving) "Saving…" else "Save")
                }
            }
            TextButton(
                onClick = actions.onClosePreview,
                modifier = Modifier.hoverHand(),
            ) {
                Text("Close")
            }
        }
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (preview != null) {
            OutlinedTextField(
                value = draft,
                onValueChange = actions.onDraftChange,
                readOnly = preview.readOnly,
                modifier =
                Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
        }
    }
}

@Composable
private fun CreateDialog(
    request: CreateRequest,
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String, tool: ToolKind) -> Unit,
) {
    var name by remember(request) { mutableStateOf("") }
    var description by remember(request) { mutableStateOf("") }
    var tool by remember(request) {
        mutableStateOf(request.availableTools.firstOrNull() ?: ToolKind.Grok)
    }
    val kindLabel = if (request.kind == CreateKind.Skill) "skill" else "memory"
    val scopeLabel = if (request.projectPath != null) "project" else "system"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New $kindLabel ($scopeLabel)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("CLI tool", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    request.availableTools.forEach { option ->
                        FilterChip(
                            selected = tool == option,
                            onClick = { tool = option },
                            label = { Text(option.displayName) },
                            leadingIcon = {
                                ToolIcon(tool = option, size = 14.dp)
                            },
                            modifier = Modifier.hoverHand(),
                        )
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (request.kind == CreateKind.Skill) {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, description, tool) },
                enabled = name.isNotBlank(),
                modifier = Modifier.hoverHand(),
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.hoverHand(),
            ) {
                Text("Cancel")
            }
        },
    )
}
