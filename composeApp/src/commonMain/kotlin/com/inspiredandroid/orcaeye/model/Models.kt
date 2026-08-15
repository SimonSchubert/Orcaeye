package com.inspiredandroid.orcaeye.model

/**
 * The agent CLIs Orcaeye knows about, each declaring its own facts so nothing has to be
 * kept in step across a stack of `when (tool)` blocks. Where a tool keeps its files on
 * disk is the one thing that lives elsewhere — see `ToolLayout` on the desktop side.
 *
 * @param cliName the binary the CLI installs as, and the name a crontab line is recognised by.
 * @param supportsRules whether the CLI loads a `rules/` directory of always-on instruction
 * files. OpenCode and Codex only read the AGENTS.md chain, so a rules folder there would be
 * a file nothing reads.
 */
enum class ToolKind(
    val cliName: String,
    val supportsRules: Boolean,
) {
    Claude(cliName = "claude", supportsRules = true),
    Grok(cliName = "grok", supportsRules = true),
    OpenCode(cliName = "opencode", supportsRules = false),
    Codex(cliName = "codex", supportsRules = false),

    // "cursor-agent", never the bare "agent": that name collides with other CLIs.
    Cursor(cliName = "cursor-agent", supportsRules = true),
    Gemini(cliName = "gemini", supportsRules = true),
    ;

    /** The enum names are already the product names, so there is no second list to maintain. */
    val displayName: String get() = name

    companion object {
        fun fromCliName(cliName: String): ToolKind? = entries.firstOrNull { it.cliName == cliName }
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
