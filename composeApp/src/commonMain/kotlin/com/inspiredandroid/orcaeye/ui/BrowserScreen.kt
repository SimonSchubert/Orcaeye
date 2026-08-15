package com.inspiredandroid.orcaeye.ui

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
import com.inspiredandroid.orcaeye.model.RuleItem
import com.inspiredandroid.orcaeye.model.SkillItem
import com.inspiredandroid.orcaeye.model.SkillOrigin
import com.inspiredandroid.orcaeye.model.ToolKind

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
        ConfirmDeleteDialog(
            title = "Delete?",
            message = "Delete \"${state.deleteConfirmTitle}\"? This cannot be undone.",
            onConfirm = contextActions.onConfirmDelete,
            onCancel = contextActions.onCancelDelete,
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
        state.loading && state.snapshot == null -> LoadingBox(Modifier.fillMaxSize())
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
                "${it.systemSkills.size} skills · ${it.systemMemories.size} memories · " +
                    "${it.systemRules.size} rules"
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
                InlineSpinner(size = 12.dp, strokeWidth = 1.5.dp)
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
                if (state.projectsLoading || state.projectDetailsLoading) {
                    LoadingBox(modifier.padding(16.dp))
                } else {
                    Box(modifier.padding(16.dp), contentAlignment = Alignment.Center) {
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
                subtitle = "Skills, memories, rules, and config across installed tools",
                loading = loading,
                onRefresh = actions.onRefresh,
            )
        }
        val installed = ToolKind.entries.filter { kind -> snapshot.tools.any { it.kind == kind && it.installed } }
        installed.forEach { kind ->
            val install = snapshot.tools.first { it.kind == kind }
            val skills = snapshot.systemSkills.filter { it.tool == kind }
            val memories = snapshot.systemMemories.filter { it.tool == kind }
            val agents = snapshot.systemAgentFiles.filter { it.tool == kind }
            val rules = snapshot.systemRules.filter { it.tool == kind }
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
                    trailing = { AddMenu(createOptions(actions, kind, projectPath = null, tools = listOf(kind))) },
                ) {
                    if (skills.isEmpty() &&
                        memories.isEmpty() &&
                        unlinked.isEmpty() &&
                        rules.isEmpty() &&
                        agents.isEmpty()
                    ) {
                        EmptyHint("Nothing set up yet")
                    }
                    SkillOriginGroups(skills = skills, onOpen = actions.onOpenSkill)
                    if (memories.isNotEmpty()) {
                        SubHeader("Memories (${memories.size})")
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
                    if (rules.isNotEmpty()) {
                        SubHeader("Rules (${rules.size})")
                        rules.forEach { rule ->
                            RuleRow(rule, actions.onOpenRule)
                        }
                    }
                    if (agents.isNotEmpty()) {
                        SubHeader("Config / agents (${agents.size})")
                        agents.forEach { file ->
                            AgentRow(file, actions.onOpenFile)
                        }
                    }
                }
            }
        }
        // One line rather than a card each: the remaining tools are supported, just absent.
        val missing = ToolKind.entries - installed.toSet()
        if (missing.isNotEmpty()) {
            item {
                EmptyHint("Not installed: ${missing.joinToString(" · ") { it.displayName }}")
            }
        }
    }
}

/** Create entries offered for a scope; rules are dropped for tools that never read them. */
private fun createOptions(
    actions: ContextActions,
    kind: ToolKind?,
    projectPath: String?,
    tools: List<ToolKind>,
): List<Pair<String, () -> Unit>> = buildList {
    add("Skill" to { actions.onShowCreate(CreateKind.Skill, projectPath, tools) })
    if (kind?.supportsRules ?: tools.any { it.supportsRules }) {
        add("Rule" to { actions.onShowCreate(CreateKind.Rule, projectPath, tools) })
    }
    add("Memory" to { actions.onShowCreate(CreateKind.Memory, projectPath, tools) })
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
                actions = {
                    AddMenu(
                        createOptions(
                            actions = actions,
                            kind = null,
                            projectPath = project.path,
                            tools = toolsForCreate,
                        ),
                    )
                },
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
                    InlineSpinner()
                    Text(
                        if (project.detailsLoaded) "Refreshing…" else "Loading project details…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (project.agentFiles.isNotEmpty()) {
            item {
                PlainCard {
                    SubHeader("Agent files (${project.agentFiles.size})")
                    project.agentFiles.forEach { AgentRow(it, actions.onOpenFile) }
                }
            }
        }
        if (project.skills.isNotEmpty()) {
            item {
                PlainCard {
                    SubHeader("Skills (${project.skills.size})")
                    project.skills.forEach { SkillRow(it, actions.onOpenSkill) }
                }
            }
        }
        if (project.rules.isNotEmpty()) {
            item {
                PlainCard {
                    SubHeader("Rules (${project.rules.size})")
                    project.rules.forEach { RuleRow(it, actions.onOpenRule) }
                }
            }
        }
        if (project.memories.isNotEmpty()) {
            item {
                PlainCard {
                    SubHeader("Memories (${project.memories.size})")
                    project.memories.forEach { MemoryRow(it, actions.onOpenMemory) }
                }
            }
        }
        val empty =
            project.agentFiles.isEmpty() &&
                project.skills.isEmpty() &&
                project.rules.isEmpty() &&
                project.memories.isEmpty()
        if (empty && project.detailsLoaded && !loading) {
            item {
                MessageCard(
                    title = "Nothing set up yet",
                    message = "Use New to add a skill, rule or memory for this project.",
                )
            }
        }
    }
}

