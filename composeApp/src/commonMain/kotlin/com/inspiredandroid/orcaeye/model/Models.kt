package com.inspiredandroid.orcaeye.model

enum class ToolKind {
    Claude,
    Grok,
    OpenCode,
    Codex,
    Cursor,
    Gemini,
    ;

    val displayName: String
        get() =
            when (this) {
                Claude -> "Claude"
                Grok -> "Grok"
                OpenCode -> "OpenCode"
                Codex -> "Codex"
                Cursor -> "Cursor"
                Gemini -> "Gemini"
            }
}

enum class SkillOrigin {
    User,
    Bundled,
    Project,
}

data class ToolInstall(
    val kind: ToolKind,
    val installed: Boolean,
    val binaryPath: String?,
    val homeDir: String?,
    val version: String? = null,
)

data class SkillItem(
    val name: String,
    val path: String,
    val tool: ToolKind,
    val origin: SkillOrigin,
    val symlinkTarget: String? = null,
    val description: String? = null,
    val skillMdPath: String? = null,
)

data class MemoryItem(
    val title: String,
    val path: String,
    val tool: ToolKind,
    val projectPath: String? = null,
)

data class AgentFileItem(
    val name: String,
    val path: String,
    val tool: ToolKind?,
)

data class ProjectInventory(
    val path: String,
    val name: String,
    val toolsPresent: Set<ToolKind>,
    val agentFiles: List<AgentFileItem>,
    val skills: List<SkillItem>,
    val memories: List<MemoryItem>,
    /**
     * False for lightweight discovery stubs (path/name/markers only).
     * True after a full scan of skills, agent files, and memories.
     */
    val detailsLoaded: Boolean = true,
)

data class AppSnapshot(
    val tools: List<ToolInstall>,
    val systemSkills: List<SkillItem>,
    val systemMemories: List<MemoryItem>,
    val systemAgentFiles: List<AgentFileItem>,
    val projects: List<ProjectInventory>,
    val unlinkedMemories: List<MemoryItem> = emptyList(),
    val warnings: List<String> = emptyList(),
)

sealed interface BrowseSelection {
    data object System : BrowseSelection

    data class Project(
        val path: String,
    ) : BrowseSelection
}

data class FilePreview(
    val path: String,
    val title: String,
    val content: String,
    /** Directory to delete for a skill; null means delete [path] only. */
    val deletePath: String? = null,
    val canDelete: Boolean = true,
    val isSkill: Boolean = false,
    /** Log files and other generated output are shown without Save/Delete. */
    val readOnly: Boolean = false,
)

enum class CreateKind {
    Skill,
    Memory,
}

/** Top-level app area: current inventory (Context) vs future Loops. */
enum class AppSection {
    Context,
    Loops,
}

data class CreateRequest(
    val kind: CreateKind,
    val projectPath: String?,
    val availableTools: List<ToolKind>,
)
