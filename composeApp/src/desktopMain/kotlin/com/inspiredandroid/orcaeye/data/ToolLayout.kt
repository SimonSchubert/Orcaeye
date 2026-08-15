package com.inspiredandroid.orcaeye.data

import com.inspiredandroid.orcaeye.model.SkillOrigin
import com.inspiredandroid.orcaeye.model.ToolKind
import java.nio.file.Path

/**
 * Where one CLI keeps its files.
 *
 * Every directory Orcaeye reads or writes for a tool is declared here once, so detection,
 * scanning and creation cannot drift apart: supporting a new CLI is a row in [LAYOUTS] plus
 * an entry in [ToolKind], not a new branch in a dozen `when (tool)` blocks.
 *
 * Paths are relative — to the user's home for the `system*` fields, to a project directory
 * for the `project*` ones.
 */
data class ToolLayout(
    val kind: ToolKind,
    /** Config directories under home; the first that exists is reported as the tool's home. */
    val homeDirs: List<String>,
    /** Project directories whose presence means the tool is set up in that project. */
    val projectMarkerDirs: List<String>,
    /**
     * System skill roots in display order. A [SkillOrigin.Bundled] root is the copy a CLI
     * ships with, and a user skill of the same name hides it.
     */
    val systemSkillRoots: List<SkillRoot>,
    /** Project skill directories; the first is where a new skill is written. */
    val projectSkillDirs: List<String>,
    /** Directory of loose `.md` memories under home. */
    val systemMemoryDir: String,
    /** Project memory directory, or null when the tool files project memories under home. */
    val projectMemoryDir: String?,
    /** Rules roots under home, most canonical first (the first is where a new rule lands). */
    val systemRuleRoots: List<String> = emptyList(),
    /** Rules roots inside a project, most canonical first. */
    val projectRuleRoots: List<String> = emptyList(),
    /** Config files under home, most canonical first; the first that exists is listed. */
    val configFiles: List<String> = emptyList(),
    /** Directory of markdown agent definitions the CLI ships with. */
    val bundledAgentsDir: String? = null,
    /** Project root files that belong to this tool. */
    val projectAgentFiles: List<String> = emptyList(),
    /** Extra files under home that prove an install even without a config directory. */
    val installMarkers: List<String> = emptyList(),
    /** Directory the CLI installs its own binary into, relative to home. */
    val homeBinDir: String? = null,
) {
    init {
        // The UI hides the "New rule" entry from ToolKind.supportsRules, while the scan and the
        // create path work off these roots; a disagreement would offer a file nothing reads.
        require(systemRuleRoots.isNotEmpty() == kind.supportsRules) {
            "${kind.displayName}: supportsRules=${kind.supportsRules} but systemRuleRoots=$systemRuleRoots"
        }
    }

    /** Directory a newly created skill goes in: inside the project, or under home. */
    fun skillsDir(
        home: Path,
        projectPath: String?,
    ): Path = if (projectPath != null) {
        Path.of(projectPath).resolve(projectSkillDirs.first())
    } else {
        home.resolve(systemSkillRoots.first { it.origin == SkillOrigin.User }.dir)
    }

    /** Rules roots for a scope, most canonical first. Empty for tools that read no rules. */
    fun ruleRoots(
        home: Path,
        projectPath: String?,
    ): List<Path> {
        val base = projectPath?.let { Path.of(it) } ?: home
        val roots = if (projectPath != null) projectRuleRoots else systemRuleRoots
        return roots.map { base.resolve(it) }
    }

    /** Binary locations to try, in order: the CLI's own bin dir, then the usual PATH homes. */
    fun binaryCandidates(home: Path): List<Path> = buildList {
        homeBinDir?.let { add(home.resolve(it).resolve(kind.cliName)) }
        add(home.resolve(LOCAL_BIN_DIR).resolve(kind.cliName))
        SHARED_BIN_DIRS.forEach { add(Path.of(it).resolve(kind.cliName)) }
    }

    data class SkillRoot(
        val dir: String,
        val origin: SkillOrigin,
    )

    companion object {
        private const val LOCAL_BIN_DIR = ".local/bin"
        private val SHARED_BIN_DIRS = listOf("/opt/homebrew/bin", "/usr/local/bin")

        private fun user(dir: String) = SkillRoot(dir, SkillOrigin.User)

        private fun bundled(dir: String) = SkillRoot(dir, SkillOrigin.Bundled)

        val LAYOUTS: Map<ToolKind, ToolLayout> =
            listOf(
                ToolLayout(
                    kind = ToolKind.Claude,
                    homeDirs = listOf(".claude"),
                    projectMarkerDirs = listOf(".claude"),
                    systemSkillRoots = listOf(user(".claude/skills")),
                    projectSkillDirs = listOf(".claude/skills"),
                    systemMemoryDir = ".claude/memory",
                    // Claude files project memories under ~/.claude/projects/<encoded path>.
                    projectMemoryDir = null,
                    systemRuleRoots = listOf(".claude/rules"),
                    projectRuleRoots = listOf(".claude/rules"),
                    configFiles = listOf(".claude/settings.json"),
                    projectAgentFiles = listOf("CLAUDE.md"),
                    installMarkers = listOf(".claude.json"),
                ),
                ToolLayout(
                    kind = ToolKind.Grok,
                    homeDirs = listOf(".grok"),
                    projectMarkerDirs = listOf(".grok"),
                    systemSkillRoots = listOf(user(".grok/skills"), bundled(".grok/bundled/skills")),
                    projectSkillDirs = listOf(".grok/skills"),
                    systemMemoryDir = ".grok/memory",
                    // Grok files project memories under ~/.grok/memory/<project slug>.
                    projectMemoryDir = null,
                    // Grok also reads .claude/rules and .cursor/rules for compatibility, but each
                    // rule is listed under the tool that owns its directory, so it appears once.
                    systemRuleRoots = listOf(".grok/rules"),
                    projectRuleRoots = listOf(".grok/rules"),
                    configFiles = listOf(".grok/config.toml"),
                    bundledAgentsDir = ".grok/bundled/agents",
                    projectAgentFiles = listOf("AGENTS.md"),
                    homeBinDir = ".grok/bin",
                ),
                ToolLayout(
                    kind = ToolKind.OpenCode,
                    homeDirs = listOf(".opencode", ".config/opencode"),
                    projectMarkerDirs = listOf(".opencode"),
                    systemSkillRoots = listOf(user(".opencode/skills"), user(".config/opencode/skills")),
                    projectSkillDirs = listOf(".opencode/skills"),
                    systemMemoryDir = ".opencode/memory",
                    projectMemoryDir = ".opencode/memory",
                    configFiles = listOf(".config/opencode/opencode.json", ".config/opencode/opencode.jsonc"),
                    projectAgentFiles = listOf("opencode.json", ".opencode.json"),
                    homeBinDir = ".opencode/bin",
                ),
                ToolLayout(
                    kind = ToolKind.Codex,
                    homeDirs = listOf(".codex"),
                    projectMarkerDirs = listOf(".codex", ".agents"),
                    systemSkillRoots = listOf(user(".codex/skills")),
                    projectSkillDirs = listOf(".agents/skills", ".codex/skills"),
                    systemMemoryDir = ".codex/memory",
                    projectMemoryDir = ".agents/memory",
                    configFiles = listOf(".codex/config.toml", ".codex/config.json"),
                    homeBinDir = ".codex/bin",
                ),
                ToolLayout(
                    kind = ToolKind.Cursor,
                    homeDirs = listOf(".cursor"),
                    projectMarkerDirs = listOf(".cursor"),
                    systemSkillRoots = listOf(user(".cursor/skills"), bundled(".cursor/skills-cursor")),
                    projectSkillDirs = listOf(".cursor/skills"),
                    systemMemoryDir = ".cursor/memory",
                    projectMemoryDir = ".cursor/memory",
                    systemRuleRoots = listOf(".cursor/rules"),
                    projectRuleRoots = listOf(".cursor/rules"),
                    configFiles = listOf(".cursor/cli-config.json", ".cursor/argv.json"),
                    // Cursor's pre-.cursor/rules format: a single root file, not a rules dir.
                    projectAgentFiles = listOf(".cursorrules"),
                    homeBinDir = ".cursor/bin",
                ),
                ToolLayout(
                    kind = ToolKind.Gemini,
                    // Antigravity shares the Gemini CLI's files but keeps workspace ones in .agent.
                    homeDirs = listOf(".gemini"),
                    projectMarkerDirs = listOf(".gemini", ".agent"),
                    systemSkillRoots = listOf(user(".gemini/skills"), user(".gemini/antigravity/skills")),
                    projectSkillDirs = listOf(".gemini/skills", ".agent/skills"),
                    systemMemoryDir = ".gemini/memory",
                    projectMemoryDir = ".gemini/memory",
                    systemRuleRoots = listOf(".gemini/rules", ".gemini/antigravity/rules"),
                    projectRuleRoots = listOf(".agent/rules", ".gemini/rules"),
                    configFiles = listOf(".gemini/settings.json", ".gemini/config.json"),
                    projectAgentFiles = listOf("GEMINI.md"),
                    homeBinDir = ".gemini/bin",
                ),
            ).associateBy { it.kind }

        /** Anything whose presence in a directory makes it worth listing as a project. */
        val PROJECT_MARKERS: List<String> =
            LAYOUTS.values.flatMap { it.projectMarkerDirs + it.projectAgentFiles }.distinct()
    }
}

val ToolKind.layout: ToolLayout get() = ToolLayout.LAYOUTS.getValue(this)