/**
 * System skills split by origin: **User** for the ones you wrote, **Bundled** for the
 * copies a CLI ships with (Grok, Cursor). Groups without members are left out.
 */
@Composable
private fun SkillOriginGroups(
    skills: List<SkillItem>,
    onOpen: (SkillItem) -> Unit,
) {
    val user = skills.filter { it.origin == SkillOrigin.User }
    val bundled = skills.filter { it.origin == SkillOrigin.Bundled }
    val other = skills.filter { it.origin != SkillOrigin.User && it.origin != SkillOrigin.Bundled }

    if (user.isNotEmpty()) {
        SubHeader("Skills (${user.size})")
        user.forEach { SkillRow(it, onOpen, muted = false) }
    }
    if (bundled.isNotEmpty()) {
        SubHeader("Bundled skills (${bundled.size})")
        bundled.forEach { SkillRow(it, onOpen, muted = true) }
    }
    if (other.isNotEmpty()) {
        SubHeader("Other skills (${other.size})")
        other.forEach { SkillRow(it, onOpen, muted = false) }
    }
}

@Composable
private fun SkillRow(
    skill: SkillItem,
    onOpen: (SkillItem) -> Unit,
    muted: Boolean = skill.origin == SkillOrigin.Bundled,
) {
    ItemRow(
        title = skill.name,
        details = listOf(skill.symlinkTarget?.let { "→ $it" } ?: skill.path),
        tool = skill.tool,
        description = skill.description,
        muted = muted,
        badge = { OriginBadge(skill.origin) },
        onClick = { onOpen(skill) },
    )
}

@Composable
private fun MemoryRow(
    memory: MemoryItem,
    onOpen: (MemoryItem) -> Unit,
) {
    ItemRow(
        title = memory.title,
        details = listOf(memory.path),
        tool = memory.tool,
        onClick = { onOpen(memory) },
    )
}

@Composable
private fun RuleRow(
    rule: RuleItem,
    onOpen: (RuleItem) -> Unit,
) {
    ItemRow(
        title = rule.name,
        details = listOfNotNull(rule.globs.joinToString(", ").takeIf { rule.globs.isNotEmpty() }, rule.path),
        tool = rule.tool,
        description = rule.description,
        // A rule with globs only loads for matching files; the rest are on in every session.
        badge = { Badge(if (rule.globs.isEmpty()) "always" else "scoped") },
        onClick = { onOpen(rule) },
    )
}

@Composable
private fun AgentRow(
    file: AgentFileItem,
    onOpenFile: (path: String, title: String) -> Unit,
) {
    ItemRow(
        title = file.name,
        details = listOf(file.path),
        tool = file.tool,
        onClick = { onOpenFile(file.path, file.name) },
    )
}

@Composable
private fun OriginBadge(origin: SkillOrigin) {
    val label =
        when (origin) {
            SkillOrigin.User -> "user"
            SkillOrigin.Bundled -> "bundled"
            SkillOrigin.Project -> "project"
        }
    // The copies a CLI ships with sit one step back from the ones the user wrote.
    Badge(label, outlined = origin == SkillOrigin.Bundled)
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
            LoadingBox(Modifier.fillMaxSize())
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
    val kindLabel =
        when (request.kind) {
            CreateKind.Skill -> "skill"
            CreateKind.Memory -> "memory"
            CreateKind.Rule -> "rule"
        }
    val scopeLabel = if (request.projectPath != null) "project" else "system"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New $kindLabel ($scopeLabel)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ToolPicker(
                    label = "CLI tool",
                    tools = request.availableTools,
                    selected = tool,
                    onSelect = { tool = it },
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (request.kind != CreateKind.Memory) {
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
