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

    /**
     * Whether the CLI loads a `rules/` directory of always-on instruction files.
     * OpenCode and Codex only read the AGENTS.md chain, so a rules folder there
     * would be a file nothing reads.
     */
    val supportsRules: Boolean
        get() =
            when (this) {
                Claude, Grok, Cursor, Gemini -> true
                OpenCode, Codex -> false
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

/**
 * One always-on instruction file from a tool's `rules/` directory
 * (`.claude/rules`, `.grok/rules`, `.cursor/rules`, `.agent/rules`, …).
 */
data class RuleItem(
    /** Path relative to the rules root, without extension, e.g. `frontend/react`. */
    val name: String,
    val path: String,
    val tool: ToolKind,
    val description: String? = null,
    /**
     * Globs from `paths:` (Claude, Grok) or `globs:` (Cursor) frontmatter.
     * Empty means the rule is loaded for every session.
     */
    val globs: List<String> = emptyList(),
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
    val rules: List<RuleItem> = emptyList(),
    /**
     * False for lightweight discovery stubs (path/name/markers only).
     * True after a full scan of skills, agent files, memories, and rules.
     */
    val detailsLoaded: Boolean = true,
)

data class AppSnapshot(
    val tools: List<ToolInstall>,
    val systemSkills: List<SkillItem>,
    val systemMemories: List<MemoryItem>,
    val systemAgentFiles: List<AgentFileItem>,
    val systemRules: List<RuleItem> = emptyList(),
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
    Rule,
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
